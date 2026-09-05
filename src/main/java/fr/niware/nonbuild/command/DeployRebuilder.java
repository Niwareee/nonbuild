package fr.niware.nonbuild.command;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import fr.niware.nonbuild.Msg;
import fr.niware.nonbuild.NonBuild;
import fr.niware.nonbuild.model.DeployedInstance;
import fr.niware.nonbuild.schematic.SpongeSchematic;
import fr.niware.nonbuild.work.BlockEraser;
import fr.niware.nonbuild.work.BlockPaster;
import fr.niware.nonbuild.world.VoidChunkGenerator;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.TypedKey;
import io.papermc.paper.registry.keys.GameRuleKeys;

/**
 * Gestion du rebuild complet du monde de production et des opérations
 * de suppression d'instances.
 */
class DeployRebuilder {

    private static final String SPAWN_SCHEMATIC = "spawn.schem";
    private static final int SPAWN_PASTE_Y = 90;

    private final NonBuild plugin;
    private final DeployPlanner planner;
    private final DeployCommand deployCommand;

    DeployRebuilder(NonBuild plugin, DeployPlanner planner, DeployCommand deployCommand) {
        this.plugin = plugin;
        this.planner = planner;
        this.deployCommand = deployCommand;
    }

    /** Entrée d'un rebuild : slug d'arène + nombre d'instances à redéployer. */
    private record RebuildEntry(String slug, int count) {
    }

    /**
     * Reconstruction complète du monde de production : suppression du monde,
     * recréation en void, collage du spawn (spawn.schem), vidage du registre
     * puis redéploiement séquentiel de toutes les instances qui y figuraient.
     */
    boolean handleRebuild(CommandSender sender) {
        if (deployCommand.isDeploying()) {
            Msg.error(sender, "Un déploiement est déjà en cours, patientez.");
            return true;
        }

        String prodName = plugin.getSettings().prodWorld();

        Map<String, Integer> counts = new LinkedHashMap<>();
        for (DeployedInstance instance : plugin.getDeployments().all()) {
            counts.merge(instance.getArena(), 1, Integer::sum);
        }
        List<RebuildEntry> entries = new ArrayList<>(counts.size());
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            entries.add(new RebuildEntry(entry.getKey(), entry.getValue()));
        }
        for (RebuildEntry entry : entries) {
            String slug = entry.slug();
            if (plugin.getArenas().get(slug) == null) {
                Msg.error(sender, "Rebuild refusé : l'arène <yellow>" + slug + "<red> (dans deployments.yml) n'existe plus côté build.");
                return true;
            }
            try {
                if (!plugin.getArenas().hasSchematic(slug)) {
                    Msg.error(sender, "Rebuild refusé : schematic manquante pour <yellow>" + slug + "<red>. Refaites /build edit " + slug + " puis /build save.");
                    return true;
                }
            } catch (SQLException e) {
                Msg.error(sender, "Rebuild refusé : erreur de vérification de la schematic pour <yellow>" + slug);
                return true;
            }
        }

        try {
            if (!plugin.getArenas().hasSpawnSchematic()) {
                Msg.error(sender, "Rebuild refusé : spawn schematic manquante en base. Importez-la avec /build importspawn ou via le script de migration.");
                return true;
            }
        } catch (SQLException e) {
            Msg.error(sender, "Rebuild refusé : erreur de vérification de la spawn schematic : " + e.getMessage());
            return true;
        }

        World prod = Bukkit.getWorld(prodName);
        if (prod != null && !prod.getPlayers().isEmpty() && evacuationDestination(prod) == null) {
            Msg.error(sender, "Rebuild refusé : des joueurs sont dans le monde de production et aucun autre monde n'est chargé.");
            return true;
        }

        deployCommand.setDeploying(true);
        long startedAt = System.currentTimeMillis();
        Msg.info(sender, "Rebuild du monde <yellow>" + prodName + "<gray> : chargement des schematics...");
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            final Map<String, SpongeSchematic> arenaSchematics = new LinkedHashMap<>();
            SpongeSchematic loadedSpawn = null;
            String failure = null;
            try {
                loadedSpawn = plugin.getArenas().loadSpawnSchematic();
                if (loadedSpawn == null) {
                    failure = "spawn schematic manquante en base";
                } else {
                    for (RebuildEntry entry : entries) {
                        SpongeSchematic schem = plugin.getArenas().loadSchematic(entry.slug());
                        if (schem == null) {
                            failure = "schematic manquante pour " + entry.slug();
                            break;
                        }
                        arenaSchematics.put(entry.slug(), schem);
                    }
                }
            } catch (IOException | SQLException e) {
                failure = e.getMessage();
            }
            if (failure != null) {
                final String message = failure;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    deployCommand.setDeploying(false);
                    Msg.error(sender, "Rebuild refusé, schematic illisible : " + message);
                });
                return;
            }
            final SpongeSchematic spawn = loadedSpawn;
            Bukkit.getScheduler().runTask(plugin, () ->
                    continueRebuild(sender, spawn, entries, arenaSchematics, prodName, startedAt));
        });
        return true;
    }

    void continueRebuild(CommandSender sender, SpongeSchematic spawn, List<RebuildEntry> entries,
                         Map<String, SpongeSchematic> arenaSchematics, String prodName, long startedAt) {
        World prod = Bukkit.getWorld(prodName);

        if (prod != null && !spawnFits(prod, spawn)) {
            deployCommand.setDeploying(false);
            Msg.error(sender, "Rebuild refusé : le spawn (Y " + spawnMinY(spawn) + " à "
                    + (spawnMinY(spawn) + spawn.getHeight() - 1) + ") ne tient pas entre "
                    + prod.getMinHeight() + " et " + (prod.getMaxHeight() - 1) + " dans le monde " + prodName + ".");
            return;
        }

        if (prod != null) {
            evictPlayersFromProd(sender, prod);
        }

        Msg.info(sender, "Suppression et recréation du monde <yellow>" + prodName + "<gray>...");
        deleteAndRecreateWorld(sender, spawn, entries, arenaSchematics, prodName, prod, startedAt);
    }

    void deleteAndRecreateWorld(CommandSender sender, SpongeSchematic spawn, List<RebuildEntry> entries,
                                Map<String, SpongeSchematic> arenaSchematics, String prodName,
                                World prod, long startedAt) {
        if (Bukkit.isTickingWorlds()) {
            Bukkit.getScheduler().runTask(plugin, () ->
                    deleteAndRecreateWorld(sender, spawn, entries, arenaSchematics, prodName, prod, startedAt));
            return;
        }

        File folder = prod != null ? prod.getWorldFolder() : new File(Bukkit.getWorldContainer(), prodName);

        boolean unloaded = prod == null || Bukkit.unloadWorld(prodName, false);
        boolean isPrimaryWorld = prod != null && !unloaded;

        if (!unloaded && !isPrimaryWorld) {
            deployCommand.setDeploying(false);
            Msg.error(sender, "Impossible de décharger le monde " + prodName + ", rebuild annulé.");
            return;
        }

        if (prod != null && !isPrimaryWorld && !folder.exists()) {
            createAndFillWorld(sender, spawn, entries, arenaSchematics, prodName, startedAt);
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                if (isPrimaryWorld) {
                    cleanWorldFolder(folder.toPath());
                } else {
                    deleteRecursively(folder.toPath());
                }
            } catch (IOException e) {
                String message = e.getMessage();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    deployCommand.setDeploying(false);
                    Msg.error(sender, "Impossible de nettoyer le dossier du monde " + prodName + " : " + message);
                    plugin.getLogger().severe("Rebuild : échec du nettoyage de " + folder + " : " + message);
                });
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (isPrimaryWorld) {
                    rebuildInPlace(sender, spawn, entries, arenaSchematics, prod, startedAt);
                } else {
                    createAndFillWorld(sender, spawn, entries, arenaSchematics, prodName, startedAt);
                }
            });
        });
    }

    void cleanWorldFolder(Path folder) throws IOException {
        for (String sub : new String[]{"region", "entities", "poi"}) {
            deleteRecursively(folder.resolve(sub));
        }
    }

    void rebuildInPlace(CommandSender sender, SpongeSchematic spawn, List<RebuildEntry> entries,
                        Map<String, SpongeSchematic> arenaSchematics, World world, long startedAt) {
        if (!spawnFits(world, spawn)) {
            deployCommand.setDeploying(false);
            Msg.error(sender, "Le spawn (Y " + spawnMinY(spawn) + " à " + (spawnMinY(spawn) + spawn.getHeight() - 1)
                    + ") ne tient pas dans le monde (" + world.getMinHeight() + " / "
                    + (world.getMaxHeight() - 1) + "). Regénérez spawn.schem.");
            return;
        }

        world.setSpawnLocation(new Location(world, 0.5, SPAWN_PASTE_Y, 0.5));
        configureProductionWorld(world);

        int[] offset = spawn.getOffset();
        Msg.info(sender, "Collage du spawn en 0, " + SPAWN_PASTE_Y + ", 0 (" + spawn.volume()
                + " blocs balayés, l'air est ignoré)...");
        BlockPaster paster = new BlockPaster(world,
                offset[0], SPAWN_PASTE_Y + offset[1], offset[2],
                spawn,
                plugin.getSettings().blocksPerTick(),
                true,
                percent -> Msg.info(sender, "Collage du spawn : " + percent + "%"),
                () -> {
                    plugin.getDeployments().clear();
                    Msg.ok(sender, "Monde <white>" + world.getName() + "<green> reconstruit in-place et spawn collé.");
                    if (entries.isEmpty()) {
                        finishRebuild(sender, 0, startedAt);
                    } else {
                        rebuildNextArena(sender, entries, arenaSchematics, world, 0, startedAt);
                    }
                },
                message -> {
                    deployCommand.setDeploying(false);
                    Msg.error(sender, "Erreur pendant le collage du spawn : " + message);
                    plugin.getLogger().severe("Rebuild : erreur de collage du spawn : " + message);
                });
        planner.scheduleAfterPreload(world,
                offset[0], offset[0] + spawn.getWidth() - 1,
                offset[2], offset[2] + spawn.getLength() - 1, paster);
    }

    void createAndFillWorld(CommandSender sender, SpongeSchematic spawn, List<RebuildEntry> entries,
                            Map<String, SpongeSchematic> arenaSchematics, String prodName, long startedAt) {
        if (Bukkit.isTickingWorlds()) {
            Bukkit.getScheduler().runTask(plugin, () ->
                    createAndFillWorld(sender, spawn, entries, arenaSchematics, prodName, startedAt));
            return;
        }

        World newWorld = new WorldCreator(prodName)
                .generator(new VoidChunkGenerator())
                .generateStructures(false)
                .seed(0L)
                .createWorld();
        if (newWorld == null) {
            deployCommand.setDeploying(false);
            Msg.error(sender, "Échec critique : le monde " + prodName + " a été supprimé mais pas recréé. Relancez /deploy rebuild ou redémarrez le serveur.");
            plugin.getLogger().severe("Rebuild : createWorld a retourné null pour " + prodName);
            return;
        }

        if (!spawnFits(newWorld, spawn)) {
            deployCommand.setDeploying(false);
            Msg.error(sender, "Le spawn (Y " + spawnMinY(spawn) + " à " + (spawnMinY(spawn) + spawn.getHeight() - 1)
                    + ") ne tient pas dans le monde recréé (" + newWorld.getMinHeight() + " / "
                    + (newWorld.getMaxHeight() - 1) + "). Regénérez spawn.schem.");
            return;
        }

        newWorld.setSpawnLocation(new Location(newWorld, 0.5, SPAWN_PASTE_Y, 0.5));
        configureProductionWorld(newWorld);

        int[] offset = spawn.getOffset();
        Msg.info(sender, "Collage du spawn en 0, " + SPAWN_PASTE_Y + ", 0 (" + spawn.volume()
                + " blocs balayés, l'air est ignoré)...");
        BlockPaster paster = new BlockPaster(newWorld,
                offset[0], SPAWN_PASTE_Y + offset[1], offset[2],
                spawn,
                plugin.getSettings().blocksPerTick(),
                true,
                percent -> Msg.info(sender, "Collage du spawn : " + percent + "%"),
                () -> {
                    plugin.getDeployments().clear();
                    Msg.ok(sender, "Monde <white>" + prodName + "<green> recréé (void) et spawn collé.");
                    if (entries.isEmpty()) {
                        finishRebuild(sender, 0, startedAt);
                    } else {
                        rebuildNextArena(sender, entries, arenaSchematics, newWorld, 0, startedAt);
                    }
                },
                message -> {
                    deployCommand.setDeploying(false);
                    Msg.error(sender, "Erreur pendant le collage du spawn : " + message);
                    plugin.getLogger().severe("Rebuild : erreur de collage du spawn : " + message);
                });
        planner.scheduleAfterPreload(newWorld,
                offset[0], offset[0] + spawn.getWidth() - 1,
                offset[2], offset[2] + spawn.getLength() - 1, paster);
    }

    void rebuildNextArena(CommandSender sender, List<RebuildEntry> entries,
                          Map<String, SpongeSchematic> arenaSchematics, World world,
                          int index, long startedAt) {
        if (index >= entries.size()) {
            int total = 0;
            for (RebuildEntry entry : entries) {
                total += entry.count();
            }
            finishRebuild(sender, total, startedAt);
            return;
        }

        RebuildEntry entry = entries.get(index);
        planner.startDeployment(sender, plugin.getArenas().get(entry.slug()), arenaSchematics.get(entry.slug()),
                world, entry.count(), startedAt, true,
                () -> rebuildNextArena(sender, entries, arenaSchematics, world, index + 1, startedAt));
    }

    void finishRebuild(CommandSender sender, int totalInstances, long startedAt) {
        deployCommand.setDeploying(false);
        double seconds = (System.currentTimeMillis() - startedAt) / 1000.0;
        Msg.ok(sender, "Rebuild terminé : monde recréé, spawn collé"
                + (totalInstances > 0 ? ", " + totalInstances + " instance(s) redéployée(s)" : "")
                + " en " + String.format(Locale.ROOT, "%.1f", seconds) + " s.");
        Msg.info(sender, "Redémarrez le serveur pour que le plugin practice relise deployments.yml.");
    }

    static int spawnMinY(SpongeSchematic spawn) {
        return SPAWN_PASTE_Y + spawn.getOffset()[1];
    }

    static boolean spawnFits(World world, SpongeSchematic spawn) {
        int minY = spawnMinY(spawn);
        return minY >= world.getMinHeight() && minY + spawn.getHeight() - 1 <= world.getMaxHeight() - 1;
    }

    static void configureProductionWorld(World world) {
        world.setTime(6000);
        world.setDifficulty(Difficulty.NORMAL);
        Registry<GameRule<?>> rules = RegistryAccess.registryAccess().getRegistry(RegistryKey.GAME_RULE);
        gr(world, rules, GameRuleKeys.RANDOM_TICK_SPEED, 0);
        gr(world, rules, GameRuleKeys.ADVANCE_TIME, false);
        gr(world, rules, GameRuleKeys.ADVANCE_WEATHER, false);
        gr(world, rules, GameRuleKeys.MOB_GRIEFING, true);
        gr(world, rules, GameRuleKeys.SPAWN_MOBS, false);
        gr(world, rules, GameRuleKeys.SHOW_ADVANCEMENT_MESSAGES, false);
        gr(world, rules, GameRuleKeys.IMMEDIATE_RESPAWN, true);
        gr(world, rules, GameRuleKeys.NATURAL_HEALTH_REGENERATION, false);
    }

    @SuppressWarnings("unchecked")
    private static <T> void gr(World world, Registry<GameRule<?>> rules, TypedKey<GameRule<?>> key, T value) {
        world.setGameRule((GameRule<T>) rules.get(nk(key)), value);
    }

    private static NamespacedKey nk(TypedKey<?> key) {
        return new NamespacedKey(key.key().namespace(), key.key().value());
    }

    private World evacuationDestination(World prod) {
        World destination = Bukkit.getWorld(plugin.getSettings().buildWorld());
        if (destination != null && destination != prod) {
            return destination;
        }
        for (World world : Bukkit.getWorlds()) {
            if (world != prod) {
                return world;
            }
        }
        return null;
    }

    void evictPlayersFromProd(CommandSender sender, World prod) {
        List<Player> players = new ArrayList<>(prod.getPlayers());
        if (players.isEmpty()) {
            return;
        }
        World destination = evacuationDestination(prod);
        if (destination == null) {
            return;
        }
        Location target = destination.getSpawnLocation();
        for (Player player : players) {
            player.teleport(target);
        }
        Msg.info(sender, players.size() + " joueur(s) évacué(s) vers le monde <yellow>" + destination.getName() + "<gray>.");
    }

    static void deleteRecursively(Path root) throws IOException {
        List<Path> paths;
        try (var stream = Files.walk(root)) {
            paths = stream.sorted(Comparator.reverseOrder()).toList();
        }
        for (Path path : paths) {
            Files.delete(path);
        }
    }

    /**
     * Suppression physique d'instances du monde et du registre.
     */
    void eraseInstances(CommandSender sender, List<DeployedInstance> instances) {
        if (instances.size() > 1) {
            Msg.info(sender, "Suppression physique de <yellow>" + instances.size() + "<gray> instance(s) de <yellow>"
                    + instances.get(0).getArena() + "<gray>...");
        }
        eraseNext(sender, instances, 0);
    }

    void eraseNext(CommandSender sender, List<DeployedInstance> instances, int index) {
        DeployedInstance instance = instances.get(index);
        String name = instance.getName();

        World world = Bukkit.getWorld(instance.getWorld());
        if (world == null) {
            Msg.error(sender, "Le monde <yellow>" + instance.getWorld() + "<red> n'est pas chargé, impossible d'effacer "
                    + name + ". Les instances restantes sont conservées.");
            return;
        }

        long volume = (long) (instance.getCorner2()[0] - instance.getCorner1()[0] + 1)
                * (instance.getCorner2()[1] - instance.getCorner1()[1] + 1)
                * (instance.getCorner2()[2] - instance.getCorner1()[2] + 1);
        Msg.info(sender, "Suppression physique de <yellow>" + name + " <gray>(" + volume + " blocs)...");

        BlockEraser eraser = new BlockEraser(world,
                instance.getCorner1(), instance.getCorner2(),
                plugin.getSettings().blocksPerTick(),
                percent -> Msg.info(sender, "Suppression de <yellow>" + name + " <gray>: " + percent + "%"),
                () -> {
                    plugin.getDeployments().remove(name);
                    Msg.ok(sender, "Instance <yellow>" + name + "<green> effacée du monde et retirée du registre.");
                    if (index + 1 < instances.size()) {
                        eraseNext(sender, instances, index + 1);
                    } else if (instances.size() > 1) {
                        Msg.ok(sender, "Terminé : <white>" + instances.size() + "<green> instance(s) supprimée(s).");
                    }
                },
                message -> {
                    Msg.error(sender, "Erreur pendant la suppression de " + name + " : " + message);
                    plugin.getLogger().severe("Erreur de suppression " + name + " : " + message);
                });
        planner.scheduleAfterPreload(world,
                instance.getCorner1()[0], instance.getCorner2()[0],
                instance.getCorner1()[2], instance.getCorner2()[2], eraser);
    }
}

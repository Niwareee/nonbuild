package fr.niware.nonbuild.command;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import fr.niware.nonbuild.Msg;
import fr.niware.nonbuild.NonBuild;
import fr.niware.nonbuild.model.Arena;
import fr.niware.nonbuild.model.DeployedInstance;
import fr.niware.nonbuild.model.Point;
import fr.niware.nonbuild.placement.DeploymentMap;
import fr.niware.nonbuild.placement.PlotAllocator;
import fr.niware.nonbuild.placement.Region2D;
import fr.niware.nonbuild.schematic.SpongeSchematic;
import fr.niware.nonbuild.storage.ArenaStorage;
import fr.niware.nonbuild.work.BlockEraser;
import fr.niware.nonbuild.work.BlockPaster;
import fr.niware.nonbuild.work.ChunkPreloader;
import fr.niware.nonbuild.world.VoidChunkGenerator;

public class DeployCommand implements TabExecutor {

    private static final int MAX_COUNT = 128;
    private static final String SPAWN_SCHEMATIC = "spawn.schem";
    private static final int SPAWN_PASTE_Y = 90;

    private final NonBuild plugin;
    private boolean deploying;
    /** Instances en cours de collage : téléportation refusée tant qu'elles ne sont pas terminées. */
    private final Set<String> pastingInstances = new HashSet<>();

    public DeployCommand(NonBuild plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String first = args[0].toLowerCase(Locale.ROOT);
        return switch (first) {
            case "list" -> handleList(sender);
            case "map" -> handleMap(sender);
            case "tp" -> handleTp(sender, args);
            case "remove" -> handleRemove(sender, args);
            case "rebuild", "--rebuild" -> handleRebuild(sender);
            case "help" -> {
                sendHelp(sender);
                yield true;
            }
            default -> handleDeploy(sender, args);
        };
    }

    private boolean handleDeploy(CommandSender sender, String[] args) {
        List<String> parsed = Args.parse(args);
        if (parsed.size() < 2) {
            Msg.error(sender, "Usage : /deploy \"nom de l'arène\" <nombre>");
            return true;
        }

        int count;
        try {
            count = Integer.parseInt(parsed.get(parsed.size() - 1));
        } catch (NumberFormatException e) {
            Msg.error(sender, "Le dernier argument doit être un nombre d'instances.");
            return true;
        }
        if (count < 1 || count > MAX_COUNT) {
            Msg.error(sender, "Le nombre d'instances doit être entre 1 et " + MAX_COUNT + ".");
            return true;
        }

        String rawName = String.join(" ", parsed.subList(0, parsed.size() - 1));
        String slug = ArenaStorage.slugify(rawName);
        Arena arena = plugin.getArenas().get(slug);
        if (arena == null) {
            Msg.error(sender, "Arène introuvable : " + slug);
            return true;
        }

        File schematicFile = plugin.getArenas().schematicFile(slug);
        if (!schematicFile.exists()) {
            Msg.error(sender, "Schematic manquante pour <yellow>" + slug + "<red>. Refaites /build edit " + slug + " puis /build save.");
            return true;
        }

        World prodWorld = Bukkit.getWorld(plugin.getSettings().prodWorld());
        if (prodWorld == null) {
            Msg.error(sender, "Le monde de production <yellow>" + plugin.getSettings().prodWorld() + "<red> n'est pas chargé (voir config.yml).");
            return true;
        }

        if (deploying) {
            Msg.error(sender, "Un déploiement est déjà en cours, patientez.");
            return true;
        }

        Msg.info(sender, "Chargement de la schematic de <yellow>" + slug + "<gray>...");
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            final SpongeSchematic schematic;
            try {
                schematic = SpongeSchematic.read(schematicFile);
            } catch (IOException e) {
                Bukkit.getScheduler().runTask(plugin, () ->
                        Msg.error(sender, "Schematic illisible : " + e.getMessage()));
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () ->
                    startDeployment(sender, arena, schematic, prodWorld, count, System.currentTimeMillis(), false, null));
        });
        return true;
    }

    /**
     * Une action de déploiement : mise à jour sur place d'une instance
     * existante (existing != null) ou création d'une nouvelle.
     */
    private record Plan(String instanceName, DeployedInstance existing, int[] targetMin,
                        Point center, Point spawn1, Point spawn2,
                        int[] corner1, int[] corner2, int[] cellMin, int[] cellMax,
                        boolean clearBefore) {
    }

    private void startDeployment(CommandSender sender, Arena arena, SpongeSchematic schematic,
                                 World prodWorld, int count, long startedAt, boolean skipAir, Runnable onAllDone) {
        deploying = true;

        int width = schematic.getWidth();
        int height = schematic.getHeight();
        int length = schematic.getLength();
        int pasteY = plugin.getSettings().pasteY();

        if (pasteY < prodWorld.getMinHeight() || pasteY + height - 1 > prodWorld.getMaxHeight() - 1) {
            Msg.error(sender, "Hauteur incompatible : paste-y=" + pasteY + " (arène collée de Y " + pasteY
                    + " à " + (pasteY + height - 1) + ") dépasse les limites du monde " + prodWorld.getName()
                    + ". Réglez placement.paste-y dans config.yml.");
            deploying = false;
            return;
        }

        if (width != arena.sizeX() || height != arena.sizeY() || length != arena.sizeZ()) {
            Msg.warn(sender, "Dimensions de la schematic différentes des corners enregistrés, la schematic fait foi.");
        }

        List<DeployedInstance> existing = plugin.getDeployments().byArena(arena.getSlug());

        PlotAllocator allocator = new PlotAllocator(
                plugin.getSettings().spawnProtectionRadius(), plugin.getSettings().margin());
        for (DeployedInstance instance : plugin.getDeployments().all()) {
            allocator.addOccupied(new Region2D(
                    instance.getCellMinXZ()[0], instance.getCellMinXZ()[1],
                    instance.getCellMaxXZ()[0], instance.getCellMaxXZ()[1]));
        }

        List<Plan> plans = new ArrayList<>();
        int margin = plugin.getSettings().margin();

        // 1) Mise à jour sur place des 'count' premières instances existantes
        int toUpdate = Math.min(count, existing.size());
        for (int i = 0; i < toUpdate; i++) {
            DeployedInstance instance = existing.get(i);
            int cellWidth = instance.getCellMaxXZ()[0] - instance.getCellMinXZ()[0] + 1;
            int cellLength = instance.getCellMaxXZ()[1] - instance.getCellMinXZ()[1] + 1;
            int[] cellMin = instance.getCellMinXZ();

            if (width + 2 * margin > cellWidth || length + 2 * margin > cellLength) {
                // L'arène a grandi au-delà de sa cellule : on lui trouve un nouvel emplacement.
                cellMin = allocator.allocate(width, length);
                if (cellMin == null) {
                    Msg.error(sender, "Espace insuffisant pour déplacer l'instance " + instance.getName()
                            + " (l'arène est plus grande que son ancien emplacement).");
                    deploying = false;
                    return;
                }
                allocator.addOccupied(allocator.cellFor(cellMin, width, length));
            }
            plans.add(buildPlan(allocator, instance.getName(), instance, cellMin, arena, schematic, pasteY));
        }

        // 2) Création des instances manquantes
        int baseIndex = plugin.getDeployments().nextIndex(arena.getSlug());
        for (int i = 0; i < count - toUpdate; i++) {
            int[] cellMin = allocator.allocate(width, length);
            if (cellMin == null) {
                Msg.error(sender, "Espace insuffisant dans le monde de production pour " + count + " instance(s).");
                deploying = false;
                return;
            }
            allocator.addOccupied(allocator.cellFor(cellMin, width, length));
            plans.add(buildPlan(allocator, arena.getSlug() + "-" + (baseIndex + i), null, cellMin, arena, schematic, pasteY));
        }

        int updated = toUpdate;
        int created = plans.size() - updated;
        int untouched = existing.size() - updated;
        if (created == 0) {
            Msg.ok(sender, "Mise à jour de <white>" + updated + "<green> instance(s) de <white>" + arena.getDisplayName() + "<green> sur leurs emplacements.");
        } else if (updated == 0) {
            Msg.ok(sender, created + " emplacement(s) réservé(s) pour <white>" + arena.getDisplayName() + "<green> :");
        } else {
            Msg.ok(sender, "Mise à jour de <white>" + updated + "<green> instance(s) et création de <white>" + created
                    + "<green> pour <white>" + arena.getDisplayName() + "<green>.");
        }
        if (untouched > 0) {
            Msg.info(sender, untouched + " instance(s) existante(s) restent inchangée(s).");
        }
        for (Plan plan : plans) {
            Msg.raw(sender, "  <gray>• <yellow>" + plan.instanceName() + " <dark_gray>→ <white>centre "
                    + plan.center().x() + ", " + plan.center().y() + ", " + plan.center().z());
        }
        Msg.info(sender, "Collage de <yellow>" + (long) width * height * length * plans.size() + "<gray> blocs, "
                + plugin.getSettings().blocksPerTick() + " blocs/tick...");

        executePlan(sender, arena, schematic, prodWorld, plans, 0, startedAt, skipAir, onAllDone);
    }

    private Plan buildPlan(PlotAllocator allocator, String instanceName, DeployedInstance existing, int[] cellMin,
                           Arena arena, SpongeSchematic schematic, int pasteY) {
        int margin = plugin.getSettings().margin();
        int targetMinX = cellMin[0] + margin;
        int targetMinZ = cellMin[1] + margin;
        double dx = targetMinX - arena.minX();
        double dy = (double) pasteY - arena.minY();
        double dz = targetMinZ - arena.minZ();

        int[] corner1 = {targetMinX, pasteY, targetMinZ};
        int[] corner2 = {targetMinX + schematic.getWidth() - 1,
                pasteY + schematic.getHeight() - 1,
                targetMinZ + schematic.getLength() - 1};

        boolean clearBefore = existing != null
                && (!Arrays.equals(existing.getCorner1(), corner1) || !Arrays.equals(existing.getCorner2(), corner2));

        Region2D cell = allocator.cellFor(cellMin, schematic.getWidth(), schematic.getLength());
        return new Plan(instanceName, existing, new int[]{targetMinX, pasteY, targetMinZ},
                arena.getCenter().withOffset(dx, dy, dz),
                arena.getSpawn1().withOffset(dx, dy, dz),
                arena.getSpawn2().withOffset(dx, dy, dz),
                corner1, corner2,
                new int[]{cell.minX(), cell.minZ()}, new int[]{cell.maxX(), cell.maxZ()},
                clearBefore);
    }

    private void executePlan(CommandSender sender, Arena arena, SpongeSchematic schematic,
                             World prodWorld, List<Plan> plans, int index, long startedAt,
                             boolean skipAir, Runnable onAllDone) {
        Plan plan = plans.get(index);

        Runnable paste = () -> pasteInstance(sender, arena, schematic, prodWorld, plans, index, startedAt, skipAir, onAllDone);

        if (plan.clearBefore()) {
            Msg.info(sender, "L'emplacement de <yellow>" + plan.instanceName() + "<gray> a changé, effacement de l'ancienne zone...");
            BlockEraser eraser = new BlockEraser(prodWorld,
                    plan.existing().getCorner1(), plan.existing().getCorner2(),
                    plugin.getSettings().blocksPerTick(),
                    percent -> Msg.info(sender, "Effacement de l'ancienne zone de <yellow>" + plan.instanceName() + " <gray>: " + percent + "%"),
                    paste,
                    message -> {
                        deploying = false;
                        Msg.error(sender, "Erreur pendant l'effacement de l'ancienne zone de " + plan.instanceName() + " : " + message);
                        plugin.getLogger().severe("Erreur d'effacement " + plan.instanceName() + " : " + message);
                    });
            scheduleAfterPreload(prodWorld,
                    plan.existing().getCorner1()[0], plan.existing().getCorner2()[0],
                    plan.existing().getCorner1()[2], plan.existing().getCorner2()[2], eraser);
        } else {
            paste.run();
        }
    }

    private void pasteInstance(CommandSender sender, Arena arena, SpongeSchematic schematic,
                               World prodWorld, List<Plan> plans, int index, long startedAt,
                               boolean skipAir, Runnable onAllDone) {
        Plan plan = plans.get(index);
        pastingInstances.add(plan.instanceName());
        BlockPaster paster = new BlockPaster(prodWorld,
                plan.targetMin()[0], plan.targetMin()[1], plan.targetMin()[2],
                schematic,
                plugin.getSettings().blocksPerTick(),
                skipAir,
                percent -> Msg.info(sender, "Collage de <yellow>" + plan.instanceName() + " <gray>: " + percent + "%"),
                () -> {
                    pastingInstances.remove(plan.instanceName());
                    DeployedInstance instance = new DeployedInstance(
                            plan.instanceName(), arena.getSlug(), prodWorld.getName(),
                            plan.center(), plan.corner1(), plan.corner2(),
                            plan.spawn1(), plan.spawn2(),
                            plan.cellMin(), plan.cellMax(),
                            System.currentTimeMillis());
                    plugin.getDeployments().put(instance);
                    if (plan.existing() != null) {
                        Msg.ok(sender, "Instance <white>" + plan.instanceName() + " <green>mise à jour sur place.");
                    } else {
                        Msg.ok(sender, "Instance <white>" + plan.instanceName() + " <green>déployée et enregistrée dans deployments.yml.");
                    }

                    if (index + 1 < plans.size()) {
                        executePlan(sender, arena, schematic, prodWorld, plans, index + 1, startedAt, skipAir, onAllDone);
                    } else {
                        double seconds = (System.currentTimeMillis() - startedAt) / 1000.0;
                        Msg.ok(sender, "Déploiement terminé : " + plans.size() + " instance(s) de <white>"
                                + arena.getDisplayName() + "<green> en " + String.format(Locale.ROOT, "%.1f", seconds) + " s.");
                        if (onAllDone != null) {
                            onAllDone.run();
                        } else {
                            deploying = false;
                        }
                    }
                },
                message -> {
                    pastingInstances.remove(plan.instanceName());
                    deploying = false;
                    Msg.error(sender, "Erreur pendant le collage de " + plan.instanceName() + " : " + message);
                    plugin.getLogger().severe("Erreur de collage " + plan.instanceName() + " : " + message);
                });
        scheduleAfterPreload(prodWorld, plan.targetMin()[0],
                plan.targetMin()[0] + schematic.getWidth() - 1,
                plan.targetMin()[2], plan.targetMin()[2] + schematic.getLength() - 1, paster);
    }

    /**
     * Planifie la tâche de blocs une fois les chunks de la région préchargés :
     * sans cela le premier getBlockAt d'un chunk froid déclenche une génération
     * synchrone sur le fil principal (freeze).
     */
    private void scheduleAfterPreload(World world, int minX, int maxX, int minZ, int maxZ,
                                      org.bukkit.scheduler.BukkitRunnable task) {
        ChunkPreloader.preload(plugin, world, minX, maxX, minZ, maxZ,
                () -> task.runTaskTimer(plugin, 1L, 1L));
    }

    /**
     * Reconstruction complète du monde de production : suppression du monde,
     * recréation en void, collage du spawn (spawn.schem), vidage du registre
     * puis redéploiement séquentiel de toutes les instances qui y figuraient.
     * Toutes les schematics sont préchargées dans la phase de validation async :
     * une illisible = refus AVANT toute destruction.
     */
    private boolean handleRebuild(CommandSender sender) {
        if (deploying) {
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
            if (!plugin.getArenas().schematicFile(slug).exists()) {
                Msg.error(sender, "Rebuild refusé : schematic manquante pour <yellow>" + slug + "<red>. Refaites /build edit " + slug + " puis /build save.");
                return true;
            }
        }

        File spawnFile = new File(plugin.getDataFolder(), SPAWN_SCHEMATIC);
        if (!spawnFile.exists()) {
            Msg.error(sender, "Rebuild refusé : <yellow>" + SPAWN_SCHEMATIC + "<red> absent du dossier du plugin (plugins/NonBuild/).");
            return true;
        }

        World prod = Bukkit.getWorld(prodName);
        if (prod != null && !prod.getPlayers().isEmpty() && evacuationDestination(prod) == null) {
            Msg.error(sender, "Rebuild refusé : des joueurs sont dans le monde de production et aucun autre monde n'est chargé.");
            return true;
        }

        deploying = true;
        long startedAt = System.currentTimeMillis();
        Msg.info(sender, "Rebuild du monde <yellow>" + prodName + "<gray> : chargement des schematics...");
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            final Map<String, SpongeSchematic> arenaSchematics = new LinkedHashMap<>();
            SpongeSchematic loadedSpawn = null;
            String failure = null;
            try {
                loadedSpawn = SpongeSchematic.read(spawnFile);
                for (RebuildEntry entry : entries) {
                    arenaSchematics.put(entry.slug(),
                            SpongeSchematic.read(plugin.getArenas().schematicFile(entry.slug())));
                }
            } catch (IOException e) {
                failure = e.getMessage();
            }
            if (failure != null) {
                final String message = failure;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    deploying = false;
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

    private void continueRebuild(CommandSender sender, SpongeSchematic spawn, List<RebuildEntry> entries,
                                 Map<String, SpongeSchematic> arenaSchematics, String prodName, long startedAt) {
        World prod = Bukkit.getWorld(prodName);

        if (prod != null && !spawnFits(prod, spawn)) {
            deploying = false;
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

    private void deleteAndRecreateWorld(CommandSender sender, SpongeSchematic spawn, List<RebuildEntry> entries,
                                        Map<String, SpongeSchematic> arenaSchematics, String prodName,
                                        World prod, long startedAt) {
        // unloadWorld lève si appelé pendant le tick des mondes.
        if (Bukkit.isTickingWorlds()) {
            Bukkit.getScheduler().runTask(plugin, () ->
                    deleteAndRecreateWorld(sender, spawn, entries, arenaSchematics, prodName, prod, startedAt));
            return;
        }

        File folder = prod != null ? prod.getWorldFolder() : new File(Bukkit.getWorldContainer(), prodName);

        if (prod != null && !Bukkit.unloadWorld(prodName, false)) {
            deploying = false;
            Msg.error(sender, "Impossible de décharger le monde " + prodName + ", rebuild annulé.");
            return;
        }

        if (!folder.exists()) {
            createAndFillWorld(sender, spawn, entries, arenaSchematics, prodName, startedAt);
            return;
        }

        // Des milliers de fichiers region : IO long, jamais sur le fil principal.
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                deleteRecursively(folder.toPath());
            } catch (IOException e) {
                String message = e.getMessage();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    deploying = false;
                    Msg.error(sender, "Impossible de supprimer le dossier du monde " + prodName + " : " + message);
                    plugin.getLogger().severe("Rebuild : échec de la suppression de " + folder + " : " + message);
                });
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () ->
                    createAndFillWorld(sender, spawn, entries, arenaSchematics, prodName, startedAt));
        });
    }

    private void createAndFillWorld(CommandSender sender, SpongeSchematic spawn, List<RebuildEntry> entries,
                                    Map<String, SpongeSchematic> arenaSchematics, String prodName, long startedAt) {
        // createWorld est main-thread only et levé pendant le tick des mondes.
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
            deploying = false;
            Msg.error(sender, "Échec critique : le monde " + prodName + " a été supprimé mais pas recréé. Relancez /deploy rebuild ou redémarrez le serveur.");
            plugin.getLogger().severe("Rebuild : createWorld a retourné null pour " + prodName);
            return;
        }

        if (!spawnFits(newWorld, spawn)) {
            deploying = false;
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
                    deploying = false;
                    Msg.error(sender, "Erreur pendant le collage du spawn : " + message);
                    plugin.getLogger().severe("Rebuild : erreur de collage du spawn : " + message);
                });
        scheduleAfterPreload(newWorld,
                offset[0], offset[0] + spawn.getWidth() - 1,
                offset[2], offset[2] + spawn.getLength() - 1, paster);
    }

    private record RebuildEntry(String slug, int count) {
    }

    private void rebuildNextArena(CommandSender sender, List<RebuildEntry> entries,
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
        startDeployment(sender, plugin.getArenas().get(entry.slug()), arenaSchematics.get(entry.slug()),
                world, entry.count(), startedAt, true,
                () -> rebuildNextArena(sender, entries, arenaSchematics, world, index + 1, startedAt));
    }

    private void finishRebuild(CommandSender sender, int totalInstances, long startedAt) {
        deploying = false;
        double seconds = (System.currentTimeMillis() - startedAt) / 1000.0;
        Msg.ok(sender, "Rebuild terminé : monde recréé, spawn collé"
                + (totalInstances > 0 ? ", " + totalInstances + " instance(s) redéployée(s)" : "")
                + " en " + String.format(Locale.ROOT, "%.1f", seconds) + " s.");
        Msg.info(sender, "Redémarrez le serveur pour que le plugin practice relise deployments.yml.");
    }

    private static int spawnMinY(SpongeSchematic spawn) {
        return SPAWN_PASTE_Y + spawn.getOffset()[1];
    }

    /**
     * Réglages persistés dans level.dat à la création du monde de production :
     * le serveur practice les reçoit déjà appliqués (remplace le passage de
     * NonWorld à chaque démarrage). random_tick_speed à 0 = plus de ticks
     * aléatoires sur les chunk chargés, le vrai gain de perf du lot.
     * Clés String plutôt que GameRules : la classe dépend du registre serveur,
     * inutilisable sous serveur mocké en tests.
     */
    @SuppressWarnings("removal") // GameRule exige le registre serveur, inutilisable sous mock
    private static void configureProductionWorld(World world) {
        world.setTime(6000);
        world.setDifficulty(Difficulty.NORMAL);
        world.setGameRuleValue("random_tick_speed", "0");
        world.setGameRuleValue("advance_time", "false");
        world.setGameRuleValue("advance_weather", "false");
        world.setGameRuleValue("mob_griefing", "true");
        world.setGameRuleValue("spawn_mobs", "false");
        world.setGameRuleValue("show_advancement_messages", "false");
        world.setGameRuleValue("immediate_respawn", "true");
        world.setGameRuleValue("natural_health_regeneration", "false");
    }

    private static boolean spawnFits(World world, SpongeSchematic spawn) {
        int minY = spawnMinY(spawn);
        return minY >= world.getMinHeight() && minY + spawn.getHeight() - 1 <= world.getMaxHeight() - 1;
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

    private void evictPlayersFromProd(CommandSender sender, World prod) {
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

    private static void deleteRecursively(Path root) throws IOException {
        List<Path> paths;
        try (var stream = Files.walk(root)) {
            paths = stream.sorted(Comparator.reverseOrder()).toList();
        }
        for (Path path : paths) {
            Files.delete(path);
        }
    }

    private boolean handleList(CommandSender sender) {
        var instances = plugin.getDeployments().all();
        Msg.raw(sender, "<gold><bold>Instances déployées : <yellow>" + instances.size());
        if (instances.isEmpty()) {
            Msg.info(sender, "Aucune instance. Déployez avec /deploy <arène> <nombre>.");
            return true;
        }
        for (DeployedInstance instance : instances) {
            Msg.raw(sender, "  <gray>• <yellow>" + instance.getName() + " <dark_gray>(" + instance.getArena() + ") <gray>monde <white>"
                    + instance.getWorld() + " <dark_gray>→ <white>"
                    + block(instance.getCenter().x()) + ", " + block(instance.getCenter().y()) + ", " + block(instance.getCenter().z()));
        }
        return true;
    }

    private boolean handleMap(CommandSender sender) {
        List<DeployedInstance> instances = new ArrayList<>(plugin.getDeployments().all());
        int[] playerPos = null;
        if (sender instanceof org.bukkit.entity.Player p) {
            org.bukkit.Location loc = p.getLocation();
            playerPos = new int[]{(int) loc.getX(), (int) loc.getZ()};
        }
        for (String line : DeploymentMap.render(instances, plugin.getSettings().spawnProtectionRadius(), playerPos)) {
            Msg.raw(sender, line);
        }
        return true;
    }

    private String block(double v) {
        return String.valueOf((int) Math.floor(v));
    }

    private boolean handleTp(CommandSender sender, String[] args) {
        if (args.length < 2) {
            Msg.error(sender, "Usage : /deploy tp <instance>");
            return true;
        }
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            Msg.error(sender, "Cette commande est réservée aux joueurs.");
            return true;
        }
        DeployedInstance instance = plugin.getDeployments().get(args[1]);
        if (instance == null) {
            Msg.error(sender, "Instance introuvable : " + args[1] + " (<gray>voir /deploy list<red>)");
            return true;
        }
        if (pastingInstances.contains(instance.getName())) {
            Msg.error(sender, "L'instance <yellow>" + instance.getName() + "<red> est en cours de collage, patientez.");
            return true;
        }
        org.bukkit.World world = Bukkit.getWorld(instance.getWorld());
        if (world == null) {
            Msg.error(sender, "Le monde <yellow>" + instance.getWorld() + "<red> n'est pas chargé. Vérifiez le nom dans config.yml (worlds.prod).");
            return true;
        }
        Location target = instance.getCenter().toLocation(world);
        Msg.info(sender, "Préchargement des chunks autour de <yellow>" + instance.getName() + "<gray>...");
        ChunkPreloader.preloadAndTeleport(plugin, player, target,
                () -> {
            player.setGameMode(GameMode.CREATIVE);
            Msg.ok(player, "Téléporté au centre de <yellow>" + instance.getName() + "<green>.");
        });
        return true;
    }

    private boolean handleRemove(CommandSender sender, String[] args) {
        if (args.length < 2) {
            Msg.error(sender, "Usage : /deploy remove <instance ou arène>");
            return true;
        }
        if (deploying) {
            Msg.error(sender, "Un déploiement est déjà en cours, patientez.");
            return true;
        }

        String name = args[1];
        DeployedInstance instance = plugin.getDeployments().get(name);
        if (instance != null) {
            eraseInstances(sender, List.of(instance));
            return true;
        }

        String slug = ArenaStorage.slugify(name);
        List<DeployedInstance> instances = new ArrayList<>(plugin.getDeployments().byArena(slug));
        if (instances.isEmpty()) {
            Msg.error(sender, "Instance ou arène introuvable : " + name);
            return true;
        }
        eraseInstances(sender, instances);
        return true;
    }

    private void eraseInstances(CommandSender sender, List<DeployedInstance> instances) {
        if (instances.size() > 1) {
            Msg.info(sender, "Suppression physique de <yellow>" + instances.size() + "<gray> instance(s) de <yellow>"
                    + instances.get(0).getArena() + "<gray>...");
        }
        eraseNext(sender, instances, 0);
    }

    private void eraseNext(CommandSender sender, List<DeployedInstance> instances, int index) {
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
        scheduleAfterPreload(world,
                instance.getCorner1()[0], instance.getCorner2()[0],
                instance.getCorner1()[2], instance.getCorner2()[2], eraser);
    }

    private void sendHelp(CommandSender sender) {
        Msg.raw(sender, "<gold><bold>Déploiement des arènes");
        Msg.raw(sender, "<yellow>/deploy \"nom de l'arène\" <nombre> <gray>— déploie <nombre> instances : les existantes sont mises à jour sur place");
        Msg.raw(sender, "<yellow>/deploy list <gray>— lister les instances déployées");
        Msg.raw(sender, "<yellow>/deploy map <gray>— carte des déploiements avec statistiques");
        Msg.raw(sender, "<yellow>/deploy tp <instance> <gray>— téléporter au centre de l'instance");
        Msg.raw(sender, "<yellow>/deploy remove <instance ou arène> <gray>— efface la zone du monde et retire du registre (un nom d'arène supprime toutes ses instances)");
        Msg.raw(sender, "<yellow>/deploy rebuild <gray>— recrée le monde de production à neuf (void + spawn.schem + toutes les instances)");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>(plugin.getArenas().all().stream().map(Arena::getSlug).toList());
            options.addAll(List.of("list", "map", "tp", "remove", "rebuild", "help"));
            return filter(options, args[0]);
        }
        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("remove") || args[0].equalsIgnoreCase("tp")) {
                List<String> options = new ArrayList<>(plugin.getDeployments().all().stream().map(DeployedInstance::getName).toList());
                if (args[0].equalsIgnoreCase("remove")) {
                    options.addAll(plugin.getDeployments().all().stream().map(DeployedInstance::getArena).distinct().toList());
                }
                return filter(options, args[1]);
            }
            if (!List.of("list", "map", "tp", "remove", "rebuild", "help").contains(args[0].toLowerCase(Locale.ROOT))) {
                return filter(List.of("1", "2", "4", "8"), args[1]);
            }
        }
        return List.of();
    }

    private List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(lower)).toList();
    }
}

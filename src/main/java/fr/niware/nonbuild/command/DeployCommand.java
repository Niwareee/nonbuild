package fr.niware.nonbuild.command;

import fr.niware.nonbuild.Msg;
import fr.niware.nonbuild.NonBuild;
import fr.niware.nonbuild.model.Arena;
import fr.niware.nonbuild.model.DeployedInstance;
import fr.niware.nonbuild.placement.DeploymentMap;
import fr.niware.nonbuild.schematic.SpongeSchematic;
import fr.niware.nonbuild.storage.ArenaStorage;
import fr.niware.nonbuild.work.ChunkPreloader;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.*;

/**
 * Point d'entrée des commandes /deploy.
 * Délègue la planification au {@link DeployPlanner} et le rebuild/suppression au {@link DeployRebuilder}.
 */
public class DeployCommand implements TabExecutor {

    private static final int MAX_COUNT = 128;

    private final NonBuild plugin;
    private boolean deploying;
    /** Instances en cours de collage : téléportation refusée tant qu'elles ne sont pas terminées. */
    private final Set<String> pastingInstances = new HashSet<>();

    private final DeployPlanner planner;
    private final DeployRebuilder rebuilder;

    public DeployCommand(NonBuild plugin) {
        this.plugin = plugin;
        this.planner = new DeployPlanner(plugin, this);
        this.rebuilder = new DeployRebuilder(plugin, this.planner, this);
    }

    // ---- State accessors (used by planner/rebuilder) ----

    boolean isDeploying() {
        return deploying;
    }

    void setDeploying(boolean value) {
        this.deploying = value;
    }

    void addPastingInstance(String name) {
        pastingInstances.add(name);
    }

    void removePastingInstance(String name) {
        pastingInstances.remove(name);
    }

    // ---- Command routing ----

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String first = args[0].toLowerCase(Locale.ROOT);
        return switch (first) {
            case "list" -> handleList(sender);
            case "map" -> handleMap(sender, args);
            case "tp" -> handleTp(sender, args);
            case "spawn" -> handleTpSpawn(sender);
            case "remove" -> handleRemove(sender, args);
            case "rebuild" -> this.rebuilder.handleRebuild(sender);
            case "help" -> {
                sendHelp(sender);
                yield true;
            }
            default -> handleDeploy(sender, args);
        };
    }

    // ---- /deploy <arena> <count> ----

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

        try {
            if (!plugin.getArenas().hasSchematic(slug)) {
                Msg.error(sender, "Schematic manquante pour <yellow>" + slug + "<red>. Refaites /build edit " + slug + " puis /build save.");
                return true;
            }
        } catch (SQLException e) {
            Msg.error(sender, "Erreur de vérification de la schematic : " + e.getMessage());
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
                schematic = plugin.getArenas().loadSchematic(slug);
                if (schematic == null) {
                    Bukkit.getScheduler().runTask(plugin, () ->
                            Msg.error(sender, "Schematic manquante pour <yellow>" + slug));
                    return;
                }
            } catch (Exception e) {
                Bukkit.getScheduler().runTask(plugin, () ->
                        Msg.error(sender, "Schematic illisible : " + e.getMessage()));
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () ->
                    planner.startDeployment(sender, arena, schematic, prodWorld, count, System.currentTimeMillis(), false, null));
        });
        return true;
    }

    // ---- /deploy list ----

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

    // ---- /deploy map ----

    private boolean handleMap(CommandSender sender, String[] args) {
        int[] playerPos = null;
        if (sender instanceof Player p) {
            Location loc = p.getLocation();
            playerPos = new int[]{(int) loc.getX(), (int) loc.getZ()};
        }
        int radius = plugin.getSettings().spawnProtectionRadius();
        if (args.length >= 2) {
            DeployedInstance focus = plugin.getDeployments().get(args[1]);
            if (focus == null) {
                Msg.error(sender, "Instance introuvable : " + args[1] + " (<gray>voir /deploy list<red>)");
                return true;
            }
            for (String line : DeploymentMap.renderZoom(focus, radius, playerPos)) {
                Msg.raw(sender, line);
            }
            return true;
        }
        List<DeployedInstance> instances = new ArrayList<>(plugin.getDeployments().all());
        for (String line : DeploymentMap.render(instances, radius, playerPos)) {
            Msg.raw(sender, line);
        }
        return true;
    }

    // ---- /deploy tp ----

    private boolean handleTp(CommandSender sender, String[] args) {
        if (args.length < 2) {
            Msg.error(sender, "Usage : /deploy tp <instance>");
            return true;
        }
        if (!(sender instanceof Player player)) {
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
        World world = Bukkit.getWorld(instance.getWorld());
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

    // ---- /deploy spawn ----

    private boolean handleTpSpawn(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            Msg.error(sender, "Cette commande est réservée aux joueurs.");
            return true;
        }
        World world = Bukkit.getWorld(plugin.getSettings().prodWorld());
        if (world == null) {
            Msg.error(sender, "Le monde de production n'est pas chargé.");
            return true;
        }
        Location spawn = world.getSpawnLocation();
        player.teleport(spawn);
        Msg.ok(sender, "Téléporté au spawn de <yellow>" + plugin.getSettings().prodWorld() + "<green>.");
        return true;
    }

    // ---- /deploy remove ----

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
            rebuilder.eraseInstances(sender, List.of(instance));
            return true;
        }

        String slug = ArenaStorage.slugify(name);
        List<DeployedInstance> instances = new ArrayList<>(plugin.getDeployments().byArena(slug));
        if (instances.isEmpty()) {
            Msg.error(sender, "Instance ou arène introuvable : " + name);
            return true;
        }
        rebuilder.eraseInstances(sender, instances);
        return true;
    }

    // ---- Help ----

    private void sendHelp(CommandSender sender) {
        Msg.raw(sender, "<gold><bold>Déploiement des arènes");
        Msg.raw(sender, "<yellow>/deploy \"nom de l'arène\" <nombre> <gray>— déploie <nombre> instances : les existantes sont mises à jour sur place");
        Msg.raw(sender, "<yellow>/deploy list <gray>— lister les instances déployées");
        Msg.raw(sender, "<yellow>/deploy map <gray>— carte des déploiements (vue d'ensemble)");
        Msg.raw(sender, "<yellow>/deploy map <instance> <gray>— zoom sur une instance (forme détaillée + coordonnées)");
        Msg.raw(sender, "<yellow>/deploy tp <instance> <gray>— téléporter au centre de l'instance");
        Msg.raw(sender, "<yellow>/deploy remove <instance ou arène> <gray>— efface la zone du monde et retire du registre (un nom d'arène supprime toutes ses instances)");
        Msg.raw(sender, "<yellow>/deploy rebuild <gray>— recrée le monde de production à neuf (void + spawn.schem + toutes les instances)");
    }

    // ---- Tab completion ----

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>(plugin.getArenas().all().stream().map(Arena::getSlug).toList());
            options.addAll(List.of("list", "map", "tp", "remove", "rebuild", "help"));
            return filter(options, args[0]);
        }
        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("remove") || args[0].equalsIgnoreCase("tp") || args[0].equalsIgnoreCase("map")) {
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

    private String block(double v) {
        return String.valueOf((int) Math.floor(v));
    }
}

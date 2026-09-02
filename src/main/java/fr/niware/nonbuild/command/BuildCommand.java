package fr.niware.nonbuild.command;

import fr.niware.nonbuild.Msg;
import fr.niware.nonbuild.NonBuild;
import fr.niware.nonbuild.edit.EditSession;
import fr.niware.nonbuild.model.Arena;
import fr.niware.nonbuild.model.DeployedInstance;
import fr.niware.nonbuild.model.Point;
import fr.niware.nonbuild.schematic.SpongeSchematic;
import fr.niware.nonbuild.storage.ArenaStorage;
import fr.niware.nonbuild.work.BlockCapture;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class BuildCommand implements TabExecutor {

    private final NonBuild plugin;

    public BuildCommand(NonBuild plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        List<String> rest = Args.parse(Arrays.copyOfRange(args, 1, args.length));

        return switch (sub) {
            case "addarena" -> requirePlayer(sender, p -> handleAddArena(p, rest));
            case "edit" -> requirePlayer(sender, p -> handleEdit(p, rest));
            case "setcorner1" -> requirePlayer(sender, p -> handleSetPoint(p, 1));
            case "setcorner2" -> requirePlayer(sender, p -> handleSetPoint(p, 2));
            case "setspawn1" -> requirePlayer(sender, p -> handleSetPoint(p, 3));
            case "setspawn2" -> requirePlayer(sender, p -> handleSetPoint(p, 4));
            case "setcenter" -> requirePlayer(sender, p -> handleSetPoint(p, 5));
            case "status" -> requirePlayer(sender, this::handleStatus);
            case "save" -> requirePlayer(sender, this::handleSave);
            case "cancel" -> requirePlayer(sender, this::handleCancel);
            case "info" -> handleInfo(sender, rest);
            case "list" -> handleList(sender);
            case "delete" -> requirePlayer(sender, p -> handleDelete(p, rest));
            case "help" -> {
                sendHelp(sender);
                yield true;
            }
            default -> {
                Msg.error(sender, "Sous-commande inconnue : " + sub);
                sendHelp(sender);
                yield true;
            }
        };
    }

    private boolean requirePlayer(CommandSender sender, java.util.function.Function<Player, Boolean> handler) {
        if (!(sender instanceof Player player)) {
            Msg.error(sender, "Cette commande est réservée aux joueurs.");
            return true;
        }
        return handler.apply(player);
    }

    private boolean handleAddArena(Player player, List<String> rest) {
        if (rest.isEmpty()) {
            Msg.error(player, "Usage : /build addarena \"nom de l'arène\"");
            return true;
        }
        if (plugin.getSessions().has(player.getUniqueId())) {
            Msg.error(player, "Vous avez déjà une session d'édition ouverte. Faites /build save ou /build cancel.");
            return true;
        }
        String buildWorld = plugin.getSettings().buildWorld();
        if (!player.getWorld().getName().equals(buildWorld)) {
            Msg.error(player, "Vous devez être dans le monde de build §e" + buildWorld + "§c pour créer une arène.");
            return true;
        }

        String displayName = String.join(" ", rest);
        if (displayName.length() > 64) {
            Msg.error(player, "Nom trop long (64 caractères max).");
            return true;
        }
        String slug = ArenaStorage.slugify(displayName);
        if (slug.isEmpty()) {
            Msg.error(player, "Nom invalide : utilisez des lettres, chiffres, espaces, tirets.");
            return true;
        }
        if (plugin.getArenas().exists(slug)) {
            Msg.error(player, "L'arène §e" + slug + "§c existe déjà. Utilisez /build edit " + slug + " pour la modifier.");
            return true;
        }

        GameMode previous = player.getGameMode();
        EditSession session = new EditSession(slug, displayName, buildWorld, previous);
        if (plugin.getSettings().setCreativeOnEdit()) {
            player.setGameMode(GameMode.CREATIVE);
            session.setCreativeApplied(true);
        }
        plugin.getSessions().put(player.getUniqueId(), session);

        Msg.ok(player, "Session d'édition ouverte pour l'arène §f" + displayName + " §7(id §e" + slug + "§7).");
        sendChecklist(player, session);
        return true;
    }

    private boolean handleEdit(Player player, List<String> rest) {
        if (rest.isEmpty()) {
            Msg.error(player, "Usage : /build edit <arène>");
            return true;
        }
        if (plugin.getSessions().has(player.getUniqueId())) {
            Msg.error(player, "Vous avez déjà une session d'édition ouverte. Faites /build save ou /build cancel.");
            return true;
        }
        String slug = ArenaStorage.slugify(String.join(" ", rest));
        Arena arena = plugin.getArenas().get(slug);
        if (arena == null) {
            Msg.error(player, "Arène introuvable : " + slug);
            return true;
        }

        org.bukkit.World world = Bukkit.getWorld(arena.getWorld());
        if (world == null) {
            Msg.error(player, "Le monde de build §e" + arena.getWorld() + "§c n'est pas chargé.");
            return true;
        }

        GameMode previous = player.getGameMode();
        EditSession session = new EditSession(slug, arena.getDisplayName(), arena.getWorld(), previous);
        session.setCorner1(toLocation(world, arena.getCorner1()));
        session.setCorner2(toLocation(world, arena.getCorner2()));
        session.setCenter(arena.getCenter().toLocation(world));
        session.setSpawn1(arena.getSpawn1().toLocation(world));
        session.setSpawn2(arena.getSpawn2().toLocation(world));
        if (plugin.getSettings().setCreativeOnEdit()) {
            player.setGameMode(GameMode.CREATIVE);
            session.setCreativeApplied(true);
        }
        plugin.getSessions().put(player.getUniqueId(), session);

        Msg.ok(player, "Arène §f" + arena.getDisplayName() + " §7chargée en édition, points pré-remplis.");
        Msg.info(player, "Repositionnez les points si besoin, puis /build save.");
        return true;
    }

    private Location toLocation(org.bukkit.World world, int[] block) {
        return new Location(world, block[0] + 0.5, block[1], block[2] + 0.5);
    }

    private boolean handleSetPoint(Player player, int point) {
        EditSession session = plugin.getSessions().get(player.getUniqueId());
        if (session == null) {
            Msg.error(player, "Aucune session d'édition. Commencez par /build addarena \"nom\".");
            return true;
        }
        if (!player.getWorld().getName().equals(session.getWorld())) {
            Msg.error(player, "Vous devez être dans le monde §e" + session.getWorld() + "§c pour définir les points.");
            return true;
        }

        Location location = square(player.getLocation());
        String next;
        switch (point) {
            case 1 -> {
                session.setCorner1(location);
                next = "/build setcorner2";
            }
            case 2 -> {
                session.setCorner2(location);
                next = "/build setspawn1";
            }
            case 3 -> {
                session.setSpawn1(location);
                next = "/build setspawn2";
            }
            case 4 -> {
                session.setSpawn2(location);
                next = "/build setcenter";
            }
            default -> {
                session.setCenter(location);
                next = "/build save";
            }
        }

        Msg.ok(player, "Point défini : §f" + location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ()
                + " §7(yaw §f" + (int) location.getYaw() + "°§7)");
        Msg.info(player, "Étape suivante : §e" + next);
        return true;
    }

    /**
     * Remet le point « d'équerre » : posé à la main, un joueur n'est jamais
     * exactement au centre du bloc ni parfaitement aligné. x/z au centre du
     * bloc, yaw/pitch sur les multiples de 90°.
     */
    static Location square(Location location) {
        double x = Math.floor(location.getX()) + 0.5;
        double z = Math.floor(location.getZ()) + 0.5;
        float yaw = Math.round(location.getYaw() / 90f) * 90f;
        float pitch = Math.round(location.getPitch() / 90f) * 90f;
        return new Location(location.getWorld(), x, location.getY(), z, yaw, pitch);
    }

    private boolean handleStatus(Player player) {
        EditSession session = plugin.getSessions().get(player.getUniqueId());
        if (session == null) {
            Msg.error(player, "Aucune session d'édition ouverte.");
            return true;
        }
        sendChecklist(player, session);
        return true;
    }

    private void sendChecklist(Player player, EditSession session) {
        Msg.raw(player, "§6§lArène §e" + session.getDisplayName() + " §7— points à définir :");
        Msg.raw(player, pointLine(session.getCorner1(), "Corner 1 (englobe l'arène)", "/build setcorner1"));
        Msg.raw(player, pointLine(session.getCorner2(), "Corner 2 (opposé)", "/build setcorner2"));
        Msg.raw(player, pointLine(session.getSpawn1(), "Spawn 1", "/build setspawn1"));
        Msg.raw(player, pointLine(session.getSpawn2(), "Spawn 2 (duel)", "/build setspawn2"));
        Msg.raw(player, pointLine(session.getCenter(), "Centre de l'arène", "/build setcenter"));
        if (session.isComplete()) {
            Msg.raw(player, "§aTous les points sont définis. Validez avec §e/build save§a.");
        }
    }

    private String pointLine(Location location, String label, String command) {
        if (location == null) {
            return "  §c✖ " + label + " §8→ §e" + command;
        }
        return "  §a✔ " + label + " §7: " + location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ();
    }

    private boolean handleSave(Player player) {
        EditSession session = plugin.getSessions().get(player.getUniqueId());
        if (session == null) {
            Msg.error(player, "Aucune session d'édition ouverte.");
            return true;
        }
        if (!session.isComplete()) {
            Msg.error(player, "Points manquants :");
            for (String missing : session.missingPoints()) {
                Msg.raw(player, "  §c✖ §e" + missing);
            }
            return true;
        }

        int minX = Math.min(session.getCorner1().getBlockX(), session.getCorner2().getBlockX());
        int minY = Math.min(session.getCorner1().getBlockY(), session.getCorner2().getBlockY());
        int minZ = Math.min(session.getCorner1().getBlockZ(), session.getCorner2().getBlockZ());
        int maxX = Math.max(session.getCorner1().getBlockX(), session.getCorner2().getBlockX());
        int maxY = Math.max(session.getCorner1().getBlockY(), session.getCorner2().getBlockY());
        int maxZ = Math.max(session.getCorner1().getBlockZ(), session.getCorner2().getBlockZ());

        int sizeX = maxX - minX + 1;
        int sizeY = maxY - minY + 1;
        int sizeZ = maxZ - minZ + 1;
        long volume = (long) sizeX * sizeY * sizeZ;

        if (volume > plugin.getSettings().maxVolume()) {
            Msg.error(player, "Volume trop grand : " + volume + " blocs (max " + plugin.getSettings().maxVolume() + ").");
            return true;
        }

        Arena arena = new Arena(session.getSlug());
        arena.setDisplayName(session.getDisplayName());
        arena.setWorld(session.getWorld());
        arena.setCorner1(new int[]{minX, minY, minZ});
        arena.setCorner2(new int[]{maxX, maxY, maxZ});
        arena.setCenter(Point.of(session.getCenter()));
        arena.setSpawn1(Point.of(session.getSpawn1()));
        arena.setSpawn2(Point.of(session.getSpawn2()));

        for (var entry : new Object[][]{
                {"centre", arena.getCenter()},
                {"spawn 1", arena.getSpawn1()},
                {"spawn 2", arena.getSpawn2()}}) {
            if (!arena.contains((Point) entry[1])) {
                Msg.error(player, "Le " + entry[0] + " est en dehors de la zone définie par les corners.");
                return true;
            }
        }

        Msg.info(player, "Capture de §e" + volume + "§7 blocs (" + sizeX + "x" + sizeY + "x" + sizeZ + ")...");

        org.bukkit.World world = player.getWorld();
        BlockCapture capture = new BlockCapture(world, minX, minY, minZ, sizeX, sizeY, sizeZ,
                plugin.getSettings().captureBlocksPerTick(),
                percent -> Msg.info(player, "Capture : §e" + percent + "%"),
                (schematic, nanos) -> finishSave(player, session, arena, schematic, nanos),
                message -> {
                    Msg.error(player, "Erreur pendant la capture : " + message);
                    plugin.getLogger().severe("Erreur de capture pour " + session.getSlug() + " : " + message);
                });
        capture.runTaskTimer(plugin, 1L, 1L);
        return true;
    }

    private void finishSave(Player player, EditSession session, Arena arena, SpongeSchematic schematic, long nanos) {
        File schematicFile = plugin.getArenas().schematicFile(session.getSlug());
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                schematic.write(schematicFile);
            } catch (IOException e) {
                Bukkit.getScheduler().runTask(plugin, () ->
                        Msg.error(player, "Impossible d'écrire la schematic : " + e.getMessage()));
                plugin.getLogger().severe("Erreur d'écriture schematic " + schematicFile + " : " + e.getMessage());
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    arena.setSavedAt(System.currentTimeMillis());
                    plugin.getArenas().save(arena);
                } catch (IOException e) {
                    Msg.error(player, "Impossible d'écrire le YAML : " + e.getMessage());
                    return;
                }
                closeSession(player, session);
                Msg.ok(player, "Arène §f" + session.getDisplayName() + " §asauvegardée en §e" + (nanos / 1_000_000) + " ms§a.");
                Msg.raw(player, "  §7YAML : §farenas/" + session.getSlug() + ".yml");
                Msg.raw(player, "  §7Schematic : §fschematics/" + session.getSlug() + ".schem");
                Msg.info(player, "Déployez-la avec §e/deploy " + session.getSlug() + " <nombre>§7.");
            });
        });
    }

    private boolean handleCancel(Player player) {
        EditSession session = plugin.getSessions().remove(player.getUniqueId());
        if (session == null) {
            Msg.error(player, "Aucune session d'édition ouverte.");
            return true;
        }
        closeSession(player, session);
        Msg.warn(player, "Édition de l'arène §e" + session.getDisplayName() + "§7 annulée.");
        return true;
    }

    private void closeSession(Player player, EditSession session) {
        plugin.getSessions().remove(player.getUniqueId());
        if (session.isCreativeApplied() && player.isOnline()) {
            player.setGameMode(session.getPreviousGameMode());
        }
    }

    private boolean handleInfo(CommandSender sender, List<String> rest) {
        if (rest.isEmpty()) {
            Msg.error(sender, "Usage : /build info <arène>");
            return true;
        }
        String slug = ArenaStorage.slugify(String.join(" ", rest));
        Arena arena = plugin.getArenas().get(slug);
        if (arena == null) {
            Msg.error(sender, "Arène introuvable : " + slug);
            return true;
        }
        File schematicFile = plugin.getArenas().schematicFile(slug);
        List<DeployedInstance> instances = plugin.getDeployments().byArena(slug);

        Msg.raw(sender, "§6§lArène : §e" + arena.getDisplayName() + " §8(" + slug + ")");
        Msg.raw(sender, "  §7Monde de build : §f" + arena.getWorld());
        Msg.raw(sender, "  §7Taille : §f" + arena.sizeX() + " x " + arena.sizeY() + " x " + arena.sizeZ()
                + " §8(" + arena.volume() + " blocs)");
        Msg.raw(sender, "  §7Corner 1 : §f" + format(arena.getCorner1()));
        Msg.raw(sender, "  §7Corner 2 : §f" + format(arena.getCorner2()));
        Msg.raw(sender, "  §7Centre : §f" + format(arena.getCenter()));
        Msg.raw(sender, "  §7Spawn 1 : §f" + format(arena.getSpawn1()));
        Msg.raw(sender, "  §7Spawn 2 : §f" + format(arena.getSpawn2()));
        Msg.raw(sender, "  §7Schematic : " + (schematicFile.exists() ? "§a✔ présente" : "§c✖ manquante"));
        Msg.raw(sender, "  §7Instances déployées : §f" + instances.size());
        Msg.raw(sender, "  §7Sauvegardée le : §f" + new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm")
                .format(new java.util.Date(arena.getSavedAt())));
        return true;
    }

    private String format(int[] block) {
        return block[0] + ", " + block[1] + ", " + block[2];
    }

    private String format(Point point) {
        return String.format(Locale.ROOT, "%.1f, %.1f, %.1f", point.x(), point.y(), point.z());
    }

    private boolean handleList(CommandSender sender) {
        var arenas = plugin.getArenas().all();
        Msg.raw(sender, "§6§lArènes enregistrées : §e" + arenas.size());
        if (arenas.isEmpty()) {
            Msg.info(sender, "Aucune arène. Créez-en une avec /build addarena \"nom\".");
            return true;
        }
        for (Arena arena : arenas) {
            int deployed = plugin.getDeployments().byArena(arena.getSlug()).size();
            Msg.raw(sender, "  §7• §e" + arena.getSlug() + " §8(" + arena.getDisplayName() + ")§7 : "
                    + arena.sizeX() + "x" + arena.sizeY() + "x" + arena.sizeZ()
                    + " §8| §7déployée x§f" + deployed);
        }
        return true;
    }

    private boolean handleDelete(Player player, List<String> rest) {
        if (rest.isEmpty()) {
            Msg.error(player, "Usage : /build delete <arène>");
            return true;
        }
        String slug = ArenaStorage.slugify(rest.get(0));
        if (!plugin.getArenas().exists(slug)) {
            Msg.error(player, "Arène introuvable : " + slug);
            return true;
        }
        List<DeployedInstance> instances = plugin.getDeployments().byArena(slug);
        if (!instances.isEmpty()) {
            Msg.error(player, "Impossible : " + instances.size() + " instance(s) déployée(s). Retirez-les d'abord avec /deploy remove " + slug + ".");
            return true;
        }
        plugin.getArenas().delete(slug);
        Msg.ok(player, "Arène §e" + slug + "§a supprimée (YAML + schematic).");
        return true;
    }

    private void sendHelp(CommandSender sender) {
        Msg.raw(sender, "§6§lÉdition des arènes");
        Msg.raw(sender, "§e/build addarena \"nom\" §7— ouvrir une session d'édition");
        Msg.raw(sender, "§e/build setcorner1|setcorner2 §7— coins englobant l'arène");
        Msg.raw(sender, "§e/build setspawn1|setspawn2 §7— spawns de duel");
        Msg.raw(sender, "§e/build setcenter §7— centre de l'arène");
        Msg.raw(sender, "§e/build save §7— capturer + sauvegarder (YAML + schematic)");
        Msg.raw(sender, "§e/build status|cancel §7— état de la session / annuler");
        Msg.raw(sender, "§e/build edit <arène> §7— recharger une arène en édition");
        Msg.raw(sender, "§e/build info <arène> §7| §e/build list §7| §e/build delete <arène>");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return filter(List.of("addarena", "edit", "setcorner1", "setcorner2", "setspawn1",
                    "setspawn2", "setcenter", "status", "save", "cancel", "info", "list", "delete", "help"), args[0]);
        }
        if (args.length == 2 && List.of("edit", "info", "delete").contains(args[0].toLowerCase(Locale.ROOT))) {
            return filter(plugin.getArenas().all().stream().map(Arena::getSlug).toList(), args[1]);
        }
        return List.of();
    }

    private List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(lower)).toList();
    }
}

package fr.niware.nonbuild.command;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import fr.niware.nonbuild.Msg;
import fr.niware.nonbuild.NonBuild;
import fr.niware.nonbuild.edit.EditSession;
import fr.niware.nonbuild.model.Arena;
import fr.niware.nonbuild.model.DeployedInstance;
import fr.niware.nonbuild.model.Point;
import fr.niware.nonbuild.schematic.SpongeSchematic;
import fr.niware.nonbuild.storage.ArenaStorage;
import fr.niware.nonbuild.work.BlockCapture;
import fr.niware.nonbuild.work.ChunkPreloader;

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
            case "tp" -> requirePlayer(sender, p -> handleTp(p, rest));
            case "delete" -> requirePlayer(sender, p -> handleDelete(p, rest));
            case "rename" -> requirePlayer(sender, p -> handleRename(p, rest));
            case "setmode" -> requirePlayer(sender, p -> handleSetMode(p, rest));
            case "goal" -> requirePlayer(sender, this::handleGoal);
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

    /**
     * Vérifie qu'un nom d'arène est un slug valide (pas d'espaces, anglais uniquement).
     * Accepte : a-zA-Z, 0-9, tirets, underscores.
     */
    private static boolean isValidArenaName(String name) {
        return name.matches("^[a-zA-Z0-9_-]+$");
    }

    private boolean handleAddArena(Player player, List<String> rest) {
        if (rest.isEmpty()) {
            Msg.error(player, "Usage : /build addarena <slug>");
            return true;
        }
        if (rest.size() > 1) {
            Msg.error(player, "Usage : /build addarena <slug> (pas d'espace dans le slug)");
            return true;
        }
        if (plugin.getSessions().has(player.getUniqueId())) {
            Msg.error(player, "Vous avez déjà une session d'édition ouverte. Faites /build save ou /build cancel.");
            return true;
        }
        String buildWorld = plugin.getSettings().buildWorld();
        if (!player.getWorld().getName().equals(buildWorld)) {
            Msg.error(player, "Vous devez être dans le monde de build <yellow>" + buildWorld + "<red> pour créer une arène.");
            return true;
        }

        String displayName = rest.get(0);
        if (displayName.length() > 64) {
            Msg.error(player, "Nom trop long (64 caractères max).");
            return true;
        }
        if (!isValidArenaName(displayName)) {
            Msg.error(player, "Nom invalide : utilisez uniquement des lettres anglaises, chiffres, tirets, underscores (pas d'espace).");
            return true;
        }
        String slug = ArenaStorage.slugify(displayName);
        if (slug.isEmpty()) {
            Msg.error(player, "Nom invalide : utilisez uniquement des lettres anglaises, chiffres, tirets, underscores (pas d'espace).");
            return true;
        }
        if (plugin.getArenas().exists(slug)) {
            Msg.error(player, "L'arène <yellow>" + slug + "<red> existe déjà. Utilisez /build edit " + slug + " pour la modifier.");
            return true;
        }

        GameMode previous = player.getGameMode();
        EditSession session = new EditSession(slug, displayName, buildWorld, previous);
        if (plugin.getSettings().setCreativeOnEdit()) {
            player.setGameMode(GameMode.CREATIVE);
            session.setCreativeApplied(true);
        }
        plugin.getSessions().put(player.getUniqueId(), session);

        Msg.ok(player, "Session d'édition ouverte pour l'arène <white>" + displayName + " <gray>(id <yellow>" + slug + "<gray>).");
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
            Msg.error(player, "Le monde de build <yellow>" + arena.getWorld() + "<red> n'est pas chargé.");
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

        Msg.ok(player, "Arène <white>" + arena.getDisplayName() + " <gray>chargée en édition, points pré-remplis.");
        Msg.info(player, "Repositionnez les points si besoin, puis /build save.");
        return true;
    }

    private Location toLocation(World world, int[] block) {
        return new Location(world, block[0] + 0.5, block[1], block[2] + 0.5);
    }

    private boolean handleSetPoint(Player player, int point) {
        EditSession session = plugin.getSessions().get(player.getUniqueId());
        if (session == null) {
            Msg.error(player, "Aucune session d'édition. Commencez par /build addarena \"nom\".");
            return true;
        }
        if (!player.getWorld().getName().equals(session.getWorld())) {
            Msg.error(player, "Vous devez être dans le monde <yellow>" + session.getWorld() + "<red> pour définir les points.");
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

        Msg.ok(player, "Point défini : <white>" + location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ()
                + " <gray>(yaw <white>" + (int) location.getYaw() + "°<gray>)");
        Msg.info(player, "Étape suivante : <yellow>" + next);
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
        Msg.raw(player, "<gold><bold>Arène <yellow>" + session.getDisplayName() + " <gray>— points à définir :");
        Msg.raw(player, pointLine(session.getCorner1(), "Corner 1 (englobe l'arène)", "/build setcorner1"));
        Msg.raw(player, pointLine(session.getCorner2(), "Corner 2 (opposé)", "/build setcorner2"));
        Msg.raw(player, pointLine(session.getSpawn1(), "Spawn 1", "/build setspawn1"));
        Msg.raw(player, pointLine(session.getSpawn2(), "Spawn 2 (duel)", "/build setspawn2"));
        Msg.raw(player, pointLine(session.getCenter(), "Centre de l'arène", "/build setcenter"));
        if (session.isComplete()) {
            Msg.raw(player, "<green>Tous les points sont définis. Validez avec <yellow>/build save<green>.");
        }
    }

    private String pointLine(Location location, String label, String command) {
        if (location == null) {
            return "  <red>✖ " + label + " <dark_gray>→ <yellow>" + command;
        }
        return "  <green>✔ " + label + " <gray>: " + location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ();
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
                Msg.raw(player, "  <red>✖ <yellow>" + missing);
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

        Msg.info(player, "Capture de <yellow>" + volume + "<gray> blocs (" + sizeX + "x" + sizeY + "x" + sizeZ + ")...");

        World world = player.getWorld();
        BlockCapture capture = new BlockCapture(world, minX, minY, minZ, sizeX, sizeY, sizeZ,
                plugin.getSettings().captureBlocksPerTick(),
                percent -> Msg.info(player, "Capture : <yellow>" + percent + "%"),
                (schematic, nanos) -> finishSave(player, session, arena, schematic, nanos),
                message -> {
                    Msg.error(player, "Erreur pendant la capture : " + message);
                    plugin.getLogger().severe("Erreur de capture pour " + session.getSlug() + " : " + message);
                });
        capture.runTaskTimer(plugin, 1L, 1L);
        return true;
    }

    private void finishSave(Player player, EditSession session, Arena arena, SpongeSchematic schematic, long nanos) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                plugin.getArenas().saveSchematic(session.getSlug(), schematic);
            } catch (SQLException | IOException e) {
                Bukkit.getScheduler().runTask(plugin, () ->
                        Msg.error(player, "Impossible d'écrire la schematic : " + e.getMessage()));
                plugin.getLogger().severe("Erreur d'écriture schematic " + session.getSlug() + " : " + e.getMessage());
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    arena.setSavedAt(System.currentTimeMillis());
                    plugin.getArenas().save(arena);
                } catch (SQLException e) {
                    Msg.error(player, "Impossible de sauvegarder en base : " + e.getMessage());
                    return;
                }
                closeSession(player, session);
                Msg.ok(player, "Arène <white>" + session.getDisplayName() + " <green>sauvegardée en <yellow>" + (nanos / 1_000_000) + " ms<green>.");
                Msg.raw(player, "  <gray>Schematic : <white>en base de données");
                Msg.info(player, "Déployez-la avec <yellow>/deploy " + session.getSlug() + " <nombre><gray>.");
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
        Msg.warn(player, "Édition de l'arène <yellow>" + session.getDisplayName() + "<gray> annulée.");
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
        List<DeployedInstance> instances = plugin.getDeployments().byArena(slug);

        Msg.raw(sender, "<gold><bold>Arène : <yellow>" + arena.getDisplayName() + " <dark_gray>(" + slug + ")");
        Msg.raw(sender, "  <gray>Monde de build : <white>" + arena.getWorld());
        Msg.raw(sender, "  <gray>Taille : <white>" + arena.sizeX() + " x " + arena.sizeY() + " x " + arena.sizeZ()
                + " <dark_gray>(" + arena.volume() + " blocs)");
        Msg.raw(sender, "  <gray>Corner 1 : <white>" + format(arena.getCorner1()));
        Msg.raw(sender, "  <gray>Corner 2 : <white>" + format(arena.getCorner2()));
        Msg.raw(sender, "  <gray>Centre : <white>" + format(arena.getCenter()));
        Msg.raw(sender, "  <gray>Spawn 1 : <white>" + format(arena.getSpawn1()));
        Msg.raw(sender, "  <gray>Spawn 2 : <white>" + format(arena.getSpawn2()));
        try {
            boolean hasSchem = plugin.getArenas().hasSchematic(slug);
            Msg.raw(sender, "  <gray>Schematic : " + (hasSchem ? "<green>✔ présente" : "<red>✖ manquante"));
        } catch (SQLException e) {
            Msg.raw(sender, "  <gray>Schematic : <red>erreur de vérification");
        }
        Msg.raw(sender, "  <gray>Instances déployées : <white>" + instances.size());
        if (arena.getGameMode() != null) {
            String modeName = plugin.getSettings().gameModes().get(arena.getGameMode());
            Msg.raw(sender, "  <gray>Mode de jeu : <white>" + (modeName != null ? modeName : arena.getGameMode()));
        } else {
            Msg.raw(sender, "  <gray>Mode de jeu : <yellow>non assigné<dark_gray> (/build setmode " + slug + " <mode>)");
        }
        Msg.raw(sender, "  <gray>Sauvegardée le : <white>" + new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm")
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
        Msg.raw(sender, "<gold><bold>Arènes enregistrées : <yellow>" + arenas.size());
        if (arenas.isEmpty()) {
            Msg.info(sender, "Aucune arène. Créez-en une avec /build addarena \"nom\".");
            return true;
        }
        for (Arena arena : arenas) {
            int deployed = plugin.getDeployments().byArena(arena.getSlug()).size();
            String modeTag = arena.getGameMode() != null ? " <dark_gray>[" + arena.getGameMode() + "]" : "";
            Msg.raw(sender, "  <gray>• <yellow>" + arena.getSlug() + modeTag + " <dark_gray>(" + arena.getDisplayName() + ")<gray> : "
                    + arena.sizeX() + "x" + arena.sizeY() + "x" + arena.sizeZ()
                    + " <dark_gray>| <gray>déployée x<white>" + deployed);
        }
        return true;
    }

    private boolean handleTp(Player player, List<String> rest) {
        if (rest.isEmpty()) {
            Msg.error(player, "Usage : /build tp <arène>");
            return true;
        }
        String slug = ArenaStorage.slugify(String.join(" ", rest));
        Arena arena = plugin.getArenas().get(slug);
        if (arena == null) {
            Msg.error(player, "Arène introuvable : " + slug + " <dark_gray>(voir /build list<red>)");
            return true;
        }
        org.bukkit.World world = Bukkit.getWorld(arena.getWorld());
        if (world == null) {
            Msg.error(player, "Le monde de build <yellow>" + arena.getWorld() + "<red> n'est pas chargé.");
            return true;
        }
        Location target = arena.getCenter().toLocation(world);
        Msg.info(player, "Préchargement des chunks autour de <yellow>" + arena.getDisplayName() + "<gray>...");
        ChunkPreloader.preloadAndTeleport(plugin, player, target,
                () -> Msg.ok(player, "Téléporté au centre de <yellow>" + arena.getDisplayName() + "<green>."));
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
        Msg.ok(player, "Arène <yellow>" + slug + "<green> supprimée (base + schematic).");
        return true;
    }

    private boolean handleRename(Player player, List<String> rest) {
        if (rest.isEmpty()) {
            Msg.error(player, "Usage : /build rename <arène> <nouveau-slug>");
            return true;
        }
        String oldSlug = ArenaStorage.slugify(rest.get(0));
        if (!plugin.getArenas().exists(oldSlug)) {
            Msg.error(player, "Arène introuvable : " + oldSlug);
            return true;
        }
        if (rest.size() < 2) {
            Msg.error(player, "Usage : /build rename <arène> <nouveau-slug>");
            return true;
        }
        if (rest.size() > 2) {
            Msg.error(player, "Usage : /build rename <arène> <nouveau-slug> (pas d'espace dans le slug)");
            return true;
        }
        String newDisplayName = rest.get(1);
        if (newDisplayName.length() > 64) {
            Msg.error(player, "Nom trop long (64 caractères max).");
            return true;
        }
        if (!isValidArenaName(newDisplayName)) {
            Msg.error(player, "Nom invalide : utilisez uniquement des lettres anglaises, chiffres, tirets, underscores (pas d'espace).");
            return true;
        }
        String newSlug = ArenaStorage.slugify(newDisplayName);
        if (newSlug.isEmpty()) {
            Msg.error(player, "Nom invalide : utilisez uniquement des lettres anglaises, chiffres, tirets, underscores (pas d'espace).");
            return true;
        }
        if (plugin.getArenas().exists(newSlug) && !newSlug.equals(oldSlug)) {
            Msg.error(player, "L'arène <yellow>" + newSlug + "<red> existe déjà.");
            return true;
        }
        Arena old = plugin.getArenas().get(oldSlug);
        try {
            plugin.getArenas().rename(oldSlug, newDisplayName);
            int deployedUpdated = plugin.getDeployments().renameArena(oldSlug, newSlug);
            Msg.ok(player, "Arène <yellow>" + oldSlug + "<green> renommée en <yellow>" + newSlug + " <gray>(" + newDisplayName + ")");
            Msg.info(player, "Schematic mise à jour : schematics/" + newSlug + ".schem");
            if (deployedUpdated > 0) {
                Msg.ok(player, deployedUpdated + " instance(s) déployée(s) mise(s) à jour (coordonnées conservées).");
            }
        } catch (SQLException e) {
            Msg.error(player, "Erreur lors du renommage : " + e.getMessage());
        }
        return true;
    }

    private boolean handleSetMode(Player player, List<String> rest) {
        if (rest.size() < 2) {
            Msg.error(player, "Usage : /build setmode <arène> <mode>");
            return true;
        }
        String slug = ArenaStorage.slugify(rest.get(0));
        Arena arena = plugin.getArenas().get(slug);
        if (arena == null) {
            Msg.error(player, "Arène introuvable : " + slug);
            return true;
        }
        String modeKey = rest.get(1).toUpperCase(Locale.ROOT);
        Map<String, String> gameModes = plugin.getSettings().gameModes();
        if (!gameModes.containsKey(modeKey)) {
            Msg.error(player, "Mode de jeu inconnu : " + modeKey);
            Msg.info(player, "Modes disponibles : " + String.join(", ", gameModes.keySet()));
            return true;
        }
        try {
            plugin.getArenas().setGameMode(slug, modeKey);
            Msg.ok(player, "Arène <yellow>" + slug + "<green> assignée au mode <yellow>" + gameModes.get(modeKey) + "<green>.");
        } catch (SQLException e) {
            Msg.error(player, "Erreur lors de la sauvegarde : " + e.getMessage());
        }
        return true;
    }

    private boolean handleGoal(Player player) {
        plugin.getGoalGUI().open(player);
        return true;
    }

    private void sendHelp(CommandSender sender) {
        Msg.raw(sender, "<gold><bold>Édition des arènes");
        Msg.raw(sender, "<yellow>/build addarena \"nom\" <gray>— ouvrir une session d'édition");
        Msg.raw(sender, "<yellow>/build setcorner1|setcorner2 <gray>— coins englobant l'arène");
        Msg.raw(sender, "<yellow>/build setspawn1|setspawn2 <gray>— spawns de duel");
        Msg.raw(sender, "<yellow>/build setcenter <gray>— centre de l'arène");
        Msg.raw(sender, "<yellow>/build save <gray>— capturer + sauvegarder (base + schematic)");
        Msg.raw(sender, "<yellow>/build status|cancel <gray>— état de la session / annuler");
        Msg.raw(sender, "<yellow>/build edit <arène> <gray>— recharger une arène en édition");
        Msg.raw(sender, "<yellow>/build tp <arène> <gray>— téléporter au centre de l'arène (monde de build)");
        Msg.raw(sender, "<yellow>/build info <arène> <gray>| <yellow>/build list <gray>| <yellow>/build delete <arène>");
        Msg.raw(sender, "<yellow>/build rename <arène> \"nouveau nom\" <gray>— renommer une arène (met à jour les instances)");
        Msg.raw(sender, "<yellow>/build goal <gray>— état des maps par mode de jeu");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return filter(List.of("addarena", "edit", "setcorner1", "setcorner2", "setspawn1",
                    "setspawn2", "setcenter", "status", "save", "cancel", "info", "list", "tp", "delete", "rename", "goal", "help"), args[0]);
        }
        if (args.length == 2 && List.of("edit", "info", "tp", "delete", "rename", "setmode").contains(args[0].toLowerCase(Locale.ROOT))) {
            return filter(plugin.getArenas().all().stream().map(Arena::getSlug).toList(), args[1]);
        }
        if (args.length == 3 && "setmode".equals(args[0].toLowerCase(Locale.ROOT))) {
            return filter(new java.util.ArrayList<>(plugin.getSettings().gameModes().keySet()), args[2]);
        }
        return List.of();
    }

    private List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(lower)).toList();
    }
}

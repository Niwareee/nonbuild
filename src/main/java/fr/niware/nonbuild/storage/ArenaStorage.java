package fr.niware.nonbuild.storage;

import java.io.IOException;
import java.sql.SQLException;
import java.text.Normalizer;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.plugin.java.JavaPlugin;

import fr.niware.nonbuild.db.ArenaDefinitionDb;
import fr.niware.nonbuild.db.ArenaDefinitionRow;
import fr.niware.nonbuild.db.SystemConfigDb;
import fr.niware.nonbuild.model.Arena;
import fr.niware.nonbuild.model.Point;
import fr.niware.nonbuild.schematic.SpongeSchematic;

public class ArenaStorage {

    private static final String CONFIG_KEY_SPAWN = "spawn";

    private final JavaPlugin plugin;
    private final ArenaDefinitionDb db;
    private final SystemConfigDb systemConfigDb;
    private final Map<String, Arena> arenas = new LinkedHashMap<>();

    public ArenaStorage(JavaPlugin plugin, ArenaDefinitionDb db, SystemConfigDb systemConfigDb) {
        this.plugin = plugin;
        this.db = db;
        this.systemConfigDb = systemConfigDb;
    }

    public void loadAll() {
        arenas.clear();
        try {
            List<ArenaDefinitionRow> rows = db.loadAll();
            for (ArenaDefinitionRow row : rows) {
                Arena arena = toArena(row);
                if (arena != null) {
                    arenas.put(arena.getSlug(), arena);
                }
            }
            plugin.getLogger().info("Arènes chargées depuis la base : " + arenas.size());
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur de chargement des arènes : " + e.getMessage());
        }
    }

    /**
     * Sauvegarde les metadata d'une arène (sans la schematic).
     */
    public void save(Arena arena) throws SQLException {
        ArenaDefinitionRow row = toRow(arena);
        db.save(row);
        arenas.put(arena.getSlug(), arena);
    }

    /**
     * Sauvegarde la schematic d'une arène en base.
     */
    public void saveSchematic(String slug, SpongeSchematic schematic) throws SQLException, IOException {
        byte[] data = schematic.toBytes();
        db.saveSchematic(slug, data);
        plugin.getLogger().info("Schematic " + slug + " sauvegardée en base (" + data.length + " octets)");
    }

    /**
     * Charge la schematic d'une arène depuis la base.
     * Retourne null si l'arène n'a pas de schematic.
     */
    public SpongeSchematic loadSchematic(String slug) throws SQLException, IOException {
        byte[] data = db.loadSchematic(slug);
        if (data == null || data.length == 0) {
            return null;
        }
        return SpongeSchematic.read(data);
    }

    /**
     * Retourne true si l'arène a une schematic en base.
     */
    public boolean hasSchematic(String slug) throws SQLException {
        byte[] data = db.loadSchematic(slug);
        return data != null && data.length > 0;
    }

    // ---- Spawn schematic (stored in system_configs) ----

    /**
     * Sauvegarde la spawn schematic en base.
     */
    public void saveSpawnSchematic(SpongeSchematic schematic) throws SQLException, IOException {
        byte[] data = schematic.toBytes();
        systemConfigDb.save(CONFIG_KEY_SPAWN, data);
        plugin.getLogger().info("Spawn schematic sauvegardée en base (" + data.length + " octets)");
    }

    /**
     * Charge la spawn schematic depuis la base.
     * Retourne null si aucune spawn n'est configurée.
     */
    public SpongeSchematic loadSpawnSchematic() throws SQLException, IOException {
        byte[] data = systemConfigDb.load(CONFIG_KEY_SPAWN);
        if (data == null || data.length == 0) {
            return null;
        }
        return SpongeSchematic.read(data);
    }

    /**
     * Retourne true si une spawn schematic existe en base.
     */
    public boolean hasSpawnSchematic() throws SQLException {
        byte[] data = systemConfigDb.load(CONFIG_KEY_SPAWN);
        return data != null && data.length > 0;
    }

    public boolean delete(String slug) {
        arenas.remove(slug);
        try {
            db.delete(slug);
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur de suppression de " + slug + " : " + e.getMessage());
        }
        return true;
    }

    /**
     * Renomme une arène : met à jour la base de données (slug + metadata).
     * La schematic est conservée (UPDATE du slug ne touche pas la colonne).
     * Retourne false si l'arène n'existe pas, le nouveau slug est invalide,
     * ou une autre arène porte déjà ce slug.
     */
    public boolean rename(String oldSlug, String newDisplayName) throws SQLException {
        Arena old = arenas.get(oldSlug);
        if (old == null) {
            return false;
        }
        String newSlug = slugify(newDisplayName);
        if (newSlug.isEmpty()) {
            return false;
        }
        if (arenas.containsKey(newSlug) && !newSlug.equals(oldSlug)) {
            return false;
        }

        boolean sameSlug = newSlug.equals(oldSlug);

        // Supprimer l'ancienne entrée (schematic reste orpheline 1 tick, puis nettoyée par ON DUPLICATE KEY)
        arenas.remove(oldSlug);
        if (!sameSlug) {
            try {
                db.delete(oldSlug);
            } catch (SQLException e) {
                plugin.getLogger().severe("Erreur de suppression de l'ancienne arène : " + e.getMessage());
            }
        }

        // Créer la nouvelle Arena avec le nouveau slug
        Arena renamed = new Arena(newSlug);
        renamed.setDisplayName(newDisplayName);
        renamed.setWorld(old.getWorld());
        renamed.setGameMode(old.getGameMode());
        renamed.setCorner1(old.getCorner1());
        renamed.setCorner2(old.getCorner2());
        renamed.setCenter(old.getCenter());
        renamed.setSpawn1(old.getSpawn1());
        renamed.setSpawn2(old.getSpawn2());
        renamed.setSavedAt(System.currentTimeMillis());

        save(renamed);
        return true;
    }

    public Arena get(String slug) {
        return arenas.get(slug);
    }

    public boolean exists(String slug) {
        return arenas.containsKey(slug);
    }

    public Collection<Arena> all() {
        return arenas.values();
    }

    /**
     * Retourne les slugs des arènes assignées au mode de jeu donné.
     */
    public java.util.Collection<String> byGameMode(String gameMode) {
        java.util.List<String> result = new java.util.ArrayList<>();
        for (Arena arena : arenas.values()) {
            if (gameMode.equals(arena.getGameMode())) {
                result.add(arena.getSlug());
            }
        }
        return result;
    }

    /**
     * Met à jour le mode de jeu d'une arène et sauvegarde en base.
     */
    public boolean setGameMode(String slug, String gameMode) throws SQLException {
        Arena arena = arenas.get(slug);
        if (arena == null) {
            return false;
        }
        arena.setGameMode(gameMode);
        save(arena);
        return true;
    }

    public int count() {
        return arenas.size();
    }

    private ArenaDefinitionRow toRow(Arena arena) {
        return new ArenaDefinitionRow(
                arena.getSlug(),
                arena.getDisplayName(),
                arena.getWorld(),
                arena.getGameMode(),
                arena.getCorner1()[0], arena.getCorner1()[1], arena.getCorner1()[2],
                arena.getCorner2()[0], arena.getCorner2()[1], arena.getCorner2()[2],
                arena.getCenter().x(), arena.getCenter().y(), arena.getCenter().z(),
                arena.getCenter().yaw(), arena.getCenter().pitch(),
                arena.getSpawn1().x(), arena.getSpawn1().y(), arena.getSpawn1().z(),
                arena.getSpawn1().yaw(), arena.getSpawn1().pitch(),
                arena.getSpawn2().x(), arena.getSpawn2().y(), arena.getSpawn2().z(),
                arena.getSpawn2().yaw(), arena.getSpawn2().pitch(),
                arena.getSavedAt(),
                null
        );
    }

    private Arena toArena(ArenaDefinitionRow row) {
        Arena arena = new Arena(row.slug());
        arena.setDisplayName(row.displayName());
        arena.setWorld(row.world());
        arena.setGameMode(row.gameMode());
        arena.setSavedAt(row.savedAt());
        arena.setCorner1(new int[]{row.corner1X(), row.corner1Y(), row.corner1Z()});
        arena.setCorner2(new int[]{row.corner2X(), row.corner2Y(), row.corner2Z()});
        arena.setCenter(new Point(row.centerX(), row.centerY(), row.centerZ(), row.centerYaw(), row.centerPitch()));
        arena.setSpawn1(new Point(row.spawn1X(), row.spawn1Y(), row.spawn1Z(), row.spawn1Yaw(), row.spawn1Pitch()));
        arena.setSpawn2(new Point(row.spawn2X(), row.spawn2Y(), row.spawn2Z(), row.spawn2Yaw(), row.spawn2Pitch()));
        if (!arena.isComplete() || arena.getWorld() == null) {
            return null;
        }
        return arena;
    }

    public static String slugify(String raw) {
        String normalized = Normalizer.normalize(raw, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
        String slug = normalized.replaceAll("[^a-z0-9_-]+", "-")
                .replaceAll("-{2,}", "-")
                .replaceAll("^-+|-+$", "");
        if (slug.length() > 40) {
            slug = slug.substring(0, 40).replaceAll("-+$", "");
        }
        return slug;
    }
}

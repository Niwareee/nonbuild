package fr.niware.nonbuild.storage;

import fr.niware.nonbuild.model.Arena;
import fr.niware.nonbuild.model.Point;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public class ArenaStorage {

    private final JavaPlugin plugin;
    private final Map<String, Arena> arenas = new LinkedHashMap<>();
    private final File arenasDir;
    private final File schematicsDir;

    public ArenaStorage(JavaPlugin plugin) {
        this.plugin = plugin;
        this.arenasDir = new File(plugin.getDataFolder(), "arenas");
        this.schematicsDir = new File(plugin.getDataFolder(), "schematics");
    }

    public void loadAll() {
        arenas.clear();
        File[] files = arenasDir.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) {
            return;
        }
        for (File file : files) {
            try {
                Arena arena = parse(YamlConfiguration.loadConfiguration(file));
                if (arena != null) {
                    arenas.put(arena.getSlug(), arena);
                } else {
                    plugin.getLogger().warning("Fichier d'arène invalide ignoré : " + file.getName());
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Impossible de lire " + file.getName() + " : " + e.getMessage());
            }
        }
    }

    public void save(Arena arena) throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("slug", arena.getSlug());
        yaml.set("display-name", arena.getDisplayName());
        yaml.set("world", arena.getWorld());
        yaml.set("saved-at", arena.getSavedAt());
        setBlock(yaml, "corner1", arena.getCorner1());
        setBlock(yaml, "corner2", arena.getCorner2());
        setPoint(yaml, "center", arena.getCenter());
        setPoint(yaml, "spawn1", arena.getSpawn1());
        setPoint(yaml, "spawn2", arena.getSpawn2());
        yaml.set("size.x", arena.sizeX());
        yaml.set("size.y", arena.sizeY());
        yaml.set("size.z", arena.sizeZ());
        yaml.set("volume", arena.volume());

        YamlFiles.saveAtomic(yaml, arenaFile(arena.getSlug()));
        arenas.put(arena.getSlug(), arena);
    }

    public boolean delete(String slug) {
        arenas.remove(slug);
        boolean removed = false;
        File yml = arenaFile(slug);
        if (yml.exists()) {
            removed = yml.delete();
        }
        File schem = schematicFile(slug);
        if (schem.exists()) {
            removed |= schem.delete();
        }
        return removed;
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

    public int count() {
        return arenas.size();
    }

    public File arenaFile(String slug) {
        return new File(arenasDir, slug + ".yml");
    }

    public File schematicFile(String slug) {
        return new File(schematicsDir, slug + ".schem");
    }

    private void setBlock(YamlConfiguration yaml, String path, int[] block) {
        yaml.set(path + ".x", block[0]);
        yaml.set(path + ".y", block[1]);
        yaml.set(path + ".z", block[2]);
    }

    private void setPoint(YamlConfiguration yaml, String path, Point point) {
        yaml.set(path + ".x", point.x());
        yaml.set(path + ".y", point.y());
        yaml.set(path + ".z", point.z());
        yaml.set(path + ".yaw", point.yaw());
        yaml.set(path + ".pitch", point.pitch());
    }

    private Arena parse(YamlConfiguration yaml) {
        String slug = yaml.getString("slug");
        if (slug == null || slug.isBlank()) {
            return null;
        }
        Arena arena = new Arena(slug);
        arena.setDisplayName(yaml.getString("display-name", slug));
        arena.setWorld(yaml.getString("world"));
        arena.setSavedAt(yaml.getLong("saved-at"));
        arena.setCorner1(readBlock(yaml, "corner1"));
        arena.setCorner2(readBlock(yaml, "corner2"));
        arena.setCenter(readPoint(yaml, "center"));
        arena.setSpawn1(readPoint(yaml, "spawn1"));
        arena.setSpawn2(readPoint(yaml, "spawn2"));
        if (!arena.isComplete() || arena.getWorld() == null) {
            return null;
        }
        return arena;
    }

    private int[] readBlock(YamlConfiguration yaml, String path) {
        ConfigurationSection section = yaml.getConfigurationSection(path);
        if (section == null) {
            return null;
        }
        return new int[]{section.getInt("x"), section.getInt("y"), section.getInt("z")};
    }

    private Point readPoint(YamlConfiguration yaml, String path) {
        ConfigurationSection section = yaml.getConfigurationSection(path);
        if (section == null) {
            return null;
        }
        return new Point(
                section.getDouble("x"),
                section.getDouble("y"),
                section.getDouble("z"),
                (float) section.getDouble("yaw"),
                (float) section.getDouble("pitch"));
    }

    public static String slugify(String raw) {
        String normalized = java.text.Normalizer.normalize(raw, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(java.util.Locale.ROOT);
        String slug = normalized.replaceAll("[^a-z0-9_-]+", "-")
                .replaceAll("-{2,}", "-")
                .replaceAll("^-+|-+$", "");
        if (slug.length() > 40) {
            slug = slug.substring(0, 40).replaceAll("-+$", "");
        }
        return slug;
    }
}

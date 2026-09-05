package fr.niware.nonbuild.storage;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import fr.niware.nonbuild.model.DeployedInstance;
import fr.niware.nonbuild.model.Point;

/**
 * Stocke les instances déployées dans deployments.yml.
 * Ce fichier est le contrat lu par le plugin practice : chaque instance expose
 * son monde, son centre, ses coins, ses deux spawns et sa cellule d'emprise.
 * La sauvegarde est faite en async pour ne jamais bloquer le main thread.
 */
public class DeploymentStorage {

    private final JavaPlugin plugin;
    private final Map<String, DeployedInstance> instances = new LinkedHashMap<>();
    private final File file;

    public DeploymentStorage(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "deployments.yml");
    }

    public void load() {
        instances.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("instances");
        if (root == null) {
            return;
        }
        for (String name : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(name);
            if (section == null) {
                continue;
            }
            DeployedInstance instance = parse(name, section);
            if (instance != null) {
                instances.put(name, instance);
            } else {
                plugin.getLogger().warning("Instance déployée invalide ignorée : " + name);
            }
        }
    }

    /**
     * Sauvegarde le registre (écriture atomique).
     * La sérialisation YAML est faite en async pour ne pas bloquer le main thread.
     * Retourne un Runnable qui, quand exécuté, attend la fin de l'écriture.
     * Pour un usage synchrone (deploy séquentiel), appeler le Runnable retourné.
     * Pour un usage asynchrone (put/remove/clear), ignorer le retour.
     */
    public Runnable save() {
        Map<String, DeployedInstance> snapshot = new LinkedHashMap<>(instances);
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            YamlConfiguration yaml = new YamlConfiguration();
            for (DeployedInstance instance : snapshot.values()) {
                String path = "instances." + instance.getName();
                yaml.set(path + ".arena", instance.getArena());
                yaml.set(path + ".world", instance.getWorld());
                yaml.set(path + ".deployed-at", instance.getDeployedAt());
                setPoint(yaml, path + ".center", instance.getCenter());
                setBlock(yaml, path + ".corner1", instance.getCorner1());
                setBlock(yaml, path + ".corner2", instance.getCorner2());
                setPoint(yaml, path + ".spawn1", instance.getSpawn1());
                setPoint(yaml, path + ".spawn2", instance.getSpawn2());
                yaml.set(path + ".cell.min-x", instance.getCellMinXZ()[0]);
                yaml.set(path + ".cell.min-z", instance.getCellMinXZ()[1]);
                yaml.set(path + ".cell.max-x", instance.getCellMaxXZ()[0]);
                yaml.set(path + ".cell.max-z", instance.getCellMaxXZ()[1]);
            }
            try {
                YamlFiles.saveAtomic(yaml, file);
            } catch (IOException e) {
                plugin.getLogger().severe("Impossible de sauvegarder deployments.yml : " + e.getMessage());
            }
            latch.countDown();
        });
        return () -> {
            try {
                latch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };
    }

    /**
     * Sauvegarde synchrone (main thread). Utilisée par le pipeline de deploy
     * qui doit garantir que chaque instance est persistée avant la suivante.
     */
    public void saveSync() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (DeployedInstance instance : instances.values()) {
            String path = "instances." + instance.getName();
            yaml.set(path + ".arena", instance.getArena());
            yaml.set(path + ".world", instance.getWorld());
            yaml.set(path + ".deployed-at", instance.getDeployedAt());
            setPoint(yaml, path + ".center", instance.getCenter());
            setBlock(yaml, path + ".corner1", instance.getCorner1());
            setBlock(yaml, path + ".corner2", instance.getCorner2());
            setPoint(yaml, path + ".spawn1", instance.getSpawn1());
            setPoint(yaml, path + ".spawn2", instance.getSpawn2());
            yaml.set(path + ".cell.min-x", instance.getCellMinXZ()[0]);
            yaml.set(path + ".cell.min-z", instance.getCellMinXZ()[1]);
            yaml.set(path + ".cell.max-x", instance.getCellMaxXZ()[0]);
            yaml.set(path + ".cell.max-z", instance.getCellMaxXZ()[1]);
        }
        try {
            YamlFiles.saveAtomic(yaml, file);
        } catch (IOException e) {
            plugin.getLogger().severe("Impossible de sauvegarder deployments.yml : " + e.getMessage());
        }
    }

    public void put(DeployedInstance instance) {
        instances.put(instance.getName(), instance);
        saveSync();
    }

    public boolean remove(String name) {
        if (instances.remove(name) == null) {
            return false;
        }
        saveSync();
        return true;
    }

    public void clear() {
        instances.clear();
        save();
    }

    public DeployedInstance get(String name) {
        return instances.get(name);
    }

    public Collection<DeployedInstance> all() {
        return instances.values();
    }

    public List<DeployedInstance> byArena(String arenaSlug) {
        List<DeployedInstance> result = new ArrayList<>();
        for (DeployedInstance instance : instances.values()) {
            if (instance.getArena().equals(arenaSlug)) {
                result.add(instance);
            }
        }
        return result;
    }

    /**
     * Retourne les instances dont le slug d'arène correspond (case-insensitive)
     * à la clé du mode de jeu. Ex: mode "GETDOWN" → arènes "getdown", "getdown-2" etc.
     */
    public List<DeployedInstance> byGameMode(String modeKey) {
        List<DeployedInstance> result = new ArrayList<>();
        String lowerKey = modeKey.toLowerCase(java.util.Locale.ROOT);
        for (DeployedInstance instance : instances.values()) {
            if (instance.getArena().toLowerCase(java.util.Locale.ROOT).startsWith(lowerKey)) {
                result.add(instance);
            }
        }
        return result;
    }

    /**
     * Met à jour le slug d'arène de toutes les instances déployées.
     * Les coordonnées physiques restent inchangées ; seul le champ {@code arena}
     * dans deployments.yml est mis à jour.
     */
    public int renameArena(String oldSlug, String newSlug) {
        int updated = 0;
        for (Map.Entry<String, DeployedInstance> entry : new java.util.ArrayList<>(instances.entrySet())) {
            DeployedInstance instance = entry.getValue();
            if (instance.getArena().equals(oldSlug)) {
                DeployedInstance updatedInstance = new DeployedInstance(
                        instance.getName(), newSlug, instance.getWorld(), instance.getCenter(),
                        instance.getCorner1(), instance.getCorner2(), instance.getSpawn1(), instance.getSpawn2(),
                        instance.getCellMinXZ(), instance.getCellMaxXZ(), instance.getDeployedAt());
                instances.put(entry.getKey(), updatedInstance);
                updated++;
            }
        }
        if (updated > 0) {
            saveSync();
        }
        return updated;
    }

    public int count() {
        return instances.size();
    }

    /**
     * Prochain numéro libre pour nommer une instance "slug-N".
     */
    public int nextIndex(String arenaSlug) {
        int max = 0;
        for (DeployedInstance instance : byArena(arenaSlug)) {
            String name = instance.getName();
            int dash = name.lastIndexOf('-');
            if (dash >= 0) {
                try {
                    max = Math.max(max, Integer.parseInt(name.substring(dash + 1)));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return max + 1;
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

    private DeployedInstance parse(String name, ConfigurationSection section) {
        String arena = section.getString("arena");
        String world = section.getString("world");
        Point center = readPoint(section, "center");
        int[] corner1 = readBlock(section, "corner1");
        int[] corner2 = readBlock(section, "corner2");
        Point spawn1 = readPoint(section, "spawn1");
        Point spawn2 = readPoint(section, "spawn2");
        ConfigurationSection cell = section.getConfigurationSection("cell");
        if (arena == null || world == null || center == null || corner1 == null || corner2 == null
                || spawn1 == null || spawn2 == null || cell == null) {
            return null;
        }
        int[] cellMin = {cell.getInt("min-x"), cell.getInt("min-z")};
        int[] cellMax = {cell.getInt("max-x"), cell.getInt("max-z")};
        return new DeployedInstance(name, arena, world, center, corner1, corner2,
                spawn1, spawn2, cellMin, cellMax, section.getLong("deployed-at"));
    }

    private int[] readBlock(ConfigurationSection section, String path) {
        ConfigurationSection sub = section.getConfigurationSection(path);
        if (sub == null) {
            return null;
        }
        return new int[]{sub.getInt("x"), sub.getInt("y"), sub.getInt("z")};
    }

    private Point readPoint(ConfigurationSection section, String path) {
        ConfigurationSection sub = section.getConfigurationSection(path);
        if (sub == null) {
            return null;
        }
        return new Point(
                sub.getDouble("x"),
                sub.getDouble("y"),
                sub.getDouble("z"),
                (float) sub.getDouble("yaw"),
                (float) sub.getDouble("pitch"));
    }
}

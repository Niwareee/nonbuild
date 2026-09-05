package fr.niware.nonbuild.storage;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import fr.niware.nonbuild.db.DeployedInstanceRow;
import fr.niware.nonbuild.db.DeploymentDb;
import fr.niware.nonbuild.model.DeployedInstance;
import fr.niware.nonbuild.model.Point;

public class DeploymentStorage {

    private final JavaPlugin plugin;
    private final DeploymentDb db;
    private final Map<String, DeployedInstance> instances = new LinkedHashMap<>();

    public DeploymentStorage(JavaPlugin plugin, DeploymentDb db) {
        this.plugin = plugin;
        this.db = db;
    }

    public void load() {
        instances.clear();
        try {
            List<String> order = new ArrayList<>();
            Map<String, DeployedInstanceRow> rows = db.loadAll(order);
            for (DeployedInstanceRow row : rows.values()) {
                DeployedInstance instance = toDeployedInstance(row);
                if (instance != null) {
                    instances.put(instance.getName(), instance);
                }
            }
            plugin.getLogger().info("Instances déployées chargées depuis la base : " + instances.size());
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur de chargement des instances : " + e.getMessage());
        }
    }

    public void save() {
        try {
            for (DeployedInstance instance : instances.values()) {
                DeployedInstanceRow row = toRow(instance);
                db.save(row);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur de sauvegarde des instances : " + e.getMessage());
        }
    }

    public void saveSync() {
        save();
    }

    public void put(DeployedInstance instance) {
        instances.put(instance.getName(), instance);
        try {
            db.save(toRow(instance));
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur de sauvegarde de " + instance.getName() + " : " + e.getMessage());
        }
    }

    public boolean remove(String name) {
        if (instances.remove(name) == null) {
            return false;
        }
        try {
            db.delete(name);
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur de suppression de " + name + " : " + e.getMessage());
        }
        return true;
    }

    public void clear() {
        instances.clear();
        try {
            db.clear();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur de suppression de toutes les instances : " + e.getMessage());
        }
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

    public List<DeployedInstance> byArenaSlugs(java.util.Collection<String> arenaSlugs) {
        List<DeployedInstance> result = new ArrayList<>();
        java.util.Set<String> slugSet = new java.util.HashSet<>(arenaSlugs);
        for (DeployedInstance instance : instances.values()) {
            if (slugSet.contains(instance.getArena())) {
                result.add(instance);
            }
        }
        return result;
    }

    public int renameArena(String oldSlug, String newSlug) {
        int updated = 0;
        for (Map.Entry<String, DeployedInstance> entry : new java.util.ArrayList<>(instances.entrySet())) {
            DeployedInstance instance = entry.getValue();
            if (instance.getArena().equals(oldSlug)) {
                String newName = instance.getName().replaceFirst("^" + oldSlug + "(-|$)", newSlug + "$1");
                DeployedInstance updatedInstance = new DeployedInstance(
                        newName, newSlug, instance.getWorld(), instance.getCenter(),
                        instance.getCorner1(), instance.getCorner2(), instance.getSpawn1(), instance.getSpawn2(),
                        instance.getCellMinXZ(), instance.getCellMaxXZ(), instance.getDeployedAt());
                instances.remove(entry.getKey());
                instances.put(newName, updatedInstance);

                try {
                    db.delete(entry.getKey());
                    db.save(toRow(updatedInstance));
                } catch (SQLException e) {
                    plugin.getLogger().severe("Erreur de renommage de " + entry.getKey() + " : " + e.getMessage());
                }
                updated++;
            }
        }
        return updated;
    }

    public int count() {
        return instances.size();
    }


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

    // --- Conversion helpers ---

    private static DeployedInstance toDeployedInstance(DeployedInstanceRow row) {
        Point center = new Point(row.centerX(), row.centerY(), row.centerZ(), row.centerYaw(), row.centerPitch());

        int[] corner1 = row.corner1X() != null ?
                new int[]{row.corner1X(), row.corner1Y(), row.corner1Z()} : null;
        int[] corner2 = row.corner2X() != null ?
                new int[]{row.corner2X(), row.corner2Y(), row.corner2Z()} : null;

        Point spawn1 = row.spawn1X() != null ?
                new Point(row.spawn1X(), row.spawn1Y(), row.spawn1Z(), row.spawn1Yaw(), row.spawn1Pitch()) : null;
        Point spawn2 = row.spawn2X() != null ?
                new Point(row.spawn2X(), row.spawn2Y(), row.spawn2Z(), row.spawn2Yaw(), row.spawn2Pitch()) : null;

        int[] cellMin = row.cellMinX() != null ?
                new int[]{row.cellMinX(), row.cellMinZ()} : null;
        int[] cellMax = row.cellMaxX() != null ?
                new int[]{row.cellMaxX(), row.cellMaxZ()} : null;

        if (corner1 == null || corner2 == null || spawn1 == null || spawn2 == null || cellMin == null || cellMax == null) {
            return null;
        }

        return new DeployedInstance(
                row.instanceName(), row.arena(), row.world(),
                center, corner1, corner2, spawn1, spawn2,
                cellMin, cellMax, row.deployedAt());
    }

    private static DeployedInstanceRow toRow(DeployedInstance inst) {
        return new DeployedInstanceRow(
                inst.getName(), inst.getArena(), inst.getWorld(),
                inst.getCenter().x(), inst.getCenter().y(), inst.getCenter().z(),
                inst.getCenter().yaw(), inst.getCenter().pitch(),
                inst.getCorner1()[0], inst.getCorner1()[1], inst.getCorner1()[2],
                inst.getCorner2()[0], inst.getCorner2()[1], inst.getCorner2()[2],
                inst.getSpawn1().x(), inst.getSpawn1().y(), inst.getSpawn1().z(),
                inst.getSpawn1().yaw(), inst.getSpawn1().pitch(),
                inst.getSpawn2().x(), inst.getSpawn2().y(), inst.getSpawn2().z(),
                inst.getSpawn2().yaw(), inst.getSpawn2().pitch(),
                inst.getCellMinXZ()[0], inst.getCellMinXZ()[1],
                inst.getCellMaxXZ()[0], inst.getCellMaxXZ()[1],
                inst.getDeployedAt()
        );
    }
}

package fr.niware.nonbuild.edit;

import org.bukkit.GameMode;
import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;

public class EditSession {

    private final String slug;
    private final String displayName;
    private final String world;

    private Location corner1;
    private Location corner2;
    private Location center;
    private Location spawn1;
    private Location spawn2;

    private final GameMode previousGameMode;
    private boolean creativeApplied;

    public EditSession(String slug, String displayName, String world, GameMode previousGameMode) {
        this.slug = slug;
        this.displayName = displayName;
        this.world = world;
        this.previousGameMode = previousGameMode;
    }

    public String getSlug() {
        return slug;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getWorld() {
        return world;
    }

    public Location getCorner1() {
        return corner1;
    }

    public void setCorner1(Location corner1) {
        this.corner1 = corner1;
    }

    public Location getCorner2() {
        return corner2;
    }

    public void setCorner2(Location corner2) {
        this.corner2 = corner2;
    }

    public Location getCenter() {
        return center;
    }

    public void setCenter(Location center) {
        this.center = center;
    }

    public Location getSpawn1() {
        return spawn1;
    }

    public void setSpawn1(Location spawn1) {
        this.spawn1 = spawn1;
    }

    public Location getSpawn2() {
        return spawn2;
    }

    public void setSpawn2(Location spawn2) {
        this.spawn2 = spawn2;
    }

    public GameMode getPreviousGameMode() {
        return previousGameMode;
    }

    public boolean isCreativeApplied() {
        return creativeApplied;
    }

    public void setCreativeApplied(boolean creativeApplied) {
        this.creativeApplied = creativeApplied;
    }

    public boolean isComplete() {
        return corner1 != null && corner2 != null && center != null && spawn1 != null && spawn2 != null;
    }

    public List<String> missingPoints() {
        List<String> missing = new ArrayList<>();
        if (corner1 == null) missing.add("/build setcorner1");
        if (corner2 == null) missing.add("/build setcorner2");
        if (spawn1 == null) missing.add("/build setspawn1");
        if (spawn2 == null) missing.add("/build setspawn2");
        if (center == null) missing.add("/build setcenter");
        return missing;
    }
}

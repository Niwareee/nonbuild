package fr.niware.nonbuild.model;

public class Arena {

    private final String slug;
    private String displayName;
    private String world;
    /** Mode de jeu assigné (ex. "GETDOWN"). Nullable si non encore assigné. */
    private String gameMode;
    private int[] corner1;
    private int[] corner2;
    private Point center;
    private Point spawn1;
    private Point spawn2;
    private long savedAt;

    public Arena(String slug) {
        this.slug = slug;
    }

    public String getSlug() {
        return slug;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getWorld() {
        return world;
    }

    public void setWorld(String world) {
        this.world = world;
    }

    public int[] getCorner1() {
        return corner1;
    }

    public void setCorner1(int[] corner1) {
        this.corner1 = corner1;
    }

    public int[] getCorner2() {
        return corner2;
    }

    public void setCorner2(int[] corner2) {
        this.corner2 = corner2;
    }

    public Point getCenter() {
        return center;
    }

    public void setCenter(Point center) {
        this.center = center;
    }

    public Point getSpawn1() {
        return spawn1;
    }

    public void setSpawn1(Point spawn1) {
        this.spawn1 = spawn1;
    }

    public Point getSpawn2() {
        return spawn2;
    }

    public void setSpawn2(Point spawn2) {
        this.spawn2 = spawn2;
    }

    public long getSavedAt() {
        return savedAt;
    }

    public void setSavedAt(long savedAt) {
        this.savedAt = savedAt;
    }

    public String getGameMode() {
        return gameMode;
    }

    public void setGameMode(String gameMode) {
        this.gameMode = gameMode;
    }

    public int minX() {
        return Math.min(corner1[0], corner2[0]);
    }

    public int maxX() {
        return Math.max(corner1[0], corner2[0]);
    }

    public int minY() {
        return Math.min(corner1[1], corner2[1]);
    }

    public int maxY() {
        return Math.max(corner1[1], corner2[1]);
    }

    public int minZ() {
        return Math.min(corner1[2], corner2[2]);
    }

    public int maxZ() {
        return Math.max(corner1[2], corner2[2]);
    }

    public int sizeX() {
        return maxX() - minX() + 1;
    }

    public int sizeY() {
        return maxY() - minY() + 1;
    }

    public int sizeZ() {
        return maxZ() - minZ() + 1;
    }

    public long volume() {
        return (long) sizeX() * sizeY() * sizeZ();
    }

    public boolean contains(Point point) {
        return point.x() >= minX() && point.x() <= maxX() + 1
                && point.y() >= minY() && point.y() <= maxY() + 1
                && point.z() >= minZ() && point.z() <= maxZ() + 1;
    }

    public boolean isComplete() {
        return corner1 != null && corner2 != null && center != null && spawn1 != null && spawn2 != null;
    }
}

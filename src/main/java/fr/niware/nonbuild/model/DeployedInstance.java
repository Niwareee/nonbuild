package fr.niware.nonbuild.model;

public class DeployedInstance {

    private final String name;
    private final String arena;
    private final String world;
    private final Point center;
    private final int[] corner1;
    private final int[] corner2;
    private final Point spawn1;
    private final Point spawn2;
    private final int[] cellMinXZ;
    private final int[] cellMaxXZ;
    private final long deployedAt;

    public DeployedInstance(String name, String arena, String world, Point center,
                            int[] corner1, int[] corner2, Point spawn1, Point spawn2,
                            int[] cellMinXZ, int[] cellMaxXZ, long deployedAt) {
        this.name = name;
        this.arena = arena;
        this.world = world;
        this.center = center;
        this.corner1 = corner1;
        this.corner2 = corner2;
        this.spawn1 = spawn1;
        this.spawn2 = spawn2;
        this.cellMinXZ = cellMinXZ;
        this.cellMaxXZ = cellMaxXZ;
        this.deployedAt = deployedAt;
    }

    public String getName() {
        return name;
    }

    public String getArena() {
        return arena;
    }

    public String getWorld() {
        return world;
    }

    public Point getCenter() {
        return center;
    }

    public int[] getCorner1() {
        return corner1;
    }

    public int[] getCorner2() {
        return corner2;
    }

    public Point getSpawn1() {
        return spawn1;
    }

    public Point getSpawn2() {
        return spawn2;
    }

    public int[] getCellMinXZ() {
        return cellMinXZ;
    }

    public int[] getCellMaxXZ() {
        return cellMaxXZ;
    }

    public long getDeployedAt() {
        return deployedAt;
    }
}

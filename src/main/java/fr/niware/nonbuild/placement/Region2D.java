package fr.niware.nonbuild.placement;

/**
 * Rectangle XZ (bornes inclusives).
 */
public record Region2D(int minX, int minZ, int maxX, int maxZ) {

    public boolean intersects(Region2D other) {
        return minX <= other.maxX && other.minX <= maxX
                && minZ <= other.maxZ && other.minZ <= maxZ;
    }
}

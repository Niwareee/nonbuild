package fr.niware.nonbuild.db;

/**
 * Row returned by the arena_definitions table.
 */
public record ArenaDefinitionRow(
        String slug,
        String displayName,
        String world,
        String gameMode,
        int corner1X, int corner1Y, int corner1Z,
        int corner2X, int corner2Y, int corner2Z,
        double centerX, double centerY, double centerZ,
        float centerYaw, float centerPitch,
        double spawn1X, double spawn1Y, double spawn1Z,
        float spawn1Yaw, float spawn1Pitch,
        double spawn2X, double spawn2Y, double spawn2Z,
        float spawn2Yaw, float spawn2Pitch,
        long savedAt,
        byte[] schematic
) {
    /**
     * Metadata-only row (schematic = null). Used for listing/filtering.
     */
    public ArenaDefinitionRow withoutSchematic() {
        return new ArenaDefinitionRow(slug, displayName, world, gameMode,
                corner1X, corner1Y, corner1Z,
                corner2X, corner2Y, corner2Z,
                centerX, centerY, centerZ, centerYaw, centerPitch,
                spawn1X, spawn1Y, spawn1Z, spawn1Yaw, spawn1Pitch,
                spawn2X, spawn2Y, spawn2Z, spawn2Yaw, spawn2Pitch,
                savedAt, null);
    }

    public boolean hasSchematic() {
        return schematic != null && schematic.length > 0;
    }
}

package fr.niware.nonbuild.db;

public record DeployedInstanceRow(
        String instanceName,
        String arena,
        String world,
        double centerX, double centerY, double centerZ,
        float centerYaw, float centerPitch,
        Integer corner1X, Integer corner1Y, Integer corner1Z,
        Integer corner2X, Integer corner2Y, Integer corner2Z,
        Double spawn1X, Double spawn1Y, Double spawn1Z,
        Float spawn1Yaw, Float spawn1Pitch,
        Double spawn2X, Double spawn2Y, Double spawn2Z,
        Float spawn2Yaw, Float spawn2Pitch,
        Integer cellMinX, Integer cellMinZ,
        Integer cellMaxX, Integer cellMaxZ,
        long deployedAt
) {}
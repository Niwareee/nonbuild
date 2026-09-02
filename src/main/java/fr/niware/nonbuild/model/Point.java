package fr.niware.nonbuild.model;

import org.bukkit.Location;
import org.bukkit.World;

public record Point(double x, double y, double z, float yaw, float pitch) {

    public static Point of(Location location) {
        return new Point(location.getX(), location.getY(), location.getZ(),
                location.getYaw(), location.getPitch());
    }

    public static Point of(double x, double y, double z) {
        return new Point(x, y, z, 0f, 0f);
    }

    public Point withOffset(double dx, double dy, double dz) {
        return new Point(x + dx, y + dy, z + dz, yaw, pitch);
    }

    public Location toLocation(World world) {
        return new Location(world, x, y, z, yaw, pitch);
    }
}

package fr.niware.nonbuild;

import org.bukkit.plugin.java.JavaPlugin;

public class Settings {

    private final JavaPlugin plugin;

    public Settings(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public String buildWorld() {
        return plugin.getConfig().getString("worlds.build", "build");
    }

    public String prodWorld() {
        return plugin.getConfig().getString("worlds.prod", "world");
    }

    public int spawnProtectionRadius() {
        return plugin.getConfig().getInt("placement.spawn-protection-radius", 512);
    }

    public int margin() {
        return plugin.getConfig().getInt("placement.margin", 32);
    }

    public int pasteY() {
        return plugin.getConfig().getInt("placement.paste-y", 60);
    }

    public int blocksPerTick() {
        return Math.max(1000, plugin.getConfig().getInt("pasting.blocks-per-tick", 20000));
    }

    public int captureBlocksPerTick() {
        return Math.max(1000, plugin.getConfig().getInt("pasting.capture-blocks-per-tick", 50000));
    }

    public int maxVolume() {
        return plugin.getConfig().getInt("limits.max-volume", 4_000_000);
    }

    public boolean setCreativeOnEdit() {
        return plugin.getConfig().getBoolean("edit.set-creative", true);
    }
}

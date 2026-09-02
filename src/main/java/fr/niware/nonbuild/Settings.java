package fr.niware.nonbuild;

import org.bukkit.plugin.java.JavaPlugin;

public class Settings {

    private final String buildWorld;
    private final String prodWorld;
    private final int spawnProtectionRadius;
    private final int margin;
    private final int pasteY;
    private final int blocksPerTick;
    private final int captureBlocksPerTick;
    private final int maxVolume;
    private final boolean setCreativeOnEdit;

    public Settings(JavaPlugin plugin) {
        org.bukkit.configuration.Configuration cfg = plugin.getConfig();
        this.buildWorld = cfg.getString("worlds.build", "build");
        this.prodWorld = cfg.getString("worlds.prod", "world");
        this.spawnProtectionRadius = cfg.getInt("placement.spawn-protection-radius", 512);
        this.margin = cfg.getInt("placement.margin", 32);
        this.pasteY = cfg.getInt("placement.paste-y", 60);
        this.blocksPerTick = Math.max(1000, cfg.getInt("pasting.blocks-per-tick", 20000));
        this.captureBlocksPerTick = Math.max(1000, cfg.getInt("pasting.capture-blocks-per-tick", 50000));
        this.maxVolume = cfg.getInt("limits.max-volume", 4_000_000);
        this.setCreativeOnEdit = cfg.getBoolean("edit.set-creative", true);
    }

    public String buildWorld() {
        return buildWorld;
    }

    public String prodWorld() {
        return prodWorld;
    }

    public int spawnProtectionRadius() {
        return spawnProtectionRadius;
    }

    public int margin() {
        return margin;
    }

    public int pasteY() {
        return pasteY;
    }

    public int blocksPerTick() {
        return blocksPerTick;
    }

    public int captureBlocksPerTick() {
        return captureBlocksPerTick;
    }

    public int maxVolume() {
        return maxVolume;
    }

    public boolean setCreativeOnEdit() {
        return setCreativeOnEdit;
    }
}

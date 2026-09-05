package fr.niware.nonbuild;

import java.util.LinkedHashMap;
import java.util.Map;

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
    private final Map<String, String> gameModes;

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

        // Lire la liste des modes de jeu (clé → nom affiché)
        Map<String, String> modes = new LinkedHashMap<>();
        org.bukkit.configuration.ConfigurationSection section = cfg.getConfigurationSection("game-modes");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                modes.put(key, section.getString(key, key));
            }
        }
        this.gameModes = Map.copyOf(modes);
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

    public Map<String, String> gameModes() {
        return gameModes;
    }
}

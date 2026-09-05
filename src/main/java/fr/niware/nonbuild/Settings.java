package fr.niware.nonbuild;

import java.util.LinkedHashMap;
import java.util.Map;

import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

public class Settings {

    private final String buildWorld;
    private final String prodWorld;
    private final int spawnProtectionRadius;
    private final int margin;
    private final int pasteY;
    private final int blocksPerTick;
    private final int captureBlocksPerTick;
    private final boolean setCreativeOnEdit;
    private final Map<String, String> gameModes;
    private final String dbHost;
    private final int dbPort;
    private final String dbName;
    private final String dbUser;
    private final String dbPassword;
    private final int dbPoolSize;

    public Settings(JavaPlugin plugin) {
        Configuration cfg = plugin.getConfig();
        this.buildWorld = cfg.getString("worlds.build", "build");
        this.prodWorld = cfg.getString("worlds.prod", "world");
        this.spawnProtectionRadius = cfg.getInt("placement.spawn-protection-radius", 512);
        this.margin = cfg.getInt("placement.margin", 32);
        this.pasteY = cfg.getInt("placement.paste-y", 60);
        this.blocksPerTick = Math.max(1000, cfg.getInt("pasting.blocks-per-tick", 20000));
        this.captureBlocksPerTick = Math.max(1000, cfg.getInt("pasting.capture-blocks-per-tick", 50000));
        this.setCreativeOnEdit = cfg.getBoolean("edit.set-creative", true);

        this.dbHost = cfg.getString("database.host", "127.0.0.1");
        this.dbPort = cfg.getInt("database.port", 3306);
        this.dbName = cfg.getString("database.name", "nontia");
        this.dbUser = cfg.getString("database.user", "nontia");
        this.dbPassword = cfg.getString("database.password", "");
        this.dbPoolSize = cfg.getInt("database.pool-size", 4);

        // Lire la liste des modes de jeu (clé → nom affiché)
        Map<String, String> modes = new LinkedHashMap<>();
        ConfigurationSection section = cfg.getConfigurationSection("game-modes");
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

    public boolean setCreativeOnEdit() {
        return setCreativeOnEdit;
    }

    public Map<String, String> gameModes() {
        return gameModes;
    }

    public String dbHost() {
        return dbHost;
    }

    public int dbPort() {
        return dbPort;
    }

    public String dbName() {
        return dbName;
    }

    public String dbUser() {
        return dbUser;
    }

    public String dbPassword() {
        return dbPassword;
    }

    public int dbPoolSize() {
        return dbPoolSize;
    }
}

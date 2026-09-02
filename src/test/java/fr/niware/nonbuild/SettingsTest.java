package fr.niware.nonbuild;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SettingsTest {

    private Settings settings(String yaml) throws Exception {
        JavaPlugin plugin = mock(JavaPlugin.class);
        YamlConfiguration config = new YamlConfiguration();
        if (yaml != null && !yaml.isBlank()) {
            config.loadFromString(yaml);
        }
        when(plugin.getConfig()).thenReturn(config);
        return new Settings(plugin);
    }

    @Test
    void valeursParDefaut() throws Exception {
        Settings settings = settings("");
        assertEquals("build", settings.buildWorld());
        assertEquals("world", settings.prodWorld());
        assertEquals(512, settings.spawnProtectionRadius());
        assertEquals(32, settings.margin());
        assertEquals(60, settings.pasteY());
        assertEquals(20_000, settings.blocksPerTick());
        assertEquals(50_000, settings.captureBlocksPerTick());
        assertEquals(4_000_000, settings.maxVolume());
        assertTrue(settings.setCreativeOnEdit());
    }

    @Test
    void valeursPersonnalisees() throws Exception {
        Settings settings = settings("""
                worlds:
                  build: "monbuild"
                  prod: "maprod"
                placement:
                  spawn-protection-radius: 256
                  margin: 16
                  paste-y: 98
                pasting:
                  blocks-per-tick: 5000
                  capture-blocks-per-tick: 8000
                limits:
                  max-volume: 100000
                edit:
                  set-creative: false
                """);
        assertEquals("monbuild", settings.buildWorld());
        assertEquals("maprod", settings.prodWorld());
        assertEquals(256, settings.spawnProtectionRadius());
        assertEquals(16, settings.margin());
        assertEquals(98, settings.pasteY());
        assertEquals(5000, settings.blocksPerTick());
        assertEquals(8000, settings.captureBlocksPerTick());
        assertEquals(100_000, settings.maxVolume());
        assertFalse(settings.setCreativeOnEdit());
    }

    @Test
    void lesBudgetsOntUnPlancher() throws Exception {
        Settings settings = settings("""
                pasting:
                  blocks-per-tick: 1
                  capture-blocks-per-tick: 1
                """);
        assertEquals(1000, settings.blocksPerTick());
        assertEquals(1000, settings.captureBlocksPerTick());
    }
}

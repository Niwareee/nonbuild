package fr.niware.nonbuild.storage;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import fr.niware.nonbuild.model.DeployedInstance;
import fr.niware.nonbuild.model.Point;
import fr.niware.nonbuild.testutil.BukkitServerFixture;

/**
 * Test de CONTRAT du format deployments.yml — côté écrivain (NonBuild).
 *
 * Ce fichier est l'API inter-plugins : le plugin practice (nongame) le lit via
 * {@code DeploymentReader}. Ce test verrouille la structure exacte produite par
 * {@link DeploymentStorage#save()} : chaque clé documentée dans AGENTS.md doit
 * être présente, du bon type, et survivre à un aller-retour écriture/lecture.
 *
 * Le miroir de ce test côté nongame (même fixture) verrouille le lecteur. Toute
 * évolution du format est additive et doit être validée des deux côtés.
 */
class DeploymentContractTest {

    @TempDir
    File tempDir;

    private JavaPlugin plugin;

    @BeforeEach
    void setup() {
        // save() passe par le scheduler (écriture async) : le fixture exécute les
        // tâches inline, donc le fichier est écrit avant la fin de put().
        BukkitServerFixture.ensure();
        plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(tempDir);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("DeploymentContractTest"));
    }

    /**
     * Instances canoniques du contrat : deux arènes, avec un TROU de numérotation
     * (getdown-1 puis getdown-3, pas de -2) pour documenter que la continuité n'est
     * jamais garantie. Les valeurs sont celles de référence du contrat.
     */
    private void writeCanonicalRegistry() {
        DeploymentStorage storage = new DeploymentStorage(plugin);
        storage.put(instance("getdown-1", "getdown", 1_756_000_000_000L));
        storage.put(instance("getdown-3", "getdown", 1_756_000_000_001L));
        storage.put(instance("yacht-1", "yacht", 1_756_000_000_002L));
    }

    private DeployedInstance instance(String name, String arena, long deployedAt) {
        return new DeployedInstance(name, arena, "world",
                new Point(100.5, 70, -200.5, 0f, 0f),
                new int[]{90, 64, -210},
                new int[]{110, 75, -190},
                new Point(95.5, 65, -200.5, 90f, 0f),
                new Point(105.5, 65, -200.5, -90f, 0f),
                new int[]{80, -220},
                new int[]{240, -60},
                deployedAt);
    }

    @Test
    void leWriterProduitLaStructureExacteDuContrat() {
        writeCanonicalRegistry();

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new File(tempDir, "deployments.yml"));
        ConfigurationSection root = yaml.getConfigurationSection("instances");
        assertNotNull(root, "la section racine 'instances' doit exister");
        assertEquals(List.of("getdown-1", "getdown-3", "yacht-1"), new ArrayList<>(root.getKeys(false)),
                "les noms d'instance sont les clés de 'instances' (ordre d'insertion)");

        for (String name : root.getKeys(false)) {
            assertContractStructure(root.getConfigurationSection(name), name);
        }
    }

    /**
     * Vérifie que chaque clé du contrat est présente et du bon type pour une instance.
     * C'est le verrou de format : une clé renommée/retirée/retypée casse le test.
     */
    private void assertContractStructure(ConfigurationSection s, String name) {
        assertNotNull(s, "l'instance " + name + " doit être une section");

        // scalaires
        assertTrue(s.isString("arena"), name + ".arena doit être une chaîne");
        assertTrue(s.isString("world"), name + ".world doit être une chaîne");
        assertTrue(s.isLong("deployed-at"), name + ".deployed-at doit être un entier (epoch ms)");

        // points (x/y/z doubles + yaw/pitch)
        assertPoint(s.getConfigurationSection("center"), name + ".center");
        assertPoint(s.getConfigurationSection("spawn1"), name + ".spawn1");
        assertPoint(s.getConfigurationSection("spawn2"), name + ".spawn2");

        // blocs (x/y/z entiers)
        assertBlock(s.getConfigurationSection("corner1"), name + ".corner1");
        assertBlock(s.getConfigurationSection("corner2"), name + ".corner2");

        // cellule d'emprise
        ConfigurationSection cell = s.getConfigurationSection("cell");
        assertNotNull(cell, name + ".cell doit exister");
        assertTrue(cell.isInt("min-x"), name + ".cell.min-x doit être un entier");
        assertTrue(cell.isInt("min-z"), name + ".cell.min-z doit être un entier");
        assertTrue(cell.isInt("max-x"), name + ".cell.max-x doit être un entier");
        assertTrue(cell.isInt("max-z"), name + ".cell.max-z doit être un entier");
    }

    private void assertPoint(ConfigurationSection p, String label) {
        assertNotNull(p, label + " doit exister");
        assertTrue(p.isDouble("x"), label + ".x doit être un nombre");
        assertTrue(p.isDouble("y"), label + ".y doit être un nombre");
        assertTrue(p.isDouble("z"), label + ".z doit être un nombre");
        assertTrue(p.isDouble("yaw"), label + ".yaw doit être un nombre");
        assertTrue(p.isDouble("pitch"), label + ".pitch doit être un nombre");
    }

    private void assertBlock(ConfigurationSection b, String label) {
        assertNotNull(b, label + " doit exister");
        assertTrue(b.isInt("x"), label + ".x doit être un entier");
        assertTrue(b.isInt("y"), label + ".y doit être un entier");
        assertTrue(b.isInt("z"), label + ".z doit être un entier");
    }

    @Test
    void lAllerRetourConserveToutesLesValeursDuContrat() {
        writeCanonicalRegistry();

        DeploymentStorage fresh = new DeploymentStorage(plugin);
        fresh.load();
        assertEquals(3, fresh.count());

        DeployedInstance d1 = fresh.get("getdown-1");
        assertNotNull(d1);
        assertEquals("getdown", d1.getArena());
        assertEquals("world", d1.getWorld());
        assertEquals(1_756_000_000_000L, d1.getDeployedAt());
        assertEquals(new Point(100.5, 70, -200.5, 0f, 0f), d1.getCenter());
        assertArrayEquals(new int[]{90, 64, -210}, d1.getCorner1());
        assertArrayEquals(new int[]{110, 75, -190}, d1.getCorner2());
        assertEquals(new Point(95.5, 65, -200.5, 90f, 0f), d1.getSpawn1());
        assertEquals(new Point(105.5, 65, -200.5, -90f, 0f), d1.getSpawn2());
        assertArrayEquals(new int[]{80, -220}, d1.getCellMinXZ());
        assertArrayEquals(new int[]{240, -60}, d1.getCellMaxXZ());

        // le trou de numérotation survit : getdown-3 existe sans getdown-2
        assertNotNull(fresh.get("getdown-3"));
        assertEquals(1_756_000_000_001L, fresh.get("getdown-3").getDeployedAt());
        assertEquals(2, fresh.byArena("getdown").size());
        assertEquals(1, fresh.byArena("yacht").size());
    }
}

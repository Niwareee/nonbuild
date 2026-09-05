package fr.niware.nonbuild.storage;

import java.util.logging.Logger;

import org.bukkit.plugin.java.JavaPlugin;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import fr.niware.nonbuild.db.DeploymentDb;
import fr.niware.nonbuild.db.InMemoryDeploymentDb;
import fr.niware.nonbuild.model.DeployedInstance;
import fr.niware.nonbuild.model.Point;

class DeploymentStorageTest {

    private JavaPlugin plugin;
    private DeploymentDb db;

    @BeforeEach
    void setup() {
        plugin = mock(JavaPlugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("DeploymentStorageTest"));
        db = new InMemoryDeploymentDb();
        db.initialize();
    }

    private DeployedInstance instance(String name, String arenaSlug) {
        return new DeployedInstance(name, arenaSlug, "world",
                new Point(100.5, 70, -200.5, 0f, 0f),
                new int[]{90, 64, -210},
                new int[]{110, 75, -190},
                new Point(95.5, 65, -200.5, 90f, 0f),
                new Point(105.5, 65, -200.5, -90f, 0f),
                new int[]{80, -220},
                new int[]{240, -60},
                1_756_000_000_000L);
    }

    @Test
    void sauvegardePuisRechargementConserveLesInstances() {
        DeploymentStorage storage = new DeploymentStorage(plugin, db);
        storage.put(instance("getdown-1", "getdown"));
        storage.put(instance("getdown-2", "getdown"));
        storage.put(instance("yacht-1", "yacht"));
        assertEquals(3, storage.count());

        DeploymentStorage fresh = new DeploymentStorage(plugin, db);
        fresh.load();
        assertEquals(3, fresh.count());

        DeployedInstance loaded = fresh.get("getdown-1");
        assertNotNull(loaded);
        assertEquals("getdown", loaded.getArena());
        assertEquals("world", loaded.getWorld());
        assertEquals(new Point(100.5, 70, -200.5, 0f, 0f), loaded.getCenter());
        assertArrayEquals(new int[]{90, 64, -210}, loaded.getCorner1());
        assertArrayEquals(new int[]{110, 75, -190}, loaded.getCorner2());
        assertEquals(new Point(95.5, 65, -200.5, 90f, 0f), loaded.getSpawn1());
        assertEquals(new Point(105.5, 65, -200.5, -90f, 0f), loaded.getSpawn2());
        assertArrayEquals(new int[]{80, -220}, loaded.getCellMinXZ());
        assertArrayEquals(new int[]{240, -60}, loaded.getCellMaxXZ());
        assertEquals(1_756_000_000_000L, loaded.getDeployedAt());

        assertEquals(2, fresh.byArena("getdown").size());
        assertEquals(1, fresh.byArena("yacht").size());
        assertEquals(0, fresh.byArena("autre").size());
    }

    @Test
    void removeSupprimeEtPersiste() {
        DeploymentStorage storage = new DeploymentStorage(plugin, db);
        storage.put(instance("getdown-1", "getdown"));
        assertTrue(storage.remove("getdown-1"));
        assertFalse(storage.remove("getdown-1"));

        DeploymentStorage fresh = new DeploymentStorage(plugin, db);
        fresh.load();
        assertEquals(0, fresh.count());
    }

    @Test
    void nextIndexDonneLeProchainNumeroLibre() {
        DeploymentStorage storage = new DeploymentStorage(plugin, db);
        assertEquals(1, storage.nextIndex("getdown"));

        storage.put(instance("getdown-1", "getdown"));
        storage.put(instance("getdown-2", "getdown"));
        storage.put(instance("getdown-x", "getdown")); // suffixe non numérique ignoré
        assertEquals(3, storage.nextIndex("getdown"));
        assertEquals(1, storage.nextIndex("yacht"));
    }

    @Test
    void loadSansFichierDonneZeroInstance() {
        DeploymentStorage fresh = new DeploymentStorage(plugin, db);
        fresh.load();
        assertEquals(0, fresh.count());
    }

    @Test
    void nextIndexIgnoreLesNomsSansSuffixeNumerique() {
        DeploymentStorage storage = new DeploymentStorage(plugin, db);
        storage.put(instance("getdown", "getdown")); // pas de tiret
        assertEquals(1, storage.nextIndex("getdown"));
        storage.put(instance("getdown-2", "getdown"));
        assertEquals(3, storage.nextIndex("getdown"));
    }
}

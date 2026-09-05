package fr.niware.nonbuild.storage;

import java.util.logging.Logger;

import org.bukkit.plugin.java.JavaPlugin;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import fr.niware.nonbuild.db.DeploymentDb;
import fr.niware.nonbuild.db.InMemoryDeploymentDb;
import fr.niware.nonbuild.model.DeployedInstance;
import fr.niware.nonbuild.model.Point;
import fr.niware.nonbuild.testutil.BukkitServerFixture;

/**
 * Test de CONTRAT du format deployments — côté écrivain (NonBuild).
 *
 * Ce fichier est l'API inter-plugins : le plugin nongame le lit via SQL.
 * Ce test verrouille la structure exacte produite par DeploymentStorage :
 * chaque champ documenté doit être présent et survivre à un aller-retour.
 */
class DeploymentContractTest {

    private JavaPlugin plugin;
    private DeploymentDb db;

    @BeforeEach
    void setup() {
        BukkitServerFixture.ensure();
        plugin = mock(JavaPlugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("DeploymentContractTest"));
        db = new InMemoryDeploymentDb();
        db.initialize();
    }

    private void writeCanonicalRegistry() {
        DeploymentStorage storage = new DeploymentStorage(plugin, db);
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

        // Vérifier que les données sont bien stockées et récupérables
        DeploymentStorage storage = new DeploymentStorage(plugin, db);
        storage.load();
        assertEquals(3, storage.count());
        assertNotNull(storage.get("getdown-1"));
        assertNotNull(storage.get("getdown-3"));
        assertNotNull(storage.get("yacht-1"));
    }

    @Test
    void lAllerRetourConserveToutesLesValeursDuContrat() {
        writeCanonicalRegistry();

        DeploymentStorage fresh = new DeploymentStorage(plugin, db);
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

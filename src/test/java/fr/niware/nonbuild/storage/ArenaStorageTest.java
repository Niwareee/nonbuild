package fr.niware.nonbuild.storage;

import fr.niware.nonbuild.model.Arena;
import fr.niware.nonbuild.model.Point;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ArenaStorageTest {

    @TempDir
    File tempDir;

    private JavaPlugin plugin;

    @BeforeEach
    void setup() {
        plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(tempDir);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("ArenaStorageTest"));
    }

    private Arena sampleArena() {
        Arena arena = new Arena("getdown");
        arena.setDisplayName("Getdown");
        arena.setWorld("build");
        arena.setCorner1(new int[]{-10, 60, -10});
        arena.setCorner2(new int[]{10, 75, 10});
        arena.setCenter(new Point(0.5, 64, 0.5, 0f, 0f));
        arena.setSpawn1(new Point(-5.5, 64, 0.5, 90.5f, -1.25f));
        arena.setSpawn2(new Point(5.5, 64, 0.5, -90.5f, 2.5f));
        arena.setSavedAt(1_756_000_000_000L);
        return arena;
    }

    @Test
    void sauvegardePuisRechargementConserveToutesLesDonnees() throws IOException {
        ArenaStorage storage = new ArenaStorage(plugin);
        storage.save(sampleArena());
        assertTrue(storage.exists("getdown"));
        assertTrue(storage.arenaFile("getdown").exists());

        ArenaStorage fresh = new ArenaStorage(plugin);
        fresh.loadAll();
        assertEquals(1, fresh.count());

        Arena loaded = fresh.get("getdown");
        assertNotNull(loaded);
        assertEquals("Getdown", loaded.getDisplayName());
        assertEquals("build", loaded.getWorld());
        assertArrayEquals(new int[]{-10, 60, -10}, loaded.getCorner1());
        assertArrayEquals(new int[]{10, 75, 10}, loaded.getCorner2());
        assertEquals(new Point(0.5, 64, 0.5, 0f, 0f), loaded.getCenter());
        assertEquals(new Point(-5.5, 64, 0.5, 90.5f, -1.25f), loaded.getSpawn1());
        assertEquals(new Point(5.5, 64, 0.5, -90.5f, 2.5f), loaded.getSpawn2());
        assertEquals(1_756_000_000_000L, loaded.getSavedAt());
        assertTrue(loaded.isComplete());
    }

    @Test
    void laSauvegardeEstAtomiqueEtNeLaissePasDeFichierTemporaire() throws IOException {
        ArenaStorage storage = new ArenaStorage(plugin);
        storage.save(sampleArena());

        File yaml = storage.arenaFile("getdown");
        assertTrue(yaml.exists());
        assertFalse(new File(yaml.getParentFile(), "getdown.yml.tmp").exists());

        ArenaStorage fresh = new ArenaStorage(plugin);
        fresh.loadAll();
        assertEquals(1, fresh.count());
    }

    @Test
    void deleteSupprimeLeYamlEtLaSchematic() throws IOException {
        ArenaStorage storage = new ArenaStorage(plugin);
        storage.save(sampleArena());

        File schem = storage.schematicFile("getdown");
        assertTrue(schem.getParentFile().mkdirs() || schem.getParentFile().exists());
        assertTrue(schem.createNewFile());

        assertTrue(storage.delete("getdown"));
        assertFalse(storage.exists("getdown"));
        assertFalse(storage.arenaFile("getdown").exists());
        assertFalse(schem.exists());
    }

    @Test
    void deleteSurUneAreneInexistanteRetourneFalse() {
        ArenaStorage storage = new ArenaStorage(plugin);
        assertFalse(storage.delete("inconnue"));
    }

    @Test
    void loadAllIgnoreLesFichiersInvalides() throws IOException {
        ArenaStorage storage = new ArenaStorage(plugin);
        storage.save(sampleArena());

        File broken = new File(tempDir, "arenas/broken.yml");
        Files.writeString(broken.toPath(), "slug: broken\n");

        ArenaStorage fresh = new ArenaStorage(plugin);
        fresh.loadAll();
        assertEquals(1, fresh.count());
        assertNull(fresh.get("broken"));
        assertNotNull(fresh.get("getdown"));
    }

    @Test
    void loadAllSansDossierDonneZeroArene() {
        ArenaStorage fresh = new ArenaStorage(plugin);
        fresh.loadAll();
        assertEquals(0, fresh.count());
    }

    @Test
    void saveEchoueSiLeDossierNePeutPasEtreCree() {
        assertTrue(tempDir.setWritable(false));
        try {
            ArenaStorage storage = new ArenaStorage(plugin);
            IOException e = assertThrows(IOException.class, () -> storage.save(sampleArena()));
            assertTrue(e.getMessage().contains("Impossible de créer le dossier"));
        } finally {
            tempDir.setWritable(true);
        }
    }
}

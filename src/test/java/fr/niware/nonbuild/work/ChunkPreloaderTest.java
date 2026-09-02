package fr.niware.nonbuild.work;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import fr.niware.nonbuild.testutil.BukkitServerFixture;

/**
 * Préchargement de chunks : calcul des coordonnées de chunk (fonction pure,
 * négatifs inclus) et flux async (callback immédiat, différé, tolérant à un
 * échec de chargement).
 */
class ChunkPreloaderTest {

    private World world;
    private Plugin plugin;

    @BeforeEach
    void setup() {
        BukkitServerFixture.ensure();
        world = mock(World.class);
        plugin = mock(Plugin.class);
    }

    @Test
    void chunksPourUneRegionAligneeSurUnChunk() {
        // [0..15] x [0..15] = exactement le chunk (0,0)
        List<int[]> chunks = ChunkPreloader.chunksForRegion(0, 15, 0, 15);
        assertEquals(1, chunks.size());
        assertArrayEquals(new int[]{0, 0}, chunks.get(0));
    }

    @Test
    void chunksPourUneRegionQuiDepassePlusieursChunks() {
        // [0..31] x [0..31] = 2x2 chunks
        List<int[]> chunks = ChunkPreloader.chunksForRegion(0, 31, 0, 31);
        assertEquals(4, chunks.size());
        assertTrue(contains(chunks, 0, 0));
        assertTrue(contains(chunks, 1, 0));
        assertTrue(contains(chunks, 0, 1));
        assertTrue(contains(chunks, 1, 1));
    }

    @Test
    void chunksGereLesCoordonneesNegatives() {
        // [-16..-1] = chunk -1 ; [-1..0] = chunks -1 et 0
        List<int[]> single = ChunkPreloader.chunksForRegion(-16, -1, -16, -1);
        assertEquals(1, single.size());
        assertArrayEquals(new int[]{-1, -1}, single.get(0));

        List<int[]> chunks = ChunkPreloader.chunksForRegion(-1, 0, -1, 0);
        assertEquals(4, chunks.size());
        assertTrue(contains(chunks, -1, -1));
        assertTrue(contains(chunks, 0, 0));
    }

    private static boolean contains(List<int[]> chunks, int cx, int cz) {
        return chunks.stream().anyMatch(c -> c[0] == cx && c[1] == cz);
    }

    @Test
    void preloadExecuteLeCallbackImmediatementSiToutEstCharge() {
        when(world.getChunkAtAsync(anyInt(), anyInt()))
                .thenReturn(CompletableFuture.completedFuture(mock(Chunk.class)));

        AtomicBoolean done = new AtomicBoolean(false);
        ChunkPreloader.preload(plugin, world, 0, 15, 0, 15, () -> done.set(true));

        assertTrue(done.get()); // runTask inline dans la fixture
    }

    @Test
    void preloadAttendLaFinDuChargementAvantLeCallback() {
        CompletableFuture<Chunk> pending = new CompletableFuture<>();
        when(world.getChunkAtAsync(anyInt(), anyInt())).thenReturn(pending);

        AtomicBoolean done = new AtomicBoolean(false);
        ChunkPreloader.preload(plugin, world, 0, 15, 0, 15, () -> done.set(true));

        assertFalse(done.get());
        pending.complete(mock(Chunk.class));
        assertTrue(done.get());
    }

    @Test
    void preloadExecuteLeCallbackMemeSiUnChunkEchoue() {
        CompletableFuture<Chunk> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("io"));
        when(world.getChunkAtAsync(anyInt(), anyInt())).thenReturn(failed);

        AtomicBoolean done = new AtomicBoolean(false);
        ChunkPreloader.preload(plugin, world, 0, 15, 0, 15, () -> done.set(true));

        assertTrue(done.get()); // un échec ne bloque pas la suite
    }
}

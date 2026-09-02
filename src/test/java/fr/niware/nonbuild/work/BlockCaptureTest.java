package fr.niware.nonbuild.work;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.bukkit.Chunk;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;
import org.bukkit.plugin.Plugin;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fr.niware.nonbuild.schematic.SpongeSchematic;
import fr.niware.nonbuild.testutil.BukkitServerFixture;
import net.kyori.adventure.text.Component;

class BlockCaptureTest {

    @BeforeEach
    void setup() {
        BukkitServerFixture.ensure();
        BukkitServerFixture.clearTimerTasks();
    }

    @Test
    void captureProduitLeSchematicAttendu() {
        World world = mock(World.class);
        Chunk chunk = mock(Chunk.class);
        when(world.getChunkAt(anyInt(), anyInt())).thenReturn(chunk);
        // chunk.getBlock(localX, localY, localZ) — on reconstruit les world coords
        // pour ce test : minX=10, minZ=30 → chunk (0, 1) → origin (0, 16)
        when(chunk.getBlock(anyInt(), anyInt(), anyInt())).thenAnswer(inv -> {
            Block block = mock(Block.class);
            BlockData data = mock(BlockData.class);
            int localX = inv.getArgument(0);
            int localY = inv.getArgument(1);
            int localZ = inv.getArgument(2);
            // chunk (0, 1) → worldX = localX, worldZ = 16 + localZ
            int worldX = localX;
            int worldZ = 16 + localZ;
            when(data.getAsString()).thenReturn("s:" + worldX + ":" + localY + ":" + worldZ);
            when(block.getBlockData()).thenReturn(data);
            return block;
        });

        List<Integer> progresses = new ArrayList<>();
        AtomicReference<SpongeSchematic> result = new AtomicReference<>();

        BlockCapture capture = new BlockCapture(world, 10, 20, 30, 2, 2, 2, 3,
                progresses::add,
                (schematic, nanos) -> result.set(schematic),
                message -> fail("erreur inattendue : " + message));
        capture.runTaskTimer(mock(Plugin.class), 1, 1);

        Runnable task = BukkitServerFixture.pollTimerTask();
        assertNotNull(task);
        task.run(); // 3 blocs sur 8
        assertNull(result.get());
        assertFalse(progresses.contains(100));
        task.run(); // 6 blocs
        assertNull(result.get());
        task.run(); // 8 blocs -> terminé
        assertNotNull(result.get());
        assertTrue(progresses.contains(100));

        SpongeSchematic schematic = result.get();
        assertEquals(2, schematic.getWidth());
        assertEquals(2, schematic.getHeight());
        assertEquals(2, schematic.getLength());
        assertEquals("s:10:20:30", schematic.stateAt(0, 0, 0));
        assertEquals("s:11:20:30", schematic.stateAt(1, 0, 0));
        assertEquals("s:10:20:31", schematic.stateAt(0, 0, 1));
        assertEquals("s:11:21:31", schematic.stateAt(1, 1, 1));
    }

    @Test
    void lesBlockEntitiesSontCapturesSansPasserParGetStatePourLeReste() {
        World world = mock(World.class);
        Chunk chunk = mock(Chunk.class);
        when(world.getChunkAt(anyInt(), anyInt())).thenReturn(chunk);

        Block signBlock = mock(Block.class);
        BlockData signData = mock(BlockData.class);
        when(signData.getAsString()).thenReturn("minecraft:oak_sign");
        when(signData.getMaterial()).thenReturn(Material.OAK_SIGN);
        when(signBlock.getBlockData()).thenReturn(signData);

        SignSide side = mock(SignSide.class);
        when(side.lines()).thenReturn(List.of(
                Component.empty(), Component.empty(), Component.empty(), Component.empty()));
        Sign sign = mock(Sign.class);
        when(sign.getType()).thenReturn(Material.OAK_SIGN);
        when(sign.getSide(Side.FRONT)).thenReturn(side);
        when(sign.getSide(Side.BACK)).thenReturn(side);
        when(sign.getColor()).thenReturn(DyeColor.WHITE);
        when(signBlock.getState()).thenReturn(sign);

        Block stoneBlock = mock(Block.class);
        BlockData stoneData = mock(BlockData.class);
        when(stoneData.getAsString()).thenReturn("minecraft:stone");
        when(stoneData.getMaterial()).thenReturn(Material.STONE);
        when(stoneBlock.getBlockData()).thenReturn(stoneData);

        // minX=10, minZ=30 → chunk (0, 1) → origin (0, 16)
        // local coords: sign at (10, 20, 14), stone at (11, 20, 14)
        when(chunk.getBlock(10, 20, 14)).thenReturn(signBlock);
        when(chunk.getBlock(11, 20, 14)).thenReturn(stoneBlock);

        AtomicReference<SpongeSchematic> result = new AtomicReference<>();
        BlockCapture capture = new BlockCapture(world, 10, 20, 30, 2, 1, 1, 10,
                percent -> {
                },
                (schematic, nanos) -> result.set(schematic),
                message -> fail("erreur inattendue : " + message));
        capture.runTaskTimer(mock(Plugin.class), 1, 1);

        BukkitServerFixture.pollTimerTask().run();

        assertNotNull(result.get());
        List<Map<String, Object>> entities = result.get().getBlockEntities();
        assertEquals(1, entities.size());
        assertEquals("minecraft:sign", entities.get(0).get("Id"));
        assertEquals(List.of(0, 0, 0), entities.get(0).get("Pos"));
        verify(stoneBlock, never()).getState(); // la pierre ne coûte aucun getState()
    }

    @Test
    void uneErreurDeLectureEstTransmiseAuCallback() {
        World world = mock(World.class);
        when(world.getChunkAt(anyInt(), anyInt()))
                .thenThrow(new RuntimeException("chunk corrompu"));

        AtomicReference<String> error = new AtomicReference<>();
        BlockCapture capture = new BlockCapture(world, 0, 64, 0, 2, 1, 1, 10,
                percent -> {
                },
                (schematic, nanos) -> fail("la capture n'aurait pas dû aboutir"),
                error::set);
        capture.runTaskTimer(mock(Plugin.class), 1, 1);

        BukkitServerFixture.pollTimerTask().run();
        assertNotNull(error.get());
        assertTrue(error.get().contains("chunk corrompu"));
    }
}

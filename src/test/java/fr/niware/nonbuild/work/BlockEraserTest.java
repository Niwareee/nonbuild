package fr.niware.nonbuild.work;

import fr.niware.nonbuild.testutil.BukkitServerFixture;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BlockEraserTest {

    private Server server;

    @BeforeEach
    void setup() {
        server = BukkitServerFixture.ensure();
        BukkitServerFixture.clearTimerTasks();
    }

    @Test
    void effaceTouteLaZoneBlocParBloc() {
        BlockData airData = mock(BlockData.class);
        when(server.createBlockData(Material.AIR)).thenReturn(airData);

        World world = mock(World.class);
        Map<String, Block> blocks = new HashMap<>();
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(inv ->
                blocks.computeIfAbsent(inv.getArgument(0) + "," + inv.getArgument(1) + "," + inv.getArgument(2),
                        k -> mock(Block.class)));

        List<Integer> progresses = new ArrayList<>();
        AtomicBoolean done = new AtomicBoolean();

        BlockEraser eraser = new BlockEraser(world, new int[]{10, 64, -20}, new int[]{11, 64, -19}, 3,
                progresses::add,
                () -> done.set(true),
                message -> fail("erreur inattendue : " + message));
        eraser.runTaskTimer(mock(Plugin.class), 1, 1);

        Runnable task = BukkitServerFixture.pollTimerTask();
        assertNotNull(task);
        task.run(); // 3 blocs sur 4
        assertFalse(done.get());
        task.run(); // dernier bloc -> terminé
        assertTrue(done.get());
        assertTrue(progresses.contains(100));

        for (Block block : blocks.values()) {
            verify(block).setBlockData(airData, false);
        }
    }

    @Test
    void uneErreurDEffacementEstTransmiseAuCallback() {
        when(server.createBlockData(Material.AIR)).thenReturn(mock(BlockData.class));

        World world = mock(World.class);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt()))
                .thenThrow(new RuntimeException("chunk non chargé"));

        AtomicReference<String> error = new AtomicReference<>();
        BlockEraser eraser = new BlockEraser(world, new int[]{0, 64, 0}, new int[]{1, 64, 1}, 100,
                percent -> {
                },
                () -> fail("l'effacement n'aurait pas dû aboutir"),
                error::set);
        eraser.runTaskTimer(mock(Plugin.class), 1, 1);

        BukkitServerFixture.pollTimerTask().run();
        assertNotNull(error.get());
        assertTrue(error.get().contains("chunk non chargé"));
    }
}

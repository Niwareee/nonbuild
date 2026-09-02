package fr.niware.nonbuild.work;

import fr.niware.nonbuild.schematic.BlockEntityIO;
import fr.niware.nonbuild.schematic.SpongeSchematic;
import fr.niware.nonbuild.testutil.BukkitServerFixture;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BlockPasterTest {

    private Server server;

    @BeforeEach
    void setup() {
        server = BukkitServerFixture.ensure();
        BukkitServerFixture.clearTimerTasks();
    }

    @Test
    void colleChaqueBlocAuBonEndroitAvecLeBonEtat() {
        Map<String, BlockData> parsed = new HashMap<>();
        when(server.createBlockData(anyString())).thenAnswer(inv ->
                parsed.computeIfAbsent(inv.getArgument(0), s -> mock(BlockData.class)));

        World world = mock(World.class);
        Map<String, Block> blocks = new HashMap<>();
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(inv ->
                blocks.computeIfAbsent(inv.getArgument(0) + "," + inv.getArgument(1) + "," + inv.getArgument(2),
                        k -> mock(Block.class)));

        SpongeSchematic schematic = SpongeSchematic.create(2, 1, 2,
                new int[]{0, 1, 2, 0},
                List.of("minecraft:stone", "minecraft:dirt", "minecraft:gold_block"));

        List<Integer> progresses = new ArrayList<>();
        AtomicBoolean done = new AtomicBoolean();

        BlockPaster paster = new BlockPaster(world, 100, 60, -200, schematic, 3, false,
                progresses::add,
                () -> done.set(true),
                message -> fail("erreur inattendue : " + message));
        paster.runTaskTimer(mock(Plugin.class), 1, 1);

        Runnable task = BukkitServerFixture.pollTimerTask();
        assertNotNull(task);
        task.run(); // 3 blocs sur 4
        assertFalse(done.get());
        task.run(); // dernier bloc -> terminé
        assertTrue(done.get());
        assertTrue(progresses.contains(100));

        // ordre Sponge : index = (y*L + z) * W + x, W=2, L=2
        verify(blocks.get("100,60,-200")).setBlockData(parsed.get("minecraft:stone"), false);
        verify(blocks.get("101,60,-200")).setBlockData(parsed.get("minecraft:dirt"), false);
        verify(blocks.get("100,60,-199")).setBlockData(parsed.get("minecraft:gold_block"), false);
        verify(blocks.get("101,60,-199")).setBlockData(parsed.get("minecraft:stone"), false);
    }

    @Test
    void unEtatInvalideEstRemplaceParDeLAir() {
        BlockData airData = mock(BlockData.class);
        when(server.createBlockData("minecraft:etat_casse")).thenThrow(new IllegalArgumentException());
        when(server.createBlockData(Material.AIR)).thenReturn(airData);

        World world = mock(World.class);
        Block block = mock(Block.class);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(block);

        SpongeSchematic schematic = SpongeSchematic.create(1, 1, 1,
                new int[]{0}, List.of("minecraft:etat_casse"));

        AtomicBoolean done = new AtomicBoolean();
        BlockPaster paster = new BlockPaster(world, 0, 64, 0, schematic, 100, false,
                percent -> {
                },
                () -> done.set(true),
                message -> fail("erreur inattendue : " + message));
        paster.runTaskTimer(mock(Plugin.class), 1, 1);

        BukkitServerFixture.pollTimerTask().run();
        assertTrue(done.get());
        verify(block).setBlockData(eq(airData), eq(false));
    }

    @Test
    void uneErreurDePoseEstTransmiseAuCallback() {
        when(server.createBlockData(anyString())).thenReturn(mock(BlockData.class));

        World world = mock(World.class);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt()))
                .thenThrow(new RuntimeException("chunk non chargé"));

        SpongeSchematic schematic = SpongeSchematic.create(1, 1, 1,
                new int[]{0}, List.of("minecraft:stone"));

        java.util.concurrent.atomic.AtomicReference<String> error = new java.util.concurrent.atomic.AtomicReference<>();
        BlockPaster paster = new BlockPaster(world, 0, 64, 0, schematic, 100, false,
                percent -> {
                },
                () -> fail("le collage n'aurait pas dû aboutir"),
                error::set);
        paster.runTaskTimer(mock(Plugin.class), 1, 1);

        BukkitServerFixture.pollTimerTask().run();
        assertNotNull(error.get());
        assertTrue(error.get().contains("chunk non chargé"));
    }

    @Test
    void leSkipAirNeCollePasLairEtLeBudgetCompteLesPoses() {
        BlockData airData = mock(BlockData.class);
        when(airData.getMaterial()).thenReturn(Material.AIR);
        BlockData stoneData = mock(BlockData.class);
        when(stoneData.getMaterial()).thenReturn(Material.STONE);
        when(server.createBlockData("minecraft:air")).thenReturn(airData);
        when(server.createBlockData("minecraft:stone")).thenReturn(stoneData);

        World world = mock(World.class);
        Map<String, Block> blocks = new HashMap<>();
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(inv ->
                blocks.computeIfAbsent(inv.getArgument(0) + "," + inv.getArgument(1) + "," + inv.getArgument(2),
                        k -> mock(Block.class)));

        // 1x1x4 : air, pierre, air, pierre
        SpongeSchematic schematic = SpongeSchematic.create(1, 1, 4,
                new int[]{0, 1, 0, 1}, List.of("minecraft:air", "minecraft:stone"));

        AtomicBoolean done = new AtomicBoolean();
        BlockPaster paster = new BlockPaster(world, 0, 64, 0, schematic, 1, true,
                percent -> {
                },
                () -> done.set(true),
                message -> fail("erreur inattendue : " + message));
        paster.runTaskTimer(mock(Plugin.class), 1, 1);

        Runnable task = BukkitServerFixture.pollTimerTask();
        task.run(); // budget d'une pose : balaie l'air, pose le premier caillou
        assertFalse(done.get());
        task.run(); // balaie l'air, pose le second -> terminé
        assertTrue(done.get());

        verify(blocks.get("0,64,1")).setBlockData(stoneData, false);
        verify(blocks.get("0,64,3")).setBlockData(stoneData, false);
        assertFalse(blocks.containsKey("0,64,0")); // l'air n'est même pas demandé au monde
        assertFalse(blocks.containsKey("0,64,2"));
    }

    @Test
    void lesBlockEntitiesSontAppliquesApresLesBlocs() {
        when(server.createBlockData(anyString())).thenReturn(mock(BlockData.class));

        World world = mock(World.class);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(mock(Block.class));

        Map<String, Object> entity1 = Map.of("Id", "minecraft:sign", "Pos", List.of(0, 0, 0));
        Map<String, Object> entity2 = Map.of("Id", "minecraft:skull", "Pos", List.of(0, 0, 1));
        SpongeSchematic schematic = SpongeSchematic.create(1, 1, 2,
                new int[]{0, 0}, List.of("minecraft:stone"), List.of(entity1, entity2));

        AtomicBoolean done = new AtomicBoolean();
        BlockPaster paster = new BlockPaster(world, 5, 64, -3, schematic, 100, false,
                percent -> {
                },
                () -> done.set(true),
                message -> fail("erreur inattendue : " + message));

        try (MockedStatic<BlockEntityIO> io = mockStatic(BlockEntityIO.class)) {
            paster.runTaskTimer(mock(Plugin.class), 1, 1);
            BukkitServerFixture.pollTimerTask().run();

            assertTrue(done.get());
            io.verify(() -> BlockEntityIO.apply(world, 5, 64, -3, entity1));
            io.verify(() -> BlockEntityIO.apply(world, 5, 64, -3, entity2));
        }
    }

    @Test
    void lesBlockEntitiesSontAppliquesParLotsDeMilleParTick() {
        when(server.createBlockData(anyString())).thenReturn(mock(BlockData.class));

        World world = mock(World.class);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(mock(Block.class));

        List<Map<String, Object>> entities = new ArrayList<>();
        for (int i = 0; i < 1500; i++) {
            entities.add(Map.of("Id", "minecraft:sign", "Pos", List.of(i, 0, 0)));
        }
        SpongeSchematic schematic = SpongeSchematic.create(1, 1, 1,
                new int[]{0}, List.of("minecraft:stone"), entities);

        AtomicBoolean done = new AtomicBoolean();
        BlockPaster paster = new BlockPaster(world, 0, 64, 0, schematic, 100, false,
                percent -> {
                },
                () -> done.set(true),
                message -> fail("erreur inattendue : " + message));

        try (MockedStatic<BlockEntityIO> io = mockStatic(BlockEntityIO.class)) {
            paster.runTaskTimer(mock(Plugin.class), 1, 1);
            Runnable task = BukkitServerFixture.pollTimerTask();

            task.run(); // blocs + 1000 entités sur 1500
            assertFalse(done.get());
            io.verify(() -> BlockEntityIO.apply(any(World.class), anyInt(), anyInt(), anyInt(), any()),
                    times(1000));

            task.run(); // les 500 restantes -> terminé
            assertTrue(done.get());
            io.verify(() -> BlockEntityIO.apply(any(World.class), anyInt(), anyInt(), anyInt(), any()),
                    times(1500));
        }
    }
}

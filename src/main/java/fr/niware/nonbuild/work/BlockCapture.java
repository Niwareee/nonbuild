package fr.niware.nonbuild.work;

import fr.niware.nonbuild.schematic.BlockEntityIO;
import fr.niware.nonbuild.schematic.SpongeSchematic;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Capture une région du monde de build vers un schematic, répartie sur
 * plusieurs ticks (budget de blocs par tick) pour éviter de freeze le serveur.
 * Les block entities sont capturés au passage (getState() uniquement pour les
 * matériaux qui en portent, le surcoût est négligeable).
 */
public class BlockCapture extends BukkitRunnable {

    private final World world;
    private final int minX;
    private final int minY;
    private final int minZ;
    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    private final int budget;
    private final Consumer<Integer> progress;
    private final BiConsumer<SpongeSchematic, Long> done;
    private final Consumer<String> error;

    private final Map<String, Integer> paletteIds = new HashMap<>();
    private final List<String> palette = new ArrayList<>();
    private final List<Map<String, Object>> blockEntities = new ArrayList<>();
    private final int[] indices;
    private final long total;
    private final long startNanos;

    private long cursor;
    private int lastReportedPercent = -1;

    public BlockCapture(World world,
                        int minX, int minY, int minZ,
                        int sizeX, int sizeY, int sizeZ,
                        int budget,
                        Consumer<Integer> progress,
                        BiConsumer<SpongeSchematic, Long> done,
                        Consumer<String> error) {
        this.world = world;
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.budget = budget;
        this.progress = progress;
        this.done = done;
        this.error = error;
        this.indices = new int[(int) ((long) sizeX * sizeY * sizeZ)];
        this.total = indices.length;
        this.startNanos = System.nanoTime();
    }

    @Override
    public void run() {
        try {
            int processed = 0;
            while (cursor < total && processed < budget) {
                int i = (int) cursor;
                int y = i / (sizeZ * sizeX);
                int rem = i - y * sizeZ * sizeX;
                int z = rem / sizeX;
                int x = rem - z * sizeX;

                Block block = world.getBlockAt(minX + x, minY + y, minZ + z);
                BlockData data = block.getBlockData();
                indices[i] = paletteIds.computeIfAbsent(data.getAsString(), s -> {
                    palette.add(s);
                    return palette.size() - 1;
                });
                if (BlockEntityIO.isBlockEntity(data.getMaterial())) {
                    Map<String, Object> entity = BlockEntityIO.capture(block.getState(), x, y, z);
                    if (entity != null) {
                        blockEntities.add(entity);
                    }
                }

                cursor++;
                processed++;
            }

            int percent = (int) (cursor * 100 / total);
            if (percent / 10 != lastReportedPercent) {
                lastReportedPercent = percent / 10;
                progress.accept(percent);
            }

            if (cursor >= total) {
                cancel();
                SpongeSchematic schematic = SpongeSchematic.create(sizeX, sizeY, sizeZ, indices, palette, blockEntities);
                done.accept(schematic, System.nanoTime() - startNanos);
            }
        } catch (Exception e) {
            cancel();
            error.accept(e.getMessage());
        }
    }
}

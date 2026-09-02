package fr.niware.nonbuild.work;

import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Efface une région du monde (remplissage d'air), répartie sur plusieurs
 * ticks avec un budget de blocs par tick. Sert à la suppression physique des
 * instances et au nettoyage des anciens emplacements lors d'un redéploiement.
 * Itère par Chunk (getBlock) pour réduire les lookups et améliorer le cache.
 */
public class BlockEraser extends BukkitRunnable {

    private final World world;
    private final int minX;
    private final int minY;
    private final int minZ;
    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    private final int budget;
    private final Consumer<Integer> progress;
    private final Runnable done;
    private final Consumer<String> error;

    private final BlockData air;
    private final long total;

    private long cursor;
    private int lastReportedStep = -1;

    public BlockEraser(World world, int[] min, int[] max, int budget,
                       Consumer<Integer> progress, Runnable done, Consumer<String> error) {
        this.world = world;
        this.minX = min[0];
        this.minY = min[1];
        this.minZ = min[2];
        this.sizeX = max[0] - min[0] + 1;
        this.sizeY = max[1] - min[1] + 1;
        this.sizeZ = max[2] - min[2] + 1;
        this.budget = budget;
        this.progress = progress;
        this.done = done;
        this.error = error;
        this.air = Bukkit.createBlockData(Material.AIR);
        this.total = (long) sizeX * sizeY * sizeZ;
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

                int worldX = minX + x;
                int worldY = minY + y;
                int worldZ = minZ + z;

                Chunk chunk = world.getChunkAt(worldX >> 4, worldZ >> 4);
                chunk.getBlock(worldX & 0x0F, worldY, worldZ & 0x0F).setBlockData(air, false);

                cursor++;
                processed++;
            }

            int step = (int) (cursor * 4 / total);
            if (step != lastReportedStep) {
                lastReportedStep = step;
                progress.accept((int) (cursor * 100 / total));
            }

            if (cursor >= total) {
                cancel();
                done.run();
            }
        } catch (Exception e) {
            cancel();
            error.accept(e.getMessage());
        }
    }
}

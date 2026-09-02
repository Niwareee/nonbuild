package fr.niware.nonbuild.work;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Précharge les chunks d'une région avant un collage/effacement.
 *
 * Sans cela, le premier getBlockAt d'un chunk non chargé déclenche une
 * génération synchrone sur le fil principal (freeze) — les arènes sont
 * placées loin du spawn par la spirale, donc leurs chunks sont froids.
 * Les chunks sont demandés en async ; le callback ne part que quand tous
 * sont résolus. Un échec de chargement d'un chunk n'empêche pas la suite
 * (le collage retombera sur le chargement synchrone, cas rare).
 */
public final class ChunkPreloader {

    private ChunkPreloader() {
    }

    /**
     * Coordonnées (cx, cz) des chunks couvrant la région [minX..maxX] × [minZ..maxZ].
     * Fonction pure, testable sans Bukkit.
     */
    public static List<int[]> chunksForRegion(int minX, int maxX, int minZ, int maxZ) {
        int cx0 = Math.floorDiv(minX, 16);
        int cx1 = Math.floorDiv(maxX, 16);
        int cz0 = Math.floorDiv(minZ, 16);
        int cz1 = Math.floorDiv(maxZ, 16);
        List<int[]> chunks = new ArrayList<>();
        for (int cx = cx0; cx <= cx1; cx++) {
            for (int cz = cz0; cz <= cz1; cz++) {
                chunks.add(new int[]{cx, cz});
            }
        }
        return chunks;
    }

    /**
     * Charge en async tous les chunks de la région, puis exécute onLoaded sur
     * le fil principal. Si aucun chunk n'est à charger, onLoaded part
     * immédiatement.
     */
    public static void preload(Plugin plugin, World world, int minX, int maxX, int minZ, int maxZ,
                               Runnable onLoaded) {
        List<CompletableFuture<Chunk>> loads = new ArrayList<>();
        for (int[] chunk : chunksForRegion(minX, maxX, minZ, maxZ)) {
            CompletableFuture<Chunk> future = world.getChunkAtAsync(chunk[0], chunk[1]);
            if (future != null) {
                loads.add(future);
            }
        }
        CompletableFuture.allOf(loads.toArray(new CompletableFuture[0]))
                .whenComplete((ignored, error) -> Bukkit.getScheduler().runTask(plugin, onLoaded));
    }

    /**
     * Précharge les 9 chunks autour de la destination (3×3) puis téléporte le
     * joueur une fois chargés. Sans cela la première arrivée déclenche une
     * génération synchrone (freeze du main thread) et le joueur peut traverser
     * le sol le temps que le chunk existe. Si le joueur se déconnecte pendant
     * le chargement, la téléportation est annulée. Un échec de chargement d'un
     * chunk n'empêche pas la téléportation (retombée sur le chargement
     * synchrone, cas rare).
     */
    public static void preloadAndTeleport(Plugin plugin, Player player, Location target, Runnable onTeleported) {
        int centerX = target.getBlockX() >> 4;
        int centerZ = target.getBlockZ() >> 4;
        List<CompletableFuture<Chunk>> loads = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                CompletableFuture<Chunk> future = target.getWorld().getChunkAtAsync(centerX + dx, centerZ + dz);
                if (future != null) {
                    loads.add(future);
                }
            }
        }
        CompletableFuture.allOf(loads.toArray(new CompletableFuture[0]))
                .whenComplete((ignored, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) {
                        player.teleport(target);
                        onTeleported.run();
                    }
                }));
    }
}

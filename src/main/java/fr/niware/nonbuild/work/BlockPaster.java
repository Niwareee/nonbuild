package fr.niware.nonbuild.work;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.scheduler.BukkitRunnable;

import fr.niware.nonbuild.schematic.BlockEntityIO;
import fr.niware.nonbuild.schematic.SpongeSchematic;

/**
 * Colle un schematic dans un monde, réparti sur plusieurs ticks
 * (budget de blocs POSÉS par tick) pour éviter de freeze le serveur.
 * Les blocs sont posés sans physique, l'arène est collée telle quelle.
 * Avec skipAir, les blocs d'air ne sont pas écrits (le budget ne compte
 * que les poses réelles, le balayage est plafonné par tick) — réservé aux
 * zones sûres où le vide est déjà présent (monde tout juste recréé).
 * Une fois les blocs posés, les block entities du schematic sont
 * appliqués à leur tour (par lots, budget par tick) avant le callback done.
 * Itère par Chunk (getBlock) pour réduire les lookups et améliorer le cache.
 */
public class BlockPaster extends BukkitRunnable {

    private static final int MAX_SCANS_PER_TICK = 1_000_000;
    private static final int MAX_ENTITIES_PER_TICK = 1_000;

    private final World world;
    private final int minX;
    private final int minY;
    private final int minZ;
    private final SpongeSchematic schematic;
    private final int budget;
    private final boolean skipAir;
    private final Consumer<Integer> progress;
    private final Runnable done;
    private final Consumer<String> error;

    private final BlockData[] resolvedPalette;
    private final boolean[] airPalette;
    private final long total;

    private long cursor;
    private int lastReportedStep = -1;
    private List<Map<String, Object>> entities;
    private int entityCursor;

    public BlockPaster(World world,
                       int minX, int minY, int minZ,
                       SpongeSchematic schematic,
                       int budget,
                       boolean skipAir,
                       Consumer<Integer> progress,
                       Runnable done,
                       Consumer<String> error) {
        this.world = world;
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.schematic = schematic;
        this.budget = budget;
        this.skipAir = skipAir;
        this.progress = progress;
        this.done = done;
        this.error = error;
        this.resolvedPalette = new BlockData[schematic.paletteSize()];
        this.airPalette = new boolean[schematic.paletteSize()];
        this.total = schematic.volume();
    }

    @Override
    public void run() {
        try {
            if (cursor < total) {
                pasteBlocks();
                if (cursor < total) {
                    return;
                }
            }
            applyBlockEntities();
        } catch (Exception e) {
            cancel();
            error.accept(e.getMessage());
        }
    }

    private void pasteBlocks() {
        int width = schematic.getWidth();
        int length = schematic.getLength();
        int writes = 0;
        int scans = 0;
        int scanCap = skipAir ? MAX_SCANS_PER_TICK : budget;

        while (cursor < total && writes < budget && scans < scanCap) {
            int i = (int) cursor;
            int y = i / (length * width);
            int rem = i - y * length * width;
            int z = rem / width;
            int x = rem - z * width;
            cursor++;
            scans++;

            int paletteIndex = schematic.paletteIndexAt(x, y, z);
            if (skipAir && isAir(paletteIndex)) {
                continue;
            }

            int worldX = minX + x;
            int worldY = minY + y;
            int worldZ = minZ + z;

            Chunk chunk = world.getChunkAt(worldX >> 4, worldZ >> 4);
            chunk.getBlock(worldX & 0x0F, worldY, worldZ & 0x0F).setBlockData(dataFor(paletteIndex), false);
            writes++;
        }

        int step = (int) (cursor * 4 / total);
        if (step != lastReportedStep) {
            lastReportedStep = step;
            progress.accept((int) (cursor * 100 / total));
        }
    }

    private void applyBlockEntities() {
        if (entities == null) {
            entities = schematic.getBlockEntities();
        }
        int applied = 0;
        while (entityCursor < entities.size() && applied < MAX_ENTITIES_PER_TICK) {
            Map<String, Object> entry = entities.get(entityCursor);
            String id = entry.get("Id") instanceof String s ? s : null;
            if ("minecraft:item_frame".equals(id) || "minecraft:painting".equals(id)) {
                BlockEntityIO.applyEntity(world, minX, minY, minZ, entry);
            } else {
                BlockEntityIO.apply(world, minX, minY, minZ, entry);
            }
            entityCursor++;
            applied++;
        }
        if (entityCursor >= entities.size()) {
            cancel();
            done.run();
        }
    }

    private BlockData dataFor(int paletteIndex) {
        BlockData data = resolvedPalette[paletteIndex];
        if (data == null) {
            String state = schematic.paletteStateAt(paletteIndex);
            try {
                data = Bukkit.createBlockData(state);
            } catch (IllegalArgumentException e) {
                data = Bukkit.createBlockData(Material.AIR);
            }
            resolvedPalette[paletteIndex] = data;
            airPalette[paletteIndex] = data.getMaterial() == Material.AIR;
        }
        return data;
    }

    private boolean isAir(int paletteIndex) {
        if (resolvedPalette[paletteIndex] == null) {
            dataFor(paletteIndex);
        }
        return airPalette[paletteIndex];
    }
}

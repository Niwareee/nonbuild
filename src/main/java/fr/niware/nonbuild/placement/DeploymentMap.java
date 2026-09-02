package fr.niware.nonbuild.placement;

import fr.niware.nonbuild.model.DeployedInstance;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Dessine une carte ASCII vue du dessus des instances déployées :
 * la cellule d'emprise (arène + marge) apparaît en contour « · », le volume
 * réellement collé (corner1/corner2) est rempli d'une lettre propre à
 * l'arène — visible même quand l'arène est plus petite qu'une case —,
 * la zone protégée du spawn est un contour « ░ » et le spawn marqué d'un +.
 * Accompagnée de statistiques (surface collée, % d'emprise).
 */
public final class DeploymentMap {

    private static final String PALETTE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int TARGET_WIDTH = 55;
    private static final int MAX_HEIGHT = 25;

    private DeploymentMap() {
    }

    public static List<String> render(List<DeployedInstance> instances, int spawnRadius) {
        List<String> lines = new ArrayList<>();
        if (instances.isEmpty()) {
            lines.add("§7Aucune instance déployée : la carte est vide. Déployez avec /deploy <arène> <nombre>.");
            return lines;
        }

        int minX = -spawnRadius;
        int maxX = spawnRadius;
        int minZ = -spawnRadius;
        int maxZ = spawnRadius;
        for (DeployedInstance instance : instances) {
            minX = Math.min(minX, instance.getCellMinXZ()[0]);
            maxX = Math.max(maxX, instance.getCellMaxXZ()[0]);
            minZ = Math.min(minZ, instance.getCellMinXZ()[1]);
            maxZ = Math.max(maxZ, instance.getCellMaxXZ()[1]);
        }
        long baseX = minX;
        long baseZ = minZ;
        long spanX0 = (long) maxX - minX + 1;
        long spanZ0 = (long) maxZ - minZ + 1;

        int pad = (int) Math.max(16, Math.max(spanX0, spanZ0) / 40);
        minX -= pad;
        maxX += pad;
        minZ -= pad;
        maxZ += pad;
        long spanX = (long) maxX - minX + 1;
        long spanZ = (long) maxZ - minZ + 1;

        long scale = Math.max(16, roundUp16(Math.max(ceilDiv(spanX, TARGET_WIDTH), ceilDiv(spanZ, MAX_HEIGHT))));
        int width = (int) ceilDiv(spanX, scale);
        int height = (int) ceilDiv(spanZ, scale);

        char[][] grid = new char[height][width];
        for (char[] row : grid) {
            Arrays.fill(row, ' ');
        }

        drawProtectedOutline(grid, minX, minZ, scale, spawnRadius);
        placeSpawn(grid, minX, minZ, scale);

        Map<String, Character> arenaChars = new LinkedHashMap<>();
        Map<String, Integer> arenaCounts = new LinkedHashMap<>();
        Map<String, int[]> cellDims = new LinkedHashMap<>();
        Map<String, int[]> arenaDims = new LinkedHashMap<>();
        for (DeployedInstance instance : instances) {
            char symbol = arenaChars.computeIfAbsent(instance.getArena(),
                    slug -> PALETTE.charAt(arenaChars.size() % PALETTE.length()));
            arenaCounts.merge(instance.getArena(), 1, Integer::sum);
            cellDims.putIfAbsent(instance.getArena(), new int[]{
                    instance.getCellMaxXZ()[0] - instance.getCellMinXZ()[0] + 1,
                    instance.getCellMaxXZ()[1] - instance.getCellMinXZ()[1] + 1});
            arenaDims.putIfAbsent(instance.getArena(), new int[]{
                    instance.getCorner2()[0] - instance.getCorner1()[0] + 1,
                    instance.getCorner2()[2] - instance.getCorner1()[2] + 1});
            drawCellBorder(grid, minX, minZ, scale, instance);
        }
        // Deuxième passe : les lettres d'arène écrasent les bordures.
        for (DeployedInstance instance : instances) {
            fillArena(grid, minX, minZ, scale, instance, arenaChars.get(instance.getArena()));
        }

        lines.add("§6▸ Carte des déploiements §8(§71 case = " + scale + " blocs · X → Est, Z ↓ Sud§8)");
        for (char[] row : grid) {
            lines.add(new String(row));
        }

        long pasted = 0;
        long cellsArea = 0;
        for (DeployedInstance instance : instances) {
            pasted += (long) (instance.getCorner2()[0] - instance.getCorner1()[0] + 1)
                    * (instance.getCorner2()[2] - instance.getCorner1()[2] + 1);
            cellsArea += (long) (instance.getCellMaxXZ()[0] - instance.getCellMinXZ()[0] + 1)
                    * (instance.getCellMaxXZ()[1] - instance.getCellMinXZ()[1] + 1);
        }
        long emprise = spanX0 * spanZ0;
        int percent = (int) (cellsArea * 100 / emprise);

        lines.add("§6▸ §e" + instances.size() + " instance(s) §8· §e" + arenaChars.size() + " arène(s) §8· §7"
                + fmt(pasted) + " blocs² collés §8· §7cellules = §e" + percent + "% §7de l'emprise (§f"
                + fmt(emprise) + " blocs²§7)");
        lines.add("§6▸ Légende §8(+ spawn · ░ zone protégée · · limite de cellule · lettre = arène collée) :");
        for (Map.Entry<String, Character> entry : arenaChars.entrySet()) {
            int[] cell = cellDims.get(entry.getKey());
            int[] arena = arenaDims.get(entry.getKey());
            lines.add("  §e" + entry.getValue() + " §7= §f" + entry.getKey()
                    + " §8(" + arenaCounts.get(entry.getKey()) + " inst., arène " + arena[0] + "×" + arena[1]
                    + ", cellule " + cell[0] + "×" + cell[1] + ")");
        }
        return lines;
    }

    private static String fmt(long value) {
        return String.format(Locale.FRENCH, "%,d", value);
    }

    private static long ceilDiv(long a, long b) {
        return (a + b - 1) / b;
    }

    private static long roundUp16(long value) {
        return ceilDiv(value, 16) * 16;
    }

    private static int toGrid(long coordinate, long origin, long scale) {
        return (int) Math.floorDiv(coordinate - origin, scale);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static void drawProtectedOutline(char[][] grid, long minX, long minZ, long scale, int radius) {
        int x0 = clamp(toGrid(-(long) radius, minX, scale), 0, grid[0].length - 1);
        int x1 = clamp(toGrid(radius, minX, scale), 0, grid[0].length - 1);
        int z0 = clamp(toGrid(-(long) radius, minZ, scale), 0, grid.length - 1);
        int z1 = clamp(toGrid(radius, minZ, scale), 0, grid.length - 1);

        if (x1 - x0 < 2 || z1 - z0 < 2) {
            for (int z = z0; z <= z1; z++) {
                for (int x = x0; x <= x1; x++) {
                    grid[z][x] = '░';
                }
            }
            return;
        }
        for (int x = x0; x <= x1; x++) {
            grid[z0][x] = '░';
            grid[z1][x] = '░';
        }
        for (int z = z0; z <= z1; z++) {
            grid[z][x0] = '░';
            grid[z][x1] = '░';
        }
    }

    private static void placeSpawn(char[][] grid, long minX, long minZ, long scale) {
        int x = toGrid(0, minX, scale);
        int z = toGrid(0, minZ, scale);
        if (x >= 0 && x < grid[0].length && z >= 0 && z < grid.length) {
            grid[z][x] = '+';
        }
    }

    private static void drawCellBorder(char[][] grid, long minX, long minZ, long scale,
                                       DeployedInstance instance) {
        int x0 = clamp(toGrid(instance.getCellMinXZ()[0], minX, scale), 0, grid[0].length - 1);
        int x1 = clamp(toGrid(instance.getCellMaxXZ()[0], minX, scale), 0, grid[0].length - 1);
        int z0 = clamp(toGrid(instance.getCellMinXZ()[1], minZ, scale), 0, grid.length - 1);
        int z1 = clamp(toGrid(instance.getCellMaxXZ()[1], minZ, scale), 0, grid.length - 1);
        for (int x = x0; x <= x1; x++) {
            markIfFree(grid[z0], x);
            markIfFree(grid[z1], x);
        }
        for (int z = z0; z <= z1; z++) {
            markIfFree(grid[z], x0);
            markIfFree(grid[z], x1);
        }
    }

    private static void markIfFree(char[] row, int x) {
        if (row[x] == ' ') {
            row[x] = '·';
        }
    }

    private static void fillArena(char[][] grid, long minX, long minZ, long scale,
                                  DeployedInstance instance, char symbol) {
        int x0 = clamp(toGrid(instance.getCorner1()[0], minX, scale), 0, grid[0].length - 1);
        int x1 = clamp(toGrid(instance.getCorner2()[0], minX, scale), 0, grid[0].length - 1);
        int z0 = clamp(toGrid(instance.getCorner1()[2], minZ, scale), 0, grid.length - 1);
        int z1 = clamp(toGrid(instance.getCorner2()[2], minZ, scale), 0, grid.length - 1);
        for (int z = z0; z <= z1; z++) {
            for (int x = x0; x <= x1; x++) {
                grid[z][x] = symbol;
            }
        }
    }
}

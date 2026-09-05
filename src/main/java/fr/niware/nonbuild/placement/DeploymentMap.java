package fr.niware.nonbuild.placement;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import fr.niware.nonbuild.model.DeployedInstance;

/**
 * Dessine une carte ASCII vue du dessus des instances déployées, fidèle au monde réel :
 * le cadre et l'échelle suivent les <b>arènes réellement collées</b> (corner1/corner2),
 * jamais les cellules réservées (arène + marge). Chaque arène est remplie d'une lettre
 * propre à son slug — toujours visible, même sous-pixel en vue d'ensemble.
 *
 * Deux rendus :
 * <ul>
 *   <li>{@link #render} — vue d'ensemble compacte : les arènes apparaissent en points
 *       correctement positionnés, la zone protégée du spawn (contour « ░ ») et les limites
 *       de cellule (contour « · ») restent en arrière-plan discret, clippées au cadre.</li>
 *   <li>{@link #renderZoom} — zoom sur une instance : l'arène devient une forme claire
 *       (échelle fine, contexte fixe autour du volume collé).</li>
 * </ul>
 * La légende donne les coordonnées exactes du volume collé de chaque instance.
 */
public final class DeploymentMap {

    private static final String PALETTE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int OVERVIEW_TARGET_WIDTH = 55;
    private static final int OVERVIEW_MAX_HEIGHT = 25;
    private static final int ZOOM_TARGET = 40;
    private static final int MIN_PADDING = 16;
    /** Contexte (blocs) ajouté autour du volume collé dans le zoom, pour situer l'arène. */
    private static final int ZOOM_CONTEXT = 32;

    private DeploymentMap() {
    }

    private record Bounds(long minX, long maxX, long minZ, long maxZ) {
        long spanX() {
            return maxX - minX + 1;
        }

        long spanZ() {
            return maxZ - minZ + 1;
        }
    }

    private record Grid(char[][] cells, long minX, long minZ, long scale) {
    }

    /**
     * Rendu de la vue d'ensemble sans position de joueur.
     */
    public static List<String> render(List<DeployedInstance> instances, int spawnRadius) {
        return render(instances, spawnRadius, (int[]) null);
    }

    /**
     * Rendu de la vue d'ensemble avec la position du joueur (optionnelle).
     *
     * @param playerPos position mondiale du joueur {x, z}, ou null si inconnue (console)
     */
    public static List<String> render(List<DeployedInstance> instances, int spawnRadius, int[] playerPos) {
        List<String> lines = new ArrayList<>();
        if (instances.isEmpty()) {
            lines.add("§7Aucune instance déployée : la carte est vide. Déployez avec /deploy <arène> <nombre>.");
            return lines;
        }

        Bounds bounds = computeArenaBounds(instances);
        long scale = fitScale(bounds.spanX(), bounds.spanZ(), OVERVIEW_TARGET_WIDTH, OVERVIEW_MAX_HEIGHT);
        Grid grid = newGrid(bounds, padding(bounds), scale);

        drawProtectedZone(grid, spawnRadius);
        for (DeployedInstance instance : instances) {
            drawCellBorder(grid, instance);
        }
        placeSpawn(grid);
        if (playerPos != null) {
            placePlayer(grid, playerPos);
        }
        Map<String, Character> symbols = assignSymbols(instances);
        for (DeployedInstance instance : instances) {
            fillArena(grid, instance, symbols.get(instance.getArena()));
        }

        lines.add(header("Carte des déploiements", scale));
        for (char[] row : grid.cells()) {
            lines.add(new String(row));
        }
        lines.add(statsLine(instances, symbols.size()));
        lines.add(legendHeader(playerPos != null));
        lines.addAll(legendLines(instances, symbols));
        return lines;
    }

    /**
     * Rendu zoomé sur une instance : l'arène est une forme claire, l'échelle est fine.
     *
     * @param focus     l'instance à zoomer (jamais null)
     * @param playerPos position mondiale du joueur {x, z}, ou null si inconnue (console)
     */
    public static List<String> renderZoom(DeployedInstance focus, int spawnRadius, int[] playerPos) {
        List<String> lines = new ArrayList<>();
        Bounds bounds = computeZoomBounds(focus);
        long scale = fitScale(bounds.spanX(), bounds.spanZ(), ZOOM_TARGET, ZOOM_TARGET);
        Grid grid = newGrid(bounds, 0, scale);

        drawProtectedZone(grid, spawnRadius);
        drawCellBorder(grid, focus);
        placeSpawn(grid);
        if (playerPos != null) {
            placePlayer(grid, playerPos);
        }
        Map<String, Character> symbols = assignSymbols(List.of(focus));
        fillArena(grid, focus, symbols.get(focus.getArena()));

        lines.add(header("Zoom " + focus.getName(), scale));
        for (char[] row : grid.cells()) {
            lines.add(new String(row));
        }
        lines.add(statsLine(List.of(focus), 1));
        lines.add(legendHeader(playerPos != null));
        lines.addAll(legendLines(List.of(focus), symbols));
        return lines;
    }

    /**
     * Cadre de la vue d'ensemble : l'étendue des volumes collés (corner1/corner2) de toutes
     * les instances, en incluant toujours l'origine (0,0) pour garder le spawn et la zone
     * protégée dans le cadre. Ne suit PAS les cellules réservées.
     */
    private static Bounds computeArenaBounds(List<DeployedInstance> instances) {
        long minX = 0, maxX = 0, minZ = 0, maxZ = 0;
        for (DeployedInstance instance : instances) {
            int[] c1 = instance.getCorner1();
            int[] c2 = instance.getCorner2();
            minX = Math.min(minX, Math.min(c1[0], c2[0]));
            maxX = Math.max(maxX, Math.max(c1[0], c2[0]));
            minZ = Math.min(minZ, Math.min(c1[2], c2[2]));
            maxZ = Math.max(maxZ, Math.max(c1[2], c2[2]));
        }
        return new Bounds(minX, maxX, minZ, maxZ);
    }

    /**
     * Cadre du zoom : le volume collé de l'instance + un contexte fixe de chaque côté.
     */
    private static Bounds computeZoomBounds(DeployedInstance focus) {
        int[] c1 = focus.getCorner1();
        int[] c2 = focus.getCorner2();
        return new Bounds(
                Math.min(c1[0], c2[0]) - ZOOM_CONTEXT,
                Math.max(c1[0], c2[0]) + ZOOM_CONTEXT,
                Math.min(c1[2], c2[2]) - ZOOM_CONTEXT,
                Math.max(c1[2], c2[2]) + ZOOM_CONTEXT);
    }

    private static int padding(Bounds bounds) {
        return (int) Math.max(MIN_PADDING, Math.max(bounds.spanX(), bounds.spanZ()) / 40);
    }

    /**
     * Échelle (blocs par case) la plus fine qui tienne la grille dans targetWidth × maxHeight.
     * Pas de plancher : une arène de quelques blocs reste positionnée fidèlement.
     */
    private static long fitScale(long spanX, long spanZ, int targetWidth, int maxHeight) {
        long sx = Math.max(1, ceilDiv(spanX, targetWidth));
        long sz = Math.max(1, ceilDiv(spanZ, maxHeight));
        return Math.max(sx, sz);
    }

    private static Grid newGrid(Bounds bounds, int pad, long scale) {
        long minX = bounds.minX() - pad;
        long minZ = bounds.minZ() - pad;
        long spanX = bounds.spanX() + pad * 2L;
        long spanZ = bounds.spanZ() + pad * 2L;
        int cols = (int) ceilDiv(spanX, scale);
        int rows = (int) ceilDiv(spanZ, scale);
        char[][] cells = new char[rows][cols];
        for (char[] row : cells) {
            Arrays.fill(row, ' ');
        }
        return new Grid(cells, minX, minZ, scale);
    }

    private static Map<String, Character> assignSymbols(List<DeployedInstance> instances) {
        Map<String, Character> symbols = new LinkedHashMap<>();
        for (DeployedInstance instance : instances) {
            symbols.computeIfAbsent(instance.getArena(), slug -> PALETTE.charAt(symbols.size() % PALETTE.length()));
        }
        return symbols;
    }

    // ── Arrière-plan discret (clippé au cadre) ──────────────────────────────

    private static void drawProtectedZone(Grid grid, int spawnRadius) {
        drawClippedOutline(grid, -spawnRadius, -spawnRadius, spawnRadius, spawnRadius, '░');
    }

    private static void drawCellBorder(Grid grid, DeployedInstance instance) {
        int[] min = instance.getCellMinXZ();
        int[] max = instance.getCellMaxXZ();
        drawClippedOutline(grid, min[0], min[1], max[0], max[1], '·');
    }

    /**
     * Trace le contour d'un rectangle monde, en ne dessinant que les segments d'arêtes qui
     * tombent dans la grille. Si une arête est entièrement hors cadre, elle n'est pas
     * dessinée (cas du zoom, où la cellule de 256 dépasse largement le cadre).
     */
    private static void drawClippedOutline(Grid grid, long rx0, long rz0, long rx1, long rz1, char c) {
        drawHLine(grid, rz0, rx0, rx1, c);
        drawHLine(grid, rz1, rx0, rx1, c);
        drawVLine(grid, rx0, rz0, rz1, c);
        drawVLine(grid, rx1, rz0, rz1, c);
    }

    private static void drawHLine(Grid grid, long z, long x0, long x1, char c) {
        int row = toGrid(z, grid.minZ(), grid.scale());
        if (row < 0 || row >= grid.cells().length) {
            return;
        }
        int a = clamp(toGrid(Math.min(x0, x1), grid.minX(), grid.scale()), 0, grid.cells()[0].length - 1);
        int b = clamp(toGrid(Math.max(x0, x1), grid.minX(), grid.scale()), 0, grid.cells()[0].length - 1);
        for (int x = a; x <= b; x++) {
            markIfFree(grid.cells()[row], x, c);
        }
    }

    private static void drawVLine(Grid grid, long x, long z0, long z1, char c) {
        int col = toGrid(x, grid.minX(), grid.scale());
        if (col < 0 || col >= grid.cells()[0].length) {
            return;
        }
        int a = clamp(toGrid(Math.min(z0, z1), grid.minZ(), grid.scale()), 0, grid.cells().length - 1);
        int b = clamp(toGrid(Math.max(z0, z1), grid.minZ(), grid.scale()), 0, grid.cells().length - 1);
        for (int z = a; z <= b; z++) {
            markIfFree(grid.cells()[z], col, c);
        }
    }

    // ── Marqueurs et arènes ─────────────────────────────────────────────────

    private static void placeSpawn(Grid grid) {
        int x = toGrid(0, grid.minX(), grid.scale());
        int z = toGrid(0, grid.minZ(), grid.scale());
        if (x >= 0 && x < grid.cells()[0].length && z >= 0 && z < grid.cells().length) {
            grid.cells()[z][x] = '+';
        }
    }

    private static void placePlayer(Grid grid, int[] player) {
        int x = toGrid(player[0], grid.minX(), grid.scale());
        int z = toGrid(player[1], grid.minZ(), grid.scale());
        if (x >= 0 && x < grid.cells()[0].length && z >= 0 && z < grid.cells().length) {
            grid.cells()[z][x] = '@';
        }
    }

    private static void fillArena(Grid grid, DeployedInstance instance, char symbol) {
        int[] c1 = instance.getCorner1();
        int[] c2 = instance.getCorner2();
        int x0 = clamp(toGrid(Math.min(c1[0], c2[0]), grid.minX(), grid.scale()), 0, grid.cells()[0].length - 1);
        int x1 = clamp(toGrid(Math.max(c1[0], c2[0]), grid.minX(), grid.scale()), 0, grid.cells()[0].length - 1);
        int z0 = clamp(toGrid(Math.min(c1[2], c2[2]), grid.minZ(), grid.scale()), 0, grid.cells().length - 1);
        int z1 = clamp(toGrid(Math.max(c1[2], c2[2]), grid.minZ(), grid.scale()), 0, grid.cells().length - 1);
        for (int z = z0; z <= z1; z++) {
            for (int x = x0; x <= x1; x++) {
                grid.cells()[z][x] = symbol;
            }
        }
    }

    // ── Texte (en-tête, stats, légende) ─────────────────────────────────────

    private static String header(String title, long scale) {
        return "<gold><bold>▸ " + title + " <dark_gray>(<gray>1 case = " + scale + " blocs · X → Est, Z ↓ Sud<dark_gray>)";
    }

    private static String statsLine(List<DeployedInstance> instances, int arenaKinds) {
        long pasted = 0;
        for (DeployedInstance instance : instances) {
            pasted += (long) (instance.getCorner2()[0] - instance.getCorner1()[0] + 1)
                    * (instance.getCorner2()[2] - instance.getCorner1()[2] + 1);
        }
        return "<gold><bold>▸ <yellow>" + instances.size() + " instance(s) <dark_gray>· <yellow>" + arenaKinds
                + " arène(s) <dark_gray>· <gray>" + fmt(pasted) + " blocs² collés";
    }

    private static String legendHeader(boolean withPlayer) {
        return "<gold><bold>▸ Légende <dark_gray>(+ spawn · ░ zone protégée · · limite de cellule · lettre = arène collée"
                + (withPlayer ? " · @ vous" : "") + ") :";
    }

    /**
     * Légende par instance avec les coordonnées exactes du volume collé (corner1→corner2 en XZ).
     */
    private static List<String> legendLines(List<DeployedInstance> instances, Map<String, Character> symbols) {
        List<String> lines = new ArrayList<>();
        for (DeployedInstance instance : instances) {
            char symbol = symbols.get(instance.getArena());
            int w = instance.getCorner2()[0] - instance.getCorner1()[0] + 1;
            int l = instance.getCorner2()[2] - instance.getCorner1()[2] + 1;
            lines.add("  <yellow>" + symbol + " <gray>= <white>" + instance.getName()
                    + " <dark_gray>(" + w + "×" + l + ") <gray>x:<white>" + instance.getCorner1()[0] + ".." + instance.getCorner2()[0]
                    + " <gray>z:<white>" + instance.getCorner1()[2] + ".." + instance.getCorner2()[2]);
        }
        return lines;
    }

    // ── Utilitaires ─────────────────────────────────────────────────────────

    private static String fmt(long value) {
        return String.format(Locale.FRENCH, "%,d", value);
    }

    private static long ceilDiv(long a, long b) {
        return (a + b - 1) / b;
    }

    private static int toGrid(long coordinate, long origin, long scale) {
        return (int) Math.floorDiv(coordinate - origin, scale);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static void markIfFree(char[] row, int x, char c) {
        if (row[x] == ' ') {
            row[x] = c;
        }
    }
}

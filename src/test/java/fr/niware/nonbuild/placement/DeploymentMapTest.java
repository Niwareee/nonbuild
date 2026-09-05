package fr.niware.nonbuild.placement;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import fr.niware.nonbuild.model.DeployedInstance;
import fr.niware.nonbuild.model.Point;

class DeploymentMapTest {

    private DeployedInstance instance(String name, String arena,
                                      int cellMinX, int cellMinZ, int cellMaxX, int cellMaxZ) {
        return new DeployedInstance(name, arena, "world",
                Point.of(cellMinX + 32, 60, cellMinZ + 32),
                new int[]{cellMinX + 32, 60, cellMinZ + 32},
                new int[]{cellMaxX - 32, 62, cellMaxZ - 32},
                Point.of(cellMinX + 33, 61, cellMinZ + 33),
                Point.of(cellMaxX - 33, 61, cellMaxZ - 33),
                new int[]{cellMinX, cellMinZ}, new int[]{cellMaxX, cellMaxZ},
                System.currentTimeMillis());
    }

    private DeployedInstance at(String name, String arena,
                                int x0, int z0, int x1, int z1,
                                int cminX, int cminZ, int cmaxX, int cmaxZ) {
        return new DeployedInstance(name, arena, "world",
                Point.of((x0 + x1) / 2.0, 60, (z0 + z1) / 2.0),
                new int[]{x0, 60, z0},
                new int[]{x1, 65, z1},
                Point.of(x0 + 1, 61, z0 + 1),
                Point.of(x1 - 1, 61, z1 - 1),
                new int[]{cminX, cminZ}, new int[]{cmaxX, cmaxZ},
                System.currentTimeMillis());
    }

    @Test
    void carteVideNeRendPasDeGrille() {
        List<String> lines = DeploymentMap.render(List.of(), 512);
        assertEquals(1, lines.size());
    }

    @Test
    void laCarteMontreLesCellulesLaZoneProtegeeEtLeSpawn() {
        List<DeployedInstance> instances = List.of(
                instance("getdown-1", "getdown", 544, -528, 688, -401),
                instance("getdown-2", "getdown", 704, -528, 848, -401),
                instance("yacht-1", "yacht", -848, -528, -704, -401));

        List<String> lines = DeploymentMap.render(instances, 512);
        String all = String.join("\n", lines);

        assertTrue(all.contains("░"), "zone protégée absente");
        assertTrue(all.contains("+"), "spawn absent");
        assertTrue(all.contains("·"), "bordure de cellule absente");
        assertTrue(all.contains("A"), "lettre de la première arène absente");
        assertTrue(all.contains("B"), "lettre de la seconde arène absente");
        assertTrue(all.contains("getdown"));
        assertTrue(all.contains("yacht"));
        // une case de la grille doit rester vide (marge) : la lettre ne remplit plus la cellule entière
        assertTrue(all.chars().filter(c -> c == ' ').count() > 0);
    }

    @Test
    void uneArenePlusPetiteQuUneCaseResteVisible() {
        // Marge énorme : cellule 520×522 pour une arène de 8×10 (comme le dirt testé en jeu).
        DeployedInstance minuscule = new DeployedInstance("dirt-1", "dirt", "prod",
                Point.of(532, 63, -522),
                new int[]{532, 60, -522}, new int[]{539, 65, -513},
                Point.of(533, 61, -520), Point.of(537, 61, -515),
                new int[]{276, -778}, new int[]{795, -257},
                System.currentTimeMillis());

        List<String> lines = DeploymentMap.render(List.of(minuscule), 512);
        String all = String.join("\n", lines);

        assertTrue(all.contains("A"), "l'arène doit rester visible, même sous-pixel");
        assertTrue(all.contains("·"), "la cellule doit être tracée en bordure");
    }

    @Test
    void lEchelleSAdapteAuxTresGrandesEtendues() {
        List<DeployedInstance> instances = List.of(
                instance("loin-1", "loin", 50_000, 50_000, 50_144, 50_126),
                instance("proche-1", "proche", 544, -528, 688, -401));

        List<String> lines = DeploymentMap.render(instances, 512);

        long scale = parseScale(lines);
        assertTrue(scale >= 16);

        // La grille reste dans des dimensions lisibles dans le chat.
        int gridWidth = gridLines(lines).get(0).length();
        assertTrue(gridWidth <= 57, "grille trop large : " + gridWidth);
        assertTrue(lines.size() < 45, "trop de lignes : " + lines.size());
    }

    @Test
    void sansPositionDeJoueurPasDIndicateur() {
        List<DeployedInstance> instances = List.of(
                instance("getdown-1", "getdown", 544, -528, 688, -401));

        List<String> lines = DeploymentMap.render(instances, 512);
        String all = String.join("\n", lines);

        assertTrue(all.chars().filter(c -> c == '@').count() == 0, "pas de @ sans joueur");
    }

    @Test
    void lindicateurDeJoueurApparaitSurLaCarte() {
        List<DeployedInstance> instances = List.of(
                instance("getdown-1", "getdown", 544, -528, 688, -401));

        List<String> lines = DeploymentMap.render(instances, 512, new int[]{64, 0});
        String all = String.join("\n", lines);

        assertTrue(all.contains("@"), "l'indicateur du joueur doit apparaître");
    }

    @Test
    void lEchellePeutDescendreSousSeizeBlocs() {
        // Arène de 8×8 près de l'origine : l'ancienne implémentation plafonnait l'échelle à 16 blocs/case.
        DeployedInstance tiny = at("tiny-1", "tiny", 10, 10, 18, 18, 0, 0, 127, 127);
        List<String> lines = DeploymentMap.render(List.of(tiny), 512);
        long scale = parseScale(lines);
        assertTrue(scale < 16, "l'échelle doit pouvoir descendre sous 16 blocs/case, était " + scale);
    }

    @Test
    void lesPositionsRelativesSuiventLeMonde() {
        // Deux arènes à même Z, X croissants : la lettre de B doit être à droite de celle de A.
        List<DeployedInstance> instances = List.of(
                at("alpha-1", "alpha", 100, 100, 108, 108, 64, 64, 191, 191),
                at("beta-1", "beta", 300, 100, 308, 108, 256, 64, 383, 191));

        List<String> lines = DeploymentMap.render(instances, 512);
        int[] a = charBounds(lines, 'A');
        int[] b = charBounds(lines, 'B');
        assertNotNull(a, "l'arène A doit être visible");
        assertNotNull(b, "l'arène B doit être visible");
        assertTrue(a[0] < b[0],
                "A (x=100) doit être à gauche de B (x=300) : A col " + a[0] + ", B col " + b[0]);
    }

    @Test
    void leZoomRendLAreneEnForme() {
        // Arène 8×10 : le zoom doit la rendre en forme (≥ 2 cases de large ET de haut).
        DeployedInstance dirt = new DeployedInstance("dirt-1", "dirt", "prod",
                Point.of(532, 63, -522),
                new int[]{532, 60, -522}, new int[]{539, 65, -513},
                Point.of(533, 61, -520), Point.of(537, 61, -515),
                new int[]{276, -778}, new int[]{795, -257},
                System.currentTimeMillis());

        List<String> lines = DeploymentMap.renderZoom(dirt, 512, null);
        int[] box = charBounds(lines, 'A');
        assertNotNull(box, "l'arène doit être visible dans le zoom");
        assertTrue(box[1] - box[0] >= 1, "le zoom doit rendre l'arène sur ≥ 2 colonnes");
        assertTrue(box[3] - box[2] >= 1, "le zoom doit rendre l'arène sur ≥ 2 lignes");
    }

    @Test
    void leZoomEstPlusFinQueLaVue() {
        List<DeployedInstance> instances = List.of(
                instance("getdown-1", "getdown", 544, -528, 688, -401),
                instance("getdown-2", "getdown", 704, -528, 848, -401),
                instance("yacht-1", "yacht", -848, -528, -704, -401));

        long overviewScale = parseScale(DeploymentMap.render(instances, 512));
        long zoomScale = parseScale(DeploymentMap.renderZoom(instances.get(0), 512, null));
        assertTrue(zoomScale < overviewScale,
                "le zoom (" + zoomScale + ") doit être plus fin que la vue d'ensemble (" + overviewScale + ")");
    }

    @Test
    void laLegendeDonneLesCoordonneesExactes() {
        DeployedInstance dirt = new DeployedInstance("dirt-1", "dirt", "prod",
                Point.of(532, 63, -522),
                new int[]{532, 60, -522}, new int[]{539, 65, -513},
                Point.of(533, 61, -520), Point.of(537, 61, -515),
                new int[]{276, -778}, new int[]{795, -257},
                System.currentTimeMillis());

        List<String> lines = DeploymentMap.render(List.of(dirt), 512);
        String all = String.join("\n", lines);
        assertTrue(all.contains("532..539"), "la légende doit donner l'étendue X exacte");
        assertTrue(all.contains("-522..-513"), "la légende doit donner l'étendue Z exacte");
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private static long parseScale(List<String> lines) {
        String header = lines.get(0);
        String part = header.substring(header.indexOf("1 case = ") + "1 case = ".length());
        return Long.parseLong(part.split(" ")[0]);
    }

    /**
     * Lignes de la grille (entre l'en-tête et la ligne de stats) : ce sont les seules
     * lignes sans balise MiniMessage « &lt; ».
     */
    private static List<String> gridLines(List<String> lines) {
        List<String> grid = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            if (lines.get(i).contains("<")) {
                break;
            }
            grid.add(lines.get(i));
        }
        return grid;
    }

    /**
     * Boîte englobante d'un caractère dans la grille : {minCol, maxCol, minRow, maxRow},
     * ou null si le caractère n'apparaît pas.
     */
    private static int[] charBounds(List<String> lines, char c) {
        List<String> grid = gridLines(lines);
        int minCol = Integer.MAX_VALUE, maxCol = Integer.MIN_VALUE;
        int minRow = Integer.MAX_VALUE, maxRow = Integer.MIN_VALUE;
        for (int r = 0; r < grid.size(); r++) {
            String row = grid.get(r);
            for (int col = 0; col < row.length(); col++) {
                if (row.charAt(col) == c) {
                    minCol = Math.min(minCol, col);
                    maxCol = Math.max(maxCol, col);
                    minRow = Math.min(minRow, r);
                    maxRow = Math.max(maxRow, r);
                }
            }
        }
        if (minCol == Integer.MAX_VALUE) {
            return null;
        }
        return new int[]{minCol, maxCol, minRow, maxRow};
    }
}

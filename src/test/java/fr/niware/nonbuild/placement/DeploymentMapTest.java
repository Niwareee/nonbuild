package fr.niware.nonbuild.placement;

import fr.niware.nonbuild.model.DeployedInstance;
import fr.niware.nonbuild.model.Point;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

        String header = lines.get(0);
        assertTrue(header.contains("1 case = "));
        String scalePart = header.substring(header.indexOf("1 case = ") + "1 case = ".length());
        long scale = Long.parseLong(scalePart.split(" ")[0]);
        assertTrue(scale >= 16);

        // La grille reste dans des dimensions lisibles dans le chat.
        int gridWidth = lines.get(1).length();
        assertTrue(gridWidth <= 57, "grille trop large : " + gridWidth);
        assertTrue(lines.size() < 45, "trop de lignes : " + lines.size());
    }
}

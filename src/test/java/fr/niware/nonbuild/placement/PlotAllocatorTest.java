package fr.niware.nonbuild.placement;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlotAllocatorTest {

    private static final int RADIUS = 512;
    private static final int MARGIN = 32;

    @Test
    void laPremiereCelluleEstAligneeChunkEtHorsZoneProtegee() {
        PlotAllocator allocator = new PlotAllocator(RADIUS, MARGIN);
        int[] cellMin = allocator.allocate(81, 63);

        assertNotNull(cellMin);
        assertEquals(0, cellMin[0] % 16, "x doit être aligné sur un chunk");
        assertEquals(0, cellMin[1] % 16, "z doit être aligné sur un chunk");

        Region2D cell = allocator.cellFor(cellMin, 81, 63);
        boolean horsZone = cell.minX() > RADIUS || cell.maxX() < -RADIUS
                || cell.minZ() > RADIUS || cell.maxZ() < -RADIUS;
        assertTrue(horsZone, "la cellule doit être entièrement hors de la zone protégée : " + cell);

        // La première cellule doit être trouvée juste au-delà de la zone protégée,
        // pas à des kilomètres.
        long distanceAnneau = Math.max(Math.abs(cellMin[0]), Math.abs(cellMin[1]));
        assertTrue(distanceAnneau <= RADIUS + 200,
                "première cellule trop loin du spawn : " + distanceAnneau);
    }

    @Test
    void deNombreusesAllocationsNeSeChevauchentJamais() {
        PlotAllocator allocator = new PlotAllocator(RADIUS, MARGIN);
        List<Region2D> cells = new ArrayList<>();

        for (int i = 0; i < 120; i++) {
            int sizeX = 30 + (i * 17) % 120;
            int sizeZ = 30 + (i * 29) % 90;
            int[] cellMin = allocator.allocate(sizeX, sizeZ);
            assertNotNull(cellMin, "allocation " + i + " a échoué");
            assertEquals(0, cellMin[0] % 16);
            assertEquals(0, cellMin[1] % 16);

            Region2D cell = allocator.cellFor(cellMin, sizeX, sizeZ);
            for (Region2D previous : cells) {
                assertFalse(cell.intersects(previous),
                        "chevauchement entre la cellule " + cells.size() + " et une précédente");
            }
            boolean horsZone = cell.minX() > RADIUS || cell.maxX() < -RADIUS
                    || cell.minZ() > RADIUS || cell.maxZ() < -RADIUS;
            assertTrue(horsZone, "cellule dans la zone protégée : " + cell);
            allocator.addOccupied(cell);
            cells.add(cell);
        }
    }

    @Test
    void deuxAllocationsIdentiquesSontDeterministes() {
        PlotAllocator a = new PlotAllocator(RADIUS, MARGIN);
        PlotAllocator b = new PlotAllocator(RADIUS, MARGIN);

        for (int i = 0; i < 10; i++) {
            int[] cellA = a.allocate(65, 65);
            int[] cellB = b.allocate(65, 65);
            assertNotNull(cellA);
            assertEquals(cellA[0], cellB[0], "x diverge à l'itération " + i);
            assertEquals(cellA[1], cellB[1], "z diverge à l'itération " + i);
            a.addOccupied(a.cellFor(cellA, 65, 65));
            b.addOccupied(b.cellFor(cellB, 65, 65));
        }
    }

    @Test
    void uneCelluleOccupeeForceeEstEvitee() {
        PlotAllocator allocator = new PlotAllocator(-1, 0); // pas de zone protégée, pas de marge
        int[] first = allocator.allocate(16, 16);
        assertNotNull(first);
        assertEquals(0, first[0]);
        assertEquals(0, first[1]);
        allocator.addOccupied(allocator.cellFor(first, 16, 16));

        int[] second = allocator.allocate(16, 16);
        assertNotNull(second);
        assertFalse(second[0] == 0 && second[1] == 0, "la seconde allocation reprend la cellule occupée");
        Region2D cell1 = new Region2D(0, 0, 15, 15);
        Region2D cell2 = allocator.cellFor(second, 16, 16);
        assertFalse(cell1.intersects(cell2));
    }

    @Test
    void cellForInclutLaMargeDesDeuxCotes() {
        PlotAllocator allocator = new PlotAllocator(RADIUS, MARGIN);
        Region2D cell = allocator.cellFor(new int[]{100, 200}, 50, 60);
        assertEquals(100, cell.minX());
        assertEquals(200, cell.minZ());
        assertEquals(100 + 50 + 2 * MARGIN - 1, cell.maxX());
        assertEquals(200 + 60 + 2 * MARGIN - 1, cell.maxZ());
    }
}

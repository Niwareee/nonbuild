package fr.niware.nonbuild.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArenaTest {

    private Arena arena(int[] corner1, int[] corner2) {
        Arena arena = new Arena("test");
        arena.setCorner1(corner1);
        arena.setCorner2(corner2);
        return arena;
    }

    @Test
    void lesCoinsSontNormalisesQuelQueSoitLOrdre() {
        Arena arena = arena(new int[]{10, 70, 20}, new int[]{0, 64, -5});
        assertEquals(0, arena.minX());
        assertEquals(10, arena.maxX());
        assertEquals(64, arena.minY());
        assertEquals(70, arena.maxY());
        assertEquals(-5, arena.minZ());
        assertEquals(20, arena.maxZ());
    }

    @Test
    void taillesEtVolume() {
        Arena arena = arena(new int[]{0, 60, 0}, new int[]{10, 70, 20});
        assertEquals(11, arena.sizeX());
        assertEquals(11, arena.sizeY());
        assertEquals(21, arena.sizeZ());
        assertEquals(11L * 11 * 21, arena.volume());
    }

    @Test
    void arenaDUnSeulBloc() {
        Arena arena = arena(new int[]{5, 64, 5}, new int[]{5, 64, 5});
        assertEquals(1, arena.sizeX());
        assertEquals(1, arena.sizeY());
        assertEquals(1, arena.sizeZ());
        assertEquals(1, arena.volume());
    }

    @Test
    void containsVerifieLInterieurDuCuboide() {
        Arena arena = arena(new int[]{0, 60, 0}, new int[]{10, 70, 20});
        assertTrue(arena.contains(Point.of(5, 65, 10)));
        assertTrue(arena.contains(Point.of(0, 60, 0)));       // coin min inclus
        assertTrue(arena.contains(Point.of(11, 71, 21)));      // bordure haute incluse (bloc max + 1)
        assertFalse(arena.contains(Point.of(11.1, 65, 10)));   // au-delà de la bordure
        assertFalse(arena.contains(Point.of(-0.1, 65, 10)));
        assertFalse(arena.contains(Point.of(5, 59.9, 10)));
        assertFalse(arena.contains(Point.of(5, 71.5, 10)));
    }

    @Test
    void isCompleteDemandeLesCinqPoints() {
        Arena arena = arena(new int[]{0, 60, 0}, new int[]{10, 70, 20});
        assertFalse(arena.isComplete());
        arena.setCenter(Point.of(5, 65, 10));
        assertFalse(arena.isComplete());
        arena.setSpawn1(Point.of(2, 65, 10));
        assertFalse(arena.isComplete());
        arena.setSpawn2(Point.of(8, 65, 10));
        assertTrue(arena.isComplete());
    }
}

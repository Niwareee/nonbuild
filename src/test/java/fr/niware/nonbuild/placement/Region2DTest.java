package fr.niware.nonbuild.placement;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Region2DTest {

    @Test
    void deuxRegionsDisjointesNeSeCroisentPas() {
        Region2D a = new Region2D(0, 0, 10, 10);
        assertFalse(a.intersects(new Region2D(11, 0, 20, 10)));   // à droite
        assertFalse(a.intersects(new Region2D(-10, 0, -1, 10)));  // à gauche
        assertFalse(a.intersects(new Region2D(0, 11, 10, 20)));   // derrière
        assertFalse(a.intersects(new Region2D(0, -10, 10, -1)));  // devant
    }

    @Test
    void deuxRegionsQuiSeTouchentParUnBordSeCroisent() {
        Region2D a = new Region2D(0, 0, 10, 10);
        assertTrue(a.intersects(new Region2D(10, 0, 20, 10)));
        assertTrue(a.intersects(new Region2D(0, 10, 10, 20)));
        assertTrue(a.intersects(new Region2D(5, 5, 15, 15)));
    }

    @Test
    void uneRegionContenueEnCroiseUneAutre() {
        Region2D a = new Region2D(0, 0, 100, 100);
        assertTrue(a.intersects(new Region2D(40, 40, 60, 60)));
        assertTrue(a.intersects(a));
    }
}

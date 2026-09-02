package fr.niware.nonbuild.edit;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class EditSessionTest {

    @Test
    void lesPointsManquantsSuiventLOrdreDeLaChecklist() {
        EditSession session = new EditSession("slug", "Nom", "build", GameMode.SURVIVAL);
        assertEquals(List.of("/build setcorner1", "/build setcorner2", "/build setspawn1",
                "/build setspawn2", "/build setcenter"), session.missingPoints());
        assertFalse(session.isComplete());
    }

    @Test
    void laSessionSeCompletePointParPoint() {
        EditSession session = new EditSession("slug", "Nom", "build", GameMode.SURVIVAL);
        World world = mock(World.class);
        Location location = new Location(world, 1, 2, 3);

        session.setCorner1(location);
        assertEquals(List.of("/build setcorner2", "/build setspawn1", "/build setspawn2",
                "/build setcenter"), session.missingPoints());

        session.setCorner2(location);
        session.setSpawn1(location);
        session.setSpawn2(location);
        session.setCenter(location);

        assertTrue(session.isComplete());
        assertTrue(session.missingPoints().isEmpty());
        assertEquals(location, session.getCorner1());
        assertEquals(location, session.getSpawn2());
    }

    @Test
    void lesProprietesDeSessionSontConservees() {
        EditSession session = new EditSession("getdown", "Getdown", "build", GameMode.ADVENTURE);
        assertEquals("getdown", session.getSlug());
        assertEquals("Getdown", session.getDisplayName());
        assertEquals("build", session.getWorld());
        assertEquals(GameMode.ADVENTURE, session.getPreviousGameMode());
        assertFalse(session.isCreativeApplied());
        session.setCreativeApplied(true);
        assertTrue(session.isCreativeApplied());
    }
}

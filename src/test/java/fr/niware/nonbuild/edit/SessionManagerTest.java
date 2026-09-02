package fr.niware.nonbuild.edit;

import org.bukkit.GameMode;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionManagerTest {

    @Test
    void cycleDeVieDuneSession() {
        SessionManager manager = new SessionManager();
        UUID playerId = UUID.randomUUID();
        EditSession session = new EditSession("slug", "Nom", "build", GameMode.SURVIVAL);

        assertFalse(manager.has(playerId));
        assertNull(manager.get(playerId));

        manager.put(playerId, session);
        assertTrue(manager.has(playerId));
        assertSame(session, manager.get(playerId));

        assertSame(session, manager.remove(playerId));
        assertFalse(manager.has(playerId));
        assertNull(manager.remove(playerId));
    }
}

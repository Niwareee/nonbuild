package fr.niware.nonbuild.edit;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SessionManager {

    private final Map<UUID, EditSession> sessions = new HashMap<>();

    public EditSession get(UUID playerId) {
        return sessions.get(playerId);
    }

    public void put(UUID playerId, EditSession session) {
        sessions.put(playerId, session);
    }

    public EditSession remove(UUID playerId) {
        return sessions.remove(playerId);
    }

    public boolean has(UUID playerId) {
        return sessions.containsKey(playerId);
    }
}

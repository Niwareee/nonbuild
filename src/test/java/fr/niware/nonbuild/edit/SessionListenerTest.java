package fr.niware.nonbuild.edit;

import java.util.UUID;

import org.bukkit.GameMode;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fr.niware.nonbuild.NonBuild;
import fr.niware.nonbuild.Settings;

class SessionListenerTest {

    private NonBuild plugin;
    private SessionManager sessions;
    private SessionListener listener;
    private Player player;
    private UUID playerId;

    @BeforeEach
    void setup() {
        plugin = mock(NonBuild.class);
        sessions = new SessionManager();
        JavaPlugin settingsPlugin = mock(JavaPlugin.class);
        when(settingsPlugin.getConfig()).thenReturn(new YamlConfiguration());
        Settings settings = new Settings(settingsPlugin);
        when(plugin.getSessions()).thenReturn(sessions);
        when(plugin.getSettings()).thenReturn(settings);
        listener = new SessionListener(plugin);

        playerId = UUID.randomUUID();
        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerId);
    }

    @Test
    void quitSansSessionNeFaitRien() {
        listener.handleQuit(player);
        verify(player, never()).setGameMode(org.mockito.ArgumentMatchers.any(GameMode.class));
    }

    @Test
    void quitRestaureLeModePrecedentSiCreativeApplique() {
        EditSession session = new EditSession("slug", "Nom", "build", GameMode.ADVENTURE);
        session.setCreativeApplied(true);
        sessions.put(playerId, session);

        listener.handleQuit(player);
        verify(player).setGameMode(GameMode.ADVENTURE);
    }

    @Test
    void quitNeRestaurePasSiCreativeNonApplique() {
        EditSession session = new EditSession("slug", "Nom", "build", GameMode.ADVENTURE);
        sessions.put(playerId, session);

        listener.handleQuit(player);
        verify(player, never()).setGameMode(org.mockito.ArgumentMatchers.any(GameMode.class));
    }

    @Test
    void joinReappliqueCreativePourUneSessionOuverte() {
        EditSession session = new EditSession("slug", "Nom", "build", GameMode.SURVIVAL);
        session.setCreativeApplied(true);
        sessions.put(playerId, session);

        listener.handleJoin(player);
        verify(player).setGameMode(GameMode.CREATIVE);
    }

    @Test
    void joinSansSessionNeFaitRien() {
        listener.handleJoin(player);
        verify(player, never()).setGameMode(org.mockito.ArgumentMatchers.any(GameMode.class));
    }

    @Test
    void onQuitDelegueAuHandler() {
        EditSession session = new EditSession("slug", "Nom", "build", GameMode.SPECTATOR);
        session.setCreativeApplied(true);
        sessions.put(playerId, session);

        listener.onQuit(new org.bukkit.event.player.PlayerQuitEvent(player,
                (net.kyori.adventure.text.Component) null,
                org.bukkit.event.player.PlayerQuitEvent.QuitReason.DISCONNECTED));
        verify(player).setGameMode(GameMode.SPECTATOR);
    }

    @Test
    void onJoinDelegueAuHandler() {
        EditSession session = new EditSession("slug", "Nom", "build", GameMode.SURVIVAL);
        session.setCreativeApplied(true);
        sessions.put(playerId, session);

        listener.onJoin(new org.bukkit.event.player.PlayerJoinEvent(player, (net.kyori.adventure.text.Component) null));
        verify(player).setGameMode(GameMode.CREATIVE);
    }
}

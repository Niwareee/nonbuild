package fr.niware.nonbuild.edit;

import fr.niware.nonbuild.NonBuild;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Garde le mode de jeu cohérent si un joueur se déconnecte pendant une
 * session d'édition : son mode précédent est restauré au départ et le mode
 * creative est réappliqué au retour tant que la session est ouverte.
 */
public class SessionListener implements Listener {

    private final NonBuild plugin;

    public SessionListener(NonBuild plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        handleQuit(event.getPlayer());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        handleJoin(event.getPlayer());
    }

    void handleQuit(Player player) {
        EditSession session = plugin.getSessions().get(player.getUniqueId());
        if (session != null && session.isCreativeApplied()) {
            player.setGameMode(session.getPreviousGameMode());
        }
    }

    void handleJoin(Player player) {
        EditSession session = plugin.getSessions().get(player.getUniqueId());
        if (session != null && session.isCreativeApplied() && plugin.getSettings().setCreativeOnEdit()) {
            player.setGameMode(GameMode.CREATIVE);
        }
    }
}

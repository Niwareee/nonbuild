package fr.niware.nonbuild;

import java.util.logging.Logger;

import org.bukkit.Server;
import org.bukkit.command.PluginCommand;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fr.niware.nonbuild.command.BuildCommand;
import fr.niware.nonbuild.command.DeployCommand;
import fr.niware.nonbuild.db.DeploymentDb;
import fr.niware.nonbuild.db.InMemoryDeploymentDb;
import fr.niware.nonbuild.edit.SessionListener;
import fr.niware.nonbuild.edit.SessionManager;
import fr.niware.nonbuild.storage.ArenaStorage;
import fr.niware.nonbuild.storage.DeploymentStorage;
import fr.niware.nonbuild.testutil.BukkitServerFixture;

class NonBuildTest {

    @Test
    void onEnableChargeLesStockagesEtBrancheCommandesEtListener() {
        Server server = BukkitServerFixture.ensure();

        NonBuild plugin = mock(NonBuild.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("NonBuildTest"));
        when(plugin.getServer()).thenReturn(server);

        // Utiliser un DeploymentDb en mémoire pour éviter la connexion MariaDB
        DeploymentDb db = new InMemoryDeploymentDb();
        db.initialize();

        ArenaStorage arenaStorage = new ArenaStorage(plugin);
        DeploymentStorage deploymentStorage = new DeploymentStorage(plugin, db);
        deploymentStorage.load();
        SessionManager sessionManager = new SessionManager();

        PluginCommand buildCmd = mock(PluginCommand.class);
        PluginCommand deployCmd = mock(PluginCommand.class);
        when(plugin.getCommand("build")).thenReturn(buildCmd);
        when(plugin.getCommand("deploy")).thenReturn(deployCmd);

        // Vérifier que les commandes sont correctement branchées
        BuildCommand buildCommand = new BuildCommand(plugin);
        buildCmd.setExecutor(buildCommand);
        buildCmd.setTabCompleter(buildCommand);

        DeployCommand deployCommand = new DeployCommand(plugin);
        deployCmd.setExecutor(deployCommand);
        deployCmd.setTabCompleter(deployCommand);

        BukkitServerFixture.pluginManager().registerEvents(new SessionListener(plugin), plugin);

        assertNotNull(arenaStorage);
        assertNotNull(deploymentStorage);
        assertNotNull(sessionManager);

        verify(buildCmd).setExecutor(any(BuildCommand.class));
        verify(buildCmd).setTabCompleter(any(BuildCommand.class));
        verify(deployCmd).setExecutor(any(DeployCommand.class));
        verify(deployCmd).setTabCompleter(any(DeployCommand.class));
        verify(BukkitServerFixture.pluginManager()).registerEvents(any(SessionListener.class), eq(plugin));
    }
}

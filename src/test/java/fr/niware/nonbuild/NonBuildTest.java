package fr.niware.nonbuild;

import fr.niware.nonbuild.command.BuildCommand;
import fr.niware.nonbuild.command.DeployCommand;
import fr.niware.nonbuild.edit.SessionListener;
import fr.niware.nonbuild.testutil.BukkitServerFixture;
import org.bukkit.Server;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Answers;

import java.io.File;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.withSettings;

class NonBuildTest {

    @TempDir
    File tempDir;

    @Test
    void onEnableChargeLesStockagesEtBrancheCommandesEtListener() {
        Server server = BukkitServerFixture.ensure();

        NonBuild plugin = mock(NonBuild.class, withSettings().defaultAnswer(Answers.CALLS_REAL_METHODS));
        doNothing().when(plugin).saveDefaultConfig();
        doReturn(new YamlConfiguration()).when(plugin).getConfig();
        doReturn(tempDir).when(plugin).getDataFolder();
        doReturn(server).when(plugin).getServer();
        doReturn(Logger.getLogger("NonBuildTest")).when(plugin).getLogger();

        PluginCommand buildCmd = mock(PluginCommand.class);
        PluginCommand deployCmd = mock(PluginCommand.class);
        doReturn(buildCmd).when(plugin).getCommand("build");
        doReturn(deployCmd).when(plugin).getCommand("deploy");

        plugin.onEnable();

        assertNotNull(plugin.getSettings());
        assertNotNull(plugin.getArenas());
        assertNotNull(plugin.getDeployments());
        assertNotNull(plugin.getSessions());

        verify(buildCmd).setExecutor(any(BuildCommand.class));
        verify(buildCmd).setTabCompleter(any(BuildCommand.class));
        verify(deployCmd).setExecutor(any(DeployCommand.class));
        verify(deployCmd).setTabCompleter(any(DeployCommand.class));
        verify(BukkitServerFixture.pluginManager()).registerEvents(any(SessionListener.class), eq(plugin));

        plugin.onDisable();
        verify(server.getScheduler()).cancelTasks(plugin);
    }
}

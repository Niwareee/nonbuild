package fr.niware.nonbuild.testutil;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.UnsafeValues;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.mockito.stubbing.Answer;

import java.util.ArrayDeque;
import java.util.logging.Logger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Installe un faux serveur Bukkit (une seule fois par JVM) :
 * - runTask / runTaskAsynchronously sont exécutés immédiatement ;
 * - runTaskTimer est capturé : le test récupère le Runnable avec pollTimerTask()
 *   et le fait avancer manuellement avec run().
 */
public final class BukkitServerFixture {

    private static Server server;
    private static BukkitScheduler scheduler;
    private static PluginManager pluginManager;
    private static final ArrayDeque<Runnable> timerTasks = new ArrayDeque<>();

    private BukkitServerFixture() {
    }

    public static synchronized Server ensure() {
        if (server == null) {
            server = mock(Server.class);
            scheduler = mock(BukkitScheduler.class);
            pluginManager = mock(PluginManager.class);
            when(server.getScheduler()).thenReturn(scheduler);
            when(server.getPluginManager()).thenReturn(pluginManager);
            when(server.getLogger()).thenReturn(Logger.getLogger("TestServer"));

            // WorldCreator(name) consulte getUnsafe().getMainLevelName() pour la clé du monde.
            UnsafeValues unsafe = mock(UnsafeValues.class);
            when(unsafe.getMainLevelName()).thenReturn("main");
            when(server.getUnsafe()).thenReturn(unsafe);

            Answer<BukkitTask> inline = inv -> {
                ((Runnable) inv.getArgument(1)).run();
                return mock(BukkitTask.class);
            };
            when(scheduler.runTask(any(), any(Runnable.class))).thenAnswer(inline);
            when(scheduler.runTask(any(), any(BukkitRunnable.class))).thenAnswer(inline);
            when(scheduler.runTaskAsynchronously(any(), any(Runnable.class))).thenAnswer(inline);
            when(scheduler.runTaskAsynchronously(any(), any(BukkitRunnable.class))).thenAnswer(inline);

            Answer<BukkitTask> capture = inv -> {
                timerTasks.addLast(inv.getArgument(1));
                return mock(BukkitTask.class);
            };
            when(scheduler.runTaskTimer(any(), any(Runnable.class), anyLong(), anyLong())).thenAnswer(capture);
            when(scheduler.runTaskTimer(any(), any(BukkitRunnable.class), anyLong(), anyLong())).thenAnswer(capture);

            Bukkit.setServer(server);
        }
        return server;
    }

    public static BukkitScheduler scheduler() {
        ensure();
        return scheduler;
    }

    public static PluginManager pluginManager() {
        ensure();
        return pluginManager;
    }

    /**
     * Récupère (FIFO) la prochaine tâche périodique capturée.
     */
    public static Runnable pollTimerTask() {
        return timerTasks.pollFirst();
    }

    public static void clearTimerTasks() {
        timerTasks.clear();
    }

    /**
     * Fait avancer une tâche périodique jusqu'à ce qu'elle se termine
     * (elle appelle cancel() une fois finie, sans effet sur le faux scheduler).
     */
    public static void runUntilDone(Runnable task, int maxTicks) {
        for (int i = 0; i < maxTicks; i++) {
            task.run();
        }
    }
}

package fr.niware.nonbuild;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import fr.niware.nonbuild.command.BuildCommand;
import fr.niware.nonbuild.command.DeployCommand;
import fr.niware.nonbuild.edit.SessionListener;
import fr.niware.nonbuild.edit.SessionManager;
import fr.niware.nonbuild.gui.GoalGUI;
import fr.niware.nonbuild.storage.ArenaStorage;
import fr.niware.nonbuild.storage.DeploymentStorage;
import fr.niware.nonbuild.world.VoidChunkGenerator;

public class NonBuild extends JavaPlugin {

    private Settings settings;
    private ArenaStorage arenaStorage;
    private DeploymentStorage deploymentStorage;
    private SessionManager sessionManager;
    private GoalGUI goalGUI;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.settings = new Settings(this);
        this.arenaStorage = new ArenaStorage(this);
        this.arenaStorage.loadAll();
        this.deploymentStorage = new DeploymentStorage(this);
        this.deploymentStorage.load();
        this.sessionManager = new SessionManager();
        this.goalGUI = new GoalGUI(this);

        // Charger le monde de production au démarrage pour s'assurer qu'il est disponible
        loadProductionWorld();

        PluginCommand build = getCommand("build");
        if (build != null) {
            BuildCommand buildCommand = new BuildCommand(this);
            build.setExecutor(buildCommand);
            build.setTabCompleter(buildCommand);
        }

        PluginCommand deploy = getCommand("deploy");
        if (deploy != null) {
            DeployCommand deployCommand = new DeployCommand(this);
            deploy.setExecutor(deployCommand);
            deploy.setTabCompleter(deployCommand);
        }

        getServer().getPluginManager().registerEvents(new SessionListener(this), this);

        getLogger().info("Activé : " + arenaStorage.count() + " arène(s), "
                + deploymentStorage.count() + " instance(s) déployée(s).");
        getLogger().info("Monde de build : " + settings.buildWorld()
                + " | Monde de production : " + settings.prodWorld());
    }

    /**
     * Charge le monde de production au démarrage du plugin.
     * Si le monde n'existe pas, tente de le créer via WorldCreator.
     */
    private void loadProductionWorld() {
        String worldName = settings.prodWorld();
        World world = Bukkit.getWorld(worldName);

        if (world == null) {
            getLogger().info("Le monde de production §e" + worldName + "§f n'est pas chargé, tentative de chargement...");
            // Monde neuf en void (cohérent avec /deploy rebuild) : pas de terrain généré.
            WorldCreator creator = new WorldCreator(worldName)
                    .generator(new VoidChunkGenerator())
                    .generateStructures(false)
                    .seed(0L);
            world = creator.createWorld();

            if (world == null) {
                getLogger().warning("Impossible de charger ou créer le monde de production §e" + worldName + "§c. "
                        + "Vérifiez que le monde existe dans le dossier du serveur ou que le nom est correct dans config.yml.");
            } else {
                getLogger().info("Monde de production §e" + worldName + "§f chargé avec succès.");
            }
        } else {
            getLogger().info("Monde de production §e" + worldName + "§f déjà chargé.");
        }
    }

    @Override
    public void onDisable() {
        getServer().getScheduler().cancelTasks(this);
    }

    public Settings getSettings() {
        return settings;
    }

    public ArenaStorage getArenas() {
        return arenaStorage;
    }

    public DeploymentStorage getDeployments() {
        return deploymentStorage;
    }

    public SessionManager getSessions() {
        return sessionManager;
    }

    public GoalGUI getGoalGUI() {
        return goalGUI;
    }
}

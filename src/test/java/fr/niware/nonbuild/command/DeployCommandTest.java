package fr.niware.nonbuild.command;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fr.niware.nonbuild.NonBuild;
import fr.niware.nonbuild.Settings;
import fr.niware.nonbuild.edit.SessionManager;
import fr.niware.nonbuild.model.Arena;
import fr.niware.nonbuild.model.DeployedInstance;
import fr.niware.nonbuild.model.Point;
import fr.niware.nonbuild.schematic.Nbt;
import fr.niware.nonbuild.schematic.SpongeSchematic;
import fr.niware.nonbuild.storage.ArenaStorage;
import fr.niware.nonbuild.storage.DeploymentStorage;
import fr.niware.nonbuild.testutil.BukkitServerFixture;

/**
 * Tests de FLUX du moteur de déploiement : registre, fichiers, blocs posés
 * ou effacés, cellules et gardes de concurrence. La formulation des messages
 * chat n'est jamais assertée.
 */
class DeployCommandTest {

    @TempDir
    File tempDir;

    private NonBuild plugin;
    private ArenaStorage arenas;
    private DeploymentStorage deployments;
    private DeployCommand command;
    private CommandSender console;
    private World prodWorld;
    private Chunk prodChunk;
    private Block prodBlock;
    private BlockData airData;
    private YamlConfiguration config;

    private final Command cmd = mock(Command.class);

    @BeforeEach
    void setup() {
        BukkitServerFixture.ensure();
        BukkitServerFixture.clearTimerTasks();
        clearInvocations(Bukkit.getServer()); // le mock serveur vit toute la JVM

        JavaPlugin storagePlugin = mock(JavaPlugin.class);
        when(storagePlugin.getDataFolder()).thenReturn(tempDir);
        when(storagePlugin.getLogger()).thenReturn(Logger.getLogger("DeployCommandTest"));
        arenas = new ArenaStorage(storagePlugin);
        deployments = new DeploymentStorage(storagePlugin);

        console = mock(CommandSender.class);

        prodWorld = mock(World.class);
        when(prodWorld.getName()).thenReturn("world");
        when(prodWorld.getMinHeight()).thenReturn(-64);
        when(prodWorld.getMaxHeight()).thenReturn(320);
        prodBlock = mock(Block.class);
        prodChunk = mock(Chunk.class);
        when(prodWorld.getChunkAt(anyInt(), anyInt())).thenReturn(prodChunk);
        when(prodChunk.getBlock(anyInt(), anyInt(), anyInt())).thenReturn(prodBlock);
        when(prodWorld.getChunkAtAsync(anyInt(), anyInt()))
                .thenReturn(CompletableFuture.completedFuture(mock(Chunk.class)));

        when(Bukkit.getServer().createBlockData(anyString())).thenAnswer(inv -> mock(BlockData.class));
        airData = mock(BlockData.class);
        when(Bukkit.getServer().createBlockData(Material.AIR)).thenReturn(airData);

        // Settings cache les valeurs au constructeur → config par défaut vide
        config = new YamlConfiguration();
        initSettings(config);
    }

    /**
     * Reconfigure Settings avec une config donnée (Settings cache les valeurs
     * au constructeur, donc on reconstruit le mock plugin).
     */
    private void initSettings(YamlConfiguration cfg) {
        config = cfg;
        JavaPlugin settingsPlugin = mock(JavaPlugin.class);
        when(settingsPlugin.getConfig()).thenReturn(config);
        Settings settings = new Settings(settingsPlugin);

        plugin = mock(NonBuild.class);
        when(plugin.getSettings()).thenReturn(settings);
        when(plugin.getArenas()).thenReturn(arenas);
        when(plugin.getDeployments()).thenReturn(deployments);
        when(plugin.getSessions()).thenReturn(new SessionManager());
        when(plugin.getLogger()).thenReturn(Logger.getLogger("DeployCommandTest"));
        command = new DeployCommand(plugin);
    }

    private boolean run(String... args) {
        return command.onCommand(console, cmd, "deploy", args);
    }

    private boolean runAs(CommandSender sender, String... args) {
        return command.onCommand(sender, cmd, "deploy", args);
    }

    /**
     * Arène 2x2x2 + schematic correspondante, prête à être déployée.
     */
    private void prepareArena() throws IOException {
        Arena arena = new Arena("getdown");
        arena.setDisplayName("Getdown");
        arena.setWorld("build");
        arena.setCorner1(new int[]{0, 60, 0});
        arena.setCorner2(new int[]{1, 61, 1});
        arena.setCenter(Point.of(0.5, 61, 0.5));
        arena.setSpawn1(Point.of(0.5, 61, 0.5));
        arena.setSpawn2(Point.of(1.5, 61, 1.5));
        arena.setSavedAt(System.currentTimeMillis());
        arenas.save(arena);

        SpongeSchematic schematic = SpongeSchematic.create(2, 2, 2,
                new int[]{0, 0, 0, 0, 0, 0, 0, 0}, List.of("minecraft:stone"));
        schematic.write(arenas.schematicFile("getdown"));
    }

    private void prodWorldOnline() {
        when(Bukkit.getServer().getWorld("world")).thenReturn(prodWorld);
    }

    private void drainScheduledTasks() {
        Runnable task;
        while ((task = BukkitServerFixture.pollTimerTask()) != null) {
            task.run();
        }
    }

    @Test
    void deploySansNombreNeDeploieRien() {
        run("getdown");
        assertEquals(0, deployments.count());
    }

    @Test
    void deployRefuseUnNombreInvalide() {
        run("getdown", "abc");
        assertEquals(0, deployments.count());
    }

    @Test
    void deployRefuseUnNombreHorsBornes() throws IOException {
        prepareArena();
        prodWorldOnline();
        run("getdown", "0");
        run("getdown", "129");
        assertEquals(0, deployments.count());
        assertNull(BukkitServerFixture.pollTimerTask());
    }

    @Test
    void deployDuneAreneIntrouvableNeDeploieRien() {
        run("fantome", "1");
        assertEquals(0, deployments.count());
    }

    @Test
    void deploySansSchematicNeDeploieRien() throws IOException {
        Arena arena = new Arena("getdown");
        arena.setDisplayName("Getdown");
        arena.setWorld("build");
        arena.setCorner1(new int[]{0, 60, 0});
        arena.setCorner2(new int[]{1, 61, 1});
        arena.setCenter(Point.of(0.5, 61, 0.5));
        arena.setSpawn1(Point.of(0.5, 61, 0.5));
        arena.setSpawn2(Point.of(1.5, 61, 1.5));
        arenas.save(arena);
        run("getdown", "1");
        assertEquals(0, deployments.count());
    }

    @Test
    void deploySansMondeDeProductionNeDeploieRien() throws IOException {
        prepareArena();
        when(Bukkit.getServer().getWorld("world")).thenReturn(null);
        run("getdown", "1");
        assertEquals(0, deployments.count());
    }

    @Test
    void deployCompletCreeLesInstancesEtColleLesBlocs() throws IOException {
        prepareArena();
        prodWorldOnline();

        run("getdown", "2");
        drainScheduledTasks();

        assertEquals(2, deployments.count());
        assertNotNull(deployments.get("getdown-1"));
        assertNotNull(deployments.get("getdown-2"));
        assertEquals("world", deployments.get("getdown-1").getWorld());
        assertEquals(60, deployments.get("getdown-1").getCorner1()[1]); // paste-y par défaut
        assertTrue(new File(tempDir, "deployments.yml").exists());
        verify(prodBlock, times(16)).setBlockData(argThat(data -> data != airData), eq(false)); // 2 x 8 blocs
    }

    @Test
    void deployAvecUneHauteurIncompatibleNeDeploieRien() throws IOException {
        prepareArena();
        config.set("placement.paste-y", 318);
        initSettings(config);
        SpongeSchematic schematic = SpongeSchematic.create(2, 5, 2, new int[20], List.of("minecraft:stone"));
        schematic.write(arenas.schematicFile("getdown"));
        prodWorldOnline();

        run("getdown", "1");
        drainScheduledTasks();

        assertEquals(0, deployments.count());
        verify(prodBlock, never()).setBlockData(argThat(data -> data != airData), eq(false));
    }

    @Test
    void deployColleAHauteurConfigurée() throws IOException {
        prepareArena();
        config.set("placement.paste-y", 100);
        initSettings(config);
        prodWorldOnline();

        run("getdown", "1");
        drainScheduledTasks();

        DeployedInstance instance = deployments.get("getdown-1");
        assertEquals(100, instance.getCorner1()[1]);
        assertEquals(101, instance.getCorner2()[1]);
        assertEquals(101, instance.getCenter().y()); // centre build à minY+1
    }

    @Test
    void leRedeploiementMetAJourSurPlaceSansAjouter() throws IOException {
        prepareArena();
        prodWorldOnline();

        run("getdown", "1");
        drainScheduledTasks();
        int[] ancienneCellule = deployments.get("getdown-1").getCellMinXZ().clone();
        long ancientDeployedAt = deployments.get("getdown-1").getDeployedAt();

        run("getdown", "1");
        drainScheduledTasks();

        assertEquals(1, deployments.count());
        DeployedInstance toujours = deployments.get("getdown-1");
        assertEquals(ancienneCellule[0], toujours.getCellMinXZ()[0]);
        assertEquals(ancienneCellule[1], toujours.getCellMinXZ()[1]);
        verify(prodBlock, times(16)).setBlockData(argThat(data -> data != airData), eq(false)); // 2 collages
        verify(prodBlock, never()).setBlockData(eq(airData), eq(false)); // même zone : rien à effacer
        assertTrue(toujours.getDeployedAt() >= ancientDeployedAt);
    }

    @Test
    void leRedeploiementAvecUnPasteYDifferentEffaceLAncienneZone() throws IOException {
        prepareArena();
        prodWorldOnline();

        config.set("placement.paste-y", 100);
        initSettings(config);
        run("getdown", "1");
        drainScheduledTasks();
        assertEquals(100, deployments.get("getdown-1").getCorner1()[1]);

        config.set("placement.paste-y", 80);
        initSettings(config);
        run("getdown", "1");
        drainScheduledTasks();

        assertEquals(1, deployments.count());
        assertEquals(80, deployments.get("getdown-1").getCorner1()[1]);
        verify(prodBlock, times(8)).setBlockData(eq(airData), eq(false)); // ancienne zone effacée
        verify(prodBlock, times(16)).setBlockData(argThat(data -> data != airData), eq(false)); // 2 collages
    }

    @Test
    void leRedeploiementDuneAreneAgrandieLaDeplace() throws IOException {
        prepareArena(); // schematic 2x2x2
        prodWorldOnline();
        run("getdown", "1");
        drainScheduledTasks();
        int[] ancienneCellule = deployments.get("getdown-1").getCellMinXZ().clone();

        SpongeSchematic plusGrande = SpongeSchematic.create(3, 2, 2, new int[12], List.of("minecraft:stone"));
        plusGrande.write(arenas.schematicFile("getdown"));

        run("getdown", "1");
        drainScheduledTasks();

        assertEquals(1, deployments.count());
        DeployedInstance deplacee = deployments.get("getdown-1");
        assertTrue(deplacee.getCellMinXZ()[0] != ancienneCellule[0]
                || deplacee.getCellMinXZ()[1] != ancienneCellule[1], "l'instance aurait dû être déplacée");
        verify(prodBlock, times(8)).setBlockData(eq(airData), eq(false)); // ancienne zone effacée
        verify(prodBlock, times(20)).setBlockData(argThat(data -> data != airData), eq(false)); // 8 + 12
    }

    @Test
    void deployCreeUniquementLeManquant() throws IOException {
        prepareArena();
        prodWorldOnline();

        run("getdown", "1");
        drainScheduledTasks();
        assertEquals(1, deployments.count());

        run("getdown", "3");
        drainScheduledTasks();

        assertEquals(3, deployments.count());
        assertNotNull(deployments.get("getdown-1"));
        assertNotNull(deployments.get("getdown-2"));
        assertNotNull(deployments.get("getdown-3"));
    }

    @Test
    void deployAvecMoinsQueLexistantLaisseLeSurplusIntact() throws IOException {
        prepareArena();
        prodWorldOnline();

        run("getdown", "3");
        drainScheduledTasks();
        assertEquals(3, deployments.count());
        long deployedAtGetdown2 = deployments.get("getdown-2").getDeployedAt();

        run("getdown", "1");
        drainScheduledTasks();

        assertEquals(3, deployments.count()); // rien supprimé
        assertEquals(deployedAtGetdown2, deployments.get("getdown-2").getDeployedAt()); // pas retouchée
    }

    @Test
    void lesInstancesSontEloigneesDuSpawnEtLesUnesDesAutres() throws IOException {
        prepareArena();
        prodWorldOnline();

        run("getdown", "4");
        drainScheduledTasks();

        List<DeployedInstance> instances = deployments.byArena("getdown");
        assertEquals(4, instances.size());
        int radius = 512; // défaut de placement.spawn-protection-radius (config vide)
        for (DeployedInstance a : instances) {
            for (DeployedInstance b : instances) {
                if (a == b) {
                    continue;
                }
                boolean seChevauchent = a.getCellMinXZ()[0] <= b.getCellMaxXZ()[0]
                        && b.getCellMinXZ()[0] <= a.getCellMaxXZ()[0]
                        && a.getCellMinXZ()[1] <= b.getCellMaxXZ()[1]
                        && b.getCellMinXZ()[1] <= a.getCellMaxXZ()[1];
                assertTrue(!seChevauchent, "cellules " + a.getName() + " et " + b.getName() + " qui se touchent");
            }
            assertTrue(a.getCellMaxXZ()[0] < -radius || a.getCellMinXZ()[0] > radius
                            || a.getCellMaxXZ()[1] < -radius || a.getCellMinXZ()[1] > radius,
                    "la cellule de " + a.getName() + " mord sur la zone protégée");
        }
    }

    @Test
    void deployAvecUneSchematicCorrompueNeDeploieRien() throws IOException {
        prepareArena();
        Files.write(arenas.schematicFile("getdown").toPath(), new byte[]{1, 2, 3, 4});
        prodWorldOnline();

        run("getdown", "1");
        assertEquals(0, deployments.count());
    }

    @Test
    void unDeuxiemeDeployEstIgnoreTantQueLePremierTourne() throws IOException {
        prepareArena();
        prodWorldOnline();

        run("getdown", "1"); // paster capturé, non exécuté
        run("getdown", "1"); // ignoré : aucune seconde tâche planifiée

        Runnable first = BukkitServerFixture.pollTimerTask();
        assertNotNull(first);
        assertNull(BukkitServerFixture.pollTimerTask());

        first.run();
        assertEquals(1, deployments.count());
    }

    @Test
    void uneErreurDeCollageNeLenregistrePas() throws IOException {
        prepareArena();
        prodWorldOnline();
        when(prodChunk.getBlock(anyInt(), anyInt(), anyInt()))
                .thenThrow(new RuntimeException("chunk non chargé"));

        run("getdown", "1");
        drainScheduledTasks();

        assertEquals(0, deployments.count());
    }

    @Test
    void uneErreurDEffacementInterromptLeRedeploiement() throws IOException {
        prepareArena();
        prodWorldOnline();
        config.set("placement.paste-y", 100);
        initSettings(config);
        run("getdown", "1");
        drainScheduledTasks();

        config.set("placement.paste-y", 80); // force l'effacement préalable
        initSettings(config);
        when(prodChunk.getBlock(anyInt(), anyInt(), anyInt()))
                .thenThrow(new RuntimeException("chunk non chargé"));
        run("getdown", "1");
        drainScheduledTasks();

        assertEquals(1, deployments.count()); // registre intact
        assertEquals(100, deployments.get("getdown-1").getCorner1()[1]); // position d'avant conservée
    }

    @Test
    void removeSupprimePhysiquementEtDuRegistre() throws IOException {
        prepareArena();
        prodWorldOnline();
        run("getdown", "1");
        drainScheduledTasks();

        run("remove", "getdown-1");
        drainScheduledTasks();

        assertEquals(0, deployments.count());
        verify(prodBlock, times(8)).setBlockData(eq(airData), eq(false));
        assertTrue(new File(tempDir, "deployments.yml").exists()); // fichier réécrit vidé
    }

    @Test
    void removeParNomDAreneSupprimeToutesLesInstances() throws IOException {
        prepareArena();
        prodWorldOnline();
        run("getdown", "3");
        drainScheduledTasks();
        assertEquals(3, deployments.count());

        run("remove", "getdown");
        drainScheduledTasks();

        assertEquals(0, deployments.count());
        verify(prodBlock, times(24)).setBlockData(eq(airData), eq(false)); // 3 x 8
    }

    @Test
    void removeInconnuNeSupprimeRien() throws IOException {
        prepareArena();
        prodWorldOnline();
        run("getdown", "1");
        drainScheduledTasks();

        run("remove", "fantome");
        assertNull(BukkitServerFixture.pollTimerTask());
        assertEquals(1, deployments.count());
    }

    @Test
    void removeRefuseUnMondeNonCharge() throws IOException {
        prepareArena();
        prodWorldOnline();
        run("getdown", "1");
        drainScheduledTasks();

        when(Bukkit.getServer().getWorld("world")).thenReturn(null);
        run("remove", "getdown-1");
        assertNull(BukkitServerFixture.pollTimerTask());
        assertEquals(1, deployments.count());
    }

    @Test
    void removeRefusePendantUnDeploiement() throws IOException {
        prepareArena();
        prodWorldOnline();
        run("getdown", "1");
        drainScheduledTasks();

        run("getdown", "1"); // re-déploiement en attente
        run("remove", "getdown-1"); // doit être ignoré, aucune tâche d'effacement planifiée

        assertNotNull(BukkitServerFixture.pollTimerTask()); // le paster du re-déploiement
        assertNull(BukkitServerFixture.pollTimerTask());    // rien d'autre
        assertEquals(1, deployments.count());
        drainScheduledTasks();
    }

    @Test
    void uneErreurPendantLaSuppressionPhysiqueLaisseLeRegistreIntact() throws IOException {
        prepareArena();
        prodWorldOnline();
        run("getdown", "1");
        drainScheduledTasks();

        when(prodChunk.getBlock(anyInt(), anyInt(), anyInt()))
                .thenThrow(new RuntimeException("chunk non chargé"));
        run("remove", "getdown-1");
        drainScheduledTasks();

        assertEquals(1, deployments.count());
    }

    @Test
    void tpTeleporteAuCentreDeLInstance() throws IOException {
        prepareArena();
        prodWorldOnline();
        run("getdown", "1");
        drainScheduledTasks();

        clearInvocations(prodWorld); // le collage a déjà préchargé des chunks, on ne compte que le tp
        when(prodWorld.getChunkAtAsync(anyInt(), anyInt()))
                .thenReturn(CompletableFuture.completedFuture(mock(org.bukkit.Chunk.class)));

        Player player = mock(Player.class);
        when(player.isOnline()).thenReturn(true);
        runAs(player, "tp", "getdown-1");

        DeployedInstance instance = deployments.get("getdown-1");
        Location target = instance.getCenter().toLocation(prodWorld);
        verify(player).teleport(target);

        // les 9 chunks autour de l'arrivée sont préchargés
        int cx = target.getBlockX() >> 4;
        int cz = target.getBlockZ() >> 4;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                verify(prodWorld).getChunkAtAsync(cx + dx, cz + dz);
            }
        }
    }

    @Test
    void tpAttendLaFinDuPrechargementAvantDeTeleporter() throws IOException {
        prepareArena();
        prodWorldOnline();
        run("getdown", "1");
        drainScheduledTasks();

        CompletableFuture<org.bukkit.Chunk> pending = new CompletableFuture<>();
        when(prodWorld.getChunkAtAsync(anyInt(), anyInt())).thenReturn(pending);

        Player player = mock(Player.class);
        when(player.isOnline()).thenReturn(true);
        runAs(player, "tp", "getdown-1");
        verify(player, never()).teleport(any(Location.class));

        pending.complete(mock(org.bukkit.Chunk.class));
        verify(player).teleport(deployments.get("getdown-1").getCenter().toLocation(prodWorld));
    }

    @Test
    void tpTeleporteMemeSiUnChunkEchoueACharger() throws IOException {
        prepareArena();
        prodWorldOnline();
        run("getdown", "1");
        drainScheduledTasks();

        CompletableFuture<org.bukkit.Chunk> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("io"));
        when(prodWorld.getChunkAtAsync(anyInt(), anyInt()))
                .thenReturn(CompletableFuture.completedFuture(mock(org.bukkit.Chunk.class)))
                .thenReturn(failed);

        Player player = mock(Player.class);
        when(player.isOnline()).thenReturn(true);
        runAs(player, "tp", "getdown-1");

        verify(player).teleport(deployments.get("getdown-1").getCenter().toLocation(prodWorld));
    }

    @Test
    void tpAnnuleSiLeJoueurSeDeconnectePendantLePrechargement() throws IOException {
        prepareArena();
        prodWorldOnline();
        run("getdown", "1");
        drainScheduledTasks();

        CompletableFuture<org.bukkit.Chunk> pending = new CompletableFuture<>();
        when(prodWorld.getChunkAtAsync(anyInt(), anyInt())).thenReturn(pending);

        Player player = mock(Player.class);
        when(player.isOnline()).thenReturn(false);
        runAs(player, "tp", "getdown-1");
        pending.complete(mock(org.bukkit.Chunk.class));

        verify(player, never()).teleport(any(Location.class));
    }

    @Test
    void tpEstRefuseTantQueLInstanceEstEnCoursDeCollage() throws IOException {
        prepareArena();
        prodWorldOnline();
        run("getdown", "1");
        drainScheduledTasks(); // getdown-1 enregistrée

        run("getdown", "1"); // re-déploiement : la tâche de collage est en file, pas exécutée

        Player player = mock(Player.class);
        when(player.isOnline()).thenReturn(true);
        runAs(player, "tp", "getdown-1");
        verify(player, never()).teleport(any(Location.class));
        // NB : le collage en cours précharge lui aussi les chunks (getChunkAtAsync),
        // on ne peut donc pas assert never() ici ; la garde est portée par le teleport.

        drainScheduledTasks(); // collage terminé : la garde est levée

        when(prodWorld.getChunkAtAsync(anyInt(), anyInt()))
                .thenReturn(CompletableFuture.completedFuture(mock(org.bukkit.Chunk.class)));
        runAs(player, "tp", "getdown-1");
        verify(player).teleport(deployments.get("getdown-1").getCenter().toLocation(prodWorld));
    }

    @Test
    void leRefusDeTpEstLeveApresUneErreurDeCollage() throws IOException {
        prepareArena();
        prodWorldOnline();
        run("getdown", "1");
        drainScheduledTasks();

        doThrow(new RuntimeException("io")).when(prodBlock).setBlockData(any(BlockData.class), anyBoolean());
        run("getdown", "1");
        drainScheduledTasks(); // le collage échoue : la garde doit être levée

        Player player = mock(Player.class);
        when(player.isOnline()).thenReturn(true);
        when(prodWorld.getChunkAtAsync(anyInt(), anyInt()))
                .thenReturn(CompletableFuture.completedFuture(mock(org.bukkit.Chunk.class)));
        runAs(player, "tp", "getdown-1");
        verify(player).teleport(any(Location.class));
    }

    @Test
    void tpNeTeleportePasSansInstanceValide() throws IOException {
        prepareArena();
        prodWorldOnline();
        run("getdown", "1");
        drainScheduledTasks();

        Player player = mock(Player.class);
        runAs(player, "tp", "fantome-1");
        runAs(player, "tp");
        verify(player, never()).teleport(org.mockito.ArgumentMatchers.any(Location.class));
    }

    @Test
    void laConsoleNePeutPasSeTeleporter() throws IOException {
        prepareArena();
        prodWorldOnline();
        run("getdown", "1");
        drainScheduledTasks();
        assertTrue(run("tp", "getdown-1")); // pas d'exception, pas de téléport possible
    }

    @Test
    void tpNeCreePasLeMondeSiIlNestPasCharge() throws IOException {
        prepareArena();
        prodWorldOnline();
        run("getdown", "1");
        drainScheduledTasks();

        when(Bukkit.getServer().getWorld("world")).thenReturn(null);
        Player player = mock(Player.class);
        runAs(player, "tp", "getdown-1");

        // plus de création on-demand : le monde absent est refusé, pas créé
        verify(Bukkit.getServer(), never()).createWorld(any(WorldCreator.class));
        verify(player, never()).teleport(any(Location.class));
    }

    @Test
    void listEtMapSontSansEffetDeBord() throws IOException {
        prepareArena();
        prodWorldOnline();
        run("getdown", "2");
        drainScheduledTasks();

        assertTrue(run("list"));
        assertTrue(run("map"));
        // zoom sur une instance existante, et erreur propre sur une instance inconnue
        assertTrue(run("map", "getdown-1"));
        assertTrue(run("map", "inexistant"));
        assertTrue(run());
        assertTrue(run("help"));
        assertEquals(2, deployments.count());
        assertNull(BukkitServerFixture.pollTimerTask());
    }

    @Test
    void laTabCompletionProposeArenesInstancesEtNombres() throws IOException {
        prepareArena();
        prodWorldOnline();
        run("getdown", "1");
        drainScheduledTasks();

        List<String> first = command.onTabComplete(console, cmd, "deploy", new String[]{""});
        assertTrue(first.contains("getdown"));
        assertTrue(first.contains("list"));
        assertTrue(first.contains("map"));
        assertTrue(first.contains("tp"));
        assertTrue(first.contains("rebuild"));

        assertTrue(command.onTabComplete(console, cmd, "deploy", new String[]{"getdown", ""}).contains("1"));
        List<String> removeOptions = command.onTabComplete(console, cmd, "deploy", new String[]{"remove", ""});
        assertTrue(removeOptions.contains("getdown-1"));
        assertTrue(removeOptions.contains("getdown"));
        assertTrue(command.onTabComplete(console, cmd, "deploy", new String[]{"tp", ""}).contains("getdown-1"));
        assertTrue(command.onTabComplete(console, cmd, "deploy", new String[]{"map", ""}).contains("getdown-1"));
        assertTrue(command.onTabComplete(console, cmd, "deploy", new String[]{"list", ""}).isEmpty());
    }

    @Test
    void leRegistreSurvitAUnRechargement() throws IOException {
        prepareArena();
        prodWorldOnline();
        run("getdown", "2");
        drainScheduledTasks();

        DeploymentStorage fresh = new DeploymentStorage(mockStoragePlugin());
        fresh.load();
        assertEquals(2, fresh.count());
        assertNotNull(fresh.get("getdown-1"));
        assertEquals(deployments.get("getdown-1").getCenter(), fresh.get("getdown-1").getCenter());
    }

    private JavaPlugin mockStoragePlugin() {
        JavaPlugin storagePlugin = mock(JavaPlugin.class);
        when(storagePlugin.getDataFolder()).thenReturn(tempDir);
        when(storagePlugin.getLogger()).thenReturn(Logger.getLogger("DeployCommandTest"));
        return storagePlugin;
    }

    // ────────────────────────────── rebuild ──────────────────────────────

    private World newWorld;
    private Chunk newChunk;
    private Block newBlock;

    private void prepareSpawnSchematic(int size) throws IOException {
        int volume = size * size * size;
        SpongeSchematic spawn = SpongeSchematic.create(size, size, size, new int[volume], List.of("minecraft:stone"));
        spawn.write(new File(tempDir, "spawn.schem"));
    }

    private void prepareSpawnSchematicHalfAir() throws IOException {
        SpongeSchematic spawn = SpongeSchematic.create(2, 2, 2,
                new int[]{0, 1, 0, 1, 0, 1, 0, 1}, List.of("minecraft:air", "minecraft:stone"));
        spawn.write(new File(tempDir, "spawn.schem"));
    }

    /**
     * Prépare la phase destructive : stubs serveur (unload, createWorld,
     * isTickingWorlds) et vrai dossier de monde dans le @TempDir.
     */
    private void prepareRebuildServer() throws IOException {
        when(plugin.getDataFolder()).thenReturn(tempDir);

        newWorld = mock(World.class);
        when(newWorld.getName()).thenReturn("world");
        when(newWorld.getMinHeight()).thenReturn(-64);
        when(newWorld.getMaxHeight()).thenReturn(320);
        newBlock = mock(Block.class);
        newChunk = mock(Chunk.class);
        when(newWorld.getChunkAt(anyInt(), anyInt())).thenReturn(newChunk);
        when(newChunk.getBlock(anyInt(), anyInt(), anyInt())).thenReturn(newBlock);
        when(newWorld.getChunkAtAsync(anyInt(), anyInt()))
                .thenReturn(CompletableFuture.completedFuture(mock(Chunk.class)));

        when(Bukkit.getServer().isTickingWorlds()).thenReturn(false);
        when(Bukkit.getServer().unloadWorld("world", false)).thenReturn(true);
        when(Bukkit.getServer().createWorld(any(WorldCreator.class))).thenReturn(newWorld);
        when(Bukkit.getServer().getWorldContainer()).thenReturn(tempDir);

        File worldFolder = new File(tempDir, "world");
        File region = new File(worldFolder, "region");
        assertTrue(region.mkdirs());
        assertTrue(new File(region, "r.0.0.mca").createNewFile());
        when(prodWorld.getWorldFolder()).thenReturn(worldFolder);
    }

    private World buildWorldOnline() {
        World buildWorld = mock(World.class);
        when(buildWorld.getName()).thenReturn("build");
        when(Bukkit.getServer().getWorld("build")).thenReturn(buildWorld);
        when(Bukkit.getServer().getWorlds()).thenReturn(List.of(buildWorld, prodWorld));
        return buildWorld;
    }

    /**
     * spawn.schem au format v3 (enveloppé dans Schematic{}, palette Blocks/Palette).
     */
    private void prepareSpawnSchematicV3(int[] offset, int height) throws IOException {
        int volume = 2 * height * 2;
        Map<String, Object> palette = new LinkedHashMap<>();
        palette.put("minecraft:air", 0);
        palette.put("minecraft:stone", 1);
        Map<String, Object> blocks = new LinkedHashMap<>();
        blocks.put("Palette", palette);
        blocks.put("Data", new byte[volume]);
        Map<String, Object> schematic = new LinkedHashMap<>();
        schematic.put("Version", 3);
        schematic.put("Width", (short) 2);
        schematic.put("Height", (short) height);
        schematic.put("Length", (short) 2);
        schematic.put("Offset", offset);
        schematic.put("Blocks", blocks);
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("Schematic", schematic);
        try (FileOutputStream out = new FileOutputStream(new File(tempDir, "spawn.schem"))) {
            Nbt.writeCompressed(root, out);
        }
    }

    @Test
    void leRebuildEstRefuseSiLeSpawnNeTientPasEnHauteur() throws IOException {
        prodWorldOnline();
        prepareRebuildServer();
        prepareSpawnSchematicV3(new int[]{0, 50, 0}, 400); // Y 140 → 539, hors du monde (-64 / 319)

        run("rebuild");
        drainScheduledTasks();

        verify(Bukkit.getServer(), never()).unloadWorld(anyString(), anyBoolean());
        verify(Bukkit.getServer(), never()).createWorld(any(WorldCreator.class));
        assertTrue(new File(tempDir, "world/region/r.0.0.mca").exists());
    }

    @Test
    void leRebuildEstRefusePendantUnDeploiement() throws IOException {
        prepareArena();
        prodWorldOnline();
        prepareRebuildServer();
        prepareSpawnSchematic(2);

        run("getdown", "1"); // paster en attente : deploying = true
        run("rebuild");

        verify(Bukkit.getServer(), never()).unloadWorld(anyString(), anyBoolean());
        BukkitServerFixture.pollTimerTask().run();
        assertEquals(1, deployments.count());
    }

    @Test
    void leRebuildEstRefuseSansSpawnSchem() throws IOException {
        prepareArena();
        prodWorldOnline();
        when(plugin.getDataFolder()).thenReturn(tempDir);
        run("getdown", "2");
        drainScheduledTasks();

        run("rebuild");

        verify(Bukkit.getServer(), never()).unloadWorld(anyString(), anyBoolean());
        assertNull(BukkitServerFixture.pollTimerTask());
        assertEquals(2, deployments.count());
    }

    @Test
    void leRebuildEstRefuseSiUneAreneDuRegistreADisparuCoteBuild() throws IOException {
        prodWorldOnline();
        prepareRebuildServer();
        deployments.put(new DeployedInstance("ghost-1", "ghost", "world",
                Point.of(0, 60, 0), new int[]{0, 60, 0}, new int[]{1, 61, 1},
                Point.of(0.5, 61, 0.5), Point.of(1.5, 61, 1.5),
                new int[]{0, 0}, new int[]{16, 16}, System.currentTimeMillis()));

        run("rebuild");

        verify(Bukkit.getServer(), never()).unloadWorld(anyString(), anyBoolean());
        assertNull(BukkitServerFixture.pollTimerTask());
        assertNotNull(deployments.get("ghost-1"));
    }

    @Test
    void leRebuildEstRefuseSiUneSchematicDAreneManque() throws IOException {
        prepareArena();
        prodWorldOnline();
        prepareRebuildServer();
        prepareSpawnSchematic(2);
        run("getdown", "1");
        drainScheduledTasks();

        assertTrue(new File(tempDir, "schematics/getdown.schem").delete());
        run("rebuild");

        verify(Bukkit.getServer(), never()).unloadWorld(anyString(), anyBoolean());
        assertEquals(1, deployments.count());
    }

    @Test
    @SuppressWarnings("removal") // vérifie le choix assumé du main : clés String via setGameRuleValue
    void leRebuildCompletColleLeSpawnEtRedeploieTout() throws IOException {
        prepareArena();
        prodWorldOnline();
        World buildWorld = buildWorldOnline();
        prepareRebuildServer();
        prepareSpawnSchematic(2);

        Player player = mock(Player.class);
        when(prodWorld.getPlayers()).thenReturn(List.of(player));
        Location buildSpawn = new Location(buildWorld, 10, 70, 10);
        when(buildWorld.getSpawnLocation()).thenReturn(buildSpawn);

        run("getdown", "2");
        drainScheduledTasks();
        assertEquals(2, deployments.count());

        run("rebuild");
        drainScheduledTasks();

        verify(Bukkit.getServer()).unloadWorld("world", false);
        verify(Bukkit.getServer()).createWorld(any(WorldCreator.class));
        assertFalse(new File(tempDir, "world").exists());
        verify(newWorld).setSpawnLocation(argThat(loc ->
                loc.getX() == 0.5 && loc.getY() == 90.0 && loc.getZ() == 0.5));
        verify(newWorld).setDifficulty(org.bukkit.Difficulty.NORMAL);
        verify(newWorld).setTime(6000L);
        verify(newWorld).setGameRuleValue("random_tick_speed", "0");
        verify(newWorld).setGameRuleValue("spawn_mobs", "false");
        verify(newWorld).setGameRuleValue("advance_time", "false");
        verify(player).teleport(buildSpawn);
        // 8 blocs de spawn + 2 instances de 8 blocs
        verify(newBlock, times(24)).setBlockData(argThat(data -> data != airData), eq(false));
        assertEquals(2, deployments.count());
        assertNotNull(deployments.get("getdown-1"));
        assertNotNull(deployments.get("getdown-2"));
        assertTrue(new File(tempDir, "deployments.yml").exists());
    }

    @Test
    void leRebuildAvecUnRegistreVideCreeLeMondeEtLeSpawn() throws IOException {
        prodWorldOnline();
        prepareRebuildServer();
        prepareSpawnSchematic(2);

        run("rebuild");
        drainScheduledTasks();

        verify(Bukkit.getServer()).unloadWorld("world", false);
        verify(Bukkit.getServer()).createWorld(any(WorldCreator.class));
        assertEquals(0, deployments.count());
        verify(newBlock, times(8)).setBlockData(argThat(data -> data != airData), eq(false));
        assertTrue(new File(tempDir, "deployments.yml").exists());
    }

    @Test
    void leRebuildColleSansAirAlorsQueLeDeployNormalColleTout() throws IOException {
        prepareArena();
        prodWorldOnline();
        prepareRebuildServer();

        BlockData airState = mock(BlockData.class);
        when(airState.getMaterial()).thenReturn(Material.AIR);
        when(Bukkit.getServer().createBlockData("minecraft:air")).thenReturn(airState);

        SpongeSchematic arenaSchem = SpongeSchematic.create(2, 2, 2,
                new int[]{0, 1, 1, 0, 0, 1, 1, 0}, List.of("minecraft:air", "minecraft:stone"));
        arenaSchem.write(arenas.schematicFile("getdown"));
        prepareSpawnSchematicHalfAir();

        run("getdown", "2");
        drainScheduledTasks();
        // collage classique : volume complet écrasé, air compris (2 x 8 poses)
        verify(prodBlock, times(16)).setBlockData(any(BlockData.class), eq(false));
        verify(prodBlock, times(8)).setBlockData(eq(airState), eq(false));

        run("rebuild");
        drainScheduledTasks();
        // monde neuf : air ignoré → 4 blocs de spawn + 2 x 4 blocs d'arène, aucune pose d'air
        verify(newBlock, times(12)).setBlockData(any(BlockData.class), eq(false));
        verify(newBlock, never()).setBlockData(eq(airState), eq(false));
        assertEquals(2, deployments.count());
    }

    @Test
    void leRebuildEstRefuseSiUneSchematicDAreneEstIllisible() throws IOException {
        prepareArena();
        prodWorldOnline();
        prepareRebuildServer();
        prepareSpawnSchematic(2);
        run("getdown", "1");
        drainScheduledTasks();

        Files.writeString(arenas.schematicFile("getdown").toPath(), "corrompu");
        run("rebuild");
        drainScheduledTasks();

        verify(Bukkit.getServer(), never()).unloadWorld(anyString(), anyBoolean());
        assertTrue(new File(tempDir, "world/region/r.0.0.mca").exists());
        assertEquals(1, deployments.count());
    }

    @Test
    void leRebuildSArreteSiLUnloadEchoue() throws IOException {
        prepareArena();
        prodWorldOnline();
        prepareRebuildServer();
        prepareSpawnSchematic(2);
        run("getdown", "1");
        drainScheduledTasks();
        when(Bukkit.getServer().unloadWorld("world", false)).thenReturn(false);

        run("rebuild");
        drainScheduledTasks();

        verify(Bukkit.getServer(), never()).createWorld(any(WorldCreator.class));
        assertTrue(new File(tempDir, "world/region/r.0.0.mca").exists());
        assertEquals(1, deployments.count());
    }

    @Test
    void leRebuildAttendLaFinDuTickDesMondes() throws IOException {
        prepareArena();
        prodWorldOnline();
        prepareRebuildServer();
        prepareSpawnSchematic(2);
        when(Bukkit.getServer().isTickingWorlds()).thenReturn(true, false);
        run("getdown", "1");
        drainScheduledTasks();

        run("rebuild");
        drainScheduledTasks();

        verify(Bukkit.getServer()).unloadWorld("world", false);
        assertEquals(1, deployments.count());
    }

    @Test
    void leRebuildRecreeUnMondeProdAbsent() throws IOException {
        prepareArena();
        prodWorldOnline();
        run("getdown", "2");
        drainScheduledTasks();
        prepareRebuildServer();
        prepareSpawnSchematic(2);
        when(Bukkit.getServer().getWorld("world")).thenReturn(null);

        run("rebuild");
        drainScheduledTasks();

        verify(Bukkit.getServer(), never()).unloadWorld(anyString(), anyBoolean());
        verify(Bukkit.getServer()).createWorld(any(WorldCreator.class));
        assertFalse(new File(tempDir, "world").exists());
        assertEquals(2, deployments.count());
        assertNotNull(deployments.get("getdown-1"));
    }

    @Test
    void leRebuildEstRefuseSiLeSpawnEstIllisible() throws IOException {
        prodWorldOnline();
        prepareRebuildServer();
        Files.writeString(new File(tempDir, "spawn.schem").toPath(), "pas du NBT gzip");

        run("rebuild");

        verify(Bukkit.getServer(), never()).unloadWorld(anyString(), anyBoolean());
        assertNull(BukkitServerFixture.pollTimerTask());
    }
}

package fr.niware.nonbuild.command;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fr.niware.nonbuild.NonBuild;
import fr.niware.nonbuild.Settings;
import fr.niware.nonbuild.edit.EditSession;
import fr.niware.nonbuild.edit.SessionManager;
import fr.niware.nonbuild.model.Arena;
import fr.niware.nonbuild.model.DeployedInstance;
import fr.niware.nonbuild.model.Point;
import fr.niware.nonbuild.storage.ArenaStorage;
import fr.niware.nonbuild.storage.DeploymentStorage;
import fr.niware.nonbuild.testutil.BukkitServerFixture;

/**
 * Tests de FLUX du moteur de commandes : seuls l'état observable compte
 * (sessions, fichiers écrits, mode de jeu, tâches planifiées). La
 * formulation des messages chat n'est jamais assertée.
 */
class BuildCommandTest {

    @TempDir
    File tempDir;

    private NonBuild plugin;
    private ArenaStorage arenas;
    private DeploymentStorage deployments;
    private SessionManager sessions;
    private World buildWorld;
    private Chunk buildChunk;
    private Player player;
    private BuildCommand command;

    private final Command cmd = mock(Command.class);

    @BeforeEach
    void setup() {
        BukkitServerFixture.ensure();
        BukkitServerFixture.clearTimerTasks();

        JavaPlugin storagePlugin = mock(JavaPlugin.class);
        when(storagePlugin.getDataFolder()).thenReturn(tempDir);
        when(storagePlugin.getLogger()).thenReturn(Logger.getLogger("BuildCommandTest"));
        arenas = new ArenaStorage(storagePlugin);
        deployments = new DeploymentStorage(storagePlugin);
        sessions = new SessionManager();

        JavaPlugin settingsPlugin = mock(JavaPlugin.class);
        when(settingsPlugin.getConfig()).thenReturn(new YamlConfiguration());

        plugin = mock(NonBuild.class);
        Settings settings = new Settings(settingsPlugin);
        when(plugin.getSettings()).thenReturn(settings);
        when(plugin.getArenas()).thenReturn(arenas);
        when(plugin.getDeployments()).thenReturn(deployments);
        when(plugin.getSessions()).thenReturn(sessions);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("BuildCommandTest"));

        buildWorld = mock(World.class);
        when(buildWorld.getName()).thenReturn("build");

        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getWorld()).thenReturn(buildWorld);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(player.isOnline()).thenReturn(true);
        when(player.getLocation()).thenReturn(new Location(buildWorld, 0, 60, 0));

        command = new BuildCommand(plugin);
    }

    private boolean run(String... args) {
        return command.onCommand(player, cmd, "build", args);
    }

    private Arena sampleArena(String slug) {
        Arena arena = new Arena(slug);
        arena.setDisplayName(slug);
        arena.setWorld("build");
        arena.setCorner1(new int[]{0, 60, 0});
        arena.setCorner2(new int[]{2, 62, 2});
        arena.setCenter(Point.of(1, 61, 1));
        arena.setSpawn1(Point.of(0.5, 61, 0.5));
        arena.setSpawn2(Point.of(1.5, 61, 1.5));
        arena.setSavedAt(System.currentTimeMillis());
        return arena;
    }

    private void stubStoneBlocks() {
        buildChunk = mock(Chunk.class);
        when(buildWorld.getChunkAt(anyInt(), anyInt())).thenReturn(buildChunk);
        when(buildChunk.getBlock(anyInt(), anyInt(), anyInt())).thenAnswer(inv -> {
            Block block = mock(Block.class);
            BlockData data = mock(BlockData.class);
            when(data.getAsString()).thenReturn("minecraft:stone");
            when(block.getBlockData()).thenReturn(data);
            return block;
        });
    }

    private void prepareCompleteSession(String name) {
        Location c1 = new Location(buildWorld, 0, 60, 0);
        Location c2 = new Location(buildWorld, 2, 62, 2);
        Location spawn1 = new Location(buildWorld, 0.5, 61, 0.5);
        Location spawn2 = new Location(buildWorld, 1.5, 61, 1.5);
        Location center = new Location(buildWorld, 1, 61, 1);
        when(player.getLocation()).thenReturn(c1, c2, spawn1, spawn2, center);

        run("addarena", name);
        run("setcorner1");
        run("setcorner2");
        run("setspawn1");
        run("setspawn2");
        run("setcenter");
    }

    @Test
    void addarenaSansNomNeCreePasDeSession() {
        run("addarena");
        assertFalse(sessions.has(player.getUniqueId()));
    }

    @Test
    void addarenaCreeUneSessionEtPasseEnCreative() {
        run("addarena", "mon-arene");
        EditSession session = sessions.get(player.getUniqueId());
        assertNotNull(session);
        assertEquals("mon-arene", session.getSlug());
        assertEquals("mon-arene", session.getDisplayName());
        verify(player).setGameMode(GameMode.CREATIVE);
    }

    @Test
    void addarenaNeRemplacePasUneSessionOuverte() {
        run("addarena", "arene-1");
        run("addarena", "arene-2");
        assertEquals("arene-1", sessions.get(player.getUniqueId()).getSlug());
    }

    @Test
    void addarenaRefuseUnMauvaisMonde() {
        World other = mock(World.class);
        when(other.getName()).thenReturn("world_nether");
        when(player.getWorld()).thenReturn(other);
        run("addarena", "arene");
        assertFalse(sessions.has(player.getUniqueId()));
        verify(player, never()).setGameMode(GameMode.CREATIVE);
    }

    @Test
    void addarenaRefuseUneAreneExistante() throws Exception {
        arenas.save(sampleArena("getdown"));
        run("addarena", "getdown");
        assertFalse(sessions.has(player.getUniqueId()));
    }

    @Test
    void addarenaRefuseUnNomSansCaracteresValides() {
        run("addarena", "!!!");
        assertFalse(sessions.has(player.getUniqueId()));
    }

    @Test
    void addarenaRefuseCaracteresNonAnglais() {
        run("addarena", "arène");
        assertFalse(sessions.has(player.getUniqueId()));
    }

    @Test
    void addarenaRefuseCaracteresSpeciaux() {
        run("addarena", "arena_ñoño");
        assertFalse(sessions.has(player.getUniqueId()));
    }

    @Test
    void addarenaAccepteNomAnglaisSimple() {
        run("addarena", "MyArena");
        assertTrue(sessions.has(player.getUniqueId()));
    }

    @Test
    void addarenaRefuseNomAvecEspace() {
        run("addarena", "mon", "arene");
        assertFalse(sessions.has(player.getUniqueId()));
    }

    @Test
    void addarenaRefuseUnNomTropLong() {
        run("addarena", "a".repeat(65));
        assertFalse(sessions.has(player.getUniqueId()));
    }

    @Test
    void setPointSansSessionNeChangeRien() {
        run("setcorner1");
        assertFalse(sessions.has(player.getUniqueId()));
    }

    @Test
    void lesCinqPointsSePosentDansLOrdre() {
        Location c1 = new Location(buildWorld, 0.5, 60, 0.5);
        Location c2 = new Location(buildWorld, 10.5, 65, 8.5);
        Location s1 = new Location(buildWorld, 2.5, 61, 4.5, 90f, 0f);
        Location s2 = new Location(buildWorld, 8.5, 61, 4.5, -90f, 0f);
        Location center = new Location(buildWorld, 5.5, 61, 4.5);
        when(player.getLocation()).thenReturn(c1, c2, s1, s2, center);

        run("addarena", "duel");
        run("setcorner1");
        run("setcorner2");
        run("setspawn1");
        run("setspawn2");
        run("setcenter");

        EditSession session = sessions.get(player.getUniqueId());
        assertEquals(c1, session.getCorner1());
        assertEquals(c2, session.getCorner2());
        assertEquals(s1, session.getSpawn1());
        assertEquals(s2, session.getSpawn2());
        assertEquals(center, session.getCenter());
        assertTrue(session.isComplete());
    }

    @Test
    void lesPointsSontRemisDEquerreApresLaPose() {
        when(player.getLocation()).thenReturn(new Location(buildWorld, 2.3, 61, 4.7, 92.4f, -3.1f));

        run("addarena", "duel");
        run("setspawn1");

        assertEquals(new Location(buildWorld, 2.5, 61, 4.5, 90f, 0f),
                sessions.get(player.getUniqueId()).getSpawn1());
    }

    @Test
    void setPointDansUnAutreMondeNEstPasEnregistre() {
        run("addarena", "arene");
        World other = mock(World.class);
        when(other.getName()).thenReturn("world_the_end");
        when(player.getWorld()).thenReturn(other);
        run("setcorner1");
        assertNull(sessions.get(player.getUniqueId()).getCorner1());
    }

    @Test
    void saveSansSessionNePlanifieRien() {
        run("save");
        assertFalse(sessions.has(player.getUniqueId()));
        assertNull(BukkitServerFixture.pollTimerTask());
    }

    @Test
    void saveIncompletNePlanifiePasDeCapture() {
        run("addarena", "arene");
        run("save");
        assertTrue(sessions.has(player.getUniqueId()));
        assertNull(BukkitServerFixture.pollTimerTask());
    }

    @Test
    void saveAvecUnVolumeTropGrandNePlanifiePasDeCapture() {
        Location huge1 = new Location(buildWorld, 0, -60, 0);
        Location huge2 = new Location(buildWorld, 300, 100, 300);
        Location inside = new Location(buildWorld, 5, 60, 5);
        when(player.getLocation()).thenReturn(huge1, huge2, inside, inside, inside);

        run("addarena", "geante");
        run("setcorner1");
        run("setcorner2");
        run("setspawn1");
        run("setspawn2");
        run("setcenter");
        run("save");

        assertNull(BukkitServerFixture.pollTimerTask());
        assertTrue(sessions.has(player.getUniqueId()));
    }

    @Test
    void saveAvecUnCentreHorsZoneNePlanifiePasDeCapture() {
        Location c1 = new Location(buildWorld, 0, 60, 0);
        Location c2 = new Location(buildWorld, 2, 62, 2);
        Location spawn1 = new Location(buildWorld, 0.5, 61, 0.5);
        Location spawn2 = new Location(buildWorld, 1.5, 61, 1.5);
        Location farAway = new Location(buildWorld, 500, 61, 500);
        when(player.getLocation()).thenReturn(c1, c2, spawn1, spawn2, farAway);

        run("addarena", "arene");
        run("setcorner1");
        run("setcorner2");
        run("setspawn1");
        run("setspawn2");
        run("setcenter");
        run("save");

        assertNull(BukkitServerFixture.pollTimerTask());
    }

    @Test
    void saveCompletCaptureSauvegardeEtFermeLaSession() throws IOException {
        stubStoneBlocks();
        prepareCompleteSession("mon-arene");

        run("save");
        Runnable capture = BukkitServerFixture.pollTimerTask();
        assertNotNull(capture);
        capture.run(); // budget 50000 > 27 blocs : terminé en un tick

        assertTrue(arenas.exists("mon-arene"));
        assertTrue(arenas.schematicFile("mon-arene").exists());
        assertFalse(sessions.has(player.getUniqueId()));
        verify(player).setGameMode(GameMode.CREATIVE);
        verify(player).setGameMode(GameMode.SURVIVAL);

        // Relu depuis le disque : le fichier écrit est bien la définition complète.
        ArenaStorage fresh = new ArenaStorage(storagePlugin());
        fresh.loadAll();
        Arena sauvegardee = fresh.get("mon-arene");
        assertNotNull(sauvegardee);
        assertEquals(3, sauvegardee.sizeX());
        assertEquals(27, sauvegardee.volume());
        assertTrue(sauvegardee.isComplete());
    }

    private JavaPlugin storagePlugin() {
        JavaPlugin storagePlugin = mock(JavaPlugin.class);
        when(storagePlugin.getDataFolder()).thenReturn(tempDir);
        when(storagePlugin.getLogger()).thenReturn(Logger.getLogger("BuildCommandTest"));
        return storagePlugin;
    }

    @Test
    void saveAvecUneErreurDEcritureSchematicGardeLaSession() throws IOException {
        Files.createFile(tempDir.toPath().resolve("schematics")); // dossier attendu, fichier présent
        stubStoneBlocks();
        prepareCompleteSession("arene");

        run("save");
        BukkitServerFixture.pollTimerTask().run();

        assertFalse(arenas.exists("arene"));
        assertTrue(sessions.has(player.getUniqueId()));
    }

    @Test
    void saveAvecUneErreurDEcritureYaml() {
        File arenasDir = new File(tempDir, "arenas");
        assertTrue(arenasDir.mkdirs());
        assertTrue(arenasDir.setWritable(false));
        try {
            stubStoneBlocks();
            prepareCompleteSession("arene");

            run("save");
            BukkitServerFixture.pollTimerTask().run();

            assertFalse(arenas.exists("arene")); // pas d'enregistrement YAML…
            assertTrue(arenas.schematicFile("arene").exists()); // …mais schematic déjà écrite
        } finally {
            arenasDir.setWritable(true);
        }
    }

    @Test
    void saveAvecUneErreurDeCaptureGardeLaSession() {
        buildChunk = mock(Chunk.class);
        when(buildWorld.getChunkAt(anyInt(), anyInt())).thenReturn(buildChunk);
        when(buildChunk.getBlock(anyInt(), anyInt(), anyInt()))
                .thenThrow(new RuntimeException("chunk corrompu"));
        prepareCompleteSession("arene");

        run("save");
        BukkitServerFixture.pollTimerTask().run();

        assertFalse(arenas.exists("arene"));
        assertTrue(sessions.has(player.getUniqueId()));
    }

    @Test
    void cancelFermeLaSessionEtRestaureLeMode() {
        run("addarena", "arene");
        run("cancel");
        assertFalse(sessions.has(player.getUniqueId()));
        verify(player).setGameMode(GameMode.SURVIVAL);
    }

    @Test
    void cancelSansSessionEstSansEffet() {
        run("cancel");
        assertFalse(sessions.has(player.getUniqueId()));
    }

    @Test
    void editChargeUneAreneExistanteAvecSesPoints() throws Exception {
        arenas.save(sampleArena("getdown"));
        World world = mock(World.class);
        when(world.getName()).thenReturn("build");
        when(Bukkit.getServer().getWorld("build")).thenReturn(world);

        run("edit", "getdown");
        EditSession session = sessions.get(player.getUniqueId());
        assertNotNull(session);
        assertTrue(session.isComplete());
    }

    @Test
    void editDuneAreneInconnueNEstPasOuverte() {
        run("edit", "fantome");
        assertFalse(sessions.has(player.getUniqueId()));
    }

    @Test
    void editNeRemplacePasUneSessionOuverte() throws Exception {
        arenas.save(sampleArena("getdown"));
        run("addarena", "autre");
        run("edit", "getdown");
        assertEquals("autre", sessions.get(player.getUniqueId()).getSlug());
    }

    @Test
    void editAvecUnMondeNonChargeNEstPasOuverte() throws Exception {
        arenas.save(sampleArena("getdown"));
        when(Bukkit.getServer().getWorld("build")).thenReturn(null);
        run("edit", "getdown");
        assertFalse(sessions.has(player.getUniqueId()));
    }

    @Test
    void deleteEstBloqueTantQueDesInstancesSontDeployees() throws Exception {
        arenas.save(sampleArena("getdown"));
        deployments.put(new DeployedInstance("getdown-1", "getdown", "world",
                Point.of(0, 60, 0), new int[]{0, 60, 0}, new int[]{2, 62, 2},
                Point.of(0.5, 61, 0.5), Point.of(1.5, 61, 1.5),
                new int[]{0, 0}, new int[]{100, 100}, System.currentTimeMillis()));
        run("delete", "getdown");
        assertTrue(arenas.exists("getdown"));
    }

    @Test
    void deleteSupprimeYamlEtSchematic() throws Exception {
        arenas.save(sampleArena("getdown"));
        File schem = arenas.schematicFile("getdown");
        assertTrue(schem.getParentFile().mkdirs());
        assertTrue(schem.createNewFile());

        run("delete", "getdown");
        assertFalse(arenas.exists("getdown"));
        assertFalse(schem.exists());
    }

    @Test
    void deleteDuneAreneInconnueNeSupprimeRien() {
        run("delete", "fantome");
        assertFalse(arenas.exists("fantome"));
    }

    @Test
    void laConsoleNePeutPasEditer() {
        CommandSender console = mock(CommandSender.class);
        assertTrue(command.onCommand(console, cmd, "build", new String[]{"addarena", "x"}));
        assertFalse(sessions.has(player.getUniqueId()));
    }

    @Test
    void lesCommandesDeLectureNontAucunEffetDeBord() throws Exception {
        arenas.save(sampleArena("getdown"));
        assertTrue(run("info", "getdown"));
        assertTrue(run("list"));
        assertTrue(run());
        assertTrue(run("nimportequoi"));
        assertNull(BukkitServerFixture.pollTimerTask());
        assertTrue(arenas.exists("getdown"));
        assertFalse(sessions.has(player.getUniqueId()));
    }

    @Test
    void laTabCompletionProposeSousCommandesEtArenes() throws Exception {
        arenas.save(sampleArena("getdown"));
        assertTrue(command.onTabComplete(player, cmd, "build", new String[]{""}).contains("addarena"));
        assertTrue(command.onTabComplete(player, cmd, "build", new String[]{""}).contains("tp"));
        assertTrue(command.onTabComplete(player, cmd, "build", new String[]{"edit", ""}).contains("getdown"));
        assertTrue(command.onTabComplete(player, cmd, "build", new String[]{"tp", ""}).contains("getdown"));
        assertTrue(command.onTabComplete(player, cmd, "build", new String[]{"delete", "getdown", ""}).isEmpty());
        assertTrue(command.onTabComplete(player, cmd, "build", new String[]{"save", ""}).isEmpty());
        assertTrue(command.onTabComplete(player, cmd, "build", new String[]{"e"}).contains("edit"));
    }

    @Test
    void lesSuggestionsTabSontFiltrees() {
        List<String> options = command.onTabComplete(player, cmd, "build", new String[]{"setc"});
        assertTrue(options.contains("setcorner1"));
        assertFalse(options.contains("save"));
    }

    // ────────────────────────────── tp ──────────────────────────────

    @Test
    void tpTeleporteAuCentreDeLArene() throws Exception {
        arenas.save(sampleArena("getdown"));
        when(Bukkit.getServer().getWorld("build")).thenReturn(buildWorld);
        when(buildWorld.getChunkAtAsync(anyInt(), anyInt()))
                .thenReturn(CompletableFuture.completedFuture(mock(Chunk.class)));

        assertTrue(run("tp", "getdown"));

        Location target = new Location(buildWorld, 1, 61, 1, 0f, 0f);
        verify(player).teleport(target);
        // les 9 chunks autour du centre sont préchargés
        int cx = target.getBlockX() >> 4;
        int cz = target.getBlockZ() >> 4;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                verify(buildWorld).getChunkAtAsync(cx + dx, cz + dz);
            }
        }
    }

    @Test
    void tpAttendLaFinDuPrechargementAvantDeTeleporter() throws Exception {
        arenas.save(sampleArena("getdown"));
        when(Bukkit.getServer().getWorld("build")).thenReturn(buildWorld);
        CompletableFuture<Chunk> pending = new CompletableFuture<>();
        when(buildWorld.getChunkAtAsync(anyInt(), anyInt())).thenReturn(pending);

        run("tp", "getdown");
        verify(player, never()).teleport(org.mockito.ArgumentMatchers.any(Location.class));

        pending.complete(mock(Chunk.class));
        verify(player).teleport(new Location(buildWorld, 1, 61, 1, 0f, 0f));
    }

    @Test
    void tpAnnuleSiLeJoueurSeDeconnectePendantLePrechargement() throws Exception {
        arenas.save(sampleArena("getdown"));
        when(Bukkit.getServer().getWorld("build")).thenReturn(buildWorld);
        CompletableFuture<Chunk> pending = new CompletableFuture<>();
        when(buildWorld.getChunkAtAsync(anyInt(), anyInt())).thenReturn(pending);
        when(player.isOnline()).thenReturn(false);

        run("tp", "getdown");
        pending.complete(mock(Chunk.class));

        verify(player, never()).teleport(org.mockito.ArgumentMatchers.any(Location.class));
    }

    @Test
    void tpSansArgumentNeTeleportePas() throws Exception {
        arenas.save(sampleArena("getdown"));
        assertTrue(run("tp"));
        verify(player, never()).teleport(org.mockito.ArgumentMatchers.any(Location.class));
    }

    @Test
    void tpDuneAreneIntrouvableNeTeleportePas() throws Exception {
        assertTrue(run("tp", "fantome"));
        verify(player, never()).teleport(org.mockito.ArgumentMatchers.any(Location.class));
    }

    @Test
    void tpRefuseUnMondeDeBuildNonCharge() throws Exception {
        arenas.save(sampleArena("getdown"));
        when(Bukkit.getServer().getWorld("build")).thenReturn(null);

        assertTrue(run("tp", "getdown"));
        verify(player, never()).teleport(org.mockito.ArgumentMatchers.any(Location.class));
    }

    @Test
    void laConsoleNePeutPasSeTeleporter() throws Exception {
        arenas.save(sampleArena("getdown"));
        CommandSender console = mock(CommandSender.class);
        assertTrue(command.onCommand(console, cmd, "build", new String[]{"tp", "getdown"}));
        verify(player, never()).teleport(org.mockito.ArgumentMatchers.any(Location.class));
    }

    // ---- rename ----

    @Test
    void renameSansArgumentNeFaitRien() throws Exception {
        arenas.save(sampleArena("getdown"));
        run("rename");
        assertTrue(arenas.exists("getdown"));
    }

    @Test
    void renameSansNouveauNomNeFaitRien() throws Exception {
        arenas.save(sampleArena("getdown"));
        run("rename", "getdown");
        assertTrue(arenas.exists("getdown"));
        assertFalse(arenas.exists("nouveau"));
    }

    @Test
    void renameAreneIntrouvableNeFaitRien() {
        run("rename", "fantome", "Nouveau");
    }

    @Test
    void renameChangeSlugEtNom() throws Exception {
        arenas.save(sampleArena("getdown"));
        run("rename", "getdown", "new-arena");

        assertFalse(arenas.exists("getdown"));
        assertTrue(arenas.exists("new-arena"));
        Arena renamed = arenas.get("new-arena");
        assertEquals("new-arena", renamed.getDisplayName());
        assertEquals("new-arena", renamed.getSlug());
    }

    @Test
    void renameConserveLesDonneesDeLArene() throws Exception {
        Arena original = sampleArena("getdown");
        arenas.save(original);
        run("rename", "getdown", "Renamed");

        Arena renamed = arenas.get("renamed");
        assertArrayEquals(new int[]{0, 60, 0}, renamed.getCorner1());
        assertArrayEquals(new int[]{2, 62, 2}, renamed.getCorner2());
        assertEquals(Point.of(1, 61, 1), renamed.getCenter());
        assertEquals(Point.of(0.5, 61, 0.5), renamed.getSpawn1());
        assertEquals(Point.of(1.5, 61, 1.5), renamed.getSpawn2());
    }

    @Test
    void renameRefuseSiAreneCibleExiste() throws Exception {
        arenas.save(sampleArena("getdown"));
        arenas.save(sampleArena("autre"));
        run("rename", "getdown", "Autre");

        assertTrue(arenas.exists("getdown"));
        assertEquals(2, arenas.count());
    }

    @Test
    void renameMetAJourInstancesDeployees() throws Exception {
        arenas.save(sampleArena("getdown"));
        DeployedInstance inst = new DeployedInstance("getdown-1", "getdown", "prod",
                Point.of(100, 60, 100), new int[]{90, 60, 90}, new int[]{110, 70, 110},
                Point.of(95, 61, 95), Point.of(105, 61, 105),
                new int[]{80, 80}, new int[]{120, 120}, System.currentTimeMillis());
        deployments.put(inst);

        run("rename", "getdown", "Nouveau");
        assertTrue(arenas.exists("nouveau"));
        assertFalse(arenas.exists("getdown"));
        // Instance déployée conserve ses coordonnées mais pointe vers le nouveau slug
        DeployedInstance updated = deployments.get("getdown-1");
        assertNotNull(updated);
        assertEquals("nouveau", updated.getArena());
        assertArrayEquals(new int[]{90, 60, 90}, updated.getCorner1());
        assertArrayEquals(new int[]{110, 70, 110}, updated.getCorner2());
    }

    @Test
    void renameRefuseNomTropLong() throws Exception {
        arenas.save(sampleArena("getdown"));
        run("rename", "getdown", "a".repeat(65));
        assertTrue(arenas.exists("getdown"));
    }

    @Test
    void renameRefuseNomInvalide() throws Exception {
        arenas.save(sampleArena("getdown"));
        run("rename", "getdown", "!!!");
        assertTrue(arenas.exists("getdown"));
    }

    @Test
    void renameRefuseCaracteresNonAnglais() throws Exception {
        arenas.save(sampleArena("getdown"));
        run("rename", "getdown", "arène");
        assertTrue(arenas.exists("getdown"));
        assertFalse(arenas.exists("arene"));
    }

    @Test
    void renameAccepteNomAnglais() throws Exception {
        arenas.save(sampleArena("getdown"));
        run("rename", "getdown", "new-arena");
        assertTrue(arenas.exists("new-arena"));
        assertFalse(arenas.exists("getdown"));
    }

    @Test
    void renameRefuseNomAvecEspace() throws Exception {
        arenas.save(sampleArena("getdown"));
        run("rename", "getdown", "new", "arena");
        assertTrue(arenas.exists("getdown"));
        assertFalse(arenas.exists("new-arena"));
    }

    @Test
    void renameSupprimeAnciensFichiers() throws Exception {
        arenas.save(sampleArena("getdown"));
        File oldYml = arenas.arenaFile("getdown");
        File oldSchem = arenas.schematicFile("getdown");
        assertTrue(oldYml.exists());
        assertTrue(oldSchem.getParentFile().mkdirs() || oldSchem.getParentFile().exists());
        Files.writeString(oldSchem.toPath(), "schematic-data");

        run("rename", "getdown", "Renamed");

        assertFalse(oldYml.exists());
        assertFalse(oldSchem.exists());
        assertTrue(arenas.arenaFile("renamed").exists());
        assertTrue(arenas.schematicFile("renamed").exists());
    }

    @Test
    void renameCopieLaSchematic() throws Exception {
        arenas.save(sampleArena("getdown"));
        File schem = arenas.schematicFile("getdown");
        assertTrue(schem.getParentFile().mkdirs() || schem.getParentFile().exists());
        Files.writeString(schem.toPath(), "binary-content-123");

        run("rename", "getdown", "Renamed");

        assertEquals("binary-content-123",
                Files.readString(arenas.schematicFile("renamed").toPath()));
    }

    @Test
    void renameMemeSlugChangeSeulementLeNom() throws Exception {
        Arena original = sampleArena("getdown");
        arenas.save(original);
        long originalSavedAt = original.getSavedAt();
        Thread.sleep(10);

        // "Getdown" slugifie en "getdown" → même slug, seul le display-name change
        run("rename", "getdown", "Getdown");

        Arena renamed = arenas.get("getdown");
        assertEquals("Getdown", renamed.getDisplayName());
        assertEquals("getdown", renamed.getSlug());
        assertTrue(renamed.getSavedAt() > originalSavedAt);
    }
}

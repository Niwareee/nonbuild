package fr.niware.nonbuild.gui;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mock;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.MockitoAnnotations;

import fr.niware.nonbuild.NonBuild;
import fr.niware.nonbuild.Settings;
import fr.niware.nonbuild.model.DeployedInstance;
import fr.niware.nonbuild.model.Point;
import fr.niware.nonbuild.storage.ArenaStorage;
import fr.niware.nonbuild.storage.DeploymentStorage;
import fr.niware.nonbuild.testutil.BukkitServerFixture;
import net.kyori.adventure.text.Component;

class GoalGUITest {

    @TempDir
    Path tempDir;

    @Mock NonBuild plugin;
    @Mock Settings settings;
    @Mock ArenaStorage arenas;
    @Mock DeploymentStorage deployments;
    @Mock Player player;
    @Mock Inventory mockInventory;
    @Mock ItemStack mockItemStack;
    @Mock ItemMeta mockItemMeta;

    GoalGUI gui;

    @BeforeEach
    void setUp() {
        BukkitServerFixture.ensure();
        MockitoAnnotations.openMocks(this);

        when(plugin.getSettings()).thenReturn(settings);
        when(plugin.getArenas()).thenReturn(arenas);
        when(plugin.getDeployments()).thenReturn(deployments);

        Map<String, String> gameModes = Map.of(
                "GETDOWN", "GetDown",
                "SKYWARS", "Skywars",
                "FFA", "FFA"
        );
        when(settings.gameModes()).thenReturn(gameModes);

        when(Bukkit.createInventory(any(), eq(54), any(Component.class))).thenReturn(mockInventory);
        when(mockInventory.getSize()).thenReturn(54);

        gui = new GoalGUI(plugin);
        gui.itemFactory = mat -> mockItemStack;
        when(mockItemStack.getItemMeta()).thenReturn(mockItemMeta);
        when(mockItemMeta.lore()).thenReturn(List.of());
    }

    @Test
    void ouvreUnInventaireDe54Slots() {
        stubDeploymentsEmpty();

        gui.open(player);

        verify(player).openInventory(mockInventory);
        assertEquals(54, mockInventory.getSize());
    }

    @Test
    void modeSansInstanceAfficheLoreAucuneMap() {
        stubDeploymentsEmpty();

        gui.open(player);

        verify(mockItemMeta, atLeast(1)).lore(argThat(lore ->
                lore.stream().anyMatch(l -> l.toString().contains("Aucune map déployée"))));
    }

    @Test
    void modeAvecInstanceAfficheLoreArenesDeployees() {
        DeployedInstance inst = mockInstance("getdown", "getdown-1");
        when(arenas.byGameMode("GETDOWN")).thenReturn(Set.of("getdown"));
        when(deployments.byArenaSlugs(Set.of("getdown"))).thenReturn(List.of(inst));
        when(arenas.byGameMode("SKYWARS")).thenReturn(Set.of());
        when(deployments.byArenaSlugs(Set.of())).thenReturn(List.of());
        when(arenas.byGameMode("FFA")).thenReturn(Set.of());
        when(arenas.get("getdown")).thenReturn(null);

        gui.open(player);

        verify(mockItemMeta, atLeast(1)).lore(argThat(lore ->
                lore.stream().anyMatch(l -> l.toString().contains("Arènes déployées"))));
    }

    @Test
    void loreAfficheNomAfficheDeLAreneSiDisponible() {
        DeployedInstance inst = mockInstance("getdown", "getdown-1");
        when(arenas.byGameMode("GETDOWN")).thenReturn(Set.of("getdown"));
        when(deployments.byArenaSlugs(Set.of("getdown"))).thenReturn(List.of(inst));
        when(arenas.byGameMode("SKYWARS")).thenReturn(Set.of());
        when(deployments.byArenaSlugs(Set.of())).thenReturn(List.of());
        when(arenas.byGameMode("FFA")).thenReturn(Set.of());

        fr.niware.nonbuild.model.Arena arena = mock(fr.niware.nonbuild.model.Arena.class);
        when(arena.getDisplayName()).thenReturn("GetDown Arena");
        when(arenas.get("getdown")).thenReturn(arena);

        gui.open(player);

        verify(mockItemMeta, atLeast(1)).lore(argThat(lore ->
                lore.stream().anyMatch(l -> l.toString().contains("GetDown Arena"))));
    }

    @Test
    void loreAfficheCompteurParArene() {
        DeployedInstance inst1 = mockInstance("getdown", "getdown-1");
        DeployedInstance inst2 = mockInstance("getdown", "getdown-2");
        when(arenas.byGameMode("GETDOWN")).thenReturn(Set.of("getdown"));
        when(deployments.byArenaSlugs(Set.of("getdown"))).thenReturn(List.of(inst1, inst2));
        when(arenas.byGameMode("SKYWARS")).thenReturn(Set.of());
        when(deployments.byArenaSlugs(Set.of())).thenReturn(List.of());
        when(arenas.byGameMode("FFA")).thenReturn(Set.of());
        when(arenas.get("getdown")).thenReturn(null);

        gui.open(player);

        verify(mockItemMeta, atLeast(1)).lore(argThat(lore ->
                lore.stream().anyMatch(l -> l.toString().contains("2 instances"))));
    }

    @Test
    void loreAfficheCompteurSingular() {
        DeployedInstance inst = mockInstance("getdown", "getdown-1");
        when(arenas.byGameMode("GETDOWN")).thenReturn(Set.of("getdown"));
        when(deployments.byArenaSlugs(Set.of("getdown"))).thenReturn(List.of(inst));
        when(arenas.byGameMode("SKYWARS")).thenReturn(Set.of());
        when(deployments.byArenaSlugs(Set.of())).thenReturn(List.of());
        when(arenas.byGameMode("FFA")).thenReturn(Set.of());
        when(arenas.get("getdown")).thenReturn(null);

        gui.open(player);

        verify(mockItemMeta, atLeast(1)).lore(argThat(lore ->
                lore.stream().anyMatch(l -> l.toString().contains("1 instance") && !l.toString().contains("instances"))));
    }

    @Test
    void clicSurInventaireFermeLeMenu() {
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        org.bukkit.inventory.InventoryView view = mock(org.bukkit.inventory.InventoryView.class);
        when(event.getView()).thenReturn(view);
        when(view.title()).thenReturn(Component.text("Objectifs de build"));
        when(event.getWhoClicked()).thenReturn(player);

        gui.onInventoryClick(event);

        verify(event).setCancelled(true);
        verify(player).closeInventory();
    }

    @Test
    void clicSurAutreInventaireEstIgnore() {
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        org.bukkit.inventory.InventoryView view = mock(org.bukkit.inventory.InventoryView.class);
        when(event.getView()).thenReturn(view);
        when(view.title()).thenReturn(Component.text("Autre inventaire"));

        gui.onInventoryClick(event);

        verify(event, never()).setCancelled(true);
    }

    @Test
    void displayNameEstEnJaune() {
        stubDeploymentsEmpty();

        gui.open(player);

        verify(mockItemMeta, atLeast(1)).displayName(argThat(name -> name.toString().contains("§e")));
    }

    private void stubDeploymentsEmpty() {
        when(arenas.byGameMode("GETDOWN")).thenReturn(Set.of());
        when(arenas.byGameMode("SKYWARS")).thenReturn(Set.of());
        when(arenas.byGameMode("FFA")).thenReturn(Set.of());
        when(deployments.byArenaSlugs(Set.of())).thenReturn(List.of());
    }

    private DeployedInstance mockInstance(String arena, String name) {
        Point center = new Point(0, 0, 0, 0, 0);
        return new DeployedInstance(name, arena, "world", center,
                new int[]{0, 0, 0}, new int[]{2, 2, 2},
                center, center,
                new int[]{-16, -16}, new int[]{16, 16},
                System.currentTimeMillis());
    }
}

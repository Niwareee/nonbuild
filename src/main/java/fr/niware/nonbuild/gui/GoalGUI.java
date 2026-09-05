package fr.niware.nonbuild.gui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import fr.niware.nonbuild.NonBuild;
import fr.niware.nonbuild.model.DeployedInstance;
import net.kyori.adventure.text.Component;

/**
 * GUI /build goal — état des lieux des maps par mode de jeu.
 * <p>
 * Chaque mode de jeu est représenté par un bloc de concrete :
 * <ul>
 *   <li>Vert si au moins une instance est déployée</li>
 *   <li>Rouge si aucune instance n'est déployée</li>
 * </ul>
 * La lore affiche la liste des arènes déployées (ou "Aucune map déployée").
 */
public final class GoalGUI implements Listener {

    private static final String TITLE = "Objectifs de build";
    private static final int SLOTS = 54; // 9 lignes, on utilise les 2 premières (12 slots)

    private final NonBuild plugin;
    /**
 * Fabrique d'ItemStack — package-private pour les tests (Paper 26.2:
 * ItemStack a un craftDelegate interne impossible à mocker).
 */
    java.util.function.Function<Material, ItemStack> itemFactory = mat -> new ItemStack(mat);

    public GoalGUI(NonBuild plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Ouvre le GUI au joueur.
     */
    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, SLOTS, TITLE);

        Map<String, String> gameModes = plugin.getSettings().gameModes();
        int slot = 0;

        for (Map.Entry<String, String> entry : gameModes.entrySet()) {
            if (slot >= SLOTS) break;

            String modeKey = entry.getKey();
            String modeName = entry.getValue();
            List<DeployedInstance> instances = plugin.getDeployments().byGameMode(modeKey);

            ItemStack item = createModeItem(modeKey, modeName, instances);
            inv.setItem(slot, item);
            slot++;
        }

        player.openInventory(inv);
    }

    private ItemStack createModeItem(String modeKey, String modeName, List<DeployedInstance> instances) {
        Material material = instances.isEmpty() ? Material.RED_CONCRETE : Material.LIME_CONCRETE;
        ItemStack item = itemFactory.apply(material);
        ItemMeta meta = item.getItemMeta();

        List<String> lore = new ArrayList<>();
        lore.add("§7" + modeName);
        lore.add("");

        if (instances.isEmpty()) {
            lore.add("§cAucune map déployée");
        } else {
            // Regrouper par slug d'arène
            Map<String, Integer> arenaCount = new LinkedHashMap<>();
            for (DeployedInstance inst : instances) {
                arenaCount.merge(inst.getArena(), 1, Integer::sum);
            }

            lore.add("§aArènes déployées :");
            for (Map.Entry<String, Integer> e : arenaCount.entrySet()) {
                String arenaSlug = e.getKey();
                int count = e.getValue();
                String displayName = arenaSlug;
                var arena = plugin.getArenas().get(arenaSlug);
                if (arena != null) {
                    displayName = arena.getDisplayName();
                }
                lore.add("  §f" + displayName + " §7(" + count + " instance" + (count > 1 ? "s" : "") + ")");
            }
        }

        meta.setDisplayName("§e" + modeName);
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!Component.text(TITLE).equals(event.getView().title())) {
            return;
        }
        event.setCancelled(true);
        if (event.getWhoClicked() instanceof Player player) {
            player.closeInventory();
        }
    }
}

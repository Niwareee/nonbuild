package fr.niware.nonbuild.schematic;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Art;
import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Banner;
import org.bukkit.block.Barrel;
import org.bukkit.block.BlastFurnace;
import org.bukkit.block.BlockState;
import org.bukkit.block.BrewingStand;
import org.bukkit.block.Campfire;
import org.bukkit.block.Chest;
import org.bukkit.block.ChiseledBookshelf;
import org.bukkit.block.Container;
import org.bukkit.block.Crafter;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.block.DecoratedPot;
import org.bukkit.block.Dispenser;
import org.bukkit.block.Dropper;
import org.bukkit.block.Furnace;
import org.bukkit.block.Hopper;
import org.bukkit.block.Jukebox;
import org.bukkit.block.Lectern;
import org.bukkit.block.ShulkerBox;
import org.bukkit.block.Sign;
import org.bukkit.block.Skull;
import org.bukkit.block.Smoker;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.banner.PatternType;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Painting;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;

/**
 * Block entities : capture au /build save et application au collage.
 * Capture par API typée (pancartes, crânes custom, bannières, conteneurs,
 * spawners...) vers des entrées au format Sponge (Id + Pos + données),
 * récrites telles quelles dans le .schem. L'application est tolérante :
 * une entrée illisible est ignorée, jamais de paste interrompu.
 */
public final class BlockEntityIO {

    /** Clé maison : inventaires sérialisés via ItemStack#serializeAsBytes. */
    public static final String ITEMS_KEY = "NonBuildItems";

    private static final Set<Material> BLOCK_ENTITY_MATERIALS = buildBlockEntityMaterials();

    private BlockEntityIO() {
    }

    private static Set<Material> buildBlockEntityMaterials() {
        Set<Material> materials = EnumSet.noneOf(Material.class);
        for (Material material : Material.values()) {
            String name = material.name();
            if (name.endsWith("_SIGN") || name.endsWith("_BANNER")
                    || name.endsWith("_HEAD") || name.endsWith("_SKULL")
                    || name.endsWith("_SHULKER_BOX")
                    || name.endsWith("_ITEM_FRAME")
                    || name.endsWith("_PAINTING")) {
                materials.add(material);
            }
        }
        materials.addAll(List.of(
                Material.CHEST, Material.TRAPPED_CHEST, Material.BARREL, Material.SHULKER_BOX,
                Material.HOPPER, Material.DISPENSER, Material.DROPPER, Material.FURNACE,
                Material.BLAST_FURNACE, Material.SMOKER, Material.BREWING_STAND, Material.JUKEBOX,
                Material.SPAWNER, Material.LECTERN, Material.CHISELED_BOOKSHELF,
                Material.DECORATED_POT, Material.CRAFTER, Material.CAMPFIRE, Material.SOUL_CAMPFIRE));
        return materials;
    }

    /**
     * Garde bon marché : seuls ces matériaux passent par Block#getState()
     * pendant la capture (le reste des blocs n'a rien à capturer).
     */
    public static boolean isBlockEntity(Material material) {
        return material != null && BLOCK_ENTITY_MATERIALS.contains(material);
    }

    /**
     * Capture l'état d'un block entity en entrée Sponge (Id + Pos + données).
     * Retourne null s'il n'y a rien qui mérite d'être sauvegardé
     * (crâne sans profil identifiable, conteneur vide...).
     */
    public static Map<String, Object> capture(BlockState state, int x, int y, int z) {
        String id;
        Map<String, Object> data;
        if (state instanceof Sign sign) {
            id = state.getType().name().contains("WALL_SIGN") ? "minecraft:wall_sign" : "minecraft:sign";
            data = captureSign(sign);
        } else if (state instanceof Skull skull) {
            id = "minecraft:skull";
            data = captureSkull(skull);
        } else if (state instanceof Banner banner) {
            id = "minecraft:banner";
            data = captureBanner(banner);
        } else if (state instanceof CreatureSpawner spawner) {
            id = "minecraft:spawner";
            data = captureSpawner(spawner);
        } else if (state instanceof Campfire campfire) {
            id = "minecraft:campfire";
            data = itemsData(captureCampfireItems(campfire));
        } else if (state instanceof Jukebox jukebox) {
            id = "minecraft:jukebox";
            data = itemsData(captureSingleItem(jukebox.getRecord()));
        } else if (state instanceof ItemFrame itemFrame) {
            id = "minecraft:item_frame";
            data = captureItemFrame(itemFrame);
        } else if (state instanceof Painting painting) {
            id = "minecraft:painting";
            data = capturePainting(painting);
        } else if (state instanceof Container container) {
            id = containerId(state);
            data = id == null ? null : itemsData(captureItems(container.getInventory()));
        } else {
            return null;
        }
        if (data == null || data.isEmpty()) {
            return null;
        }
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("Id", id);
        entry.put("Pos", List.of(x, y, z));
        entry.putAll(data);
        return entry;
    }

    private static Map<String, Object> captureSign(Sign sign) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("front_text", captureSide(sign.getSide(Side.FRONT)));
        data.put("back_text", captureSide(sign.getSide(Side.BACK)));
        data.put("color", sign.getColor().name().toLowerCase(Locale.ROOT));
        return data;
    }

    private static Map<String, Object> captureSide(SignSide side) {
        Map<String, Object> map = new LinkedHashMap<>();
        List<Object> messages = new ArrayList<>(4);
        for (Component line : side.lines()) {
            messages.add(GsonComponentSerializer.gson().serialize(line));
        }
        map.put("messages", messages);
        map.put("has_glowing_text", (byte) (side.isGlowingText() ? 1 : 0));
        return map;
    }

    private static Map<String, Object> captureSkull(Skull skull) {
        PlayerProfile profile = skull.getPlayerProfile();
        if (profile == null) {
            return null;
        }
        String name = profile.getName();
        boolean hasName = name != null && !name.isBlank();
        // Un profil non résolu par le serveur (nom sans propriété textures) reste
        // sauvegardable : applySkull recrée le profil depuis le nom seul et le
        // serveur y résout la skin au collage.
        if (!hasName && profile.getProperties().isEmpty()) {
            return null;
        }
        Map<String, Object> serialized = new LinkedHashMap<>();
        if (hasName) {
            serialized.put("name", name);
        }
        List<Object> properties = new ArrayList<>();
        for (ProfileProperty property : profile.getProperties()) {
            Map<String, Object> propertyMap = new LinkedHashMap<>();
            propertyMap.put("name", property.getName());
            propertyMap.put("value", property.getValue());
            if (property.getSignature() != null) {
                propertyMap.put("signature", property.getSignature());
            }
            properties.add(propertyMap);
        }
        if (!properties.isEmpty()) {
            serialized.put("properties", properties);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("profile", serialized);
        return data;
    }

    @SuppressWarnings("removal") // PatternType n'expose aucun accesseur non déprécié en 26.2
    private static Map<String, Object> captureBanner(Banner banner) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("base_color", banner.getBaseColor().name().toLowerCase(Locale.ROOT));
        List<Object> patterns = new ArrayList<>();
        for (Pattern pattern : banner.getPatterns()) {
            Map<String, Object> patternMap = new LinkedHashMap<>();
            patternMap.put("pattern", pattern.getPattern().key().toString());
            patternMap.put("color", pattern.getColor().name().toLowerCase(Locale.ROOT));
            patterns.add(patternMap);
        }
        data.put("patterns", patterns);
        return data;
    }

    private static Map<String, Object> captureSpawner(CreatureSpawner spawner) {
        EntityType type = spawner.getSpawnedType();
        if (type == null) {
            return null;
        }
        Map<String, Object> entity = new LinkedHashMap<>();
        entity.put("id", type.getKey().toString());
        Map<String, Object> spawnData = new LinkedHashMap<>();
        spawnData.put("entity", entity);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("spawn_data", spawnData);
        return data;
    }

    private static List<Object> captureItems(Inventory inventory) {
        List<Object> items = new ArrayList<>();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            addSerializedItem(items, slot, inventory.getItem(slot));
        }
        return items;
    }

    private static List<Object> captureCampfireItems(Campfire campfire) {
        List<Object> items = new ArrayList<>();
        for (int slot = 0; slot < campfire.getSize(); slot++) {
            addSerializedItem(items, slot, campfire.getItem(slot));
        }
        return items;
    }

    private static List<Object> captureSingleItem(ItemStack item) {
        List<Object> items = new ArrayList<>();
        addSerializedItem(items, 0, item);
        return items;
    }

    private static void addSerializedItem(List<Object> items, int slot, ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return;
        }
        byte[] bytes;
        try {
            bytes = item.serializeAsBytes();
        } catch (Exception e) {
            return;
        }
        Map<String, Object> itemMap = new LinkedHashMap<>();
        itemMap.put("Slot", (byte) slot);
        itemMap.put("Data", bytes);
        items.add(itemMap);
    }

    private static Map<String, Object> itemsData(List<Object> items) {
        if (items.isEmpty()) {
            return null;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put(ITEMS_KEY, items);
        return data;
    }

    private static String containerId(BlockState state) {
        if (state.getType() == Material.TRAPPED_CHEST) return "minecraft:trapped_chest";
        if (state instanceof Chest) return "minecraft:chest";
        if (state instanceof Barrel) return "minecraft:barrel";
        if (state instanceof ShulkerBox) return "minecraft:shulker_box";
        if (state instanceof BlastFurnace) return "minecraft:blast_furnace";
        if (state instanceof Smoker) return "minecraft:smoker";
        if (state instanceof Furnace) return "minecraft:furnace";
        if (state instanceof Dropper) return "minecraft:dropper";
        if (state instanceof Dispenser) return "minecraft:dispenser";
        if (state instanceof Hopper) return "minecraft:hopper";
        if (state instanceof BrewingStand) return "minecraft:brewing_stand";
        if (state instanceof Lectern) return "minecraft:lectern";
        if (state instanceof ChiseledBookshelf) return "minecraft:chiseled_bookshelf";
        if (state instanceof DecoratedPot) return "minecraft:decorated_pot";
        if (state instanceof Crafter) return "minecraft:crafter";
        return null;
    }

    /**
     * Applique une entrée block entity sur le bloc collé en minX+Pos.
     * Le bloc collé fait foi : si son type ne correspond pas à l'entrée
     * (schematic corrompue, bloc remplacé), l'entrée est ignorée.
     */
    public static void apply(World world, int minX, int minY, int minZ, Map<String, Object> entry) {
        // Pos = list d'ints (capture NonBuild) ou int[] (WorldEdit) selon le producteur du .schem.
        Object posObject = entry.get("Pos");
        int dx, dy, dz;
        if (posObject instanceof List<?> pos && pos.size() == 3) {
            dx = asInt(pos.get(0));
            dy = asInt(pos.get(1));
            dz = asInt(pos.get(2));
        } else if (posObject instanceof int[] pos && pos.length == 3) {
            dx = pos[0];
            dy = pos[1];
            dz = pos[2];
        } else {
            return;
        }
        BlockState state;
        try {
            state = world.getBlockAt(minX + dx, minY + dy, minZ + dz).getState();
        } catch (Exception e) {
            return;
        }
        try {
            if (state instanceof Sign sign) {
                applySign(sign, entry);
            } else if (state instanceof Skull skull) {
                applySkull(skull, entry);
            } else if (state instanceof Banner banner) {
                applyBanner(banner, entry);
            } else if (state instanceof CreatureSpawner spawner) {
                applySpawner(spawner, entry);
            } else if (state instanceof Campfire campfire) {
                applyCampfire(campfire, entry);
            } else if (state instanceof Jukebox jukebox) {
                jukebox.setRecord(firstItem(entry));
            } else if (state instanceof Container container) {
                applyItems(container.getInventory(), entry);
            } else {
                return;
            }
            state.update(true, false);
        } catch (Exception ignored) {
            // Tolérance : une entrée illisible ne doit jamais interrompre un collage.
        }
    }

    private static void applySign(Sign sign, Map<String, Object> entry) {
        applySide(sign.getSide(Side.FRONT), entry.get("front_text"));
        applySide(sign.getSide(Side.BACK), entry.get("back_text"));
        if (entry.get("color") instanceof String color) {
            sign.setColor(dyeColor(color));
        }
    }

    private static void applySide(SignSide side, Object raw) {
        if (!(raw instanceof Map<?, ?> map)) {
            return;
        }
        if (map.get("messages") instanceof List<?> messages) {
            List<Component> lines = new ArrayList<>(4);
            for (int i = 0; i < 4; i++) {
                lines.add(i < messages.size() ? deserializeComponent(messages.get(i)) : Component.empty());
            }
            for (int i = 0; i < 4; i++) {
                side.line(i, lines.get(i));
            }
        }
        if (asInt(map.get("has_glowing_text")) != 0) {
            side.setGlowingText(true);
        }
    }

    private static Component deserializeComponent(Object value) {
        if (!(value instanceof String text) || text.isEmpty()) {
            return Component.empty();
        }
        try {
            return GsonComponentSerializer.gson().deserialize(text);
        } catch (Exception e) {
            return Component.text(text);
        }
    }

    private static void applySkull(Skull skull, Map<String, Object> entry) {
        Map<?, ?> profileMap = entry.get("profile") instanceof Map<?, ?> modern ? modern
                : entry.get("SkullOwner") instanceof Map<?, ?> legacy ? legacy : null;
        if (profileMap == null) {
            return;
        }
        String name = profileMap.get("name") instanceof String n && !n.isBlank() ? n : null;
        PlayerProfile profile = name != null
                ? Bukkit.createProfile(UUID.randomUUID(), name)
                : Bukkit.createProfile(UUID.randomUUID());
        addModernProperties(profile, profileMap.get("properties"));
        addLegacyTextures(profile, profileMap.get("Properties"));
        if (!profile.getProperties().isEmpty()) {
            skull.setPlayerProfile(profile);
        }
    }

    private static void addModernProperties(PlayerProfile profile, Object raw) {
        if (!(raw instanceof List<?> properties)) {
            return;
        }
        for (Object property : properties) {
            if (property instanceof Map<?, ?> map
                    && map.get("name") instanceof String propertyName
                    && map.get("value") instanceof String value) {
                String signature = map.get("signature") instanceof String s ? s : null;
                profile.setProperty(new ProfileProperty(propertyName, value, signature));
            }
        }
    }

    /** Format WorldEdit pré-1.20.5 : SkullOwner.Properties.textures. */
    private static void addLegacyTextures(PlayerProfile profile, Object raw) {
        if (!(raw instanceof Map<?, ?> properties) || !(properties.get("textures") instanceof List<?> textures)) {
            return;
        }
        for (Object texture : textures) {
            if (texture instanceof Map<?, ?> map && map.get("Value") instanceof String value) {
                String signature = map.get("Signature") instanceof String s ? s : null;
                profile.setProperty(new ProfileProperty("textures", value, signature));
            }
        }
    }

    private static void applyBanner(Banner banner, Map<String, Object> entry) {
        if (entry.get("base_color") instanceof String base) {
            banner.setBaseColor(dyeColor(base));
        }
        if (entry.get("patterns") instanceof List<?> list) {
            List<Pattern> patterns = new ArrayList<>();
            for (Object raw : list) {
                if (raw instanceof Map<?, ?> map && map.get("pattern") instanceof String patternName) {
                    PatternType type = patternType(patternName);
                    DyeColor color = map.get("color") instanceof String c ? dyeColor(c) : DyeColor.WHITE;
                    if (type != null) {
                        patterns.add(new Pattern(color, type));
                    }
                }
            }
            banner.setPatterns(patterns);
        }
    }

    private static void applySpawner(CreatureSpawner spawner, Map<String, Object> entry) {
        if (entry.get("spawn_data") instanceof Map<?, ?> spawnData
                && spawnData.get("entity") instanceof Map<?, ?> entity
                && entity.get("id") instanceof String id) {
            EntityType type = EntityType.fromName(id.substring(id.lastIndexOf(':') + 1));
            if (type != null) {
                spawner.setSpawnedType(type);
            }
        }
    }

    private static void applyItems(Inventory inventory, Map<String, Object> entry) {
        for (Map.Entry<Integer, ItemStack> item : decodedItems(entry).entrySet()) {
            if (item.getKey() < inventory.getSize()) {
                inventory.setItem(item.getKey(), item.getValue());
            }
        }
    }

    private static void applyCampfire(Campfire campfire, Map<String, Object> entry) {
        for (Map.Entry<Integer, ItemStack> item : decodedItems(entry).entrySet()) {
            if (item.getKey() < campfire.getSize()) {
                campfire.setItem(item.getKey(), item.getValue());
            }
        }
    }

    private static ItemStack firstItem(Map<String, Object> entry) {
        for (ItemStack item : decodedItems(entry).values()) {
            return item;
        }
        return null;
    }

    private static Map<Integer, ItemStack> decodedItems(Map<String, Object> entry) {
        Map<Integer, ItemStack> items = new LinkedHashMap<>();
        if (!(entry.get(ITEMS_KEY) instanceof List<?> list)) {
            return items;
        }
        for (Object raw : list) {
            if (raw instanceof Map<?, ?> map
                    && map.get("Slot") instanceof Number slot
                    && map.get("Data") instanceof byte[] data) {
                try {
                    items.put(slot.intValue(), ItemStack.deserializeBytes(data));
                } catch (Exception ignored) {
                }
            }
        }
        return items;
    }

    private static DyeColor dyeColor(String raw) {
        try {
            return DyeColor.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return DyeColor.WHITE;
        }
    }

    private static PatternType patternType(String raw) {
        String name = raw.substring(raw.lastIndexOf(':') + 1).toLowerCase(Locale.ROOT);
        NamespacedKey key = NamespacedKey.fromString(name);
        if (key == null) {
            return null;
        }
        return RegistryAccess.registryAccess().getRegistry(RegistryKey.BANNER_PATTERN).get(key);
    }

    private static Map<String, Object> captureItemFrame(ItemFrame itemFrame) {
        Map<String, Object> data = new LinkedHashMap<>();
        ItemStack item = itemFrame.getItem();
        if (item != null && item.getType() != Material.AIR) {
            data.put("item", item.serializeAsBytes());
        } else {
            data.put("item", null);
        }
        data.put("rotation", itemFrame.getRotation());
        return data;
    }

    private static Map<String, Object> capturePainting(Painting painting) {
        Map<String, Object> data = new LinkedHashMap<>();
        Art art = painting.getArt();
        data.put("painting", art.toString().toLowerCase(Locale.ROOT));
        // Orientation is handled via block data in Paper 26.2, stored as rotation
        data.put("orientation", "unknown");
        return data;
    }

    private static int asInt(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }
}

package fr.niware.nonbuild.schematic;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Banner;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Campfire;
import org.bukkit.block.Chest;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.block.Jukebox;
import org.bukkit.block.Sign;
import org.bukkit.block.Skull;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.banner.PatternType;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Painting;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.material.Attachable;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.MockedStatic;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.destroystokyo.paper.profile.ProfileProperty;

import fr.niware.nonbuild.testutil.BukkitServerFixture;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import net.kyori.adventure.text.Component;

/**
 * Capture et application des block entities : données sérialisées fidèlement
 * (pancartes, crânes custom, bannières, conteneurs, spawners) et application
 * tolérante (une entrée illisible n'interrompt jamais un collage).
 */
class BlockEntityIOTest {

    @BeforeEach
    void setup() {
        BukkitServerFixture.ensure();
    }

    @Test
    void isBlockEntityReconnaitLesMateriauxPorteurs() {
        assertTrue(BlockEntityIO.isBlockEntity(Material.OAK_SIGN));
        assertTrue(BlockEntityIO.isBlockEntity(Material.OAK_WALL_SIGN));
        assertTrue(BlockEntityIO.isBlockEntity(Material.CHEST));
        assertTrue(BlockEntityIO.isBlockEntity(Material.TRAPPED_CHEST));
        assertTrue(BlockEntityIO.isBlockEntity(Material.PLAYER_HEAD));
        assertTrue(BlockEntityIO.isBlockEntity(Material.WHITE_BANNER));
        assertTrue(BlockEntityIO.isBlockEntity(Material.RED_SHULKER_BOX));
        assertTrue(BlockEntityIO.isBlockEntity(Material.SPAWNER));
        assertTrue(BlockEntityIO.isBlockEntity(Material.JUKEBOX));
        assertTrue(BlockEntityIO.isBlockEntity(Material.CAMPFIRE));
        assertTrue(BlockEntityIO.isBlockEntity(Material.BARREL));
        assertFalse(BlockEntityIO.isBlockEntity(Material.STONE));
        assertFalse(BlockEntityIO.isBlockEntity(Material.AIR));
        assertFalse(BlockEntityIO.isBlockEntity(null));
    }

    @Test
    @SuppressWarnings("deprecation") // Sign.getColor()/setColor() : pas d'alternative non-dépréciée
    void captureUnePancarteMuraleProduitLesDeuxFaces() {
        SignSide front = mock(SignSide.class);
        when(front.lines()).thenReturn(List.of(
                Component.text("Salut"), Component.text("ligne 2"), Component.empty(), Component.empty()));
        when(front.isGlowingText()).thenReturn(true);
        SignSide back = mock(SignSide.class);
        when(back.lines()).thenReturn(List.of(
                Component.empty(), Component.empty(), Component.empty(), Component.empty()));
        when(back.isGlowingText()).thenReturn(false);

        Sign sign = mock(Sign.class);
        when(sign.getType()).thenReturn(Material.OAK_WALL_SIGN);
        when(sign.getSide(Side.FRONT)).thenReturn(front);
        when(sign.getSide(Side.BACK)).thenReturn(back);
        when(sign.getColor()).thenReturn(DyeColor.RED);

        Map<String, Object> entry = BlockEntityIO.capture(sign, 3, 4, 5);

        assertEquals("minecraft:wall_sign", entry.get("Id"));
        assertEquals(List.of(3, 4, 5), entry.get("Pos"));
        assertEquals("red", entry.get("color"));

        Map<?, ?> frontText = (Map<?, ?>) entry.get("front_text");
        List<?> messages = (List<?>) frontText.get("messages");
        assertEquals(4, messages.size());
        assertTrue(((String) messages.get(0)).contains("Salut"));
        assertEquals((byte) 1, frontText.get("has_glowing_text"));

        Map<?, ?> backText = (Map<?, ?>) entry.get("back_text");
        assertEquals((byte) 0, backText.get("has_glowing_text"));
    }

    @Test
    void captureUnCraneCustomProduitLeProfil() {
        ResolvableProfile profile = mock(ResolvableProfile.class);
        when(profile.name()).thenReturn("Steve");
        when(profile.properties()).thenReturn(Set.of(new ProfileProperty("textures", "valeur", "signature")));

        Skull skull = mock(Skull.class);
        when(skull.getProfile()).thenReturn(profile);

        Map<String, Object> entry = BlockEntityIO.capture(skull, 0, 1, 0);
        assertEquals("minecraft:skull", entry.get("Id"));

        Map<?, ?> serialized = (Map<?, ?>) entry.get("profile");
        assertEquals("Steve", serialized.get("name"));
        Map<?, ?> property = (Map<?, ?>) ((List<?>) serialized.get("properties")).get(0);
        assertEquals("textures", property.get("name"));
        assertEquals("valeur", property.get("value"));
        assertEquals("signature", property.get("signature"));
    }

    @Test
    void captureUnCraneSansProfilRetourneNull() {
        Skull skull = mock(Skull.class);
        when(skull.getProfile()).thenReturn(null);
        assertNull(BlockEntityIO.capture(skull, 0, 0, 0));

        ResolvableProfile emptyProfile = mock(ResolvableProfile.class);
        when(emptyProfile.name()).thenReturn(null);
        when(emptyProfile.properties()).thenReturn(Set.of());
        when(skull.getProfile()).thenReturn(emptyProfile);
        assertNull(BlockEntityIO.capture(skull, 0, 0, 0));
    }

    @Test
    void captureUnCraneAvecNomSeulConserveLeProfilNonResolu() {
        ResolvableProfile profile = mock(ResolvableProfile.class);
        when(profile.name()).thenReturn("Notch");
        when(profile.properties()).thenReturn(Set.of());

        Skull skull = mock(Skull.class);
        when(skull.getProfile()).thenReturn(profile);

        Map<String, Object> entry = BlockEntityIO.capture(skull, 0, 0, 0);
        assertEquals("minecraft:skull", entry.get("Id"));
        Map<?, ?> serialized = (Map<?, ?>) entry.get("profile");
        assertEquals("Notch", serialized.get("name"));
        assertFalse(serialized.containsKey("properties"));
    }

    @Test
    @SuppressWarnings("removal") // PatternType n'expose aucun accesseur non déprécié en 26.2
    void captureUneBanniereProduitBaseEtMotifs() {
        Banner banner = mock(Banner.class);
        when(banner.getBaseColor()).thenReturn(DyeColor.BLUE);
        when(banner.getPatterns()).thenReturn(List.of(new Pattern(DyeColor.ORANGE, PatternType.BASE)));

        Map<String, Object> entry = BlockEntityIO.capture(banner, 1, 2, 3);
        assertEquals("minecraft:banner", entry.get("Id"));
        assertEquals("blue", entry.get("base_color"));

        Map<?, ?> motif = (Map<?, ?>) ((List<?>) entry.get("patterns")).get(0);
        // PatternType n'expose aucun accesseur non déprécié en 26.2
        assertEquals(PatternType.BASE.key().toString(), motif.get("pattern"));
        assertEquals("orange", motif.get("color"));
    }

    @Test
    void captureUnSpawnerProduitLEntite() {
        CreatureSpawner spawner = mock(CreatureSpawner.class);
        when(spawner.getSpawnedType()).thenReturn(EntityType.ZOMBIE);

        Map<String, Object> entry = BlockEntityIO.capture(spawner, 0, 0, 0);
        assertEquals("minecraft:spawner", entry.get("Id"));
        Map<?, ?> spawnData = (Map<?, ?>) entry.get("spawn_data");
        Map<?, ?> entity = (Map<?, ?>) spawnData.get("entity");
        assertEquals("minecraft:zombie", entity.get("id"));
    }

    @Test
    void captureUnCoffreAvecObjetsSerialiseLesStacks() {
        ItemStack item = mock(ItemStack.class);
        when(item.getType()).thenReturn(Material.STONE);
        when(item.serializeAsBytes()).thenReturn(new byte[]{1, 2, 3});

        Inventory inventory = mock(Inventory.class);
        when(inventory.getSize()).thenReturn(2);
        when(inventory.getItem(0)).thenReturn(item);
        when(inventory.getItem(1)).thenReturn(null);

        Chest chest = mock(Chest.class);
        when(chest.getInventory()).thenReturn(inventory);

        Map<String, Object> entry = BlockEntityIO.capture(chest, 0, 0, 0);
        assertEquals("minecraft:chest", entry.get("Id"));

        List<?> items = (List<?>) entry.get(BlockEntityIO.ITEMS_KEY);
        assertEquals(1, items.size());
        Map<?, ?> slot0 = (Map<?, ?>) items.get(0);
        assertEquals((byte) 0, slot0.get("Slot"));
        assertArrayEquals(new byte[]{1, 2, 3}, (byte[]) slot0.get("Data"));
    }

    @Test
    void captureUnConteneurVideRetourneNull() {
        Inventory inventory = mock(Inventory.class);
        when(inventory.getSize()).thenReturn(1);
        when(inventory.getItem(0)).thenReturn(null);

        Chest chest = mock(Chest.class);
        when(chest.getInventory()).thenReturn(inventory);
        assertNull(BlockEntityIO.capture(chest, 0, 0, 0));
    }

    @Test
    void captureUnFeuDeCampAvecUnObjetLeConserve() {
        ItemStack item = mock(ItemStack.class);
        when(item.getType()).thenReturn(Material.BAKED_POTATO);
        when(item.serializeAsBytes()).thenReturn(new byte[]{7});

        Campfire campfire = mock(Campfire.class);
        when(campfire.getSize()).thenReturn(1);
        when(campfire.getItem(0)).thenReturn(item);

        Map<String, Object> entry = BlockEntityIO.capture(campfire, 0, 0, 0);
        assertEquals("minecraft:campfire", entry.get("Id"));
        assertEquals(1, ((List<?>) entry.get(BlockEntityIO.ITEMS_KEY)).size());
    }

    @Test
    void captureUnJukeboxSansDisqueRetourneNull() {
        Jukebox jukebox = mock(Jukebox.class);
        when(jukebox.getRecord()).thenReturn(null);
        assertNull(BlockEntityIO.capture(jukebox, 0, 0, 0));
    }

    @Test
    @SuppressWarnings("deprecation") // Sign.setColor() : pas d'alternative non-dépréciée
    void appliqueUnePancarteSurLeBlocColle() {
        World world = mock(World.class);
        Block block = mock(Block.class);
        Sign sign = mock(Sign.class);
        SignSide front = mock(SignSide.class);
        when(world.getBlockAt(11, 66, -2)).thenReturn(block);
        when(block.getState()).thenReturn(sign);
        when(sign.getSide(Side.FRONT)).thenReturn(front);
        when(sign.getSide(Side.BACK)).thenReturn(mock(SignSide.class));

        Map<String, Object> frontText = new LinkedHashMap<>();
        frontText.put("messages", List.of("{\"text\":\"Salut\"}", "", "", ""));
        frontText.put("has_glowing_text", (byte) 1);
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("Id", "minecraft:sign");
        entry.put("Pos", List.of(1, 2, 3));
        entry.put("front_text", frontText);
        entry.put("color", "red");

        BlockEntityIO.apply(world, 10, 64, -5, entry);

        verify(front).line(0, Component.text("Salut"));
        verify(front).line(1, Component.empty());
        verify(front).line(2, Component.empty());
        verify(front).line(3, Component.empty());
        verify(front).setGlowingText(true);
        verify(sign).setColor(DyeColor.RED);
        verify(sign).update(true, false);
    }

    @Test
    void appliqueUnCraneAvecProfilModerne() {
        World world = mock(World.class);
        Block block = mock(Block.class);
        Skull skull = mock(Skull.class);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(block);
        when(block.getState()).thenReturn(skull);

        ResolvableProfile.Builder builder = mock(ResolvableProfile.Builder.class);
        ResolvableProfile builtProfile = mock(ResolvableProfile.class);
        when(builtProfile.properties()).thenReturn(Set.of(new ProfileProperty("textures", "valeur", "sig")));
        when(builtProfile.name()).thenReturn("Steve");
        when(builder.name(anyString())).thenReturn(builder);
        when(builder.uuid(any(UUID.class))).thenReturn(builder);
        when(builder.addProperty(any(ProfileProperty.class))).thenReturn(builder);
        when(builder.build()).thenReturn(builtProfile);

        try (MockedStatic<ResolvableProfile> rs = mockStatic(ResolvableProfile.class)) {
            rs.when(ResolvableProfile::resolvableProfile).thenReturn(builder);

            Map<String, Object> property = new LinkedHashMap<>();
            property.put("name", "textures");
            property.put("value", "valeur");
            property.put("signature", "sig");
            Map<String, Object> profileMap = new LinkedHashMap<>();
            profileMap.put("name", "Steve");
            profileMap.put("properties", List.of(property));
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("Id", "minecraft:skull");
            entry.put("Pos", List.of(0, 0, 0));
            entry.put("profile", profileMap);

            BlockEntityIO.apply(world, 5, 64, 5, entry);

            verify(skull).setProfile(builtProfile);
            verify(skull).update(true, false);
        }
    }

    @Test
    void appliqueUnCraneLegacySkullOwner() {
        World world = mock(World.class);
        Block block = mock(Block.class);
        Skull skull = mock(Skull.class);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(block);
        when(block.getState()).thenReturn(skull);

        ResolvableProfile.Builder builder = mock(ResolvableProfile.Builder.class);
        ResolvableProfile builtProfile = mock(ResolvableProfile.class);
        when(builtProfile.properties()).thenReturn(Set.of(new ProfileProperty("textures", "valeur2", null)));
        when(builder.name(any())).thenReturn(builder);
        when(builder.uuid(any(UUID.class))).thenReturn(builder);
        when(builder.addProperty(any(ProfileProperty.class))).thenReturn(builder);
        when(builder.build()).thenReturn(builtProfile);

        try (MockedStatic<ResolvableProfile> rs = mockStatic(ResolvableProfile.class)) {
            rs.when(ResolvableProfile::resolvableProfile).thenReturn(builder);

            Map<String, Object> texture = new LinkedHashMap<>();
            texture.put("Value", "valeur2");
            texture.put("Signature", "sig2");
            Map<String, Object> legacyProperties = new LinkedHashMap<>();
            legacyProperties.put("textures", List.of(texture));
            Map<String, Object> skullOwner = new LinkedHashMap<>();
            skullOwner.put("Properties", legacyProperties);
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("Id", "minecraft:skull");
            entry.put("Pos", List.of(0, 0, 0));
            entry.put("SkullOwner", skullOwner);

            BlockEntityIO.apply(world, 0, 64, 0, entry);

            verify(skull).setProfile(builtProfile);
        }
    }

    @Test
    @SuppressWarnings("removal") // PatternType n'expose aucun accesseur non déprécié en 26.2
    void appliqueUneBanniere() {
        World world = mock(World.class);
        Block block = mock(Block.class);
        Banner banner = mock(Banner.class);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(block);
        when(block.getState()).thenReturn(banner);

        Map<String, Object> motif = new LinkedHashMap<>();
        motif.put("pattern", PatternType.BASE.key().toString());
        motif.put("color", "orange");
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("Id", "minecraft:banner");
        entry.put("Pos", List.of(0, 0, 0));
        entry.put("base_color", "blue");
        entry.put("patterns", List.of(motif));

        BlockEntityIO.apply(world, 0, 64, 0, entry);

        verify(banner).setBaseColor(DyeColor.BLUE);
        verify(banner).setPatterns(List.of(new Pattern(DyeColor.ORANGE, PatternType.BASE)));
        verify(banner).update(true, false);
    }

    @Test
    void appliqueUnSpawner() {
        World world = mock(World.class);
        Block block = mock(Block.class);
        CreatureSpawner spawner = mock(CreatureSpawner.class);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(block);
        when(block.getState()).thenReturn(spawner);

        Map<String, Object> entity = new LinkedHashMap<>();
        entity.put("id", "minecraft:zombie");
        Map<String, Object> spawnData = new LinkedHashMap<>();
        spawnData.put("entity", entity);
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("Id", "minecraft:spawner");
        entry.put("Pos", List.of(0, 0, 0));
        entry.put("spawn_data", spawnData);

        BlockEntityIO.apply(world, 0, 64, 0, entry);

        verify(spawner).setSpawnedType(EntityType.ZOMBIE);
        verify(spawner).update(true, false);
    }

    @Test
    void appliqueDesObjetsEnIgnorantLesSlotsHorsBornes() {
        World world = mock(World.class);
        Block block = mock(Block.class);
        Chest chest = mock(Chest.class);
        Inventory inventory = mock(Inventory.class);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(block);
        when(block.getState()).thenReturn(chest);
        when(chest.getInventory()).thenReturn(inventory);
        when(inventory.getSize()).thenReturn(27);

        ItemStack decoded = mock(ItemStack.class);
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("Id", "minecraft:chest");
        entry.put("Pos", List.of(0, 0, 0));
        entry.put(BlockEntityIO.ITEMS_KEY, List.of(
                Map.of("Slot", (byte) 2, "Data", new byte[]{9}),
                Map.of("Slot", (byte) 30, "Data", new byte[]{9})));

        try (MockedStatic<ItemStack> items = mockStatic(ItemStack.class)) {
            items.when(() -> ItemStack.deserializeBytes(any(byte[].class))).thenReturn(decoded);
            BlockEntityIO.apply(world, 0, 64, 0, entry);
        }

        verify(inventory).setItem(2, decoded);
        verify(inventory, never()).setItem(eq(30), any());
        verify(chest).update(true, false);
    }

    @Test
    void uneEntreeSansPosEstIgnoree() {
        World world = mock(World.class);
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("Id", "minecraft:sign");

        BlockEntityIO.apply(world, 0, 64, 0, entry);

        verifyNoInteractions(world);
    }

    @Test
    void unPosEnTableauIntEstAccepte() {
        World world = mock(World.class);
        Block block = mock(Block.class);
        when(world.getBlockAt(11, 66, -2)).thenReturn(block);
        when(block.getState()).thenReturn(mock(BlockState.class));

        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("Id", "minecraft:sign");
        entry.put("Pos", new int[]{1, 2, 3});

        BlockEntityIO.apply(world, 10, 64, -5, entry);

        verify(world).getBlockAt(11, 66, -2);
    }

    @Test
    void unBlocColleDifferentsDeLEntreeEstIgnore() {
        World world = mock(World.class);
        Block block = mock(Block.class);
        BlockState state = mock(BlockState.class);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(block);
        when(block.getState()).thenReturn(state);

        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("Id", "minecraft:sign");
        entry.put("Pos", List.of(0, 0, 0));

        BlockEntityIO.apply(world, 0, 64, 0, entry);

        verify(state, never()).update(anyBoolean(), anyBoolean());
    }

    @Test
    void uneExceptionDuMondeEstIgnoree() {
        World world = mock(World.class);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt()))
                .thenThrow(new RuntimeException("chunk non chargé"));

        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("Id", "minecraft:sign");
        entry.put("Pos", List.of(0, 0, 0));

        BlockEntityIO.apply(world, 0, 64, 0, entry); // ne doit pas lever
    }

    // === Entity capture tests (ItemFrame / Painting) ===

    @Test
    void captureEntitiesRetourneVideSiAucuneEntite() {
        World world = mock(World.class);
        when(world.getEntitiesByClass(ItemFrame.class)).thenReturn(List.of());
        when(world.getEntitiesByClass(Painting.class)).thenReturn(List.of());

        List<Map<String, Object>> result = BlockEntityIO.captureEntities(world, 0, 64, 0, 10, 10, 10);

        assertEquals(0, result.size());
    }

    @Test
    void captureEntitiesCaptureUnItemFrameDansLaRegion() {
        World world = mock(World.class);
        ItemFrame frame = mock(ItemFrame.class);
        org.bukkit.block.Block locationBlock = mock(org.bukkit.block.Block.class);
        org.bukkit.block.Block attached = mock(org.bukkit.block.Block.class);
        Location frameLoc = mock(Location.class);
        when(frame.getLocation()).thenReturn(frameLoc);
        when(frameLoc.getBlock()).thenReturn(locationBlock);
        // NORTH → face.getModZ() < 0 → getRelative(SOUTH) = bloc attaché
        when(locationBlock.getRelative(BlockFace.SOUTH)).thenReturn(attached);
        when(attached.getX()).thenReturn(5);
        when(attached.getY()).thenReturn(68);
        when(attached.getZ()).thenReturn(5);
        when(frame.getItem()).thenReturn(mock(ItemStack.class));
        when(frame.getRotation()).thenReturn(org.bukkit.Rotation.CLOCKWISE);
        when(((Attachable) frame).getAttachedFace()).thenReturn(BlockFace.NORTH);
        when(world.getEntitiesByClass(ItemFrame.class)).thenReturn(List.of(frame));
        when(world.getEntitiesByClass(Painting.class)).thenReturn(List.of());

        List<Map<String, Object>> result = BlockEntityIO.captureEntities(world, 0, 64, 0, 10, 10, 10);

        assertEquals(1, result.size());
        assertEquals("minecraft:item_frame", result.get(0).get("Id"));
        assertEquals(List.of(5, 4, 5), result.get(0).get("Pos"));
        assertEquals("clockwise", result.get(0).get("rotation"));
        assertEquals("NORTH", result.get(0).get("facing"));
    }

    @Test
    void captureEntitiesIgnoreUnItemFrameHorsRegion() {
        World world = mock(World.class);
        ItemFrame frame = mock(ItemFrame.class);
        when(frame.getX()).thenReturn(15.0); // hors région (0..9)
        when(frame.getY()).thenReturn(68.0);
        when(frame.getZ()).thenReturn(5.0);
        when(world.getEntitiesByClass(ItemFrame.class)).thenReturn(List.of(frame));
        when(world.getEntitiesByClass(Painting.class)).thenReturn(List.of());

        List<Map<String, Object>> result = BlockEntityIO.captureEntities(world, 0, 64, 0, 10, 10, 10);

        assertEquals(0, result.size());
    }

    @Test
    void captureEntitiesEstTolerantAuxExceptions() {
        World world = mock(World.class);
        when(world.getEntitiesByClass(ItemFrame.class)).thenThrow(new RuntimeException("mock"));

        List<Map<String, Object>> result = BlockEntityIO.captureEntities(world, 0, 64, 0, 10, 10, 10);

        assertEquals(0, result.size()); // pas d'exception levée
    }

    // === Entity apply tests ===

    @Test
    void applyEntitySpawnUnItemFrameAvecRotationEtFacing() {
        World world = mock(World.class);
        ItemFrame frame = mock(ItemFrame.class);
        when(world.spawnEntity(any(Location.class), eq(EntityType.ITEM_FRAME))).thenReturn(frame);

        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("Id", "minecraft:item_frame");
        entry.put("Pos", List.of(2, 3, 4));
        entry.put("rotation", "counter_clockwise");
        entry.put("facing", "SOUTH");

        BlockEntityIO.applyEntity(world, 10, 64, 10, entry);

        // Spawn dans le bloc d'air devant le mur (Pos + facing), pas dans le bloc solide.
        verify(world).spawnEntity(eq(new Location(world, 12.5, 67.5, 15.5)), eq(EntityType.ITEM_FRAME));
        verify(frame).setRotation(org.bukkit.Rotation.COUNTER_CLOCKWISE);
        verify(frame).setFacingDirection(BlockFace.SOUTH, true);
    }

    @Test
    void applyEntitySansFacingSpawnAuCentreDuBloc() {
        World world = mock(World.class);
        ItemFrame frame = mock(ItemFrame.class);
        when(world.spawnEntity(any(Location.class), eq(EntityType.ITEM_FRAME))).thenReturn(frame);

        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("Id", "minecraft:item_frame");
        entry.put("Pos", List.of(2, 3, 4));

        BlockEntityIO.applyEntity(world, 10, 64, 10, entry);

        verify(world).spawnEntity(eq(new Location(world, 12.5, 67.5, 14.5)), eq(EntityType.ITEM_FRAME));
    }

    @Test
    void applyEntityIgnoreUneEntiteInconnue() {
        World world = mock(World.class);

        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("Id", "minecraft:cow");
        entry.put("Pos", List.of(0, 0, 0));

        BlockEntityIO.applyEntity(world, 0, 64, 0, entry);

        verify(world, never()).spawnEntity(any(Location.class), any(EntityType.class));
    }

    @Test
    void applyEntityIgnoreUneEntreeSansPos() {
        World world = mock(World.class);

        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("Id", "minecraft:item_frame");

        BlockEntityIO.applyEntity(world, 0, 64, 0, entry);

        verify(world, never()).spawnEntity(any(Location.class), any(EntityType.class));
    }
}

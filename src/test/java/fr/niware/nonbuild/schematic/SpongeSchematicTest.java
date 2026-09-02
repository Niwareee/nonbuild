package fr.niware.nonbuild.schematic;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpongeSchematicTest {

    @TempDir
    Path tempDir;

    private SpongeSchematic buildSchematic(int width, int height, int length, int paletteSize) {
        List<String> palette = new ArrayList<>();
        for (int i = 0; i < paletteSize; i++) {
            palette.add("minecraft:block_" + i);
        }
        int volume = width * height * length;
        int[] indices = new int[volume];
        for (int i = 0; i < volume; i++) {
            indices[i] = i % paletteSize;
        }
        return SpongeSchematic.create(width, height, length, indices, palette);
    }

    @Test
    void roundTripConserveTousLesBlocs() throws IOException {
        int w = 5, h = 4, l = 6;
        SpongeSchematic original = buildSchematic(w, h, l, 10);
        File file = tempDir.resolve("arena.schem").toFile();
        original.write(file);

        SpongeSchematic back = SpongeSchematic.read(file);
        assertEquals(w, back.getWidth());
        assertEquals(h, back.getHeight());
        assertEquals(l, back.getLength());
        assertEquals(original.volume(), back.volume());

        for (int y = 0; y < h; y++) {
            for (int z = 0; z < l; z++) {
                for (int x = 0; x < w; x++) {
                    assertEquals(original.stateAt(x, y, z), back.stateAt(x, y, z),
                            "état différent en " + x + "," + y + "," + z);
                }
            }
        }
    }

    @Test
    void varintMultiOctetsAvecGrossePalette() throws IOException {
        // 300 états distincts => des indices >= 128 encodés sur 2 octets en varint
        SpongeSchematic original = buildSchematic(7, 3, 9, 300);
        File file = tempDir.resolve("big.schem").toFile();
        original.write(file);

        SpongeSchematic back = SpongeSchematic.read(file);
        for (int y = 0; y < 3; y++) {
            for (int z = 0; z < 9; z++) {
                for (int x = 0; x < 7; x++) {
                    assertEquals(original.stateAt(x, y, z), back.stateAt(x, y, z));
                }
            }
        }
    }

    @Test
    void roundTripConserveLesBlockEntities() throws IOException {
        Map<String, Object> frontText = new LinkedHashMap<>();
        frontText.put("messages", List.of("{\"text\":\"Salut\"}", "", "", ""));
        frontText.put("has_glowing_text", (byte) 1);
        Map<String, Object> sign = new LinkedHashMap<>();
        sign.put("Id", "minecraft:sign");
        sign.put("Pos", List.of(1, 2, 3));
        sign.put("front_text", frontText);

        SpongeSchematic original = SpongeSchematic.create(2, 1, 1,
                new int[]{0, 0}, List.of("minecraft:oak_sign"), List.of(sign));
        File file = tempDir.resolve("be.schem").toFile();
        original.write(file);

        SpongeSchematic back = SpongeSchematic.read(file);
        assertEquals(1, back.getBlockEntities().size());
        Map<String, Object> entry = back.getBlockEntities().get(0);
        assertEquals("minecraft:sign", entry.get("Id"));
        assertEquals(List.of(1, 2, 3), entry.get("Pos"));
        Map<?, ?> front = (Map<?, ?>) entry.get("front_text");
        assertEquals((byte) 1, front.get("has_glowing_text"));
        assertEquals("{\"text\":\"Salut\"}", ((List<?>) front.get("messages")).get(0));
    }

    @Test
    void sansBlockEntitiesLaListeEstVide() throws IOException {
        SpongeSchematic original = buildSchematic(1, 1, 1, 1);
        File file = tempDir.resolve("noblocks.schem").toFile();
        original.write(file);

        SpongeSchematic back = SpongeSchematic.read(file);
        assertTrue(back.getBlockEntities().isEmpty());
    }

    @Test
    void lesBlockEntitiesV3ImbriqueesSousDataSontAplatis() throws IOException {
        File file = tempDir.resolve("bev3.schem").toFile();
        Map<String, Object> palette = new LinkedHashMap<>();
        palette.put("minecraft:air", 0);
        Map<String, Object> blocks = new LinkedHashMap<>();
        blocks.put("Palette", palette);
        blocks.put("Data", new byte[]{0});
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("has_glowing_text", (byte) 1);
        Map<String, Object> entity = new LinkedHashMap<>();
        entity.put("Id", "minecraft:sign");
        entity.put("Pos", new int[]{0, 1, 0});
        entity.put("Data", data);
        Map<String, Object> schematic = new LinkedHashMap<>();
        schematic.put("Version", 3);
        schematic.put("Width", (short) 1);
        schematic.put("Height", (short) 1);
        schematic.put("Length", (short) 1);
        schematic.put("Blocks", blocks);
        schematic.put("BlockEntities", List.of(entity));
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("Schematic", schematic);
        write(root, file);

        SpongeSchematic back = SpongeSchematic.read(file);
        assertEquals(1, back.getBlockEntities().size());
        Map<String, Object> entry = back.getBlockEntities().get(0);
        assertEquals("minecraft:sign", entry.get("Id"));
        assertFalse(entry.containsKey("Data"));
        assertEquals((byte) 1, entry.get("has_glowing_text"));
    }

    @Test
    void indexAtSuitLOrdreSponge() {
        SpongeSchematic schematic = buildSchematic(4, 3, 5, 2);
        for (int y = 0; y < 3; y++) {
            for (int z = 0; z < 5; z++) {
                for (int x = 0; x < 4; x++) {
                    assertEquals((y * 5L + z) * 4 + x, schematic.indexAt(x, y, z));
                }
            }
        }
    }

    @Test
    void rejetteUneVersion1() throws IOException {
        File file = tempDir.resolve("v1.schem").toFile();
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("Version", 1);
        root.put("Width", (short) 1);
        root.put("Height", (short) 1);
        root.put("Length", (short) 1);
        root.put("BlockStatePalette", Map.of("0", "minecraft:air"));
        root.put("BlockData", new byte[]{0});
        write(root, file);

        IOException e = assertThrows(IOException.class, () -> SpongeSchematic.read(file));
        assertTrue(e.getMessage().contains("version 1"));
    }

    @Test
    void litUnSchematicV3WorldEdit() throws IOException {
        File file = tempDir.resolve("v3.schem").toFile();
        Map<String, Object> palette = new LinkedHashMap<>();
        palette.put("minecraft:air", 0);
        palette.put("minecraft:glass", 1);
        palette.put("minecraft:stone[facing=north]", 2);
        Map<String, Object> blocks = new LinkedHashMap<>();
        blocks.put("Palette", palette);
        blocks.put("Data", new byte[]{2, 1, 0, 2});
        Map<String, Object> worldEdit = new LinkedHashMap<>();
        worldEdit.put("Version", "2.15.4");
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("Date", 1788120027409L);
        metadata.put("WorldEdit", worldEdit);
        Map<String, Object> schematic = new LinkedHashMap<>();
        schematic.put("Version", 3);
        schematic.put("DataVersion", 4903);
        schematic.put("Metadata", metadata);
        schematic.put("Width", (short) 2);
        schematic.put("Height", (short) 1);
        schematic.put("Length", (short) 2);
        schematic.put("Offset", new int[]{-178, -132, -169});
        schematic.put("Blocks", blocks);
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("Schematic", schematic);
        write(root, file);

        SpongeSchematic back = SpongeSchematic.read(file);
        assertEquals(2, back.getWidth());
        assertEquals(1, back.getHeight());
        assertEquals(2, back.getLength());
        assertArrayEquals(new int[]{-178, -132, -169}, back.getOffset());
        assertEquals("minecraft:stone[facing=north]", back.stateAt(0, 0, 0));
        assertEquals("minecraft:glass", back.stateAt(1, 0, 0));
        assertEquals("minecraft:air", back.stateAt(0, 0, 1));
        assertEquals("minecraft:stone[facing=north]", back.stateAt(1, 0, 1));
    }

    @Test
    void rejetteDesDimensionsInvalides() throws IOException {
        File file = tempDir.resolve("dim.schem").toFile();
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("Version", 2);
        root.put("Width", (short) 0);
        root.put("Height", (short) 1);
        root.put("Length", (short) 1);
        root.put("BlockStatePalette", Map.of("0", "minecraft:air"));
        root.put("BlockData", new byte[]{0});
        write(root, file);

        IOException e = assertThrows(IOException.class, () -> SpongeSchematic.read(file));
        assertTrue(e.getMessage().contains("Dimensions"));
    }

    @Test
    void rejetteUnePaletteManquante() throws IOException {
        File file = tempDir.resolve("nopal.schem").toFile();
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("Version", 2);
        root.put("Width", (short) 1);
        root.put("Height", (short) 1);
        root.put("Length", (short) 1);
        root.put("BlockData", new byte[]{0});
        write(root, file);

        IOException e = assertThrows(IOException.class, () -> SpongeSchematic.read(file));
        assertTrue(e.getMessage().contains("BlockStatePalette"));
    }

    @Test
    void rejetteUnBlockDataTronque() throws IOException {
        File file = tempDir.resolve("trunc.schem").toFile();
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("Version", 2);
        root.put("Width", (short) 2);
        root.put("Height", (short) 2);
        root.put("Length", (short) 2); // volume 8
        root.put("BlockStatePalette", Map.of("0", "minecraft:air"));
        root.put("BlockData", new byte[]{0, 0}); // seulement 2 varints
        write(root, file);

        IOException e = assertThrows(IOException.class, () -> SpongeSchematic.read(file));
        assertTrue(e.getMessage().contains("incomplet"));
    }

    @Test
    void writeVersUnCheminImpossibleEchoue() throws IOException {
        File obstacle = tempDir.resolve("obstacle").toFile();
        assertTrue(obstacle.createNewFile());
        File target = new File(obstacle, "sous/arena.schem");

        SpongeSchematic schematic = buildSchematic(1, 1, 1, 1);
        IOException e = assertThrows(IOException.class, () -> schematic.write(target));
        assertTrue(e.getMessage().contains("Impossible de créer le dossier"));
    }

    @Test
    void rejetteUnBlockDataManquant() throws IOException {
        File file = tempDir.resolve("nodata.schem").toFile();
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("Version", 2);
        root.put("Width", (short) 1);
        root.put("Height", (short) 1);
        root.put("Length", (short) 1);
        root.put("BlockStatePalette", Map.of("0", "minecraft:air"));
        write(root, file);

        IOException e = assertThrows(IOException.class, () -> SpongeSchematic.read(file));
        assertTrue(e.getMessage().contains("BlockData"));
    }

    @Test
    void rejetteUnBlockDataTropGrand() throws IOException {
        File file = tempDir.resolve("toobig.schem").toFile();
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("Version", 2);
        root.put("Width", (short) 2);
        root.put("Height", (short) 1);
        root.put("Length", (short) 1); // volume 2
        root.put("BlockStatePalette", Map.of("0", "minecraft:air"));
        root.put("BlockData", new byte[]{0, 0, 0}); // 3 varints pour 2 blocs
        write(root, file);

        IOException e = assertThrows(IOException.class, () -> SpongeSchematic.read(file));
        assertTrue(e.getMessage().contains("trop grand"));
    }

    @Test
    void rejetteUnVarintCorrompu() throws IOException {
        File file = tempDir.resolve("corrupt.schem").toFile();
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("Version", 2);
        root.put("Width", (short) 1);
        root.put("Height", (short) 1);
        root.put("Length", (short) 1);
        root.put("BlockStatePalette", Map.of("0", "minecraft:air"));
        root.put("BlockData", new byte[]{(byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80});
        write(root, file);

        IOException e = assertThrows(IOException.class, () -> SpongeSchematic.read(file));
        assertTrue(e.getMessage().contains("corrompu"));
    }

    @Test
    void rejetteDesDimensionsNonNumeriques() throws IOException {
        File file = tempDir.resolve("baddim.schem").toFile();
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("Version", 2);
        root.put("Width", "pas un nombre");
        root.put("Height", (short) 1);
        root.put("Length", (short) 1);
        root.put("BlockStatePalette", Map.of("0", "minecraft:air"));
        root.put("BlockData", new byte[]{0});
        write(root, file);

        IOException e = assertThrows(IOException.class, () -> SpongeSchematic.read(file));
        assertTrue(e.getMessage().contains("Dimensions"));
    }

    @Test
    void litLOffsetEtIgnoreLesTagsWorldEditSuperflus() throws IOException {
        File file = tempDir.resolve("worldedit.schem").toFile();
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("Version", 2);
        root.put("DataVersion", 3955);
        root.put("Width", (short) 2);
        root.put("Height", (short) 2);
        root.put("Length", (short) 2);
        root.put("Offset", new int[]{-3, -1, 5});
        root.put("PaletteMax", 1);
        root.put("BlockStatePalette", Map.of("0", "minecraft:stone"));
        root.put("BlockData", new byte[8]);
        Map<String, Object> blockEntity = new LinkedHashMap<>();
        blockEntity.put("Id", "minecraft:sign");
        blockEntity.put("Pos", new int[]{0, 1, 0});
        root.put("BlockEntities", List.of(blockEntity));
        write(root, file);

        SpongeSchematic back = SpongeSchematic.read(file);
        assertEquals(2, back.getWidth());
        assertArrayEquals(new int[]{-3, -1, 5}, back.getOffset());
        assertEquals("minecraft:stone", back.stateAt(1, 1, 1));
    }

    @Test
    void offsetVautZeroSansTagOffset() throws IOException {
        SpongeSchematic original = buildSchematic(2, 2, 2, 1);
        File file = tempDir.resolve("nooffset.schem").toFile();
        original.write(file);

        SpongeSchematic back = SpongeSchematic.read(file);
        assertArrayEquals(new int[]{0, 0, 0}, back.getOffset());
    }

    private void write(Map<String, Object> root, File file) throws IOException {
        try (FileOutputStream out = new FileOutputStream(file)) {
            Nbt.writeCompressed(root, out);
        }
    }
}

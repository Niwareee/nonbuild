package fr.niware.nonbuild.schematic;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Schematic au format Sponge ("schematic v2", extension .schem), le format
 * utilisé par WorldEdit. Lecture et écriture sans dépendance externe.
 *
 * Convention : les blocs sont stockés en coordonnées relatives à partir du
 * coin minimum (Offset = 0), dans l'ordre x rapide puis z puis y :
 * index = (y * length + z) * width + x.
 *
 * Les block entities (pancartes, crânes custom, bannières, conteneurs...)
 * sont portés par la clé BlockEntities (format Sponge : Id + Pos + données,
 * position relative au coin min). À la lecture, le format v3 (données
 * imbriquées sous Data) est normalisé vers la forme plate de la v2.
 */
public final class SpongeSchematic {

    private final int width;
    private final int height;
    private final int length;
    private final int[] indices;
    private final String[] palette;
    /** Coin min relatif à la position de copie (convention WorldEdit : Offset = min − copie). */
    private final int[] offset;
    private final List<Map<String, Object>> blockEntities;

    private SpongeSchematic(int width, int height, int length, int[] indices, String[] palette,
                            int[] offset, List<Map<String, Object>> blockEntities) {
        this.width = width;
        this.height = height;
        this.length = length;
        this.indices = indices;
        this.palette = palette;
        this.offset = offset;
        this.blockEntities = blockEntities;
    }

    public static SpongeSchematic create(int width, int height, int length, int[] indices, List<String> palette) {
        return create(width, height, length, indices, palette, List.of());
    }

    public static SpongeSchematic create(int width, int height, int length, int[] indices,
                                         List<String> palette, List<Map<String, Object>> blockEntities) {
        return new SpongeSchematic(width, height, length, indices, palette.toArray(new String[0]),
                new int[]{0, 0, 0}, List.copyOf(blockEntities));
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getLength() {
        return length;
    }

    public int[] getOffset() {
        return offset.clone();
    }

    public List<Map<String, Object>> getBlockEntities() {
        return blockEntities;
    }

    public long volume() {
        return (long) width * height * length;
    }

    public int indexAt(int x, int y, int z) {
        return (y * length + z) * width + x;
    }

    public String stateAt(int x, int y, int z) {
        return palette[indices[indexAt(x, y, z)]];
    }

    public int paletteSize() {
        return palette.length;
    }

    public String paletteStateAt(int paletteIndex) {
        return palette[paletteIndex];
    }

    public int paletteIndexAt(int x, int y, int z) {
        return indices[indexAt(x, y, z)];
    }

    public void write(File file) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Impossible de créer le dossier " + parent);
        }
        try (FileOutputStream out = new FileOutputStream(file)) {
            Nbt.writeCompressed(buildRoot(), out);
        }
    }

    /**
     * Serialize this schematic to a compressed byte array (NBT).
     */
    public byte[] toBytes() throws IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Nbt.writeCompressed(buildRoot(), out);
            return out.toByteArray();
        }
    }

    /**
     * Write this schematic to an output stream (compressed NBT).
     */
    public void write(OutputStream out) throws IOException {
        Nbt.writeCompressed(buildRoot(), out);
    }

    public static SpongeSchematic read(File file) throws IOException {
        try (FileInputStream in = new FileInputStream(file)) {
            return read(in);
        }
    }

    /**
     * Read a schematic from a byte array.
     */
    public static SpongeSchematic read(byte[] data) throws IOException {
        try (ByteArrayInputStream in = new ByteArrayInputStream(data)) {
            return read(in);
        }
    }

    /**
     * Read a schematic from an input stream.
     */
    public static SpongeSchematic read(InputStream in) throws IOException {
        Map<String, Object> root;
        try {
            root = Nbt.readCompressed(in);
        } finally {
            in.close();
        }

        Object wrapped = root.get("Schematic");
        if (wrapped instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> schematic = (Map<String, Object>) wrapped;
            root = schematic;
        }

        int version = asInt(root.get("Version"), -1);
        if (version < 2) {
            throw new IOException("Format non supporté (version " + version + ")");
        }

        int width = asInt(root.get("Width"), -1);
        int height = asInt(root.get("Height"), -1);
        int length = asInt(root.get("Length"), -1);
        if (width <= 0 || height <= 0 || length <= 0) {
            throw new IOException("Dimensions invalides");
        }

        String[] states;
        byte[] data;
        if (version >= 3) {
            if (!(root.get("Blocks") instanceof Map)) {
                throw new IOException("Compound Blocks manquant");
            }
            Map<?, ?> blocks = (Map<?, ?>) root.get("Blocks");

            if (!(blocks.get("Palette") instanceof Map)) {
                throw new IOException("Blocks/Palette manquant");
            }
            Map<?, ?> paletteCompound = (Map<?, ?>) blocks.get("Palette");
            int maxId = -1;
            for (Object value : paletteCompound.values()) {
                maxId = Math.max(maxId, asInt(value, -1));
            }
            if (maxId < 0) {
                throw new IOException("Blocks/Palette vide");
            }
            states = new String[maxId + 1];
            for (Map.Entry<?, ?> entry : paletteCompound.entrySet()) {
                int id = asInt(entry.getValue(), -1);
                if (id < 0) {
                    throw new IOException("Palette invalide (id " + entry.getValue() + ")");
                }
                states[id] = String.valueOf(entry.getKey());
            }

            if (!(blocks.get("Data") instanceof byte[])) {
                throw new IOException("Blocks/Data manquant");
            }
            data = (byte[]) blocks.get("Data");
        } else {
            Object paletteObj = root.get("BlockStatePalette");
            if (!(paletteObj instanceof Map)) {
                throw new IOException("BlockStatePalette manquant");
            }
            Map<?, ?> paletteCompound = (Map<?, ?>) paletteObj;
            int maxId = -1;
            for (Object key : paletteCompound.keySet()) {
                maxId = Math.max(maxId, Integer.parseInt(String.valueOf(key)));
            }
            states = new String[maxId + 1];
            for (Map.Entry<?, ?> entry : paletteCompound.entrySet()) {
                states[Integer.parseInt(String.valueOf(entry.getKey()))] = String.valueOf(entry.getValue());
            }

            Object dataObj = root.get("BlockData");
            if (!(dataObj instanceof byte[])) {
                throw new IOException("BlockData manquant");
            }
            data = (byte[]) dataObj;
        }

        long volume = (long) width * height * length;
        int[] indices = decodeVarInts(data, volume, "stream");

        Object offsetObj = root.get("Offset");
        int[] offset = {0, 0, 0};
        if (offsetObj instanceof int[] array && array.length == 3) {
            offset = array.clone();
        }

        return new SpongeSchematic(width, height, length, indices, states, offset, parseBlockEntities(root));
    }

    Map<String, Object> buildRoot() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("Version", 2);
        root.put("Width", (short) width);
        root.put("Height", (short) height);
        root.put("Length", (short) length);
        root.put("Offset", new int[]{0, 0, 0});
        root.put("PaletteMax", palette.length);

        Map<String, Object> paletteCompound = new LinkedHashMap<>();
        for (int i = 0; i < palette.length; i++) {
            paletteCompound.put(String.valueOf(i), palette[i]);
        }
        root.put("BlockStatePalette", paletteCompound);
        root.put("BlockData", encodeVarInts(indices));
        if (!blockEntities.isEmpty()) {
            root.put("BlockEntities", new ArrayList<Object>(blockEntities));
        }
        return root;
    }

    /**
     * Entrées plates (v2 : Id + Pos + données en vrac) ; la v3 imbrique les
     * données sous un compound Data, normalisé ici vers la forme plate.
     */
    private static List<Map<String, Object>> parseBlockEntities(Map<String, Object> root) {
        List<Map<String, Object>> entities = new ArrayList<>();
        if (!(root.get("BlockEntities") instanceof List<?> list)) {
            return entities;
        }
        for (Object raw : list) {
            if (!(raw instanceof Map<?, ?> compound)) {
                continue;
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            for (Map.Entry<?, ?> tag : compound.entrySet()) {
                entry.put(String.valueOf(tag.getKey()), tag.getValue());
            }
            if (entry.get("Data") instanceof Map<?, ?> data) {
                entry.remove("Data");
                for (Map.Entry<?, ?> tag : data.entrySet()) {
                    entry.put(String.valueOf(tag.getKey()), tag.getValue());
                }
            }
            entities.add(entry);
        }
        return entities;
    }

    private static byte[] encodeVarInts(int[] values) {
        byte[] buffer = new byte[values.length * 5];
        int size = 0;
        for (int value : values) {
            while ((value & ~0x7F) != 0) {
                buffer[size++] = (byte) ((value & 0x7F) | 0x80);
                value >>>= 7;
            }
            buffer[size++] = (byte) value;
        }
        byte[] out = new byte[size];
        System.arraycopy(buffer, 0, out, 0, size);
        return out;
    }

    private static int[] decodeVarInts(byte[] data, long expected, String fileName) throws IOException {
        int[] out = new int[(int) expected];
        int count = 0;
        int value = 0;
        int shift = 0;
        for (byte b : data) {
            value |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                if (count >= out.length) {
                    throw new IOException("BlockData trop grand dans " + fileName);
                }
                out[count++] = value;
                value = 0;
                shift = 0;
            } else {
                shift += 7;
                if (shift > 28) {
                    throw new IOException("Varint corrompu dans " + fileName);
                }
            }
        }
        if (count != expected) {
            throw new IOException("BlockData incomplet dans " + fileName + " (" + count + "/" + expected + ")");
        }
        return out;
    }

    private static int asInt(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return fallback;
    }
}

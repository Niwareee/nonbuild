package fr.niware.nonbuild.schematic;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Codec NBT minimal (lecture/écriture), suffisant pour le format schematic Sponge.
 * Représentation Java : compound = Map<String, Object>, list = List<Object>,
 * primitives boxed, tableaux byte[]/int[]/long[].
 */
public final class Nbt {

    public static final int END = 0;
    public static final int BYTE = 1;
    public static final int SHORT = 2;
    public static final int INT = 3;
    public static final int LONG = 4;
    public static final int FLOAT = 5;
    public static final int DOUBLE = 6;
    public static final int BYTE_ARRAY = 7;
    public static final int STRING = 8;
    public static final int LIST = 9;
    public static final int COMPOUND = 10;
    public static final int INT_ARRAY = 11;
    public static final int LONG_ARRAY = 12;

    private Nbt() {
    }

    public static Map<String, Object> readCompressed(InputStream in) throws IOException {
        try (DataInputStream data = new DataInputStream(new BufferedInputStream(new GZIPInputStream(in)))) {
            int type = data.readUnsignedByte();
            if (type != COMPOUND) {
                throw new IOException("Racine NBT compound attendue, trouvé le type " + type);
            }
            data.readUTF();
            return readCompound(data);
        }
    }

    public static void writeCompressed(Map<String, Object> compound, OutputStream out) throws IOException {
        try (DataOutputStream data = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(out)))) {
            data.writeByte(COMPOUND);
            data.writeUTF("");
            writeCompound(data, compound);
        }
    }

    private static Map<String, Object> readCompound(DataInputStream in) throws IOException {
        Map<String, Object> map = new LinkedHashMap<>();
        while (true) {
            int type = in.readUnsignedByte();
            if (type == END) {
                return map;
            }
            String name = in.readUTF();
            map.put(name, readPayload(in, type));
        }
    }

    private static Object readPayload(DataInputStream in, int type) throws IOException {
        return switch (type) {
            case BYTE -> in.readByte();
            case SHORT -> in.readShort();
            case INT -> in.readInt();
            case LONG -> in.readLong();
            case FLOAT -> in.readFloat();
            case DOUBLE -> in.readDouble();
            case BYTE_ARRAY -> {
                int length = in.readInt();
                byte[] array = new byte[length];
                in.readFully(array);
                yield array;
            }
            case STRING -> in.readUTF();
            case LIST -> {
                int elementType = in.readUnsignedByte();
                int length = in.readInt();
                List<Object> list = new ArrayList<>(Math.max(0, length));
                for (int i = 0; i < length; i++) {
                    list.add(readPayload(in, elementType));
                }
                yield list;
            }
            case COMPOUND -> readCompound(in);
            case INT_ARRAY -> {
                int length = in.readInt();
                int[] array = new int[length];
                for (int i = 0; i < length; i++) {
                    array[i] = in.readInt();
                }
                yield array;
            }
            case LONG_ARRAY -> {
                int length = in.readInt();
                long[] array = new long[length];
                for (int i = 0; i < length; i++) {
                    array[i] = in.readLong();
                }
                yield array;
            }
            default -> throw new IOException("Tag NBT inconnu: " + type);
        };
    }

    public static int typeOf(Object value) {
        if (value instanceof Byte) return BYTE;
        if (value instanceof Boolean) return BYTE; // NBT n'a pas de booléen natif → Byte 0/1
        if (value instanceof Short) return SHORT;
        if (value instanceof Integer) return INT;
        if (value instanceof Long) return LONG;
        if (value instanceof Float) return FLOAT;
        if (value instanceof Double) return DOUBLE;
        if (value instanceof byte[]) return BYTE_ARRAY;
        if (value instanceof String) return STRING;
        if (value instanceof List) return LIST;
        if (value instanceof Map) return COMPOUND;
        if (value instanceof int[]) return INT_ARRAY;
        if (value instanceof long[]) return LONG_ARRAY;
        throw new IllegalArgumentException("Type NBT non géré: " + value.getClass());
    }

    private static void writeCompound(DataOutputStream out, Map<String, Object> map) throws IOException {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            out.writeByte(typeOf(entry.getValue()));
            out.writeUTF(entry.getKey());
            writePayload(out, entry.getValue());
        }
        out.writeByte(END);
    }

    private static void writePayload(DataOutputStream out, Object value) throws IOException {
        switch (typeOf(value)) {
            case BYTE -> {
                if (value instanceof Boolean b) {
                    out.writeByte(b ? (byte) 1 : (byte) 0);
                } else {
                    out.writeByte((Byte) value);
                }
            }
            case SHORT -> out.writeShort((Short) value);
            case INT -> out.writeInt((Integer) value);
            case LONG -> out.writeLong((Long) value);
            case FLOAT -> out.writeFloat((Float) value);
            case DOUBLE -> out.writeDouble((Double) value);
            case BYTE_ARRAY -> {
                byte[] array = (byte[]) value;
                out.writeInt(array.length);
                out.write(array);
            }
            case STRING -> out.writeUTF((String) value);
            case LIST -> {
                List<?> list = (List<?>) value;
                int elementType = list.isEmpty() ? END : typeOf(list.get(0));
                out.writeByte(elementType);
                out.writeInt(list.size());
                for (Object element : list) {
                    writePayload(out, element);
                }
            }
            case COMPOUND -> {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) value;
                writeCompound(out, map);
            }
            case INT_ARRAY -> {
                int[] array = (int[]) value;
                out.writeInt(array.length);
                for (int v : array) {
                    out.writeInt(v);
                }
            }
            case LONG_ARRAY -> {
                long[] array = (long[]) value;
                out.writeInt(array.length);
                for (long v : array) {
                    out.writeLong(v);
                }
            }
            default -> throw new IOException("Impossible d'écrire ce tag NBT");
        }
    }
}

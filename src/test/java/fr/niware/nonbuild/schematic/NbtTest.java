package fr.niware.nonbuild.schematic;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NbtTest {

    @Test
    void roundTripTousLesTypes() throws Exception {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("b", (byte) 5);
        root.put("s", (short) 300);
        root.put("i", 70_000);
        root.put("l", 5_000_000_000L);
        root.put("f", 1.5f);
        root.put("d", -3.25);
        root.put("str", "héllo wörld");
        root.put("bytes", new byte[]{1, -2, 3});
        root.put("ints", new int[]{1, 2, 3});
        root.put("longs", new long[]{9L, -9L});
        root.put("list", List.of("x", "y"));

        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("k", 1);
        nested.put("deep", List.of(Map.of("a", (byte) 1)));
        root.put("nested", nested);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Nbt.writeCompressed(root, out);
        Map<String, Object> back = Nbt.readCompressed(new ByteArrayInputStream(out.toByteArray()));

        assertEquals((byte) 5, back.get("b"));
        assertEquals((short) 300, back.get("s"));
        assertEquals(70_000, back.get("i"));
        assertEquals(5_000_000_000L, back.get("l"));
        assertEquals(1.5f, back.get("f"));
        assertEquals(-3.25, back.get("d"));
        assertEquals("héllo wörld", back.get("str"));
        assertArrayEquals(new byte[]{1, -2, 3}, (byte[]) back.get("bytes"));
        assertArrayEquals(new int[]{1, 2, 3}, (int[]) back.get("ints"));
        assertArrayEquals(new long[]{9L, -9L}, (long[]) back.get("longs"));
        assertEquals(List.of("x", "y"), back.get("list"));

        Map<?, ?> nestedBack = (Map<?, ?>) back.get("nested");
        assertEquals(1, nestedBack.get("k"));
        List<?> deep = (List<?>) nestedBack.get("deep");
        assertEquals(1, deep.size());
        assertEquals((byte) 1, ((Map<?, ?>) deep.get(0)).get("a"));
    }

    @Test
    void roundTripCompoundVide() throws Exception {
        Map<String, Object> root = new LinkedHashMap<>();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Nbt.writeCompressed(root, out);
        Map<String, Object> back = Nbt.readCompressed(new ByteArrayInputStream(out.toByteArray()));
        assertTrue(back.isEmpty());
    }

    @Test
    void typeOfReconnaitLesTypes() {
        assertEquals(Nbt.BYTE, Nbt.typeOf((byte) 1));
        assertEquals(Nbt.SHORT, Nbt.typeOf((short) 1));
        assertEquals(Nbt.INT, Nbt.typeOf(1));
        assertEquals(Nbt.LONG, Nbt.typeOf(1L));
        assertEquals(Nbt.FLOAT, Nbt.typeOf(1f));
        assertEquals(Nbt.DOUBLE, Nbt.typeOf(1d));
        assertEquals(Nbt.BYTE_ARRAY, Nbt.typeOf(new byte[0]));
        assertEquals(Nbt.STRING, Nbt.typeOf("s"));
        assertEquals(Nbt.LIST, Nbt.typeOf(List.of()));
        assertEquals(Nbt.COMPOUND, Nbt.typeOf(Map.of()));
        assertEquals(Nbt.INT_ARRAY, Nbt.typeOf(new int[0]));
        assertEquals(Nbt.LONG_ARRAY, Nbt.typeOf(new long[0]));
    }

    @Test
    void rejetteUneRacineNonCompound() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (java.io.DataOutputStream data = new java.io.DataOutputStream(new java.util.zip.GZIPOutputStream(out))) {
            data.writeByte(Nbt.BYTE);
            data.writeUTF("racine");
            data.writeByte(5);
        }
        assertThrows(IOException.class,
                () -> Nbt.readCompressed(new ByteArrayInputStream(out.toByteArray())));
    }

    @Test
    void rejetteUnTypeDeTagInconnu() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (java.io.DataOutputStream data = new java.io.DataOutputStream(new java.util.zip.GZIPOutputStream(out))) {
            data.writeByte(Nbt.COMPOUND);
            data.writeUTF("");
            data.writeByte(99);
            data.writeUTF("bizarre");
        }
        IOException e = assertThrows(IOException.class,
                () -> Nbt.readCompressed(new ByteArrayInputStream(out.toByteArray())));
        assertTrue(e.getMessage().contains("inconnu"));
    }

    @Test
    void typeOfRejetteUnTypeInconnu() {
        assertThrows(IllegalArgumentException.class, () -> Nbt.typeOf(new Object()));
    }
}

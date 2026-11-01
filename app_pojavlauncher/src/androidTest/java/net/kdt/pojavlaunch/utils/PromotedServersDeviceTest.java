package net.kdt.pojavlaunch.utils;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.*;
import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;
import java.util.zip.GZIPOutputStream;
import org.junit.Test;
import static org.junit.Assert.*;

public class PromotedServersDeviceTest {
    @Test public void createsReadableListOnFirstLaunch() throws Exception { verify(false, false); }
    @Test public void restoresPromotionsAfterMinecraftDeletesAllEntries() throws Exception { verify(true, false); }
    @Test public void repairsPreviouslyCompressedListWithoutLosingPersonalServer() throws Exception { verify(false, true); }

    private void verify(boolean empty, boolean legacy) throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String id = "promoted-regression-" + UUID.randomUUID();
        File directory = new File(context.getCacheDir(), id);
        assertTrue(directory.mkdir());
        File file = new File(directory, "servers.dat");
        SharedPreferences prefs = context.getSharedPreferences(id, 0);
        try {
            if (empty || legacy) {
                OutputStream raw = new FileOutputStream(file);
                try (DataOutputStream out = new DataOutputStream(legacy ? new GZIPOutputStream(raw) : raw)) {
                    out.writeByte(10); out.writeUTF("");
                    out.writeByte(9); out.writeUTF("servers");
                    out.writeByte(empty ? 0 : 10); out.writeInt(empty ? 0 : 1);
                    if (!empty) {
                        string(out, "name", "My own server");
                        string(out, "ip", "personal.invalid");
                        string(out, "customField", "keep-me");
                        out.writeByte(0);
                    }
                    out.writeByte(0);
                }
            }
            Method parse = PromotedServersManager.class.getDeclaredMethod("parsePromotions", String.class);
            parse.setAccessible(true);
            Object promotions = parse.invoke(null, "{\"promoted_servers\":[{\"name\":\"QA Promotion\",\"ip\":\"promotion.invalid\",\"enabled\":true}]}");
            Method merge = PromotedServersManager.class.getDeclaredMethod("merge", SharedPreferences.class, File.class, List.class);
            merge.setAccessible(true);
            // Repeated sync must not duplicate promotions or remove personal metadata.
            for (int attempt = 0; attempt < 2; attempt++) {
                merge.invoke(null, prefs, directory, promotions);
                try (DataInputStream in = new DataInputStream(new FileInputStream(file))) {
                    assertEquals("servers.dat must be uncompressed NBT", 10, in.readUnsignedByte());
                    assertEquals("", in.readUTF());
                    assertEquals(9, in.readUnsignedByte()); assertEquals("servers", in.readUTF());
                    assertEquals(10, in.readUnsignedByte()); assertEquals(legacy ? 2 : 1, in.readInt());
                    assertEquals("QA Promotion", readServer(in).get("name"));
                    if (legacy) {
                        java.util.Map<String, String> personal = readServer(in);
                        assertEquals("personal.invalid", personal.get("ip"));
                        assertEquals("keep-me", personal.get("customField"));
                    }
                    assertEquals(0, in.readUnsignedByte()); assertEquals(-1, in.read());
                }
            }
        } finally {
            prefs.edit().clear().commit();
            File[] files = directory.listFiles();
            if (files != null) for (File child : files) assertTrue(child.delete());
            assertTrue(directory.delete());
        }
    }

    private static void string(DataOutputStream out, String key, String value) throws IOException {
        out.writeByte(8); out.writeUTF(key); out.writeUTF(value);
    }

    private static java.util.Map<String, String> readServer(DataInputStream in) throws IOException {
        java.util.Map<String, String> result = new java.util.HashMap<>();
        int type;
        while ((type = in.readUnsignedByte()) != 0) {
            String key = in.readUTF();
            if (type == 8) result.put(key, in.readUTF());
            else if (type == 1) in.readByte();
            else throw new IOException("Unexpected test server tag " + type);
        }
        return result;
    }
}

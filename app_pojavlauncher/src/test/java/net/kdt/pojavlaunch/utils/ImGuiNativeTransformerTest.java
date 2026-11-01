package net.kdt.pojavlaunch.utils;

import org.junit.Test;
import java.io.*;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import static org.junit.Assert.*;

public class ImGuiNativeTransformerTest {
    @Test
    public void redirectsOnlyMatchingVersionAndArchitectureWithoutChangingOtherMethods() throws Exception {
        File nativeFile = File.createTempFile("battly-imgui-native", ".so");
        String key = "battly.imgui.moulberry92.path";
        String old = System.getProperty(key);
        String oldArch = System.getProperty("os.arch");
        byte[] elf = new byte[20];
        elf[0] = 0x7f; elf[1] = 'E'; elf[2] = 'L'; elf[3] = 'F';
        elf[4] = 2; elf[5] = 1; elf[18] = (byte) 183;
        Files.write(nativeFile.toPath(), elf);
        try (URLClassLoader agent = new URLClassLoader(new URL[]{new File(
                "src/main/assets/components/lwjgl3/lwjgl-patch-agent.jar").toURI().toURL()}, null)) {
            Class<?> transformer = agent.loadClass("net.kdt.patch.ImGuiNativeTransformer");
            java.lang.reflect.Method transform = transformer.getMethod("transform", ClassLoader.class, byte[].class);
            byte[] original;
            try (InputStream in = getClass().getResourceAsStream("/imgui/moulberry92/ImGui.class")) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096]; int count;
                while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
                original = out.toByteArray();
            }
            System.setProperty(key, nativeFile.getAbsolutePath());
            System.setProperty("os.arch", "aarch64");
            ClassLoader supported = versionLoader("1.92-moulberry.2");
            byte[] patched = (byte[]) transform.invoke(null, supported, original);
            assertNotNull(patched);
            Class<?> fixture = new ClassLoader(null) {
                Class<?> define() { return defineClass(null, patched, 0, patched.length); }
            }.define();
            assertEquals(nativeFile.getAbsolutePath(), fixture.getMethod("loadPath").invoke(null));
            assertEquals(92, fixture.getMethod("untouched").invoke(null));
            assertNull(transform.invoke(null, versionLoader("1.92-moulberry.99"), original));
            System.setProperty("os.arch", "amd64");
            assertNull(transform.invoke(null, supported, original));
            System.clearProperty(key);
            assertNull(transform.invoke(null, supported, original));
        } finally {
            if (old == null) System.clearProperty(key); else System.setProperty(key, old);
            System.setProperty("os.arch", oldArch);
            nativeFile.delete();
        }
    }

    private static ClassLoader versionLoader(String version) {
        return new ClassLoader(null) {
            @Override public InputStream getResourceAsStream(String name) {
                if (!name.equals("imgui/moulberry92/imgui-java.properties")) return null;
                return new ByteArrayInputStream(("imgui.java.version=" + version).getBytes(StandardCharsets.UTF_8));
            }
        };
    }
}

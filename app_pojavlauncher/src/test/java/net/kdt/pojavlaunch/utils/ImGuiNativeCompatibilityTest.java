package net.kdt.pojavlaunch.utils;

import org.junit.Test;
import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.zip.ZipFile;
import static org.junit.Assert.*;

public class ImGuiNativeCompatibilityTest {
    @Test
    public void keepsRelocatedImGuiSeparateFromLegacyImGui() throws Exception {
        File directory = Files.createTempDirectory("battly-imgui").toFile();
        File legacy = new File(directory, "libimgui-java.so");
        File relocated = new File(directory, "libimgui-moulberry92-java.so");
        try {
            assertTrue(legacy.createNewFile());
            assertTrue(relocated.createNewFile());
            List<NativeDependencyCatalog.Dependency> dependencies =
                    NativeDependencyCatalog.bundledDependencies(directory);
            assertEquals(2, dependencies.size());
            assertTrue(dependencies.get(0).jvmArguments.contains("-Dimgui.library.name=libimgui-java.so"));
            assertTrue(dependencies.get(1).jvmArguments.contains(
                    "-Dbattly.imgui.moulberry92.path=" + relocated.getAbsolutePath()));
        } finally {
            legacy.delete(); relocated.delete(); directory.delete();
        }
    }

    @Test
    public void packagesAndroidElfForEachAbiNotDesktopX64() throws Exception {
        String[] abis = {"arm64-v8a", "armeabi-v7a", "x86_64", "x86"};
        int[] machines = {183, 40, 62, 3};
        int[] bits = {2, 1, 2, 1};
        try (ZipFile archive = new ZipFile("libs/imgui-moulberry92-1.92-moulberry.2.aar")) {
            for (int i = 0; i < abis.length; i++) {
                java.util.zip.ZipEntry entry = archive.getEntry(
                        "jni/" + abis[i] + "/libimgui-moulberry92-java.so");
                assertNotNull(abis[i], entry);
                try (java.io.InputStream input = archive.getInputStream(entry)) {
                    byte[] header = new byte[20];
                    new java.io.DataInputStream(input).readFully(header);
                    assertEquals(0x7f, header[0]);
                    assertEquals('E', header[1]);
                    assertEquals('L', header[2]);
                    assertEquals('F', header[3]);
                    assertEquals(bits[i], header[4]);
                    assertEquals(1, header[5]);
                    assertEquals(abis[i], machines[i], (header[18] & 255) | ((header[19] & 255) << 8));
                }
            }
        }
    }
}

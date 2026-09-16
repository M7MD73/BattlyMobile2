package net.kdt.pojavlaunch.utils;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NativeLibraryPluginPolicyTest {
    @Test
    public void resolvesPluginNativePathAndKeepsOrdinaryProperties() throws Exception {
        File root = new File("build/native-plugin-test").getCanonicalFile();
        List<String> parsed = NativeLibraryPluginPolicy.parseJvmProperties(
                "-Dzstd.jni.path={nativeLibraryDir}libzstd-jni.so -Dplugin.mode=android", root);

        assertEquals(2, parsed.size());
        assertEquals("-Dzstd.jni.path=" + new File(root, "libzstd-jni.so").getCanonicalPath(), parsed.get(0));
        assertEquals("-Dplugin.mode=android", parsed.get(1));
    }

    @Test
    public void rejectsTraversalMalformedAndLauncherOwnedProperties() {
        File root = new File("build/native-plugin-test");
        List<String> parsed = NativeLibraryPluginPolicy.parseJvmProperties(
                "-Dsafe.path={nativeLibraryDir}../escape.so "
                        + "-Djava.library.path={nativeLibraryDir} "
                        + "-Dorg.lwjgl.opengl.libname={nativeLibraryDir}libbad.so malformed", root);

        assertTrue(parsed.isEmpty());
    }

    @Test
    public void appliesMinecraftVersionRange() {
        assertTrue(NativeLibraryPluginPolicy.supportsMinecraftVersion("1.20.1", "1.18", "1.21.4"));
        assertFalse(NativeLibraryPluginPolicy.supportsMinecraftVersion("1.17.1", "1.18", null));
        assertFalse(NativeLibraryPluginPolicy.supportsMinecraftVersion("1.21.5", null, "1.21.4"));
        assertTrue(NativeLibraryPluginPolicy.supportsMinecraftVersion("1.21.4-forge-54", "1.21", "1.21.4"));
    }

    @Test
    public void recognizesExistingDistantHorizonsZstdOverride() throws Exception {
        File library = new File("build/native-plugin-test/libzstd-jni-dhcompat.so").getCanonicalFile();
        File parent = library.getParentFile();
        assertTrue(parent.isDirectory() || parent.mkdirs());
        try (FileOutputStream ignored = new FileOutputStream(library)) {
            // The plugin manager only needs to verify that the declared native library exists.
        }
        assertTrue(NativeLibraryPluginManager.hasDistantHorizonsZstdOverride(
                Collections.singletonList("-DZstdNativePath=" + library.getCanonicalPath())));
    }
}

package net.kdt.pojavlaunch.utils;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

    @Test
    public void infersCapabilitiesFromValidatedPluginProperties() throws Exception {
        File root = new File("build/native-plugin-capabilities").getCanonicalFile();
        assertTrue(root.isDirectory() || root.mkdirs());
        File zstd = createFile(root, "libzstd-jni-1.5.7-6-dhcompat.so");
        File imgui = createFile(root, "libimgui-java.so");
        File rapier = createFile(root, "libsable_rapier.so");
        File sqlite = createFile(root, "libsqlitejdbc-dh.so");

        List<String> arguments = java.util.Arrays.asList(
                "-DZstdNativePath=" + zstd.getCanonicalPath(),
                "-Dimgui.library.path=" + root.getCanonicalPath(),
                "-Dimgui.library.name=" + imgui.getName(),
                "-Ddh_sqlite.lib.path=" + root.getCanonicalPath(),
                "-Ddh_sqlite.lib.name=" + sqlite.getName(),
                "-Dsable_rapier_path=" + rapier.getCanonicalPath());

        Set<String> expected = new HashSet<>(java.util.Arrays.asList(
                NativeDependencyCatalog.CAPABILITY_DISTANT_HORIZONS_ZSTD,
                NativeDependencyCatalog.CAPABILITY_SQLITE_JDBC_ANDROID,
                NativeDependencyCatalog.CAPABILITY_IMGUI_JAVA,
                NativeDependencyCatalog.CAPABILITY_SABLE_RAPIER));
        assertEquals(expected, NativeDependencyCatalog.inferCapabilities(arguments));
    }

    @Test
    public void ignoresCapabilitiesWhoseNativeFileDoesNotExist() {
        List<String> arguments = java.util.Arrays.asList(
                "-DZstdNativePath=/missing/libzstd.so",
                "-Dimgui.library.path=/missing",
                "-Dimgui.library.name=libimgui-java.so");

        assertTrue(NativeDependencyCatalog.inferCapabilities(arguments).isEmpty());
    }

    @Test
    public void createsJvmArgumentsForBundledDependencies() throws Exception {
        File root = new File("build/bundled-native-capabilities").getCanonicalFile();
        assertTrue(root.isDirectory() || root.mkdirs());
        File zstd = createFile(root, "libzstd-jni-1.5.7-6-dhcompat.so");
        File imgui = createFile(root, "libimgui-java.so");
        File sqlite = createFile(root, "libsqlitejdbc-dh.so");

        List<NativeDependencyCatalog.Dependency> dependencies =
                NativeDependencyCatalog.bundledDependencies(root);

        assertEquals(3, dependencies.size());
        assertEquals("-DZstdNativePath=" + zstd.getAbsolutePath(),
                dependencies.get(0).jvmArguments.get(0));
        assertTrue(dependencies.get(1).jvmArguments.contains(
                "-Dimgui.library.path=" + root.getAbsolutePath()));
        assertTrue(dependencies.get(1).jvmArguments.contains(
                "-Dimgui.library.name=" + imgui.getName()));
        assertTrue(dependencies.get(2).jvmArguments.contains(
                "-Ddh_sqlite.lib.path=" + root.getAbsolutePath()));
        assertTrue(dependencies.get(2).jvmArguments.contains(
                "-Ddh_sqlite.lib.name=" + sqlite.getName()));
        assertTrue(dependencies.get(2).classpathArtifacts.isEmpty());
    }

    private static File createFile(File root, String name) throws Exception {
        File file = new File(root, name);
        try (FileOutputStream ignored = new FileOutputStream(file)) {
            // Only existence and the declared path are relevant to the resolver.
        }
        return file;
    }
}

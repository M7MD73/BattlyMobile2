package net.kdt.pojavlaunch.utils;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

public class AndroidNativeModDetectorTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void voiceChatRequestsItsCodecsWithoutAttributingThemToMoreCosmetics() throws Exception {
        File mod = createJar("voice-renamed.jar", "de/maxhenkel/voicechat/natives/OpusManager.class");
        Set<String> codecs = new HashSet<>(java.util.Arrays.asList(
                "codec_opus4j", "codec_rnnoise4j", "codec_speex4j", "codec_lame4j"));
        org.junit.Assert.assertEquals(codecs, AndroidNativeModDetector.requiredCapabilities(mod));
        assertFalse(AndroidNativeModDetector.isIncompatible(mod, codecs));
        assertTrue(AndroidNativeModDetector.isIncompatible(mod, Collections.singleton("codec_opus4j")));
        assertTrue(AndroidNativeModDetector.requiredCapabilities(createJar("cosmetics-renamed.jar",
                "com/cosmeticsmod/morecosmetics/MoreCosmetics.class")).isEmpty());
    }

    @Test
    public void detectsWindowsOnlyHunterWithoutBlockingOrdinaryKrypton() throws Exception {
        assertTrue(AndroidNativeModDetector.isIncompatible(
                createJar("renamed-client.jar", "net/hnt3r/utils/HunterProtection.class")));
        assertFalse(AndroidNativeModDetector.isIncompatible(
                createJar("krypton-network.jar", "me/steinborn/krypton/Krypton.class")));
    }

    @Test
    public void rejectsVoxyWhichLoadsDesktopLibcDirectly() throws Exception {
        File mod = createJar("voxy-0.2.11-alpha.jar", "fabric.mod.json");
        assertTrue(AndroidNativeModDetector.isIncompatible(mod));
    }

    @Test
    public void rejectsDistantHorizonsBuildWithDesktopZstd() throws Exception {
        File mod = createJar("DistantHorizons-2.4.0.jar",
                "linux/aarch64/libzstd-jni_dh-1.5.7-6.so");
        assertTrue(AndroidNativeModDetector.isIncompatible(mod));
    }

    @Test
    public void allowsDesktopDistantHorizonsBuildWhenAndroidZstdPluginIsAvailable() throws Exception {
        File mod = createJar("DistantHorizons-2.4.0.jar",
                "linux/aarch64/libzstd-jni_dh-1.5.7-6.so");
        assertFalse(AndroidNativeModDetector.isIncompatible(mod, new HashSet<>(java.util.Arrays.asList(
                NativeDependencyCatalog.CAPABILITY_DISTANT_HORIZONS_ZSTD,
                NativeDependencyCatalog.CAPABILITY_SQLITE_JDBC_ANDROID))));
    }

    @Test
    public void allowsAxiomWhenAndroidImGuiIsAvailable() throws Exception {
        File mod = createJar("axiom-4.0.0.jar", "fabric.mod.json");

        assertTrue(AndroidNativeModDetector.isIncompatible(mod));
        assertFalse(AndroidNativeModDetector.isIncompatible(mod,
                Collections.singleton(NativeDependencyCatalog.CAPABILITY_IMGUI_JAVA)));
    }

    @Test
    public void relocatedImGuiRequiresItsOwnAndroidLibraryEvenIfModIsRenamed() throws Exception {
        File mod = createJar("renamed-editor.jar", "imgui/moulberry92/ImGui.class");
        assertTrue(AndroidNativeModDetector.isIncompatible(mod,
                Collections.singleton(NativeDependencyCatalog.CAPABILITY_IMGUI_JAVA)));
        assertFalse(AndroidNativeModDetector.isIncompatible(mod,
                Collections.singleton(NativeDependencyCatalog.CAPABILITY_IMGUI_MOULBERRY92)));
        assertFalse(AndroidNativeModDetector.requiredCapabilities(mod).contains(
                NativeDependencyCatalog.CAPABILITY_IMGUI_JAVA));
    }

    @Test
    public void axiomSixActivatesRelocatedNativeNotLegacyNative() throws Exception {
        File mod = createJar("Axiom-6.0.5.jar", "imgui/moulberry92/ImGui.class");
        assertTrue(AndroidNativeModDetector.requiredCapabilities(mod).equals(
                Collections.singleton(NativeDependencyCatalog.CAPABILITY_IMGUI_MOULBERRY92)));
    }

    @Test
    public void reportsAllKnownRequirementsForOneMod() throws Exception {
        File mod = createJar("DistantHorizons-2.4.0.jar",
                "linux/aarch64/libzstd-jni_dh-1.5.7-6.so");

        Set<String> expected = new HashSet<>();
        expected.add(NativeDependencyCatalog.CAPABILITY_DISTANT_HORIZONS_ZSTD);
        expected.add(NativeDependencyCatalog.CAPABILITY_SQLITE_JDBC_ANDROID);
        assertTrue(AndroidNativeModDetector.requiredCapabilities(mod).equals(expected));
    }

    @Test
    public void allowsDistantHorizonsBuildWithoutDesktopZstd() throws Exception {
        File mod = createJar("DistantHorizons-android.jar", "fabric.mod.json");
        assertTrue(AndroidNativeModDetector.isIncompatible(mod));
        assertFalse(AndroidNativeModDetector.isIncompatible(mod,
                Collections.singleton(NativeDependencyCatalog.CAPABILITY_SQLITE_JDBC_ANDROID)));
    }

    @Test
    public void doesNotRejectUnrelatedCrossPlatformMod() throws Exception {
        File mod = createJar("sodium.jar", "fabric.mod.json");
        assertFalse(AndroidNativeModDetector.isIncompatible(mod));
    }

    @Test
    public void detectsDistantHorizonsFromJarContentsInsteadOfFilename() throws Exception {
        File mod = createJar("renamed-mod.jar", "dh_sqlite/SQLiteJDBCLoader.class");
        assertTrue(AndroidNativeModDetector.requiredCapabilities(mod).contains(
                NativeDependencyCatalog.CAPABILITY_SQLITE_JDBC_ANDROID));
    }

    @Test
    public void aggregatesRequirementsFromInstanceModsDirectory() throws Exception {
        File gameDirectory = temporaryFolder.newFolder("instance");
        File modsDirectory = new File(gameDirectory, "mods");
        assertTrue(modsDirectory.mkdir());
        createJar(new File(modsDirectory, "renamed.jar"),
                "dh_sqlite/SQLiteJDBCLoader.class");

        assertTrue(AndroidNativeModDetector.requiredCapabilitiesFromDirectory(gameDirectory)
                .contains(NativeDependencyCatalog.CAPABILITY_SQLITE_JDBC_ANDROID));
    }

    private File createJar(String name, String entryName) throws Exception {
        File file = temporaryFolder.newFile(name);
        createJar(file, entryName);
        return file;
    }

    @Test
    public void vanillaInstanceDoesNotRequestNativeOverrides() throws Exception {
        File instance = temporaryFolder.newFolder("vanilla-1.7.10");
        assertTrue(AndroidNativeModDetector.requiredCapabilitiesFromDirectory(instance).isEmpty());
    }

    @Test
    public void disabledModsDoNotActivateTheirNativeDependencies() throws Exception {
        File instance = temporaryFolder.newFolder("forge-1.20.1");
        File mods = new File(instance, "mods");
        assertTrue(mods.mkdir());
        createJar(new File(mods, "DistantHorizons.jar.disabledmod"),
                "dh_sqlite/SQLiteJDBCLoader.class");
        createJar(new File(mods, "sodium.jar"), "fabric.mod.json");
        assertTrue(AndroidNativeModDetector.requiredCapabilitiesFromDirectory(instance).isEmpty());
    }

    @Test
    public void nativeRequirementsStayWithinTheirOwnInstance() throws Exception {
        File modded = temporaryFolder.newFolder("fabric-26.2");
        File vanilla = temporaryFolder.newFolder("vanilla-1.20.1");
        File mods = new File(modded, "mods");
        assertTrue(mods.mkdir());
        createJar(new File(mods, "DistantHorizons.jar"), "dh_sqlite/SQLiteJDBCLoader.class");
        assertFalse(AndroidNativeModDetector.requiredCapabilitiesFromDirectory(modded).isEmpty());
        assertTrue(AndroidNativeModDetector.requiredCapabilitiesFromDirectory(vanilla).isEmpty());
    }

    private void createJar(File file, String entryName) throws Exception {
        try (JarOutputStream output = new JarOutputStream(new FileOutputStream(file))) {
            output.putNextEntry(new JarEntry(entryName));
            output.write(1);
            output.closeEntry();
        }
    }
}

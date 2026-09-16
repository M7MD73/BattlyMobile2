package net.kdt.pojavlaunch.utils;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

public class AndroidNativeModDetectorTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

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
        assertFalse(AndroidNativeModDetector.isIncompatible(mod, true));
    }

    @Test
    public void allowsDistantHorizonsBuildWithoutDesktopZstd() throws Exception {
        File mod = createJar("DistantHorizons-android.jar", "fabric.mod.json");
        assertFalse(AndroidNativeModDetector.isIncompatible(mod));
    }

    @Test
    public void doesNotRejectUnrelatedCrossPlatformMod() throws Exception {
        File mod = createJar("sodium.jar", "fabric.mod.json");
        assertFalse(AndroidNativeModDetector.isIncompatible(mod));
    }

    private File createJar(String name, String entryName) throws Exception {
        File file = temporaryFolder.newFile(name);
        try (JarOutputStream output = new JarOutputStream(new FileOutputStream(file))) {
            output.putNextEntry(new JarEntry(entryName));
            output.write(1);
            output.closeEntry();
        }
        return file;
    }
}

package net.kdt.pojavlaunch.utils;

import java.io.File;
import java.io.IOException;
import java.util.Enumeration;
import java.util.Locale;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/** Detects known desktop-native mod builds that cannot load under Android's bionic libc. */
public final class AndroidNativeModDetector {
    private AndroidNativeModDetector() {}

    public static boolean isIncompatible(File modFile) {
        return isIncompatible(modFile, false);
    }

    public static boolean isIncompatible(File modFile, boolean distantHorizonsNativeSupport) {
        if (modFile == null || !modFile.isFile()) return false;
        String name = modFile.getName().toLowerCase(Locale.ROOT);
        if (name.contains("axiom") || name.contains("voxy")) {
            return true;
        }
        if (!name.contains("distanthorizons") && !name.contains("distant-horizons")) {
            return false;
        }
        return !distantHorizonsNativeSupport && containsDesktopDhZstd(modFile);
    }

    static boolean containsDesktopDhZstd(File modFile) {
        try (JarFile jar = new JarFile(modFile)) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                String path = entries.nextElement().getName().toLowerCase(Locale.ROOT);
                if (path.contains("libzstd-jni_dh") && path.endsWith(".so")
                        && (path.contains("/linux/") || path.startsWith("linux/"))) {
                    return true;
                }
            }
        } catch (IOException | RuntimeException ignored) {
            // A damaged JAR is handled by the normal mod-loader diagnostics.
        }
        return false;
    }
}

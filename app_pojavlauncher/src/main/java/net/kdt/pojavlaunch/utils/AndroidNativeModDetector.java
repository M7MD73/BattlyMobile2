package net.kdt.pojavlaunch.utils;

import java.io.File;
import java.io.IOException;
import java.util.Enumeration;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/** Detects known desktop-native mod builds that cannot load under Android's bionic libc. */
public final class AndroidNativeModDetector {
    private AndroidNativeModDetector() {}

    public static boolean isIncompatible(File modFile) {
        return isIncompatible(modFile, Collections.emptySet());
    }

    public static boolean isIncompatible(File modFile, boolean distantHorizonsNativeSupport) {
        Set<String> capabilities = new LinkedHashSet<>();
        if (distantHorizonsNativeSupport) {
            capabilities.add(NativeDependencyCatalog.CAPABILITY_DISTANT_HORIZONS_ZSTD);
            capabilities.add(NativeDependencyCatalog.CAPABILITY_SQLITE_JDBC_ANDROID);
        }
        return isIncompatible(modFile, capabilities);
    }

    public static boolean isIncompatible(File modFile, Set<String> availableCapabilities) {
        if (modFile == null || !modFile.isFile()) return false;
        Set<String> required = requiredCapabilities(modFile);
        return availableCapabilities == null || !availableCapabilities.containsAll(required);
    }

    public static Set<String> requiredCapabilities(File modFile) {
        if (modFile == null || !modFile.isFile()) return Collections.emptySet();
        String name = modFile.getName().toLowerCase(Locale.ROOT);
        Set<String> required = new LinkedHashSet<>();
        boolean voiceChat = containsEntry(modFile, "de/maxhenkel/voicechat/natives/OpusManager.class");
        for (String codec : new String[]{"opus4j", "rnnoise4j", "speex4j", "lame4j"}) {
            if (voiceChat || containsEntryPrefix(modFile, "de/maxhenkel/" + codec + "/")) {
                required.add("codec_" + codec);
            }
        }
        // This is Hunter's Windows-only client, not the Krypton renderer/network mod.
        if (containsEntry(modFile, "net/hnt3r/utils/HunterProtection.class")) {
            required.add("windows_kernel32");
        }
        boolean isAxiom = name.contains("axiom")
                || containsEntryPrefix(modFile, "com/moulberry/axiom/");
        boolean isVoxy = name.contains("voxy")
                || containsEntryPrefix(modFile, "me/cortex/voxy/");
        boolean isDistantHorizons = name.contains("distanthorizons")
                || name.contains("distant-horizons")
                || containsEntry(modFile, "dh_sqlite/SQLiteJDBCLoader.class")
                || containsEntryPrefix(modFile, "com/seibel/distanthorizons/");
        boolean hasRelocatedImGui = containsEntry(modFile, "imgui/moulberry92/ImGui.class");
        if (hasRelocatedImGui) {
            required.add(NativeDependencyCatalog.CAPABILITY_IMGUI_MOULBERRY92);
        }
        if (isAxiom && (!hasRelocatedImGui || containsEntry(modFile, "imgui/ImGui.class"))) {
            required.add(NativeDependencyCatalog.CAPABILITY_IMGUI_JAVA);
        }
        if (isVoxy) {
            required.add(NativeDependencyCatalog.CAPABILITY_VOXY_NATIVE);
        }
        if (isDistantHorizons && containsDesktopDhZstd(modFile)) {
            required.add(NativeDependencyCatalog.CAPABILITY_DISTANT_HORIZONS_ZSTD);
        }
        if (isDistantHorizons) {
            required.add(NativeDependencyCatalog.CAPABILITY_SQLITE_JDBC_ANDROID);
        }
        return Collections.unmodifiableSet(required);
    }

    public static Set<String> requiredCapabilitiesFromDirectory(File gameDirectory) {
        if (gameDirectory == null) return Collections.emptySet();
        File[] mods = new File(gameDirectory, "mods").listFiles(file -> file.isFile()
                && file.getName().toLowerCase(Locale.ROOT).endsWith(".jar"));
        if (mods == null) return Collections.emptySet();
        Set<String> required = new LinkedHashSet<>();
        for (File mod : mods) required.addAll(requiredCapabilities(mod));
        return Collections.unmodifiableSet(required);
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

    private static boolean containsEntry(File modFile, String expectedPath) {
        try (JarFile jar = new JarFile(modFile)) {
            return jar.getEntry(expectedPath) != null;
        } catch (IOException | RuntimeException ignored) {
            return false;
        }
    }

    private static boolean containsEntryPrefix(File modFile, String expectedPrefix) {
        try (JarFile jar = new JarFile(modFile)) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                if (entries.nextElement().getName().startsWith(expectedPrefix)) return true;
            }
        } catch (IOException | RuntimeException ignored) {
            // A damaged JAR is handled by the normal mod-loader diagnostics.
        }
        return false;
    }
}

package net.kdt.pojavlaunch.utils;

import androidx.annotation.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Pure validation for the FCLNativePlugin manifest contract. */
final class NativeLibraryPluginPolicy {
    private static final String NATIVE_DIR_PLACEHOLDER = "{nativeLibraryDir}";
    private static final Set<String> RESERVED_PROPERTIES = new HashSet<>(Arrays.asList(
            "java.library.path",
            "org.lwjgl.librarypath",
            "org.lwjgl.opengl.libname",
            "org.lwjgl.freetype.libname",
            "org.lwjgl.spvc.libname",
            "org.lwjgl.shaderc.libname",
            "org.lwjgl.vma.libname"
    ));

    private NativeLibraryPluginPolicy() {
    }

    static List<String> parseJvmProperties(@Nullable String environment, File nativeDirectory) {
        if (environment == null || environment.trim().isEmpty() || nativeDirectory == null) {
            return Collections.emptyList();
        }
        String canonicalBase;
        try {
            canonicalBase = nativeDirectory.getCanonicalPath();
        } catch (Exception exception) {
            return Collections.emptyList();
        }

        List<String> result = new ArrayList<>();
        for (String entry : environment.trim().split("\\s+")) {
            int separator = entry.indexOf('=');
            if (!entry.startsWith("-D") || separator <= 2) continue;
            String key = entry.substring(2, separator);
            if (!key.matches("[A-Za-z0-9_.-]+") || RESERVED_PROPERTIES.contains(key)) continue;

            String value = entry.substring(separator + 1);
            if (value.startsWith(NATIVE_DIR_PLACEHOLDER)) {
                String suffix = value.substring(NATIVE_DIR_PLACEHOLDER.length());
                try {
                    File resolved = suffix.isEmpty()
                            ? nativeDirectory
                            : new File(nativeDirectory, stripLeadingSeparators(suffix));
                    String canonicalResolved = resolved.getCanonicalPath();
                    if (!isInside(canonicalBase, canonicalResolved)) continue;
                    value = canonicalResolved;
                } catch (Exception exception) {
                    continue;
                }
            }
            result.add("-D" + key + "=" + value);
        }
        return Collections.unmodifiableList(result);
    }

    static boolean supportsMinecraftVersion(@Nullable String minecraftVersion,
                                            @Nullable String minimum,
                                            @Nullable String maximum) {
        if (minecraftVersion == null || minecraftVersion.trim().isEmpty()) return true;
        if (minimum != null && !minimum.trim().isEmpty()
                && compareVersions(minecraftVersion, minimum) < 0) return false;
        return maximum == null || maximum.trim().isEmpty()
                || compareVersions(minecraftVersion, maximum) <= 0;
    }

    static int compareVersions(String left, String right) {
        int[] leftParts = numericParts(left);
        int[] rightParts = numericParts(right);
        int length = Math.max(leftParts.length, rightParts.length);
        for (int i = 0; i < length; i++) {
            int leftValue = i < leftParts.length ? leftParts[i] : 0;
            int rightValue = i < rightParts.length ? rightParts[i] : 0;
            if (leftValue != rightValue) return Integer.compare(leftValue, rightValue);
        }
        return 0;
    }

    private static int[] numericParts(String version) {
        String normalized = version == null ? "" : version.toLowerCase(Locale.ROOT);
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\d+").matcher(normalized);
        List<Integer> values = new ArrayList<>();
        while (matcher.find() && values.size() < 3) {
            try {
                values.add(Integer.parseInt(matcher.group()));
            } catch (NumberFormatException ignored) {
                values.add(0);
            }
        }
        int[] result = new int[values.size()];
        for (int i = 0; i < values.size(); i++) result[i] = values.get(i);
        return result;
    }

    private static String stripLeadingSeparators(String value) {
        int index = 0;
        while (index < value.length() && (value.charAt(index) == '/' || value.charAt(index) == '\\')) {
            index++;
        }
        return value.substring(index);
    }

    private static boolean isInside(String base, String candidate) {
        return candidate.equals(base) || candidate.startsWith(base + File.separator);
    }
}

package net.kdt.pojavlaunch.utils;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Version-specific MobileGlues settings that must also work for inherited mod-loader profiles. */
public final class MobileGluesVersionPolicy {
    private static final Pattern RELEASE_PATTERN = Pattern.compile("^(\\d+)\\.(\\d+)");
    private static final Pattern SNAPSHOT_PATTERN = Pattern.compile("snapshot[-_ ]?(\\d+)");

    private MobileGluesVersionPolicy() {
    }

    public static String resolveMinecraftVersion(String versionId, String inheritsFrom) {
        if (inheritsFrom != null && !inheritsFrom.trim().isEmpty()) {
            return inheritsFrom.trim();
        }
        return versionId == null ? null : versionId.trim();
    }

    public static boolean requiresShaderErrorWorkaround(String minecraftVersion) {
        if (minecraftVersion == null) return false;
        String normalized = minecraftVersion.toLowerCase(Locale.ROOT).trim();
        Matcher release = RELEASE_PATTERN.matcher(normalized);
        if (!release.find()) return false;

        int major = Integer.parseInt(release.group(1));
        int minor = Integer.parseInt(release.group(2));
        if (major > 26 || major == 26 && minor > 3) return true;
        if (major != 26 || minor != 3) return false;

        Matcher snapshot = SNAPSHOT_PATTERN.matcher(normalized);
        return !snapshot.find() || Integer.parseInt(snapshot.group(1)) >= 3;
    }
}

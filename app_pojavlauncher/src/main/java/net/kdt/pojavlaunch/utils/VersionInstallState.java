package net.kdt.pojavlaunch.utils;

import java.io.File;

/** Checks whether a selected Minecraft version has usable local metadata. */
public final class VersionInstallState {
    private VersionInstallState() {
    }

    public static boolean isLoaderVersion(String versionId) {
        if (versionId == null) return false;
        String id = versionId.toLowerCase(java.util.Locale.ROOT);
        return id.contains("-forge-") || id.contains("-neoforge-")
                || id.startsWith("fabric-loader-") || id.startsWith("quilt-loader-")
                || id.contains("-optifine_");
    }

    public static boolean hasLocalMetadata(File versionsDirectory, String versionId) {
        if (versionsDirectory == null || versionId == null || versionId.trim().isEmpty()) {
            return false;
        }
        File versionDirectory = new File(versionsDirectory, versionId);
        File metadata = new File(versionDirectory, versionId + ".json");
        return metadata.isFile() && metadata.canRead() && metadata.length() > 0;
    }
}

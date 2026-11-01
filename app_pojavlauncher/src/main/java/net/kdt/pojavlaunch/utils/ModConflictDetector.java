package net.kdt.pojavlaunch.utils;

import org.json.JSONObject;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Detects enabled mod combinations that are known to fail before Minecraft opens. */
public final class ModConflictDetector {
    private ModConflictDetector() {
    }

    public static Conflict findKryptonE4mc(File gameDirectory) {
        if (gameDirectory == null) return null;
        File modsDirectory = new File(gameDirectory, "mods");
        File krypton = findEnabledFabricMod(modsDirectory, "krypton");
        if (krypton == null) return null;
        File e4mc = findEnabledFabricMod(modsDirectory, "e4mc");
        return e4mc == null ? null : new Conflict(krypton, e4mc, krypton);
    }

    static File findEnabledFabricMod(File modsDirectory, String modId) {
        if (modsDirectory == null || !modsDirectory.isDirectory()) return null;
        File[] files = modsDirectory.listFiles(file -> file.isFile()
                && file.getName().toLowerCase(Locale.ROOT).endsWith(".jar"));
        if (files == null) return null;
        String id = modId.toLowerCase(Locale.ROOT);
        for (File file : files) {
            if (!file.getName().toLowerCase(Locale.ROOT).contains(id)) continue;
            if (id.equals(readFabricModId(file))) return file;
        }
        return null;
    }

    private static String readFabricModId(File file) {
        try (ZipFile zip = new ZipFile(file)) {
            ZipEntry entry = zip.getEntry("fabric.mod.json");
            if (entry == null) return "";
            try (InputStream stream = zip.getInputStream(entry)) {
                byte[] data = new byte[(int) Math.min(entry.getSize() < 0 ? 64 * 1024 : entry.getSize(), 64 * 1024)];
                int offset = 0;
                int read;
                while (offset < data.length && (read = stream.read(data, offset, data.length - offset)) > 0) {
                    offset += read;
                }
                return new JSONObject(new String(data, 0, offset, StandardCharsets.UTF_8))
                        .optString("id", "").trim().toLowerCase(Locale.ROOT);
            }
        } catch (Exception ignored) {
            return "";
        }
    }

    public static final class Conflict {
        public final File krypton;
        public final File e4mc;
        public final File recommendedDisable;

        Conflict(File krypton, File e4mc, File recommendedDisable) {
            this.krypton = krypton;
            this.e4mc = e4mc;
            this.recommendedDisable = recommendedDisable;
        }

        public boolean disableRecommended() {
            File target = new File(recommendedDisable.getParentFile(),
                    recommendedDisable.getName() + ".disabledmod");
            return !target.exists() && recommendedDisable.renameTo(target);
        }
    }
}

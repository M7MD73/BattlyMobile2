package net.kdt.pojavlaunch.utils;

import java.util.Locale;

public final class LibraryContentRules {
    public static final int INITIAL_VISIBLE_ITEMS = 5;
    public static final int LOAD_MORE_BATCH_SIZE = 10;

    private LibraryContentRules() {
    }

    public static boolean isSupportedModFile(String fileName) {
        return fileName != null && fileName.toLowerCase(Locale.ROOT).endsWith(".jar");
    }

    public static String sanitizeImportedModName(String displayName) {
        if (displayName == null) return "";
        String normalized = displayName.replace('\\', '/').trim();
        int separator = normalized.lastIndexOf('/');
        if (separator >= 0) normalized = normalized.substring(separator + 1);
        normalized = normalized.replaceAll("[\\p{Cntrl}]", "_").trim();
        if (normalized.isEmpty() || ".".equals(normalized) || "..".equals(normalized)) return "";
        return normalized;
    }

    public static int nextVisibleCount(int currentVisible, int totalItems) {
        if (totalItems <= 0) return 0;
        if (currentVisible <= 0) return Math.min(INITIAL_VISIBLE_ITEMS, totalItems);
        return Math.min(totalItems, currentVisible + LOAD_MORE_BATCH_SIZE);
    }
}

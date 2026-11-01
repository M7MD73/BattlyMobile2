package net.kdt.pojavlaunch.utils;

import java.io.File;
import java.io.IOException;

/** Files supplied by archives and document clients must stay within their assigned root. */
public final class SafeFilePaths {
    private SafeFilePaths() { }

    public static boolean isInside(File root, File candidate) throws IOException {
        String base = root.getCanonicalPath();
        String path = candidate.getCanonicalPath();
        return path.equals(base) || path.startsWith(base + File.separator);
    }

    public static File requireInside(File root, File candidate) throws IOException {
        if (!isInside(root, candidate)) throw new IOException("Path is outside the allowed directory");
        return candidate.getCanonicalFile();
    }

    public static File resolveRelative(File root, String relative) throws IOException {
        if (relative == null || relative.isEmpty() || relative.indexOf('\0') >= 0
                || relative.startsWith("/") || relative.indexOf('\\') >= 0
                || relative.matches("^[A-Za-z]:.*")) {
            throw new IOException("Invalid relative file path");
        }
        String normalized = relative;
        for (String part : normalized.split("/")) {
            if ("..".equals(part)) throw new IOException("Parent paths are not allowed");
        }
        File result = requireInside(root, new File(root, normalized));
        if (result.equals(root.getCanonicalFile())) throw new IOException("Cannot replace the root directory");
        return result;
    }

    public static void requireFileName(String name) throws IOException {
        if (name == null || name.isEmpty() || ".".equals(name) || "..".equals(name)
                || name.indexOf('/') >= 0 || name.indexOf('\\') >= 0 || name.indexOf('\0') >= 0) {
            throw new IOException("Invalid document name");
        }
    }

    public static String downloadFileName(String url) throws IOException {
        try {
            String path = new java.net.URI(url).getPath();
            if (path == null) throw new IOException("Missing download path");
            String name = path.substring(path.lastIndexOf('/') + 1);
            requireFileName(name);
            return name;
        } catch (java.net.URISyntaxException invalid) {
            throw new IOException("Invalid download URL", invalid);
        }
    }
}

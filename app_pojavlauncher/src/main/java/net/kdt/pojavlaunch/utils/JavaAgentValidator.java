package net.kdt.pojavlaunch.utils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

public final class JavaAgentValidator {
    private JavaAgentValidator() {
    }

    public static boolean isUsable(File jarFile, int javaVersion, String classEntry) {
        if (jarFile == null || !jarFile.isFile() || jarFile.length() == 0 || classEntry == null) {
            return false;
        }
        try (JarFile jar = new JarFile(jarFile)) {
            Manifest manifest = jar.getManifest();
            if (manifest == null) return false;

            String expectedPremain = classEntry.substring(0, classEntry.length() - ".class".length())
                    .replace('/', '.');
            String premain = manifest.getMainAttributes().getValue("Premain-Class");
            if (!expectedPremain.equals(premain)) return false;

            JarEntry entry = jar.getJarEntry(classEntry);
            if (entry == null) return false;
            try (InputStream inputStream = jar.getInputStream(entry)) {
                byte[] header = new byte[8];
                int offset = 0;
                while (offset < header.length) {
                    int read = inputStream.read(header, offset, header.length - offset);
                    if (read < 0) return false;
                    offset += read;
                }
                if ((header[0] & 0xff) != 0xca || (header[1] & 0xff) != 0xfe
                        || (header[2] & 0xff) != 0xba || (header[3] & 0xff) != 0xbe) {
                    return false;
                }
                int classMajor = ((header[6] & 0xff) << 8) | (header[7] & 0xff);
                return classMajor <= maxClassMajor(javaVersion);
            }
        } catch (IOException | RuntimeException exception) {
            return false;
        }
    }

    static int maxClassMajor(int javaVersion) {
        if (javaVersion <= 8) return 52;
        if (javaVersion <= 11) return 55;
        if (javaVersion <= 17) return 61;
        if (javaVersion <= 21) return 65;
        if (javaVersion <= 25) return 69;
        return javaVersion + 44;
    }
}

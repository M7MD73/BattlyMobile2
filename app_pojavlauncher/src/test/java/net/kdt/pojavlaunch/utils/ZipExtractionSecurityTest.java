package net.kdt.pojavlaunch.utils;

import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.zip.*;
import static org.junit.Assert.*;

public class ZipExtractionSecurityTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();
    private File pack(String path) throws Exception {
        File zip = temp.newFile();
        try (ZipOutputStream output = new ZipOutputStream(new FileOutputStream(zip))) {
            output.putNextEntry(new ZipEntry(path));
            output.write("example".getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        return zip;
    }
    @Test public void legitimatePackOverridesStillExtract() throws Exception {
        File destination = temp.newFolder("instance");
        try (ZipFile zip = new ZipFile(pack("overrides/config/example.txt"))) {
            ZipUtils.zipExtract(zip, "overrides/", destination);
        }
        assertEquals("example", new String(Files.readAllBytes(new File(destination, "config/example.txt").toPath()), StandardCharsets.UTF_8));
    }
    @Test public void curseforgeOverrideDirectoryWithoutTrailingSlashStillWorks() throws Exception {
        File destination = temp.newFolder("curseforge-instance");
        try (ZipFile zip = new ZipFile(pack("overrides/config/example.txt"))) {
            ZipUtils.zipExtract(zip, "overrides", destination);
        }
        assertTrue(new File(destination, "config/example.txt").isFile());
    }
    @Test public void rejectsCrossInstanceOverrideAndPreservesVictim() throws Exception {
        File destination = temp.newFolder("instance");
        File victim = temp.newFile("victim.txt");
        Files.write(victim.toPath(), "original".getBytes(StandardCharsets.UTF_8));
        try (ZipFile zip = new ZipFile(pack("overrides/../victim.txt"))) {
            try { ZipUtils.zipExtract(zip, "overrides/", destination); fail(); }
            catch (IOException expected) { }
        }
        assertEquals("original", new String(Files.readAllBytes(victim.toPath()), StandardCharsets.UTF_8));
    }
}

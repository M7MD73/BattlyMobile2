package net.kdt.pojavlaunch.utils;

import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import static org.junit.Assert.*;

public class SafeFilePathsTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();

    @Test public void allowsNormalPackPaths() throws Exception {
        File root = temp.newFolder("instance");
        assertEquals(new File(root, "mods/example.jar"), SafeFilePaths.resolveRelative(root, "mods/example.jar"));
        assertEquals(new File(root, "config/example.json"), SafeFilePaths.resolveRelative(root, "./config/example.json"));
    }
    @Test public void downloadFilenameIsRelativeAndExcludesQuery() throws Exception {
        assertEquals("example.jar", SafeFilePaths.downloadFileName("https://example.test/files/example.jar?download=1"));
        assertEquals("example mod.jar", SafeFilePaths.downloadFileName("https://example.test/files/example%20mod.jar"));
        try { SafeFilePaths.downloadFileName("https://example.test/files/"); fail(); } catch (IOException expected) { }
    }

    @Test public void rejectsEscapesAndRootWrites() throws Exception {
        File root = temp.newFolder("instance");
        for (String value : new String[]{"../other/options.txt", "mods/../../outside", "/absolute", "C:/outside", "a\\..\\outside", "", ".", "a/..", "bad\0name"}) {
            try { SafeFilePaths.resolveRelative(root, value); fail(value); } catch (IOException expected) { }
        }
    }

    @Test public void containmentIsCanonicalAndSeparatorAware() throws Exception {
        File root = temp.newFolder("allowed");
        File outside = temp.newFolder("allowed-other");
        assertFalse(SafeFilePaths.isInside(root, outside));
        assertFalse(SafeFilePaths.isInside(root, new File(root, "../allowed-other")));
        assertTrue(SafeFilePaths.isInside(root, new File(root, "child")));
    }

    @Test public void rejectsExistingSymlinkOutsideRoot() throws Exception {
        // Windows File.getCanonicalPath does not resolve NTFS symbolic links like Android does.
        org.junit.Assume.assumeTrue(File.separatorChar == '/');
        File root = temp.newFolder("allowed");
        File outside = temp.newFolder("outside");
        try { Files.createSymbolicLink(new File(root, "link").toPath(), outside.toPath()); }
        catch (IOException | UnsupportedOperationException unavailable) { org.junit.Assume.assumeNoException(unavailable); }
        try { SafeFilePaths.resolveRelative(root, "link/file"); fail(); } catch (IOException expected) { }
    }
}

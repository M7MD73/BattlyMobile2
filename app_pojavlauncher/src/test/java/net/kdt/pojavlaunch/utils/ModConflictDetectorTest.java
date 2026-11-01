package net.kdt.pojavlaunch.utils;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ModConflictDetectorTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void detectsKryptonAndE4mcAndDisablesOnlyKrypton() throws Exception {
        File game = temporaryFolder.newFolder("game");
        File mods = new File(game, "mods");
        assertTrue(mods.mkdirs());
        File krypton = fabricMod(mods, "krypton-0.2.8.jar", "krypton");
        File e4mc = fabricMod(mods, "e4mc_minecraft-6.1.1.jar", "e4mc");

        ModConflictDetector.Conflict conflict = ModConflictDetector.findKryptonE4mc(game);

        assertNotNull(conflict);
        assertTrue(conflict.disableRecommended());
        assertFalse(krypton.exists());
        assertTrue(new File(mods, krypton.getName() + ".disabledmod").isFile());
        assertTrue(e4mc.isFile());
    }

    @Test
    public void doesNotReportEitherModOnItsOwn() throws Exception {
        File game = temporaryFolder.newFolder("single");
        File mods = new File(game, "mods");
        assertTrue(mods.mkdirs());
        fabricMod(mods, "krypton-0.2.8.jar", "krypton");

        assertNull(ModConflictDetector.findKryptonE4mc(game));
    }

    private static File fabricMod(File directory, String name, String id) throws Exception {
        File file = new File(directory, name);
        try (ZipOutputStream output = new ZipOutputStream(new FileOutputStream(file))) {
            output.putNextEntry(new ZipEntry("fabric.mod.json"));
            output.write(("{\"schemaVersion\":1,\"id\":\"" + id + "\"}")
                    .getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        return file;
    }
}

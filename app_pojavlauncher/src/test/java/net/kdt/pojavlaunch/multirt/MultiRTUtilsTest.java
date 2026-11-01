package net.kdt.pojavlaunch.multirt;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;

public class MultiRTUtilsTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void promotesCompleteRuntimeWithoutExposingPartialFiles() throws Exception {
        File root = temporaryFolder.newFolder("runtimes");
        File destination = new File(root, "jre-21");
        File staging = new File(root, ".jre-21.installing");
        File backup = new File(root, ".jre-21.backup");
        assertTrue(destination.mkdirs());
        write(new File(destination, "old-marker"));
        assertTrue(new File(staging, "bin").mkdirs());
        write(new File(staging, "bin/java"));
        write(new File(staging, "release"));

        RuntimeInstallTransaction.validate(staging);
        RuntimeInstallTransaction.promote(staging, destination, backup);

        assertTrue(new File(destination, "bin/java").isFile());
        assertFalse(new File(destination, "old-marker").exists());
        assertFalse(staging.exists());
        assertFalse(backup.exists());
    }

    @Test
    public void restoresPreviousRuntimeAfterInterruptedPromotion() throws Exception {
        File root = temporaryFolder.newFolder("interrupted-runtimes");
        File destination = new File(root, "jre-8");
        File backup = new File(root, ".jre-8.backup");
        assertTrue(backup.mkdirs());
        write(new File(backup, "old-marker"));

        RuntimeInstallTransaction.recover(destination, backup);

        assertTrue(new File(destination, "old-marker").isFile());
        assertFalse(backup.exists());
    }

    private static void write(File file) throws Exception {
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(1);
        }
    }
}

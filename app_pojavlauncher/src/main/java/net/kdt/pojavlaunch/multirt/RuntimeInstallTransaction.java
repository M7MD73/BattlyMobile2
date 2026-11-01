package net.kdt.pojavlaunch.multirt;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;

final class RuntimeInstallTransaction {
    private RuntimeInstallTransaction() {
    }

    static void validate(File staging) throws IOException {
        if (!new File(staging, "release").isFile()
                || !new File(staging, "bin/java").isFile()) {
            throw new IOException("Extracted runtime is incomplete");
        }
    }

    static void recover(File destination, File backup) throws IOException {
        if (!backup.exists()) return;
        if (destination.exists()) {
            FileUtils.deleteDirectory(backup);
            return;
        }
        if (!backup.renameTo(destination)) {
            throw new IOException("Could not restore the previous runtime");
        }
    }

    static void promote(File staging, File destination, File backup) throws IOException {
        if (backup.exists()) FileUtils.deleteDirectory(backup);
        boolean hadPreviousRuntime = destination.exists();
        if (hadPreviousRuntime && !destination.renameTo(backup)) {
            throw new IOException("Could not preserve the previous runtime");
        }
        if (!staging.renameTo(destination)) {
            if (hadPreviousRuntime && !backup.renameTo(destination)) {
                throw new IOException("Could not install or restore the previous runtime");
            }
            throw new IOException("Could not activate the extracted runtime");
        }
        if (backup.exists()) FileUtils.deleteDirectory(backup);
    }
}

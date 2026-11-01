package net.kdt.pojavlaunch.utils;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class VersionInstallStateTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void loaderProfilesAreNotVanillaCatalogEntries() {
        assertTrue(VersionInstallState.isLoaderVersion("1.12.2-forge-14.23.5.2847"));
        assertTrue(VersionInstallState.isLoaderVersion("fabric-loader-0.19.5-26.3"));
        assertFalse(VersionInstallState.isLoaderVersion("26.3"));
        assertFalse(VersionInstallState.isLoaderVersion(null));
    }

    @Test
    public void missingOrEmptyMetadataIsNotInstalled() throws Exception {
        File versions = temporaryFolder.newFolder("versions");
        assertFalse(VersionInstallState.hasLocalMetadata(versions, "1.12.2"));

        File versionDirectory = new File(versions, "1.12.2");
        assertTrue(versionDirectory.mkdirs());
        assertTrue(new File(versionDirectory, "1.12.2.json").createNewFile());
        assertFalse(VersionInstallState.hasLocalMetadata(versions, "1.12.2"));
    }

    @Test
    public void readableNonEmptyMetadataIsInstalled() throws Exception {
        File versions = temporaryFolder.newFolder("versions");
        File versionDirectory = new File(versions, "26.3");
        assertTrue(versionDirectory.mkdirs());
        File metadata = new File(versionDirectory, "26.3.json");
        try (FileOutputStream output = new FileOutputStream(metadata)) {
            output.write("{}".getBytes(StandardCharsets.UTF_8));
        }

        assertTrue(VersionInstallState.hasLocalMetadata(versions, "26.3"));
    }

    @Test
    public void invalidVersionIdIsNotInstalled() throws Exception {
        File versions = temporaryFolder.newFolder("versions");
        assertFalse(VersionInstallState.hasLocalMetadata(versions, null));
        assertFalse(VersionInstallState.hasLocalMetadata(versions, "  "));
    }
}

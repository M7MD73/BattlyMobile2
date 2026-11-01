package net.kdt.pojavlaunch.tasks;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MinecraftDownloaderTest {
    @Test
    public void prioritizesUnknownAndLargeDownloadsBeforeSmallAssets() {
        assertEquals(Long.MAX_VALUE, MinecraftDownloader.downloadPriority(0));
        assertEquals(Long.MAX_VALUE, MinecraftDownloader.downloadPriority(-1));
        assertTrue(MinecraftDownloader.downloadPriority(80L * 1024L * 1024L)
                > MinecraftDownloader.downloadPriority(16L * 1024L));
    }
}

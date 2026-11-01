package net.kdt.pojavlaunch.utils;

import android.os.Bundle;
import android.os.SystemClock;
import androidx.test.platform.app.InstrumentationRegistry;
import net.kdt.pojavlaunch.Logger;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.Test;
import static org.junit.Assert.*;

public class NativeLogIoDeviceTest {
    @Test public void writesEveryLogEntryWithoutPerLineStorageBarriers() throws Exception {
        System.loadLibrary("pojavexec");
        File file = new File(InstrumentationRegistry.getInstrumentation().getTargetContext()
                .getCacheDir(), "native-log-throughput-qa.txt");
        if (!file.exists()) assertTrue(file.createNewFile());
        Logger.begin(file.getAbsolutePath());
        long start = SystemClock.elapsedRealtimeNanos();
        for (int i = 0; i < 2000; i++) Logger.appendToLog("BATTLY_LOG_IO_QA_" + i);
        double elapsedMs = (SystemClock.elapsedRealtimeNanos() - start) / 1e6;
        // Reads see all writes immediately, without waiting for the durability interval.
        String saved = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        for (int i = 0; i < 2000; i++) assertTrue(saved.contains("BATTLY_LOG_IO_QA_" + i + "\n"));
        Bundle result = new Bundle();
        result.putString("nativeLogElapsedMs", Double.toString(elapsedMs));
        InstrumentationRegistry.getInstrumentation().sendStatus(0, result);
        assertTrue(file.delete());
    }
}

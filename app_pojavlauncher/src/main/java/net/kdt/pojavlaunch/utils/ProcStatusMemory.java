package net.kdt.pojavlaunch.utils;

import androidx.annotation.NonNull;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Locale;

/** Memory counters reported by the Linux kernel for the current process. */
public final class ProcStatusMemory {
    public final long rssMb;
    public final long rssAnonMb;
    public final long rssFileMb;
    public final long swapMb;

    private ProcStatusMemory(long rssKb, long rssAnonKb, long rssFileKb, long swapKb) {
        rssMb = kbToMb(rssKb);
        rssAnonMb = kbToMb(rssAnonKb);
        rssFileMb = kbToMb(rssFileKb);
        swapMb = kbToMb(swapKb);
    }

    @NonNull
    public static ProcStatusMemory readSelf() {
        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/self/status"))) {
            StringBuilder status = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) status.append(line).append('\n');
            return parse(status.toString());
        } catch (IOException ignored) {
            return unavailable();
        }
    }

    @NonNull
    static ProcStatusMemory parse(String status) {
        long rssKb = -1;
        long rssAnonKb = -1;
        long rssFileKb = -1;
        long swapKb = -1;
        if (status != null) {
            for (String line : status.split("\\r?\\n")) {
                if (line.startsWith("VmRSS:")) rssKb = parseKb(line);
                else if (line.startsWith("RssAnon:")) rssAnonKb = parseKb(line);
                else if (line.startsWith("RssFile:")) rssFileKb = parseKb(line);
                else if (line.startsWith("VmSwap:")) swapKb = parseKb(line);
            }
        }
        return new ProcStatusMemory(rssKb, rssAnonKb, rssFileKb, swapKb);
    }

    @NonNull
    public String describe() {
        return String.format(Locale.ROOT, "rss=%dMB, rssAnon=%dMB, rssFile=%dMB, swap=%dMB",
                rssMb, rssAnonMb, rssFileMb, swapMb);
    }

    private static long parseKb(String line) {
        String[] parts = line.trim().split("\\s+");
        if (parts.length < 2) return -1;
        try {
            return Long.parseLong(parts[1]);
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private static long kbToMb(long kb) {
        return kb < 0 ? -1 : kb / 1024L;
    }

    private static ProcStatusMemory unavailable() {
        return new ProcStatusMemory(-1, -1, -1, -1);
    }
}

package net.kdt.pojavlaunch.utils;

/** Launch-time JVM heap sizing that leaves Android room for native and graphics memory. */
public final class JvmMemoryPolicy {
    private static final int MIN_INITIAL_HEAP_MB = 128;
    private static final int MAX_INITIAL_HEAP_MB = 512;
    private static final int MODERN_32_BIT_MAX_HEAP_MB = 512;

    private JvmMemoryPolicy() {
    }

    public static int initialHeapMb(int maximumHeapMb) {
        if (maximumHeapMb <= 0) return MIN_INITIAL_HEAP_MB;
        int proportional = maximumHeapMb / 8;
        int initial = Math.max(MIN_INITIAL_HEAP_MB,
                Math.min(MAX_INITIAL_HEAP_MB, proportional));
        return Math.min(maximumHeapMb, initial);
    }

    public static int maximumHeapMb(int configuredHeapMb, boolean is32Bit, int javaVersion) {
        if (is32Bit && javaVersion >= 21) {
            return Math.min(configuredHeapMb, MODERN_32_BIT_MAX_HEAP_MB);
        }
        return configuredHeapMb;
    }

    public static boolean useLowMemoryWorkerLimits(boolean is32Bit, int javaVersion,
                                                   int totalMemoryMb) {
        return is32Bit && javaVersion >= 21 && totalMemoryMb > 0 && totalMemoryMb < 2500;
    }

    public static int activeProcessorCount(int availableProcessors, boolean lowMemoryWorkerLimits) {
        int available = Math.max(1, availableProcessors);
        return lowMemoryWorkerLimits ? Math.min(2, available) : available;
    }
}

package net.kdt.pojavlaunch.utils;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class JvmMemoryPolicyTest {
    @Test
    public void modern32BitRuntimePreservesNativeAddressSpace() {
        assertEquals(512, JvmMemoryPolicy.maximumHeapMb(696, true, 21));
        assertEquals(384, JvmMemoryPolicy.maximumHeapMb(384, true, 21));
    }

    @Test
    public void heapCapDoesNotAffect64BitOrLegacyJava() {
        assertEquals(4096, JvmMemoryPolicy.maximumHeapMb(4096, false, 21));
        assertEquals(696, JvmMemoryPolicy.maximumHeapMb(696, true, 17));
    }

    @Test
    public void largeMaximumDoesNotBecomeInitialHeap() {
        assertEquals(512, JvmMemoryPolicy.initialHeapMb(8808));
    }

    @Test
    public void commonMaximumUsesProportionalInitialHeap() {
        assertEquals(256, JvmMemoryPolicy.initialHeapMb(2048));
        assertEquals(512, JvmMemoryPolicy.initialHeapMb(4096));
    }

    @Test
    public void smallMaximumIsNeverExceeded() {
        assertEquals(96, JvmMemoryPolicy.initialHeapMb(96));
        assertEquals(128, JvmMemoryPolicy.initialHeapMb(512));
    }

    @Test
    public void modern32BitLowRamDevicesLimitConcurrentWorkers() {
        boolean limited = JvmMemoryPolicy.useLowMemoryWorkerLimits(true, 21, 1794);

        assertEquals(true, limited);
        assertEquals(2, JvmMemoryPolicy.activeProcessorCount(4, limited));
    }

    @Test
    public void workerLimitsDoNotAffect64BitDevices() {
        boolean limited = JvmMemoryPolicy.useLowMemoryWorkerLimits(false, 21, 1794);

        assertEquals(false, limited);
        assertEquals(8, JvmMemoryPolicy.activeProcessorCount(8, limited));
    }
}

package net.kdt.pojavlaunch.utils;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ProcStatusMemoryTest {
    @Test
    public void parsesKernelMemoryCounters() {
        ProcStatusMemory memory = ProcStatusMemory.parse(
                "Name:\tjava\n"
                        + "VmRSS:\t 5033164 kB\n"
                        + "RssAnon:\t 4718592 kB\n"
                        + "RssFile:\t 314572 kB\n"
                        + "VmSwap:\t 131072 kB\n");

        assertEquals(4915, memory.rssMb);
        assertEquals(4608, memory.rssAnonMb);
        assertEquals(307, memory.rssFileMb);
        assertEquals(128, memory.swapMb);
    }

    @Test
    public void missingCountersRemainUnavailable() {
        ProcStatusMemory memory = ProcStatusMemory.parse("Name:\tjava\nVmRSS:\tn/a kB\n");

        assertEquals(-1, memory.rssMb);
        assertEquals(-1, memory.rssAnonMb);
        assertEquals(-1, memory.swapMb);
    }
}

package net.kdt.pojavlaunch.customcontrols.keyboard;

import org.junit.Test;
import java.util.ArrayList;
import java.util.Arrays;
import static org.junit.Assert.*;

public class HeldKeyBindingsTest {
    @Test public void duplicateDownDoesNotRepeatAndReleaseKeepsOriginalBinding() {
        HeldKeyBindings keys = new HeldKeyBindings();
        assertEquals(42, keys.press(1, 10, 42));
        assertEquals(-1, keys.press(1, 10, 43));
        assertEquals(42, keys.release(1, 10));
        assertEquals(-1, keys.release(1, 10));
    }
    @Test public void sharedDestinationStaysPressedUntilLastSourceReleases() {
        HeldKeyBindings keys = new HeldKeyBindings();
        assertEquals(42, keys.press(1, 10, 42));
        assertEquals(-1, keys.press(1, 11, 42));
        assertEquals(-1, keys.release(1, 10));
        assertEquals(42, keys.release(1, 11));
    }
    @Test public void separatesDevicesAndReleasesOnFocusLoss() {
        HeldKeyBindings keys = new HeldKeyBindings();
        keys.press(1, 10, 42);
        keys.press(2, 10, 42);
        assertEquals(-1, keys.release(1, 10));
        ArrayList<Integer> released = new ArrayList<>();
        keys.releaseAll(released::add);
        assertEquals(Arrays.asList(42), released);
        assertEquals(-1, keys.release(2, 10));
    }
    @Test public void unplugReleasesOnlyTheRemovedDevice() {
        HeldKeyBindings keys = new HeldKeyBindings();
        keys.press(1, 10, 42);
        keys.press(2, 10, 43);
        ArrayList<Integer> released = new ArrayList<>();
        keys.releaseDevice(1, released::add);
        assertEquals(Arrays.asList(42), released);
        assertEquals(43, keys.release(2, 10));
    }
    @Test public void unplugDoesNotReleaseAnotherDevicesSharedKey() {
        HeldKeyBindings keys = new HeldKeyBindings();
        keys.press(1, 10, 42);
        keys.press(2, 10, 42);
        ArrayList<Integer> released = new ArrayList<>();
        keys.releaseDevice(1, released::add);
        assertTrue(released.isEmpty());
        assertEquals(42, keys.release(2, 10));
    }
}

package net.kdt.pojavlaunch.customcontrols.mouse;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class CursorCoordinatesTest {
    @Test public void usesActualFramebufferRatherThanRequestedScale() {
        assertEquals(500f, CursorCoordinates.toGame(1000f, 2000, 1000, 0.6f), 0.001f);
    }

    @Test public void roundedFramebufferEdgesRemainAligned() {
        assertEquals(346f, CursorCoordinates.toGame(1155f, 2310, 692, 0.3f), 0.001f);
    }

    @Test public void preservesAllNineSlotCentersAcrossResolutions() {
        for (int width : new int[]{1039, 1920, 2310}) {
            for (int target : new int[]{346, 692, 1280}) {
                for (int slot = 0; slot < 9; slot++) {
                    float x = width * (slot + 0.5f) / 9f;
                    assertEquals(slot, (int) (CursorCoordinates.toGame(x, width, target, 0.3f) * 9 / target));
                }
            }
        }
    }

    @Test public void fallsBackBeforeSurfaceIsMeasured() {
        assertEquals(30f, CursorCoordinates.toGame(100f, 0, 0, 0.3f), 0.001f);
    }
}

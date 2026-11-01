package net.kdt.pojavlaunch.customcontrols.mouse;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class GameCursorManagerTest {
    @Test
    public void unknownStyleFallsBackToDefault() {
        assertEquals(GameCursorManager.STYLE_DEFAULT,
                GameCursorManager.normalizeStyle("unsupported"));
    }

    @Test
    public void centerHotspotPlacesClickAtDrawableCenter() {
        assertArrayEquals(new int[]{-20, -10, 20, 10},
                GameCursorManager.calculateBounds(40, 20, GameCursorManager.HOTSPOT_CENTER));
    }

    @Test
    public void topLeftHotspotPreservesPointerOrigin() {
        assertArrayEquals(new int[]{0, 0, 40, 20},
                GameCursorManager.calculateBounds(40, 20, GameCursorManager.HOTSPOT_TOP_LEFT));
    }
}

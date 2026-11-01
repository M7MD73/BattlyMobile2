package net.kdt.pojavlaunch.customcontrols.mouse;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DropGestureTest {
    @Test
    public void dropGestureIsEnabledByDefault() {
        assertFalse(DropGesture.isDisabled(false, false));
    }

    @Test
    public void globalGestureSettingDisablesDropGesture() {
        assertTrue(DropGesture.isDisabled(true, false));
    }

    @Test
    public void dedicatedSettingDisablesOnlyDropGesture() {
        assertTrue(DropGesture.isDisabled(false, true));
    }

    @Test
    public void bothSettingsKeepDropGestureDisabled() {
        assertTrue(DropGesture.isDisabled(true, true));
    }
}

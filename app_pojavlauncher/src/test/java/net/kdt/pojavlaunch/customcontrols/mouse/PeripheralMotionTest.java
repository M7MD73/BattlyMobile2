package net.kdt.pojavlaunch.customcontrols.mouse;

import org.junit.Test;
import static org.junit.Assert.*;

public class PeripheralMotionTest {
    @Test public void pcModeIsIndependentOfRenderResolution() {
        assertEquals(8f, PeripheralMotion.mouseAxis(8f, 100, false, true, .3f), 0f);
        assertEquals(8f, PeripheralMotion.mouseAxis(8f, 100, false, true, 1f), 0f);
    }
    @Test public void legacyModeRetainsResolutionScaling() {
        assertEquals(4f, PeripheralMotion.mouseAxis(8f, 100, false, false, .5f), 0f);
    }
    @Test public void linearGainDoesNotDependOnSpeedOrPacketCount() {
        float single = PeripheralMotion.mouseAxis(20f, 75, false, true, 1f);
        float packets = 0;
        for (int i = 0; i < 20; i++) packets += PeripheralMotion.mouseAxis(1f, 75, false, true, 1f);
        assertEquals(single, packets, .0001f);
    }
    @Test public void supportsFractionalMotionAndAxisInversion() {
        assertEquals(-.125f, PeripheralMotion.mouseAxis(.25f, 50, true, true, 1f), 0f);
    }
    @Test public void invalidInputCannotPoisonCursor() {
        assertEquals(0f, PeripheralMotion.mouseAxis(Float.NaN, 100, false, true, 1f), 0f);
        assertEquals(0f, PeripheralMotion.mouseAxis(Float.POSITIVE_INFINITY, 100, false, true, 1f), 0f);
    }
    @Test public void stickCurvesAreBoundedAndLinearIsActuallyLinear() {
        assertEquals(.5d, PeripheralMotion.stickMagnitude(.5d, true), 0d);
        assertEquals(.25d, PeripheralMotion.stickMagnitude(.5d, false), 0d);
        assertEquals(1d, PeripheralMotion.stickMagnitude(2d, true), 0d);
    }
}

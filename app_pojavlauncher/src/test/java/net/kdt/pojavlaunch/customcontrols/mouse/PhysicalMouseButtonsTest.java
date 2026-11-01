package net.kdt.pojavlaunch.customcontrols.mouse;

import android.view.KeyEvent;
import android.view.MotionEvent;
import org.junit.Test;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

public class PhysicalMouseButtonsTest {
    @Test public void mapsAllFiveButtons() {
        int[] buttons = {1, 2, 4, 8, 16};
        for (int i = 0; i < buttons.length; i++) assertEquals(i, PhysicalMouseButtons.toGlfw(buttons[i]));
        assertEquals(-1, PhysicalMouseButtons.toGlfw(0));
        assertEquals(-1, PhysicalMouseButtons.toGlfw(3));
    }

    @Test public void mouseBackIsNotRightClickUnlessDriverReportsBtnRight() {
        assertEquals(MotionEvent.BUTTON_BACK, PhysicalMouseButtons.fromKey(KeyEvent.KEYCODE_BACK, 0));
        assertEquals(MotionEvent.BUTTON_BACK, PhysicalMouseButtons.fromKey(KeyEvent.KEYCODE_BACK, 275));
        assertEquals(MotionEvent.BUTTON_SECONDARY, PhysicalMouseButtons.fromKey(KeyEvent.KEYCODE_BACK, 273));
        assertEquals(MotionEvent.BUTTON_FORWARD, PhysicalMouseButtons.fromKey(KeyEvent.KEYCODE_FORWARD, 0));
        assertEquals(0, PhysicalMouseButtons.fromKey(KeyEvent.KEYCODE_ESCAPE, 0));
    }

    @Test public void deduplicatesMotionAndKeyWhileKeepingBothSideButtonsIndependent() {
        List<String> sent = new ArrayList<>();
        PhysicalMouseButtons input = new PhysicalMouseButtons((button, down) -> sent.add(button + ":" + down));
        input.send(1, 8, true);
        input.send(1, PhysicalMouseButtons.fromKey(KeyEvent.KEYCODE_BACK, 275), true);
        input.send(1, 16, true);
        input.send(1, 8, false);
        input.send(1, 8, false);
        input.send(1, 16, false);
        assertEquals(Arrays.asList("3:true", "4:true", "3:false", "4:false"), sent);
    }

    @Test public void focusLossReleasesHeldButtonsAndDoesNotReleaseAnotherMouseEarly() {
        List<String> sent = new ArrayList<>();
        PhysicalMouseButtons input = new PhysicalMouseButtons((button, down) -> sent.add(button + ":" + down));
        input.send(1, 8, true);
        input.send(2, 8, true);
        input.send(1, 8, false);
        input.releaseAll();
        input.releaseAll();
        input.send(2, 8, false);
        assertEquals(Arrays.asList("3:true", "3:false"), sent);
    }

    @Test public void sdlKeepsMouse4And5Order() {
        assertEquals(16, PhysicalMouseButtons.toSdlAndroidButton(3));
        assertEquals(8, PhysicalMouseButtons.toSdlAndroidButton(4));
        assertEquals(0, PhysicalMouseButtons.toSdlAndroidButton(-1));
    }
}

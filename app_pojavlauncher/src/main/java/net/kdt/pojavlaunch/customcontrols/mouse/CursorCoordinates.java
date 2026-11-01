package net.kdt.pojavlaunch.customcontrols.mouse;

public final class CursorCoordinates {
    private CursorCoordinates() {}

    public static float toGame(float coordinate, int viewSize, int gameSize, float fallbackScale) {
        // The measured framebuffer can differ from the requested resolution ratio.
        return viewSize > 0 && gameSize > 0
                ? coordinate * ((float) gameSize / viewSize) : coordinate * fallbackScale;
    }
}

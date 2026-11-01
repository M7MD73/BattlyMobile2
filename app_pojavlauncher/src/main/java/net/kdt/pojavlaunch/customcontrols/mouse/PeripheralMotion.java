package net.kdt.pojavlaunch.customcontrols.mouse;

/** Stateless gain only: no speed-dependent acceleration, rounding or delayed samples. */
public final class PeripheralMotion {
    private PeripheralMotion() {}

    public static float mouseAxis(float delta, int sensitivityPercent, boolean inverted,
                                  boolean pcMode, float renderScale) {
        if (Float.isNaN(delta) || Float.isInfinite(delta)) return 0f;
        float gain = Math.max(10, Math.min(400, sensitivityPercent)) / 100f;
        return delta * gain * (inverted ? -1f : 1f) * (pcMode ? 1f : renderScale);
    }

    public static double stickMagnitude(double magnitude, boolean linear) {
        if (Double.isNaN(magnitude) || Double.isInfinite(magnitude)) return 0;
        double bounded = Math.max(0, Math.min(1, magnitude));
        return linear ? bounded : bounded * bounded;
    }
}

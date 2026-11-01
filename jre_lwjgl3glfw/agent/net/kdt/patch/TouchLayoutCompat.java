package net.kdt.patch;

/** Empty text constraints can occur while a small TouchController panel is measuring. */
public final class TouchLayoutCompat {
    private TouchLayoutCompat() {}

    public static int coerceIn(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(value, Math.max(minimum, maximum)));
    }
}

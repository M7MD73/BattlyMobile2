package top.fifthlight.touchcontroller.relocated.kotlin.ranges;

public final class RangesKt {
    public static int coerceIn(int value, int min, int max) {
        if (max < min) throw new IllegalArgumentException("empty range");
        return Math.max(min, Math.min(value, max));
    }
}

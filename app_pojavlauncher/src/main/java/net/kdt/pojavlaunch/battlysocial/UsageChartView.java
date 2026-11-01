package net.kdt.pojavlaunch.battlysocial;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.DecelerateInterpolator;
import net.kdt.pojavlaunch.R;

/** Bounded, native chart: no WebView, rendering loop or work after detach. */
public final class UsageChartView extends View {
    private static final int GAME = 0xFF5CE7D6, LAUNCHER = 0xFFFFC263;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private long[] game = new long[0], launcher = new long[0];
    private String[] labels = new String[0], descriptions = new String[0];
    private final String[] ticks = new String[4];
    private float reveal = 1, max = 60, downX, downY;
    private int selected = -1;
    private boolean bars, moved;
    private ValueAnimator animation;
    private Selection listener;

    public interface Selection { void select(int index); }
    public UsageChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setFocusable(true);
        setClickable(true);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }

    public void setSeries(long[] game, long[] launcher, String[] labels, String[] descriptions,
                          boolean bars, Selection listener) {
        if (game.length != launcher.length || game.length != labels.length || game.length != descriptions.length)
            throw new IllegalArgumentException("Chart series lengths must match");
        stopAnimation();
        this.game = game.clone();
        this.launcher = launcher.clone();
        this.labels = labels.clone();
        this.descriptions = descriptions.clone();
        this.bars = bars;
        this.listener = listener;
        long peak = 0;
        for (int i = 0; i < game.length; i++) peak = Math.max(peak, Math.max(game[i], launcher[i]));
        max = axisMaximum(peak);
        for (int i = 0; i < 4; i++) {
            long seconds = Math.round(max * i / 3);
            ticks[i] = getResources().getString(max >= 10800 ? R.string.battly_stats_chart_hours : R.string.battly_stats_duration_minutes,
                    max >= 10800 ? seconds / 3600 : seconds / 60);
        }
        selected = game.length == 0 ? -1 : game.length - 1;
        updateDescription();
        reveal = 0;
        if (isAttachedToWindow()) animateIn();
        invalidate();
    }

    static long axisMaximum(long peak) {
        long unit = peak >= 10800 ? 3600 : 60;
        double target = Math.max(1, peak / (3.0 * unit));
        double power = Math.pow(10, Math.floor(Math.log10(target)));
        for (int step : new int[]{1, 2, 3, 5, 10}) {
            if (step * power >= target) return (long) (step * power * 3 * unit);
        }
        return peak;
    }

    static int indexAt(float x, float left, float right, int size, boolean bars) {
        if (size <= 0 || right <= left) return -1;
        float position = (x - left) / (right - left);
        int index = bars ? (int) Math.floor(position * size) : Math.round(position * (size - 1));
        return Math.max(0, Math.min(size - 1, index));
    }

    public void selectIndex(int index) {
        selected = Math.max(-1, Math.min(game.length - 1, index));
        updateDescription();
        invalidate();
    }
    public int getSelectedIndex() { return selected; }
    public boolean isAnimating() { return animation != null && animation.isRunning(); }

    private void updateDescription() {
        setContentDescription(selected >= 0 ? descriptions[selected] : getResources().getString(R.string.battly_stats_empty));
    }

    private void animateIn() {
        boolean enabled = Build.VERSION.SDK_INT >= 26 ? ValueAnimator.areAnimatorsEnabled()
                : Settings.Global.getFloat(getContext().getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1) != 0;
        if (!enabled || game.length == 0 || !isShown()) { reveal = 1; invalidate(); return; }
        animation = ValueAnimator.ofFloat(0, 1);
        animation.setDuration(650);
        animation.setInterpolator(new DecelerateInterpolator());
        animation.addUpdateListener(value -> { reveal = (float) value.getAnimatedValue(); invalidate(); });
        animation.start();
    }
    private void stopAnimation() {
        if (animation != null) { animation.cancel(); animation.removeAllUpdateListeners(); animation = null; }
        reveal = 1;
    }
    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); if (reveal < 1) animateIn(); }
    @Override protected void onDetachedFromWindow() { stopAnimation(); super.onDetachedFromWindow(); }
    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility != VISIBLE) stopAnimation();
    }
    @Override protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (visibility != VISIBLE) stopAnimation();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float left = dp(46), right = getWidth() - dp(12), top = dp(17), bottom = getHeight() - dp(30);
        if (right <= left || bottom <= top) return;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF111C22);
        canvas.drawRoundRect(0, 0, getWidth(), getHeight(), dp(6), dp(6), paint);
        paint.setTextSize(sp(10));
        for (int i = 0; i < 4; i++) {
            float y = bottom - (bottom - top) * i / 3;
            paint.setColor(0xFF2B3D44); paint.setStrokeWidth(dp(1));
            canvas.drawLine(left, y, right, y, paint);
            paint.setColor(0xFF9EAFB7); paint.setTextAlign(Paint.Align.RIGHT);
            if (ticks[i] != null) canvas.drawText(ticks[i], left - dp(7), y + dp(3), paint);
        }
        if (game.length == 0) return;
        for (int i = 0; i < 5; i++) {
            float x = left + (right - left) * i / 4;
            paint.setColor(0xFF213139);
            canvas.drawLine(x, top, x, bottom, paint);
        }
        if (selected >= 0) {
            float x = x(selected, left, right);
            paint.setColor(0xFF59747D);
            canvas.drawLine(x, top, x, bottom, paint);
        }
        drawSeries(canvas, game, GAME, left, right, top, bottom, false);
        drawSeries(canvas, launcher, LAUNCHER, left, right, top, bottom, true);
        int[] indices = {0, (game.length - 1) / 2, game.length - 1};
        paint.setTextSize(sp(10)); paint.setColor(0xFFB6C8CD); paint.setStyle(Paint.Style.FILL);
        for (int n = 0; n < indices.length; n++) {
            if (n > 0 && indices[n] == indices[n - 1]) continue;
            paint.setTextAlign(n == 0 ? Paint.Align.LEFT : n == 2 ? Paint.Align.RIGHT : Paint.Align.CENTER);
            canvas.drawText(labels[indices[n]], x(indices[n], left, right),
                    getHeight() - dp(10), paint);
        }
    }

    private void drawSeries(Canvas canvas, long[] values, int color, float left, float right, float top, float bottom, boolean second) {
        paint.setStyle(Paint.Style.FILL);
        if (bars) {
            float slot = (right - left) / values.length;
            float width = Math.max(1, slot * .31f);
            for (int i = 0; i < values.length; i++) {
                if (values[i] <= 0) continue;
                float center = x(i, left, right) + (second ? width * .55f : -width * .55f);
                float y = y(values[i], top, bottom);
                paint.setColor(color); paint.setAlpha(i == selected ? 255 : 175);
                canvas.drawRect(center - width / 2, y, center + width / 2, bottom, paint);
                paint.setAlpha(255);
                canvas.drawRect(center - width / 2, y, center + width / 2, Math.min(bottom, y + dp(2)), paint);
            }
        } else {
            path.reset(); path.moveTo(x(0, left, right), bottom);
            for (int i = 0; i < values.length; i++) path.lineTo(x(i, left, right), y(values[i], top, bottom));
            path.lineTo(x(values.length - 1, left, right), bottom); path.close();
            paint.setColor(color); paint.setAlpha(18); canvas.drawPath(path, paint);
            path.reset(); path.moveTo(x(0, left, right), y(values[0], top, bottom));
            for (int i = 1; i < values.length; i++) path.lineTo(x(i, left, right), y(values[i], top, bottom));
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStrokeWidth(dp(7)); paint.setAlpha(22); canvas.drawPath(path, paint);
            paint.setStrokeWidth(dp(1.8f)); paint.setAlpha(255); canvas.drawPath(path, paint);
            paint.setStyle(Paint.Style.FILL);
        }
        if (selected >= 0) {
            float x = x(selected, left, right), y = y(values[selected], top, bottom);
            if (bars) x += Math.max(1, (right - left) / values.length * .31f) * (second ? .55f : -.55f);
            paint.setColor(color); paint.setAlpha(35); canvas.drawCircle(x, y, dp(7), paint);
            paint.setAlpha(255); canvas.drawCircle(x, y, dp(3), paint);
        }
        paint.setAlpha(255);
    }
    private float x(int index, float left, float right) {
        return bars ? left + (index + .5f) * (right - left) / game.length
                : game.length == 1 ? (left + right) / 2 : left + index * (right - left) / (game.length - 1);
    }
    private float y(long value, float top, float bottom) { return bottom - (bottom - top) * Math.max(0, value) / max * reveal; }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (game.length == 0) return false;
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) { downX = event.getX(); downY = event.getY(); moved = false; return true; }
        if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
            int slop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
            moved |= Math.abs(event.getX() - downX) > slop || Math.abs(event.getY() - downY) > slop;
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_UP) {
            if (!moved) { choose(indexAt(event.getX(), dp(46), getWidth() - dp(12), game.length, bars)); performClick(); }
            return true;
        }
        return super.onTouchEvent(event);
    }
    @Override public boolean performClick() { super.performClick(); return true; }
    private void choose(int index) { selectIndex(index); if (listener != null && selected >= 0) listener.select(selected); }
    @Override public boolean onKeyDown(int code, KeyEvent event) {
        if (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT) {
            choose(Math.max(0, selected + (code == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1))); return true;
        }
        return super.onKeyDown(code, event);
    }
    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setScrollable(game.length > 1);
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);
    }
    @Override public boolean performAccessibilityAction(int action, Bundle arguments) {
        if (action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD || action == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) {
            choose(Math.max(0, selected + (action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD ? 1 : -1)));
            sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_VIEW_SELECTED); return true;
        }
        return super.performAccessibilityAction(action, arguments);
    }
    private float dp(float value) { return value * getResources().getDisplayMetrics().density; }
    private float sp(float value) { return value * getResources().getDisplayMetrics().scaledDensity; }
}

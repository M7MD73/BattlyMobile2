package net.kdt.pojavlaunch.utils;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ReplacementSpan;
import android.widget.TextView;

import net.kdt.pojavlaunch.BuildConfig;
import net.kdt.pojavlaunch.R;

/** Discovery labels for this release, separate from the Worlds first-visit campaign. */
public final class NewFeatureBadge {
    private NewFeatureBadge() {}

    public static boolean isCurrentRelease() {
        return "2.2.7".equals(BuildConfig.VERSION_NAME);
    }

    public static boolean isNewPreference(String key) {
        if (key == null) return false;
        switch (key) {
            case "control_screen_setting":
            case "java_screen_setting":
            case "native_library_plugins":
            case "bundled_native_dependencies":
            case "cursorStyle":
            case "cursorCustomImage":
            case "cursorOpacity":
            case "cursorHotspot":
                return true;
            default:
                return false;
        }
    }

    public static void apply(TextView view) {
        view.setText(title(view.getContext(), view.getText()));
    }

    public static CharSequence title(Context context, CharSequence title) {
        if (!isCurrentRelease() || title == null) return title;
        if (title instanceof Spanned
                && ((Spanned) title).getSpans(0, title.length(), BadgeSpan.class).length > 0) return title;
        SpannableStringBuilder text = new SpannableStringBuilder(title).append("  ");
        int start = text.length();
        text.append(context.getString(R.string.battly_new_badge));
        text.setSpan(new BadgeSpan(), start, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return text;
    }

    private static final class BadgeSpan extends ReplacementSpan {
        private Paint labelPaint(Paint source) {
            Paint label = new Paint(source);
            label.setTextSize(source.getTextSize() * .7f);
            label.setFakeBoldText(true);
            label.setTextAlign(Paint.Align.LEFT);
            return label;
        }

        @Override public int getSize(Paint paint, CharSequence text, int start, int end,
                                      Paint.FontMetricsInt metrics) {
            if (metrics != null) paint.getFontMetricsInt(metrics);
            return (int) Math.ceil(labelPaint(paint).measureText(text, start, end) + paint.getTextSize() * .65f);
        }

        @Override public void draw(Canvas canvas, CharSequence text, int start, int end,
                                   float x, int top, int y, int bottom, Paint paint) {
            Paint label = labelPaint(paint);
            Paint.FontMetrics fm = label.getFontMetrics();
            float pad = paint.getTextSize() * .325f;
            float verticalPad = paint.getTextSize() * .1f;
            float baseline = y - paint.getTextSize() * .08f;
            label.setColor(0xFF79E0C1);
            canvas.drawRoundRect(x, baseline + fm.ascent - verticalPad,
                    x + getSize(paint, text, start, end, null), baseline + fm.descent + verticalPad,
                    verticalPad * 2, verticalPad * 2, label);
            label.setColor(0xFF10221E);
            canvas.drawText(text, start, end, x + pad, baseline, label);
        }
    }
}

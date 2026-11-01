package net.kdt.pojavlaunch.customcontrols.mouse;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public final class GameCursorManager {
    public static final String STYLE_DEFAULT = "default";
    public static final String STYLE_DOT = "dot";
    public static final String STYLE_CROSSHAIR = "crosshair";
    public static final String STYLE_CUSTOM = "custom";
    public static final String HOTSPOT_TOP_LEFT = "top_left";
    public static final String HOTSPOT_CENTER = "center";

    private static final long MAX_SOURCE_BYTES = 8L * 1024L * 1024L;
    private static final int MAX_BITMAP_EDGE = 256;
    private static final String CURSOR_FILE_NAME = "game-cursor.png";

    private GameCursorManager() {
    }

    @NonNull
    public static Drawable createDrawable(@NonNull Context context) {
        String style = normalizeStyle(LauncherPreferences.PREF_CURSOR_STYLE);
        Drawable drawable;
        if (STYLE_CUSTOM.equals(style)) {
            drawable = loadCustomDrawable(context, LauncherPreferences.PREF_CURSOR_CUSTOM_PATH);
            if (drawable == null) drawable = defaultDrawable(context);
        } else if (STYLE_DOT.equals(style)) {
            drawable = new ShapeCursorDrawable(false);
        } else if (STYLE_CROSSHAIR.equals(style)) {
            drawable = new ShapeCursorDrawable(true);
        } else {
            drawable = defaultDrawable(context);
        }
        drawable.setAlpha(Math.round(255f * clamp(LauncherPreferences.PREF_CURSOR_OPACITY, 20, 100) / 100f));
        return drawable;
    }

    public static void applyBounds(@NonNull Drawable drawable, float scale, @Nullable String hotspot) {
        int intrinsicWidth = drawable.getIntrinsicWidth();
        int intrinsicHeight = drawable.getIntrinsicHeight();
        if (intrinsicWidth <= 0 || intrinsicHeight <= 0) {
            intrinsicWidth = 36;
            intrinsicHeight = 54;
        }
        int maximumEdge = Math.max(1, Math.round(54f * scale));
        float ratio = Math.min((float) maximumEdge / intrinsicWidth, (float) maximumEdge / intrinsicHeight);
        int width = Math.max(1, Math.round(intrinsicWidth * ratio));
        int height = Math.max(1, Math.round(intrinsicHeight * ratio));
        int[] bounds = calculateBounds(width, height, hotspot);
        drawable.setBounds(bounds[0], bounds[1], bounds[2], bounds[3]);
    }

    static int[] calculateBounds(int width, int height, @Nullable String hotspot) {
        if (HOTSPOT_CENTER.equals(hotspot)) {
            int left = -width / 2;
            int top = -height / 2;
            return new int[]{left, top, left + width, top + height};
        }
        return new int[]{0, 0, width, height};
    }

    @NonNull
    public static String importCustomCursor(@NonNull Context context, @NonNull Uri uri) throws IOException {
        byte[] source = readBounded(context, uri);
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(source, 0, source.length, bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw new IOException(context.getString(R.string.cursor_custom_invalid));
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight);
        Bitmap bitmap = BitmapFactory.decodeByteArray(source, 0, source.length, options);
        if (bitmap == null) throw new IOException(context.getString(R.string.cursor_custom_invalid));
        Bitmap output = bitmap;
        int largestEdge = Math.max(bitmap.getWidth(), bitmap.getHeight());
        if (largestEdge > MAX_BITMAP_EDGE) {
            float scale = (float) MAX_BITMAP_EDGE / largestEdge;
            output = Bitmap.createScaledBitmap(bitmap,
                    Math.max(1, Math.round(bitmap.getWidth() * scale)),
                    Math.max(1, Math.round(bitmap.getHeight() * scale)), true);
        }

        File destination = new File(context.getFilesDir(), CURSOR_FILE_NAME);
        File pending = new File(context.getFilesDir(), CURSOR_FILE_NAME + ".pending");
        try (FileOutputStream stream = new FileOutputStream(pending)) {
            if (!output.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                throw new IOException(context.getString(R.string.cursor_custom_invalid));
            }
        } finally {
            if (output != bitmap) output.recycle();
            bitmap.recycle();
        }
        File backup = new File(context.getFilesDir(), CURSOR_FILE_NAME + ".backup");
        backup.delete();
        if (destination.exists() && !destination.renameTo(backup)) {
            pending.delete();
            throw new IOException(context.getString(R.string.cursor_custom_invalid));
        }
        if (!pending.renameTo(destination)) {
            backup.renameTo(destination);
            pending.delete();
            throw new IOException(context.getString(R.string.cursor_custom_invalid));
        }
        backup.delete();
        return destination.getAbsolutePath();
    }

    @NonNull
    public static String normalizeStyle(@Nullable String style) {
        if (STYLE_DOT.equals(style) || STYLE_CROSSHAIR.equals(style) || STYLE_CUSTOM.equals(style)) {
            return style;
        }
        return STYLE_DEFAULT;
    }

    private static int sampleSize(int width, int height) {
        int sample = 1;
        while (width / sample > MAX_BITMAP_EDGE * 2 || height / sample > MAX_BITMAP_EDGE * 2) {
            sample *= 2;
        }
        return sample;
    }

    @NonNull
    private static byte[] readBounded(Context context, Uri uri) throws IOException {
        try (InputStream input = context.getContentResolver().openInputStream(uri);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (input == null) throw new IOException(context.getString(R.string.cursor_custom_invalid));
            byte[] buffer = new byte[16 * 1024];
            long total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > MAX_SOURCE_BYTES) {
                    throw new IOException(context.getString(R.string.cursor_custom_too_large));
                }
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    @Nullable
    private static Drawable loadCustomDrawable(Context context, String path) {
        if (path == null || path.trim().isEmpty()) return null;
        Bitmap bitmap = BitmapFactory.decodeFile(path);
        return bitmap == null ? null : new BitmapDrawable(context.getResources(), bitmap);
    }

    @NonNull
    private static Drawable defaultDrawable(Context context) {
        Drawable drawable = ContextCompat.getDrawable(context, R.drawable.ic_mouse_pointer);
        if (drawable == null) return new ShapeCursorDrawable(true);
        return drawable.mutate();
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class ShapeCursorDrawable extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final boolean crosshair;
        private int alpha = 255;

        ShapeCursorDrawable(boolean crosshair) {
            this.crosshair = crosshair;
            paint.setColor(Color.WHITE);
            paint.setStrokeWidth(3f);
            paint.setStyle(crosshair ? Paint.Style.STROKE : Paint.Style.FILL);
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            paint.setAlpha(alpha);
            float centerX = getBounds().exactCenterX();
            float centerY = getBounds().exactCenterY();
            float radius = Math.min(getBounds().width(), getBounds().height()) * (crosshair ? 0.38f : 0.22f);
            if (crosshair) {
                canvas.drawCircle(centerX, centerY, radius * 0.42f, paint);
                canvas.drawLine(centerX - radius, centerY, centerX - radius * 0.55f, centerY, paint);
                canvas.drawLine(centerX + radius * 0.55f, centerY, centerX + radius, centerY, paint);
                canvas.drawLine(centerX, centerY - radius, centerX, centerY - radius * 0.55f, paint);
                canvas.drawLine(centerX, centerY + radius * 0.55f, centerX, centerY + radius, paint);
            } else {
                canvas.drawCircle(centerX, centerY, radius, paint);
            }
        }

        @Override
        public void setAlpha(int alpha) {
            this.alpha = alpha;
            invalidateSelf();
        }

        @Override
        public void setColorFilter(@Nullable android.graphics.ColorFilter colorFilter) {
            paint.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }

        @Override
        public int getIntrinsicWidth() {
            return 48;
        }

        @Override
        public int getIntrinsicHeight() {
            return 48;
        }
    }
}

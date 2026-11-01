package org.libsdl.app;

import android.content.Context;
import android.os.Looper;
import android.os.SystemClock;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;
import java.util.concurrent.atomic.AtomicInteger;

/** Keeps Android's IME synchronized with SDL's text-input lifecycle. */
final class SdlImeController {
    enum Source { GAME, LAUNCHER, BACK }

    private static final String TAG = "SdlImeController";
    private static final int HEIGHT_PADDING = 15;
    private static final long SHOW_RETRY_DELAY_MS = 100L;
    private static final long RETURN_DEDUPLICATION_MS = 250L;

    private static SDLDummyEdit sEdit;
    private static boolean sGameTextInputActive;
    private static boolean sKeyboardVisible;
    private static long sSuppressUntil;
    private static long sLastReturnAt;
    private static final AtomicInteger GENERATION = new AtomicInteger();

    private SdlImeController() {
    }

    static boolean isKeyboardActive() {
        return sKeyboardVisible || (sEdit != null && sEdit.isEnabled());
    }

    static boolean isTextInputAccepted() {
        return sGameTextInputActive || isKeyboardActive();
    }

    static void backspace() {
        post(() -> {
            if (sEdit != null && sEdit.ic != null) sEdit.ic.deleteSurroundingTextInCodePoints(1, 0);
        });
    }

    static void reset() {
        GENERATION.incrementAndGet();
        removeEditor(false);
        sGameTextInputActive = false;
        sKeyboardVisible = false;
        sSuppressUntil = 0L;
        sLastReturnAt = 0L;
    }

    static boolean requestShow(Source source) {
        return requestShow(source,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_NORMAL,
                0, 0, 1, 1);
    }

    static boolean requestShow(Source source, int inputType, int x, int y, int w, int h) {
        Log.i(TAG, "Text input requested by " + source);
        if (source == Source.GAME) {
            sGameTextInputActive = true;
            if (!net.kdt.pojavlaunch.customcontrols.keyboard.GameKeyboardPreferences
                    .autoOpen(SDLActivity.getContext())) return true;
        } else {
            sSuppressUntil = 0L;
        }
        final int generation = GENERATION.incrementAndGet();
        return post(() -> showOnUiThread(source, inputType, x, y, w, h, generation));
    }

    static void requestHide(Source source) {
        Log.i(TAG, "Text input closed by " + source);
        if (source == Source.GAME) {
            sGameTextInputActive = false;
        } else {
            // Some IMEs cause SDL to briefly request text input again while Enter is handled.
            sSuppressUntil = SystemClock.uptimeMillis() + 700L;
        }
        final int generation = GENERATION.incrementAndGet();
        post(() -> {
            if (generation == GENERATION.get()) hideOnUiThread(source);
        });
    }

    static boolean submitReturn() {
        long now = SystemClock.uptimeMillis();
        synchronized (SdlImeController.class) {
            if (now - sLastReturnAt < RETURN_DEDUPLICATION_MS) return true;
            sLastReturnAt = now;
        }

        // A soft-keyboard editor action is not an SDL key event. Minecraft needs
        // the balanced key pair to submit chat and confirm focused text fields.
        SDLActivity.onNativeKeyDown(android.view.KeyEvent.KEYCODE_ENTER);
        SDLActivity.onNativeKeyUp(android.view.KeyEvent.KEYCODE_ENTER);
        SDLActivity.onNativeSoftReturnKey();
        requestHide(Source.LAUNCHER);
        return true;
    }

    static void notifyVisibilityChanged(boolean visible) {
        if (visible && !isTextInputAccepted()) {
            hideOnUiThread(Source.BACK);
            return;
        }
        setKeyboardVisible(visible);
    }

    private static boolean post(Runnable action) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action.run();
            return true;
        }
        return SDLActivity.getTextInputHandler().post(action);
    }

    private static void showOnUiThread(Source source, int inputType,
                                       int x, int y, int w, int h, int generation) {
        if (generation != GENERATION.get()) return;
        if (source == Source.GAME && !sGameTextInputActive) return;

        long remainingSuppression = sSuppressUntil - SystemClock.uptimeMillis();
        if (source == Source.GAME && remainingSuppression > 0L) {
            SDLActivity.getTextInputHandler().postDelayed(() -> {
                if (generation == GENERATION.get() && sGameTextInputActive) {
                    showOnUiThread(source, inputType, x, y, w, h, generation);
                }
            }, remainingSuppression);
            return;
        }

        ViewGroup host = SDLActivity.getTextInputHostLayout();
        Context context = SDLActivity.getContext();
        if (host == null || context == null) {
            Log.w(TAG, "Ignoring text input request without an Android host");
            return;
        }

        if (sEdit == null) {
            sEdit = new SDLDummyEdit(context);
            host.addView(sEdit, makeLayoutParams(host, x, y, w, h));
        } else {
            if (sEdit.getParent() != host) {
                if (sEdit.getParent() instanceof ViewGroup) {
                    ((ViewGroup) sEdit.getParent()).removeView(sEdit);
                }
                host.addView(sEdit, makeLayoutParams(host, x, y, w, h));
            } else {
                sEdit.setLayoutParams(makeLayoutParams(host, x, y, w, h));
            }
        }

        boolean wasActive = sEdit.isEnabled() && sEdit.hasFocus();
        sEdit.setInputType(inputType);
        sEdit.setEnabled(true);
        sEdit.setFocusable(true);
        sEdit.setFocusableInTouchMode(true);
        sEdit.setVisibility(View.VISIBLE);
        sEdit.requestFocus();

        InputMethodManager imm = (InputMethodManager)
                context.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm == null) return;
        if (!wasActive || !imm.isActive(sEdit)) imm.restartInput(sEdit);
        boolean accepted = imm.showSoftInput(sEdit, InputMethodManager.SHOW_IMPLICIT);
        if (accepted || imm.isAcceptingText()) {
            setKeyboardVisible(true);
        }

        // requestFocus and the window attachment can complete one frame after SDL's request.
        SDLActivity.getTextInputHandler().postDelayed(() -> {
            if (generation != GENERATION.get() || sEdit == null || !isTextInputAccepted()) return;
            sEdit.requestFocus();
            InputMethodManager retryImm = (InputMethodManager)
                    context.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (retryImm != null && !retryImm.isActive(sEdit)) retryImm.restartInput(sEdit);
            if (retryImm != null
                    && (retryImm.showSoftInput(sEdit, InputMethodManager.SHOW_IMPLICIT)
                    || retryImm.isAcceptingText())) {
                setKeyboardVisible(true);
            }
        }, SHOW_RETRY_DELAY_MS);
    }

    private static ViewGroup.LayoutParams makeLayoutParams(ViewGroup host,
                                                            int x, int y, int w, int h) {
        int width = Math.max(1, w);
        int height = Math.max(1, h + HEIGHT_PADDING);
        if (host instanceof RelativeLayout) {
            RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(width, height);
            params.leftMargin = Math.max(0, x);
            params.topMargin = Math.max(0, y);
            return params;
        }
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(width, height);
        params.leftMargin = Math.max(0, x);
        params.topMargin = Math.max(0, y);
        return params;
    }

    private static void hideOnUiThread(Source source) {
        removeEditor(true);
        if (source != Source.GAME && !sGameTextInputActive) {
            sKeyboardVisible = false;
        }
    }

    private static void removeEditor(boolean notifyNative) {
        Context context = SDLActivity.getContext();
        if (sEdit != null) {
            InputMethodManager imm = context == null ? null : (InputMethodManager)
                    context.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null && sEdit.getWindowToken() != null) {
                imm.hideSoftInputFromWindow(sEdit.getWindowToken(), 0);
            }
            sEdit.clearFocus();
            sEdit.setEnabled(false);
            if (sEdit.getParent() instanceof ViewGroup) {
                ((ViewGroup) sEdit.getParent()).removeView(sEdit);
            }
            sEdit = null;
        }
        if (notifyNative) setKeyboardVisible(false);
    }

    private static void setKeyboardVisible(boolean visible) {
        if (sKeyboardVisible == visible) return;
        sKeyboardVisible = visible;
        try {
            if (visible) {
                SDLActivity.onNativeScreenKeyboardShown();
            } else {
                SDLActivity.onNativeScreenKeyboardHidden();
            }
        } catch (UnsatisfiedLinkError ignored) {
            // SDL may not have completed JNI initialization during activity recreation.
        }
    }
}

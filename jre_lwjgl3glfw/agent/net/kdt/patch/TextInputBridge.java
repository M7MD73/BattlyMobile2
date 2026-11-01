package net.kdt.patch;

import org.lwjgl.glfw.GLFW;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/** Bridges Minecraft text-widget focus to Android's soft keyboard without linking Minecraft to Android. */
public final class TextInputBridge {
    private static final long HIDE_DELAY_MS = 120L;
    private static final AtomicLong FOCUS_GENERATION = new AtomicLong();
    private static final ScheduledExecutorService EXECUTOR =
            Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
                @Override
                public Thread newThread(Runnable runnable) {
                    Thread thread = new Thread(runnable, "Battly text input bridge");
                    thread.setDaemon(true);
                    return thread;
                }
            });

    private TextInputBridge() {
    }

    public static void onTextFocusChanged(boolean focused) {
        long generation = FOCUS_GENERATION.incrementAndGet();
        if (focused) {
            invoke(true);
            return;
        }
        EXECUTOR.schedule(() -> {
            if (FOCUS_GENERATION.get() == generation) invoke(false);
        }, HIDE_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    private static void invoke(boolean show) {
        try {
            GLFW.setAndroidTextInputActive(show);
        } catch (Throwable error) {
            System.err.println("[BattlyInput] Unable to "
                    + (show ? "show" : "hide") + " Android keyboard: " + error);
        }
    }
}

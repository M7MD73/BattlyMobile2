package net.kdt.patch.audio;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Mixer;

/** Keeps the Android capture mixer visible across Forge's isolated class loaders. */
public final class AndroidAudioSystemBridge {
    private static final AndroidCaptureMixerProvider ANDROID_PROVIDER =
            new AndroidCaptureMixerProvider();
    private static final Mixer.Info[] ANDROID_INFOS = ANDROID_PROVIDER.getMixerInfo();

    private AndroidAudioSystemBridge() {
    }

    public static Mixer.Info[] getMixerInfo() {
        Mixer.Info[] discovered = AudioSystem.getMixerInfo();
        for (Mixer.Info info : discovered) {
            if (AndroidCaptureMixerProvider.isAndroidMixer(info)) return discovered;
        }
        List<Mixer.Info> mixers = new ArrayList<>(Arrays.asList(discovered));
        mixers.addAll(Arrays.asList(ANDROID_INFOS));
        System.out.println("[BattlyAudio] Exposed Android microphones to isolated mod class loader");
        return mixers.toArray(new Mixer.Info[0]);
    }

    public static Mixer getMixer(Mixer.Info info) {
        if (AndroidCaptureMixerProvider.isAndroidMixer(info)) {
            return ANDROID_PROVIDER.getMixer(info);
        }
        return AudioSystem.getMixer(info);
    }
}

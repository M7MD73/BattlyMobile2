package net.kdt.pojavlaunch.battlyworlds;

import android.media.MediaRecorder;
import android.media.audiofx.AcousticEchoCanceler;

final class BattlyWorldsVoiceAudioPolicy {
    final int audioSource;
    final boolean hardwareEchoCanceler;
    final boolean hardwareNoiseSuppressor;

    private BattlyWorldsVoiceAudioPolicy(int audioSource,
                                        boolean hardwareEchoCanceler,
                                        boolean hardwareNoiseSuppressor) {
        this.audioSource = audioSource;
        this.hardwareEchoCanceler = hardwareEchoCanceler;
        this.hardwareNoiseSuppressor = hardwareNoiseSuppressor;
    }

    static BattlyWorldsVoiceAudioPolicy forInput(boolean externalInput) {
        boolean echoCancelerAvailable;
        try {
            echoCancelerAvailable = AcousticEchoCanceler.isAvailable();
        } catch (Throwable ignored) {
            echoCancelerAvailable = false;
        }
        return forInput(externalInput, echoCancelerAvailable);
    }

    static BattlyWorldsVoiceAudioPolicy forInput(boolean externalInput,
                                                 boolean echoCancelerAvailable) {
        if (externalInput) {
            return new BattlyWorldsVoiceAudioPolicy(
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    echoCancelerAvailable, true);
        }

        // Keep MIC because some vendor VOICE_COMMUNICATION implementations pick
        // a secondary microphone. Hardware AEC still receives Android's complete
        // playback reference, including Minecraft, and prevents speaker bleed.
        return new BattlyWorldsVoiceAudioPolicy(
                MediaRecorder.AudioSource.MIC, echoCancelerAvailable, false);
    }
}

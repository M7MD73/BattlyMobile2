package net.kdt.pojavlaunch.battlyworlds;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.media.MediaRecorder;

import org.junit.Test;

public class BattlyWorldsVoiceAudioPolicyTest {
    @Test
    public void builtInMicrophoneAvoidsBrokenVendorAudioSource() {
        BattlyWorldsVoiceAudioPolicy policy = BattlyWorldsVoiceAudioPolicy.forInput(false, true);

        assertTrue(policy.audioSource == MediaRecorder.AudioSource.MIC);
        assertTrue(policy.hardwareEchoCanceler);
        assertFalse(policy.hardwareNoiseSuppressor);
    }

    @Test
    public void unavailableEchoCancelerIsNotRequested() {
        BattlyWorldsVoiceAudioPolicy policy = BattlyWorldsVoiceAudioPolicy.forInput(false, false);

        assertFalse(policy.hardwareEchoCanceler);
    }

    @Test
    public void externalMicrophoneKeepsCommunicationProcessing() {
        BattlyWorldsVoiceAudioPolicy policy = BattlyWorldsVoiceAudioPolicy.forInput(true, true);

        assertTrue(policy.audioSource == MediaRecorder.AudioSource.VOICE_COMMUNICATION);
        assertTrue(policy.hardwareEchoCanceler);
        assertTrue(policy.hardwareNoiseSuppressor);
    }
}

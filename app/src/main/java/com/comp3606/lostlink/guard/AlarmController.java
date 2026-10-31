package com.comp3606.lostlink.guard;

import android.content.Context;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;

import com.comp3606.lostlink.util.LiveEvents;

// The RING command: a synthesised siren at full alarm volume, vibration, a strobing torch,
// all stopped automatically by a CountDownTimer.
public final class AlarmController {
    private static final int SAMPLE_RATE = 22050;

    private static final Handler main = new Handler(Looper.getMainLooper());
    private static AudioTrack siren;
    private static CountDownTimer timer;
    private static Vibrator vibrator;
    private static CameraManager cameras;
    private static String torchId;
    private static boolean torchOn;
    private static int secondsLeft;
    private static int totalSeconds;

    private AlarmController() {}

    public static boolean isRinging() { return siren != null; }

    public static int secondsLeft() { return secondsLeft; }

    public static int totalSeconds() { return totalSeconds; }

    public static void start(Context context, int seconds) {
        Context c = context.getApplicationContext();
        release();

        // The ALARM stream still plays when the ringer is on silent or vibrate.
        AudioManager audio = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        try {
            audio.setStreamVolume(AudioManager.STREAM_ALARM, audio.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0);
        } catch (SecurityException ignored) {
            // Do Not Disturb can block volume changes; the siren still plays at the current level.
        }

        siren = buildSiren();
        siren.play();
        startVibration(c);
        startStrobe(c);

        totalSeconds = seconds;
        secondsLeft = seconds;
        timer = new CountDownTimer(seconds * 1000L, 250) {
            @Override
            public void onTick(long millisUntilFinished) {
                int left = (int) ((millisUntilFinished + 999) / 1000);
                if (left != secondsLeft) {
                    secondsLeft = left;
                    LiveEvents.post(LiveEvents.RING_TICK, left);
                }
            }

            @Override
            public void onFinish() {
                stop();
            }
        }.start();
        LiveEvents.post(LiveEvents.RING_TICK, seconds);
    }

    public static void stop() {
        boolean wasRinging = isRinging();
        release();
        if (wasRinging) LiveEvents.post(LiveEvents.RING_STOPPED, null);
    }

    private static void release() {
        if (timer != null) timer.cancel();
        timer = null;
        if (siren != null) {
            try {
                siren.stop();
            } catch (IllegalStateException ignored) {
            }
            siren.release();
            siren = null;
        }
        if (vibrator != null) vibrator.cancel();
        main.removeCallbacks(strobe);
        setTorch(false);
        secondsLeft = 0;
    }

    // One second of a "wail" siren: the pitch sweeps 650 Hz -> 1500 Hz -> 650 Hz, looped forever.
    private static AudioTrack buildSiren() {
        int frames = SAMPLE_RATE;
        short[] pcm = new short[frames];
        double phase = 0;
        for (int i = 0; i < frames; i++) {
            double t = (double) i / frames;
            double sweep = t < 0.5 ? t * 2 : (1 - t) * 2;
            double freq = 650 + 850 * sweep;
            phase += 2 * Math.PI * freq / SAMPLE_RATE;
            double sample = Math.max(-0.8, Math.min(0.8, Math.sin(phase) * 1.6)) / 0.8;
            pcm[i] = (short) (sample * 30000);
        }
        AudioTrack track = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build())
                .setAudioFormat(new AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build())
                .setBufferSizeInBytes(frames * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build();
        track.write(pcm, 0, frames);
        track.setLoopPoints(0, frames, -1);
        return track;
    }

    private static void startVibration(Context c) {
        if (Build.VERSION.SDK_INT >= 31) {
            vibrator = ((VibratorManager) c.getSystemService(Context.VIBRATOR_MANAGER_SERVICE)).getDefaultVibrator();
        } else {
            vibrator = (Vibrator) c.getSystemService(Context.VIBRATOR_SERVICE);
        }
        if (vibrator != null && vibrator.hasVibrator()) {
            vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0, 700, 300}, 0));
        }
    }

    private static final Runnable strobe = new Runnable() {
        @Override
        public void run() {
            setTorch(!torchOn);
            main.postDelayed(this, 220);
        }
    };

    private static void startStrobe(Context c) {
        try {
            cameras = (CameraManager) c.getSystemService(Context.CAMERA_SERVICE);
            torchId = null;
            for (String id : cameras.getCameraIdList()) {
                Boolean flash = cameras.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                if (Boolean.TRUE.equals(flash)) {
                    torchId = id;
                    break;
                }
            }
            if (torchId != null) main.post(strobe);
        } catch (Exception ignored) {
            // No camera / flash (e.g. the emulator): the siren still works.
        }
    }

    private static void setTorch(boolean on) {
        if (cameras == null || torchId == null) return;
        try {
            cameras.setTorchMode(torchId, on);
            torchOn = on;
        } catch (Exception ignored) {
        }
    }
}

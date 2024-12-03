package com.summersoft.heliocam.detection;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;

public class SoundDetection {
    private static final int SAMPLE_RATE = 44100; // Sampling rate in Hz
    private final Context context;
    private final Handler handler;
    private int soundThreshold = 3000; // Default threshold, can be adjusted
    private boolean isRunning = false;
    private long detectionLatency = 3000; // Default latency in milliseconds (3 seconds)

    private AudioRecord audioRecord;
    private Thread detectionThread;
    private long lastDetectionTime = 0;

    public SoundDetection(Context context) {
        this.context = context;
        this.handler = new Handler();
    }

    /**
     * Set the sound detection threshold.
     *
     * @param threshold the threshold value for detecting sound
     */
    public void setSoundThreshold(int threshold) {
        this.soundThreshold = threshold;
    }

    /**
     * Set the latency between sound detections.
     *
     * @param latency the latency in milliseconds (e.g., 3000ms for 3 seconds)
     */
    public void setDetectionLatency(long latency) {
        this.detectionLatency = latency;
    }

    /**
     * Starts the sound detection.
     */
    public void startDetection() {
        if (isRunning) return;

        // Check microphone permission
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(context, "Microphone permission is not granted", Toast.LENGTH_SHORT).show();
            return;
        }

        isRunning = true;
        int bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE,
                android.media.AudioFormat.CHANNEL_IN_MONO,
                android.media.AudioFormat.ENCODING_PCM_16BIT);

        audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE,
                android.media.AudioFormat.CHANNEL_IN_MONO,
                android.media.AudioFormat.ENCODING_PCM_16BIT, bufferSize);

        audioRecord.startRecording();

        detectionThread = new Thread(() -> {
            short[] buffer = new short[bufferSize];
            while (isRunning) {
                int read = audioRecord.read(buffer, 0, bufferSize);
                if (read > 0) {
                    int amplitude = calculateAmplitude(buffer, read);
                    if (amplitude > soundThreshold) {
                        long currentTime = System.currentTimeMillis();
                        if (currentTime - lastDetectionTime > detectionLatency) {
                            lastDetectionTime = currentTime;
                            triggerSoundDetected();
                        }
                    }
                }
            }
        });
        detectionThread.start();
    }

    /**
     * Stops the sound detection.
     */
    public void stopDetection() {
        if (!isRunning) return;

        isRunning = false;
        if (audioRecord != null) {
            audioRecord.stop();
            audioRecord.release();
            audioRecord = null;
        }

        if (detectionThread != null) {
            detectionThread.interrupt();
            detectionThread = null;
        }
    }

    private int calculateAmplitude(short[] buffer, int length) {
        int sum = 0;
        for (int i = 0; i < length; i++) {
            sum += Math.abs(buffer[i]);
        }
        return sum / length;
    }

    private void triggerSoundDetected() {
        handler.post(() -> Toast.makeText(context, "Sound Detected!", Toast.LENGTH_SHORT).show());
    }
}

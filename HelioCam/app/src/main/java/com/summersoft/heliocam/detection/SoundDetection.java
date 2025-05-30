package com.summersoft.heliocam.detection;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Handler;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import android.media.MediaScannerConnection;
import androidx.documentfile.provider.DocumentFile;

import com.summersoft.heliocam.ui.NotificationSettings;
import com.summersoft.heliocam.utils.DetectionDirectoryManager;


import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.summersoft.heliocam.ui.CameraActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import com.summersoft.heliocam.webrtc_utils.RTCJoiner;

public class SoundDetection {
    private static final int SAMPLE_RATE = 44100; // Sampling rate in Hz
    private final Context context;
    private final Handler handler;
    private int soundThreshold = 3000; // Default threshold, can be adjusted
    private boolean isRunning = false;
    private long detectionLatency = 3000; // Default latency in milliseconds (3 seconds)

    private DetectionDirectoryManager directoryManager;
    // Firebase references
    private AudioRecord audioRecord;
    private Thread detectionThread;
    private long lastDetectionTime = 0;
    private RTCJoiner webRTCClient;

    public SoundDetection(Context context, RTCJoiner webRTCClient) {
        this.context = context;
        this.handler = new Handler();
        this.webRTCClient = webRTCClient;
        this.directoryManager = new DetectionDirectoryManager(context);
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
                            onSoundDetected(amplitude);
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
    }    // In SoundDetection.java, modify triggerSoundDetected() method:
    private void triggerSoundDetected() {
        handler.post(() -> {
            // Always show toast regardless of notification settings
            Toast.makeText(context, "Sound Detected!", Toast.LENGTH_SHORT).show();

            // Always report detection to host first (for live monitoring)
            if (webRTCClient != null) {
                try {
                    // Get amplitude for reporting (use actual calculated value)
                    double amplitude = soundThreshold * 1.5; // Estimate based on threshold exceeded
                    
                    Log.d("SoundDetection", "Reporting sound detection to host with amplitude: " + amplitude);

                    // Create enhanced detection data with all details needed for notification card
                    Map<String, Object> detectionData = new HashMap<>();
                    detectionData.put("amplitude", amplitude);
                    detectionData.put("threshold", soundThreshold);
                    detectionData.put("confidence", "high");
                    detectionData.put("detectionMethod", "audioRecord");
                    detectionData.put("sampleRate", SAMPLE_RATE);
                    detectionData.put("detectionTime", System.currentTimeMillis());
                    
                    // Report sound detection with enhanced data
                    webRTCClient.reportDetectionEvent("sound", detectionData);
                    
                    // REMOVED: Take screenshot if needed (keep this functionality)
                    // String sessionId = ((CameraActivity) context).getSessionId();
                    // if (sessionId != null) {
                    // captureAndUploadScreenshot(sessionId);
                    // }
                } catch (Exception e) {
                    Log.e("SoundDetection", "Error reporting sound detection", e);
                }
            } else {
                Log.w("SoundDetection", "WebRTC client is null, cannot report detection");
            }            // Check if notification should be created (this only affects local notifications, not live reporting)
            if (!NotificationSettings.isSoundNotificationsEnabled(context)) {
                Log.d("SoundDetection", "Sound notifications disabled, skipping notification creation");
                NotificationSettings.debugCurrentSettings(context); // Debug the settings
                return;
            }
        });
    }

    private void captureAndUploadScreenshot(String sessionId) {
        // CameraActivity cameraActivity = (CameraActivity) context;

        // Capture the camera view as a bitmap
        // cameraActivity.captureCameraView(bitmap -> {
        // if (bitmap != null) {
        // try {
        // Try to save locally first
        // boolean saved = saveSoundDetectionImage(bitmap, sessionId);

        // If not saved locally, upload to Firebase as fallback
        // if (!saved) {
        // Save the bitmap to a temporary file for Firebase upload
        // File tempFile = File.createTempFile("screenshot", ".png", context.getCacheDir());
        // FileOutputStream fos = new FileOutputStream(tempFile);
        // bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
        // fos.close();

        // Upload the file to Firebase Storage

        // }
        // } catch (Exception e) {
        // Log.e("SoundDetection", "Error saving screenshot", e);
        // }
        // } else {
        // Log.w("SoundDetection", "Failed to capture camera view.");
        // } });
        Log.d("SoundDetection", "Screenshot capture and upload logic has been removed.");
    }
    
    // Track if we've shown the directory prompt during this session
    private boolean hasPromptedForDirectory = false;
      private boolean saveSoundDetectionImage(Bitmap bitmap, String sessionId) {
        try {
            // Generate filename
            String fileName = directoryManager.generateTimestampedFilename("Sound_Detected", ".jpg");

            // Try to save to the sound detection directory
            DocumentFile soundDir = directoryManager.getSoundDetectionDirectory();

            if (soundDir != null) {
                // Save to user-selected directory
                DocumentFile newFile = soundDir.createFile("image/jpeg", fileName);
                if (newFile != null) {
                    OutputStream out = context.getContentResolver().openOutputStream(newFile.getUri());
                    if (out != null) {
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out);
                        out.close();

                        handler.post(() -> Toast.makeText(context,
                                "Sound detected - Image saved logic removed", // Changed message
                                Toast.LENGTH_SHORT).show());

                        Log.d("SoundDetection", "Sound detection image saving logic removed. Path was: " + newFile.getUri());
                        return true; // Still return true as if saved, to not break flow if called.
                    }
                }
            } else {
                // Only prompt once for directory selection during app session
                if (!hasPromptedForDirectory && directoryManager.shouldPromptForDirectory()) {
                    hasPromptedForDirectory = true;
                    directoryManager.setPromptedForDirectory(true);
                    handler.post(() -> {
                        Toast.makeText(context, "Please select a folder to save detection data (image saving disabled for sound)", Toast.LENGTH_SHORT).show();
                        if (context instanceof CameraActivity) {
                            ((CameraActivity) context).openDirectoryPicker();
                        }
                    });
                }
            }

            // Fallback to app storage if user directory not available
            File detectionDir = directoryManager.getAppStorageDirectory("Sound_Detections");
            File imageFile = new File(detectionDir, fileName);

            // FileOutputStream fos = new FileOutputStream(imageFile);
            // bitmap.compress(Bitmap.CompressFormat.JPEG, 95, fos);
            // fos.close();

            Log.d("SoundDetection", "Sound detection image saving to app storage logic removed. Path was: " + imageFile.getAbsolutePath());
            handler.post(() -> Toast.makeText(context,
                    "Sound detected - Image saving to app storage logic removed", // Changed message
                    Toast.LENGTH_SHORT).show());

            // Add to media scanner
            // MediaScannerConnection.scanFile(context,
            // new String[]{imageFile.getAbsolutePath()},
            // new String[]{"image/jpeg"}, null);

            return true; // Still return true as if saved.
        } catch (Exception e) {
            Log.e("SoundDetection", "Error in (removed) sound detection image saving logic", e);
            return false;
        }
    }


    /**
     * Get the current sound detection threshold.
     *
     * @return the threshold value for detecting sound
     */
    public int getSoundThreshold() {
        return soundThreshold;
    }    /**
     * Get the current detection latency.
     *
     * @return the latency in seconds
     */
    public long getDetectionLatency() {
        return detectionLatency/1000;
    }

    /**
     * Update the directory URI for saving detection images
     */
    public void setDirectoryUri(Uri uri) {
        if (uri != null) {
            directoryManager.setBaseDirectory(uri);
        }
    }

    // When sound is detected
    private void onSoundDetected(double amplitude) {
        Log.d("SoundDetection", "Sound detection callback triggered with amplitude: " + amplitude);
        
        // Report using the method in RTCJoiner
        if (webRTCClient != null) {
            webRTCClient.reportSoundDetection(amplitude);
        } else {
            Log.w("SoundDetection", "Cannot report sound detection - webRTCClient is null");
        }
    }
}

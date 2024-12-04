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

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.summersoft.heliocam.ui.CameraActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

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
        handler.post(() -> {
            Toast.makeText(context, "Sound Detected!", Toast.LENGTH_SHORT).show();

            // Get Firebase references
            DatabaseReference database = FirebaseDatabase.getInstance().getReference();
            String userEmail = FirebaseAuth.getInstance().getCurrentUser().getEmail().replace(".", "_");
            String sessionId = ((CameraActivity) context).getSessionId(); // Assuming context is CameraActivity

            if (userEmail != null && sessionId != null) {
                // Create the notification structure
                String notificationId = "notification_" + System.currentTimeMillis();
                Map<String, Object> notificationData = new HashMap<>();
                notificationData.put("date", new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date()));
                notificationData.put("time", new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date()));
                notificationData.put("reason", "Sound Detected");

                // Update Firebase
                database.child("users")
                        .child(userEmail)
                        .child("sessions")
                        .child(sessionId)
                        .child("notifications")
                        .child(notificationId)
                        .setValue(notificationData)
                        .addOnCompleteListener(task -> {
                            if (task.isSuccessful()) {
                                Log.d("SoundDetection", "Notification logged in Firebase");
                            } else {
                                Log.e("SoundDetection", "Failed to log notification in Firebase", task.getException());
                            }
                        });

                // Capture and upload a screenshot
                captureAndUploadScreenshot(sessionId);
            } else {
                Log.w("SoundDetection", "User email or session ID is null. Cannot log notification.");
            }
        });
    }

    private void captureAndUploadScreenshot(String sessionId) {
        CameraActivity cameraActivity = (CameraActivity) context;

        // Capture the camera view as a bitmap
        cameraActivity.captureCameraView(bitmap -> {
            if (bitmap != null) {
                try {
                    // Save the bitmap to a temporary file
                    File tempFile = File.createTempFile("screenshot", ".png", context.getCacheDir());
                    FileOutputStream fos = new FileOutputStream(tempFile);
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                    fos.close();

                    // Upload the file to Firebase Storage
                    uploadScreenshotToFirebase(sessionId, tempFile);
                } catch (Exception e) {
                    Log.e("SoundDetection", "Error saving screenshot", e);
                }
            } else {
                Log.w("SoundDetection", "Failed to capture camera view.");
            }
        });
    }

    private void uploadScreenshotToFirebase(String sessionId, File file) {
        String storagePath = "screenshots/" + sessionId + ".png";
        FirebaseStorage storage = FirebaseStorage.getInstance();
        StorageReference storageRef = storage.getReference(storagePath);

        storageRef.putFile(Uri.fromFile(file))
                .addOnSuccessListener(taskSnapshot -> Log.d("SoundDetection", "Screenshot uploaded successfully."))
                .addOnFailureListener(e -> Log.e("SoundDetection", "Failed to upload screenshot", e));
    }


    /**
     * Get the current sound detection threshold.
     *
     * @return the threshold value for detecting sound
     */
    public int getSoundThreshold() {
        return soundThreshold;
    }

    /**
     * Get the current detection latency.
     *
     * @return the latency in milliseconds
     */
    public long getDetectionLatency() {
        return detectionLatency/1000;
    }


}

package com.summersoft.heliocam.detection;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;
import android.media.MediaScannerConnection;

import androidx.documentfile.provider.DocumentFile;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.summersoft.heliocam.ui.CameraActivity;
import com.summersoft.heliocam.ui.NotificationSettings;
import com.summersoft.heliocam.utils.DetectionDirectoryManager;
import com.summersoft.heliocam.utils.FileUtils;
import com.summersoft.heliocam.utils.ImageUtils;
import com.summersoft.heliocam.webrtc_utils.RTCHost;

import org.tensorflow.lite.Interpreter;
import org.webrtc.VideoFrame;
import org.webrtc.VideoSink;
import org.webrtc.YuvConverter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class PersonDetection implements VideoSink {
    private static final String TAG = "PersonDetection";
    private static final String MODEL_FILE = "yolov8n.tflite";
    private static final String LABEL_FILE = "labels.txt";
    private static final float CONFIDENCE_THRESHOLD = 0.5f;
    private static final int INPUT_WIDTH = 640;
    private static final int INPUT_HEIGHT = 640;

    private static final int REQUEST_DIRECTORY_PICKER = 1001;
    private Uri savedDirectoryUri = null;

    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private final Context context;
    private final Handler handler;
    private final Executor executor;
    private final RTCHost webRTCClient;
    private Interpreter tflite;
    private List<String> labels;

    // Use AtomicBoolean for thread-safe state management
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final AtomicBoolean isInLatencyPeriod = new AtomicBoolean(false);
    private final AtomicBoolean isModelLoaded = new AtomicBoolean(false);
    private final AtomicBoolean isProcessingFrame = new AtomicBoolean(false);

    private DetectionDirectoryManager directoryManager;

    private long lastDetectionTime = 0;
    private DetectionListener detectionListener;
    private YuvConverter yuvConverter = new YuvConverter();
    private final Paint boxPaint;

    // Detection latency variables
    private int detectionLatency = 5000; // Default 5 seconds (in milliseconds)
    private final Handler latencyHandler = new Handler(Looper.getMainLooper());
    private final Runnable resumeDetectionRunnable = new Runnable() {
        @Override
        public void run() {
            if (isInLatencyPeriod.compareAndSet(true, false)) {
                Log.d(TAG, "Detection latency period ended. Resuming detection.");
                if (detectionListener != null) {
                    detectionListener.onDetectionStatusChanged(false);
                }
            }
        }
    };

    private void run() {
        try {
            // Load model and labels using FileUtils
            MappedByteBuffer model = FileUtils.loadModelFile(context, MODEL_FILE);
            labels = FileUtils.readLabels(context, LABEL_FILE);

            // Initialize interpreter with CPU only (no GPU delegate)
            Interpreter.Options options = new Interpreter.Options();
            options.setUseXNNPACK(true); // Enable XNNPACK for CPU acceleration
            options.setNumThreads(4);    // Optimize thread usage

            Log.d(TAG, "Using CPU with XNNPACK acceleration");

            tflite = new Interpreter(model, options);
            Log.d(TAG, "Model loaded successfully with CPU");
            isModelLoaded.set(true);
        } catch (IOException e) {
            Log.e(TAG, "Error loading model", e);
            handler.post(() -> Toast.makeText(context, "Failed to load detection model", Toast.LENGTH_LONG).show());
        }
    }

    public interface DetectionListener {
        void onPersonDetected(Bitmap detectionBitmap);
        void onDetectionStatusChanged(boolean isDetecting);
    }

    public PersonDetection(Context context, RTCHost webRTCClient) {
        this.context = context;
        this.webRTCClient = webRTCClient;
        this.handler = new Handler(Looper.getMainLooper());
        this.executor = Executors.newSingleThreadExecutor();
        this.boxPaint = new Paint();
        this.boxPaint.setColor(Color.RED);
        this.boxPaint.setStyle(Paint.Style.STROKE);
        this.boxPaint.setStrokeWidth(8.0f);
        this.directoryManager = new DetectionDirectoryManager(context);
        loadModel();
    }

    public void setDetectionListener(DetectionListener listener) {
        this.detectionListener = listener;
    }

    public void start() {
        if (isRunning.compareAndSet(false, true)) {
            Log.d(TAG, "Person detection started");
            // Reset the latency period when starting
            isInLatencyPeriod.set(false);
            latencyHandler.removeCallbacks(resumeDetectionRunnable);
        }
    }

    public void stop() {
        if (isRunning.compareAndSet(true, false)) {
            Log.d(TAG, "Person detection stopped");
            isInLatencyPeriod.set(false);
            latencyHandler.removeCallbacks(resumeDetectionRunnable);

            // Don't close the interpreter here to avoid reloading it
            // Just stop the detection process
        }
    }

    public void shutdown() {
        stop();
        if (tflite != null) {
            tflite.close();
            tflite = null;
            isModelLoaded.set(false);
        }
    }

    /**
     * Set the detection latency in milliseconds.
     * This defines how long to wait after a person is detected before resuming detection.
     *
     * @param milliseconds The latency time in milliseconds
     */
    public void setDetectionLatency(int milliseconds) {
        this.detectionLatency = milliseconds;
        Log.d(TAG, "Detection latency set to " + milliseconds + " ms");
    }

    /**
     * Get the current detection latency in milliseconds.
     *
     * @return The current detection latency
     */
    public int getDetectionLatency() {
        return detectionLatency;
    }

    /**
     * Check whether detection is currently in the latency (paused) period
     *
     * @return true if in latency period, false otherwise
     */
    public boolean isInLatencyPeriod() {
        return isInLatencyPeriod.get();
    }

    /**
     * Force exit from latency period and resume detection immediately
     */
    public void forceResumeDetection() {
        if (isInLatencyPeriod.get()) {
            latencyHandler.removeCallbacks(resumeDetectionRunnable);
            isInLatencyPeriod.set(false);
            Log.d(TAG, "Detection resumed forcefully");
            if (detectionListener != null) {
                detectionListener.onDetectionStatusChanged(false);
            }
        }
    }

    private void loadModel() {
        executor.execute(this::run);
    }

    @Override
    public void onFrame(VideoFrame frame) {
        // Early return conditions with atomic variable checks for thread safety
        if (!isRunning.get() ||
                !isModelLoaded.get() ||
                isInLatencyPeriod.get() ||
                isProcessingFrame.get()) {
            return;
        }

        long currentTime = System.currentTimeMillis();
        if (currentTime - lastDetectionTime < 500) return; // Smaller cooldown for smoother detection

        // Set processing flag to avoid parallel processing of frames
        if (isProcessingFrame.compareAndSet(false, true)) {
            try {
                Bitmap bitmap = ImageUtils.videoFrameToBitmap(frame);
                if (bitmap != null) {
                    detectPerson(bitmap);
                }
            } finally {
                // Release the processing lock regardless of outcome
                isProcessingFrame.set(false);
            }
        }
    }

    private void detectPerson(Bitmap bitmap) {
        executor.execute(() -> {
            if (!isRunning.get() || isInLatencyPeriod.get()) {
                return; // Double-check state has not changed
            }

            try {
                // Prepare input tensor
                ByteBuffer inputBuffer = ImageUtils.bitmapToByteBuffer(bitmap, INPUT_WIDTH, INPUT_HEIGHT, 0f, 255f);

                // Output buffer for YOLOv8 - match the actual output shape [1, 84, 8400]
                float[][][] output = new float[1][84][8400];

                // Run inference
                tflite.run(inputBuffer, output);

                // Process YOLOv8 output
                List<Detection> detections = new ArrayList<>();
                boolean personDetected = false;

                // YOLOv8 format: first 4 values are box coords, remaining 80 are class scores
                int numClasses = 80;

                for (int i = 0; i < output[0][0].length; i++) { // Iterate through 8400 predictions
                    // Find max confidence and class index among the class scores
                    float maxConfidence = 0;
                    int detectedClass = -1;

                    for (int c = 0; c < numClasses; c++) {
                        float confidence = output[0][c + 4][i];
                        if (confidence > maxConfidence) {
                            maxConfidence = confidence;
                            detectedClass = c;
                        }
                    }

                    // Check if it's a person with sufficient confidence
                    if (maxConfidence > CONFIDENCE_THRESHOLD &&
                            detectedClass >= 0 && detectedClass < labels.size() &&
                            labels.get(detectedClass).equals("person")) {

                        personDetected = true;

                        // Get bounding box coordinates (xywh format in YOLOv8)
                        float x = output[0][0][i]; // center x
                        float y = output[0][1][i]; // center y
                        float w = output[0][2][i]; // width
                        float h = output[0][3][i]; // height

                        // Convert to corner format (left, top, right, bottom)
                        float xScale = (float) bitmap.getWidth() / INPUT_WIDTH;
                        float yScale = (float) bitmap.getHeight() / INPUT_HEIGHT;

                        float left = (x - w/2) * xScale;
                        float top = (y - h/2) * yScale;
                        float right = (x + w/2) * xScale;
                        float bottom = (y + h/2) * yScale;

                        RectF boundingBox = new RectF(left, top, right, bottom);
                        detections.add(new Detection("person", maxConfidence, boundingBox));
                    }
                }

                if (personDetected) {
                    lastDetectionTime = System.currentTimeMillis();

                    // Create a copy of the bitmap to draw bounding boxes
                    Bitmap finalBitmap = bitmap.copy(bitmap.getConfig(), true);
                    Canvas canvas = new Canvas(finalBitmap);

                    // Draw bounding boxes
                    for (Detection detection : detections) {
                        canvas.drawRect(detection.boundingBox, boxPaint);
                    }

                    handler.post(() -> {
                        // Only enter latency period if we're not already in it
                        if (isInLatencyPeriod.compareAndSet(false, true)) {
                            // Trigger person detected notification
                            triggerPersonDetected(finalBitmap);

                            if (detectionListener != null) {
                                detectionListener.onPersonDetected(finalBitmap);
                                detectionListener.onDetectionStatusChanged(true);
                            }

                            Log.d(TAG, "Person detected - pausing detection for " + detectionLatency + " ms");
                            handler.post(() -> Toast.makeText(context, "Detection paused for " + (detectionLatency/1000) + " seconds", Toast.LENGTH_SHORT).show());

                            // Schedule resumption after user-defined latency period
                            latencyHandler.removeCallbacks(resumeDetectionRunnable);
                            latencyHandler.postDelayed(resumeDetectionRunnable, detectionLatency);
                        }
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Detection error", e);
            }
        });
    }

    // In PersonDetection.java, modify triggerPersonDetected() method:
    private void triggerPersonDetected(Bitmap bitmap) {
        try {
            // Check if notification should be shown
            if (!NotificationSettings.isPersonNotificationsEnabled(context)) {
                // Skip notification creation but still show toast
                Toast.makeText(context, "Person detected", Toast.LENGTH_SHORT).show();
                return;
            }

            DatabaseReference database = FirebaseDatabase.getInstance().getReference();
            String userEmail = FirebaseAuth.getInstance().getCurrentUser().getEmail().replace(".", "_");
            String sessionId = ((CameraActivity) context).getSessionId();

            if (userEmail != null && sessionId != null) {
                // Create notification
                String notificationId = "notification_" + System.currentTimeMillis();
                Map<String, Object> notificationData = new HashMap<>();
                notificationData.put("date", new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date()));
                notificationData.put("time", new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date()));
                notificationData.put("reason", "Person Detected");

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
                                Log.d(TAG, "Notification logged in Firebase");
                            } else {
                                Log.e(TAG, "Failed to log notification", task.getException());
                            }
                        });

                // Save image
                saveDetectionImage(bitmap, sessionId);

                // Replay buffer
                webRTCClient.replayBuffer(context);
            } else {
                Log.w(TAG, "User email or session ID is null");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error handling person detection", e);
        }
    }

    private void saveDetectionImage(Bitmap bitmap, String sessionId) {
        try {
            // Generate filename
            String fileName = directoryManager.generateTimestampedFilename("Person_Detected", ".jpg");

            // Try to save to the person detection directory
            DocumentFile personDir = directoryManager.getPersonDetectionDirectory();

            if (personDir != null) {
                // Save to user-selected directory
                DocumentFile newFile = personDir.createFile("image/jpeg", fileName);
                if (newFile != null) {
                    OutputStream out = context.getContentResolver().openOutputStream(newFile.getUri());
                    if (out != null) {
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out);
                        out.close();

                        uiHandler.post(() -> Toast.makeText(context,
                                "Person detected - Image saved",
                                Toast.LENGTH_SHORT).show());

                        Log.d(TAG, "Detection image saved to: " + newFile.getUri());
                        return;
                    }
                }
            } else {
                // If we don't have a user directory, prompt once
                uiHandler.post(() -> {
                    Toast.makeText(context, "Please select a folder to save detection data", Toast.LENGTH_SHORT).show();
                    if (context instanceof CameraActivity) {
                        ((CameraActivity) context).openDirectoryPicker();
                    }
                });
            }

            // Fallback to app storage
            saveToAppStorage(bitmap, sessionId);

        } catch (Exception e) {
            Log.e(TAG, "Error saving detection image", e);
            saveToAppStorage(bitmap, sessionId);
        }
    }


    private void saveToAppStorage(Bitmap bitmap, String sessionId) {
        try {
            File detectionDir = directoryManager.getAppStorageDirectory("Person_Detections");
            String fileName = directoryManager.generateTimestampedFilename("Person_Detected", ".jpg");
            File imageFile = new File(detectionDir, fileName);

            FileOutputStream fos = new FileOutputStream(imageFile);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, fos);
            fos.close();

            Log.d(TAG, "Detection image saved to app storage: " + imageFile.getAbsolutePath());
            uiHandler.post(() -> Toast.makeText(context,
                    "Person detected - Image saved to app storage",
                    Toast.LENGTH_SHORT).show());

            // Add to media scanner
            MediaScannerConnection.scanFile(context,
                    new String[]{imageFile.getAbsolutePath()},
                    new String[]{"image/jpeg"}, null);
        } catch (IOException e) {
            Log.e(TAG, "Error saving to app storage", e);
        }
    }

    // Method to set the directory URI after user selection
    // Replace setDirectoryUri method
    public void setDirectoryUri(Uri uri) {
        if (uri != null) {
            directoryManager.setBaseDirectory(uri);
        }
    }



    private boolean isValidDirectory(Uri uri) {
        try {
            DocumentFile directory = DocumentFile.fromTreeUri(context, uri);
            return directory != null && directory.exists() && directory.isDirectory();
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            if (yuvConverter != null) {
                yuvConverter.release();
            }
            shutdown();
        } finally {
            super.finalize();
        }
    }

    private static class Detection {
        String className;
        float confidence;
        RectF boundingBox;

        Detection(String className, float confidence, RectF boundingBox) {
            this.className = className;
            this.confidence = confidence;
            this.boundingBox = boundingBox;
        }
    }
}
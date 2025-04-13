package com.summersoft.heliocam.detection;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.summersoft.heliocam.ui.CameraActivity;
import com.summersoft.heliocam.utils.FileUtils;
import com.summersoft.heliocam.utils.ImageUtils;
import com.summersoft.heliocam.webrtc_utils.RTCHost;

import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.gpu.GpuDelegate;
import org.webrtc.VideoFrame;
import org.webrtc.VideoSink;
import org.webrtc.YuvConverter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
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

public class PersonDetection implements VideoSink {
    private static final String TAG = "PersonDetection";
    private static final String MODEL_FILE = "yolov8n.tflite";
    private static final String LABEL_FILE = "labels.txt";
    private static final float CONFIDENCE_THRESHOLD = 0.5f;
    private static final int INPUT_WIDTH = 640;
    private static final int INPUT_HEIGHT = 640;

    private final Context context;
    private final Handler handler;
    private final Executor executor;
    private final RTCHost webRTCClient;
    private Interpreter tflite;
    private List<String> labels;
    private boolean isRunning = false;
    private long lastDetectionTime = 0;
    private DetectionListener detectionListener;
    private YuvConverter yuvConverter = new YuvConverter();
    private final Paint boxPaint;

    // Detection latency variables
    private int detectionLatency = 5000; // Default 5 seconds (in milliseconds)
    private boolean isInLatencyPeriod = false;
    private final Handler latencyHandler = new Handler(Looper.getMainLooper());
    private final Runnable resumeDetectionRunnable = new Runnable() {
        @Override
        public void run() {
            isInLatencyPeriod = false;
            Log.d(TAG, "Detection latency period ended. Resuming detection.");
            if (detectionListener != null) {
                detectionListener.onDetectionStatusChanged(false);
            }
            // No need to explicitly resume detection as it will be handled in onFrame
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

            // Remove GPU delegate attempt completely
            Log.d(TAG, "Using CPU with XNNPACK acceleration");

            tflite = new Interpreter(model, options);
            Log.d(TAG, "Model loaded successfully with CPU");
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
        loadModel();
    }

    public void setDetectionListener(DetectionListener listener) {
        this.detectionListener = listener;
    }

    public void start() {
        if (isRunning) return;
        isRunning = true;
    }

    public void stop() {
        if (!isRunning) return;
        isRunning = false;
        isInLatencyPeriod = false;
        latencyHandler.removeCallbacks(resumeDetectionRunnable);
        if (tflite != null) {
            tflite.close();
            tflite = null;
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

    private void loadModel() {
        executor.execute(this::run);
    }

    @Override
    public void onFrame(VideoFrame frame) {
        if (!isRunning || tflite == null || isInLatencyPeriod) return;

        long currentTime = System.currentTimeMillis();
        if (currentTime - lastDetectionTime < 3000) return; // 3 second cooldown

        Bitmap bitmap = ImageUtils.videoFrameToBitmap(frame);
        if (bitmap != null) {
            detectPerson(bitmap);
        }
    }

    private void detectPerson(Bitmap bitmap) {
        executor.execute(() -> {
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
                        triggerPersonDetected(finalBitmap);
                        if (detectionListener != null) {
                            detectionListener.onPersonDetected(finalBitmap);
                            detectionListener.onDetectionStatusChanged(true);
                        }

                        // Enter latency period
                        enterLatencyPeriod();
                    });
                } else if (detectionListener != null) {
                    handler.post(() -> detectionListener.onDetectionStatusChanged(false));
                }
            } catch (Exception e) {
                Log.e(TAG, "Detection error", e);
            }
        });
    }

    /**
     * Enter the latency period after a person is detected.
     * During this period, detection is paused.
     */
    private void enterLatencyPeriod() {
        if (isInLatencyPeriod) {
            // Already in latency period, reset the timer
            latencyHandler.removeCallbacks(resumeDetectionRunnable);
        } else {
            isInLatencyPeriod = true;
            Log.d(TAG, "Entering detection latency period for " + detectionLatency + " ms");
            handler.post(() -> Toast.makeText(context, "Detection paused for " + (detectionLatency/1000) + " seconds", Toast.LENGTH_SHORT).show());
        }

        // Schedule resumption of detection after latency period
        latencyHandler.postDelayed(resumeDetectionRunnable, detectionLatency);
    }

    private void triggerPersonDetected(Bitmap bitmap) {
        Toast.makeText(context, "Person Detected!", Toast.LENGTH_SHORT).show();

        try {
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

                // Upload image
                uploadDetectionImage(bitmap, sessionId);

                // Replay buffer
                webRTCClient.replayBuffer(context);
            } else {
                Log.w(TAG, "User email or session ID is null");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error handling person detection", e);
        }
    }

    private void uploadDetectionImage(Bitmap bitmap, String sessionId) {
        try {
            File tempFile = File.createTempFile("detection", ".png", context.getCacheDir());
            FileOutputStream fos = new FileOutputStream(tempFile);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.close();

            String storagePath = "detections/" + sessionId + "_" + System.currentTimeMillis() + ".png";
            StorageReference storageRef = FirebaseStorage.getInstance().getReference(storagePath);

            storageRef.putFile(android.net.Uri.fromFile(tempFile))
                    .addOnSuccessListener(taskSnapshot -> Log.d(TAG, "Image uploaded"))
                    .addOnFailureListener(e -> Log.e(TAG, "Upload failed", e));
        } catch (IOException e) {
            Log.e(TAG, "Error saving image", e);
        }
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            if (yuvConverter != null) {
                yuvConverter.release();
            }
            if (tflite != null) {
                tflite.close();
                tflite = null;
            }
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
package com.summersoft.heliocam.detection;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.net.Uri;
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
import com.summersoft.heliocam.webrtc_utils.RTCJoiner;

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
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class PersonDetection implements VideoSink {
    private static final String TAG = "PersonDetection";
    private static final String MODEL_FILE = "yolov8n.tflite";
    private static final String LABEL_FILE = "labels.txt";
    private static final float CONFIDENCE_THRESHOLD = 0.6f;
    private static final int INPUT_WIDTH = 320;
    private static final int INPUT_HEIGHT = 320;

    private static final int REQUEST_DIRECTORY_PICKER = 1001;
    private Uri savedDirectoryUri = null;

    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private final Context context;
    private final Handler handler;
    private final Executor executor;
    private final RTCJoiner webRTCClient;
    private Interpreter tflite;
    private List<String> labels;

    // Use AtomicBoolean for thread-safe state management
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final AtomicBoolean isInLatencyPeriod = new AtomicBoolean(false);
    private final AtomicBoolean isModelLoaded = new AtomicBoolean(false);
    private final AtomicBoolean isProcessingFrame = new AtomicBoolean(false);
    private final AtomicInteger frameCounter = new AtomicInteger(0);
    private DetectionDirectoryManager directoryManager;



    private boolean hasPromptedForDirectory = false;

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
            // Check if model files exist before loading
            try {
                context.getAssets().open(MODEL_FILE).close();
                context.getAssets().open(LABEL_FILE).close();
            } catch (IOException e) {
                Log.e(TAG, "Model files not found: " + e.getMessage());
                handler.post(() -> Toast.makeText(context, "Person detection model files missing", Toast.LENGTH_LONG).show());
                return;
            }

            // Load model and labels using FileUtils
            MappedByteBuffer model = FileUtils.loadModelFile(context, MODEL_FILE);
            labels = FileUtils.readLabels(context, LABEL_FILE);

            // More aggressive optimization for the interpreter
            Interpreter.Options options = new Interpreter.Options();
            options.setUseXNNPACK(true);
            options.setNumThreads(2);  // Use fewer threads to avoid contention

            tflite = new Interpreter(model, options);
            
            // Verify model input/output shapes
            int[] inputShape = tflite.getInputTensor(0).shape();
            int[] outputShape = tflite.getOutputTensor(0).shape();
            
            Log.d(TAG, "Model input shape: " + Arrays.toString(inputShape));
            Log.d(TAG, "Model output shape: " + Arrays.toString(outputShape));
            
            Log.d(TAG, "Model loaded successfully with optimized settings");
            isModelLoaded.set(true);
        } catch (IOException e) {
            Log.e(TAG, "Error loading model", e);
            handler.post(() -> Toast.makeText(context, "Failed to load detection model: " + e.getMessage(), Toast.LENGTH_LONG).show());
        }
    }

    public interface DetectionListener {
        void onPersonDetected(Bitmap detectionBitmap);
        void onDetectionStatusChanged(boolean isDetecting);
    }

    // Add this method for debugging
    private void debugModelFiles() {
        try {
            // Check if model files exist
            android.content.res.AssetManager assetManager = context.getAssets();
            String[] assets = assetManager.list("");
            
            Log.d(TAG, "Available assets:");
            for (String asset : assets) {
                Log.d(TAG, "  " + asset);
            }
            
            // Specifically check for our model files
            try {
                assetManager.open(MODEL_FILE).close();
                Log.d(TAG, "✓ Model file found: " + MODEL_FILE);
            } catch (IOException e) {
                Log.e(TAG, "✗ Model file NOT found: " + MODEL_FILE);
            }
            
            try {
                assetManager.open(LABEL_FILE).close();
                Log.d(TAG, "✓ Label file found: " + LABEL_FILE);
            } catch (IOException e) {
                Log.e(TAG, "✗ Label file NOT found: " + LABEL_FILE);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking model files: " + e.getMessage());
        }
    }

    public PersonDetection(Context context, RTCJoiner webRTCClient) {
        this.context = context;
        this.webRTCClient = webRTCClient;
        this.handler = new Handler(Looper.getMainLooper());
        this.executor = Executors.newSingleThreadExecutor();
        this.boxPaint = new Paint();
        this.boxPaint.setColor(Color.RED);
        this.boxPaint.setStyle(Paint.Style.STROKE);
        this.boxPaint.setStrokeWidth(8.0f);
        this.directoryManager = new DetectionDirectoryManager(context);
        
        // Debug model files
        debugModelFiles();
        
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
        // Skip processing entirely if we're not ready
        if (!isRunning.get() || !isModelLoaded.get() || isInLatencyPeriod.get()) {
            return;
        }

        // Adaptive frame skipping based on processing load
        int skipFactor = isProcessingFrame.get() ? 6 : 3; // Reduced for better detection
        if (frameCounter.incrementAndGet() % skipFactor != 0) {
            return;
        }

        // Don't process if already processing a frame
        if (!isProcessingFrame.compareAndSet(false, true)) {
            return;
        }

        // Retain the frame before passing to background thread
        frame.retain();

        // Process frame on executor thread
        executor.execute(() -> {
            try {
                // Convert frame to bitmap with better error handling
                Bitmap bitmap = ImageUtils.videoFrameToBitmap(frame);
                if (bitmap != null && !bitmap.isRecycled()) {
                    detectPerson(bitmap);
                    // Don't recycle here - let detectPerson handle it
                } else {
                    Log.w(TAG, "Failed to convert frame to bitmap or bitmap is recycled");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error processing frame: " + e.getMessage(), e);
            } finally {
                // Always release the frame and reset processing flag
                try {
                    frame.release();
                } catch (Exception e) {
                    Log.w(TAG, "Error releasing frame: " + e.getMessage());
                }
                isProcessingFrame.set(false);
            }
        });
    }

    private void detectPerson(Bitmap bitmap) {
        if (!isRunning.get() || isInLatencyPeriod.get() || bitmap == null || bitmap.isRecycled()) {
            if (bitmap != null && !bitmap.isRecycled()) {
                bitmap.recycle();
            }
            return;
        }

        try {
            // Verify model is still loaded
            if (tflite == null) {
                Log.e(TAG, "TensorFlow Lite interpreter is null");
                bitmap.recycle();
                return;
            }

            // Get model input shape
            int[] inputShape = tflite.getInputTensor(0).shape();
            if (inputShape.length != 4) {
                Log.e(TAG, "Invalid input shape: " + Arrays.toString(inputShape));
                bitmap.recycle();
                return;
            }
            
            int inputWidth = inputShape[2];  // Width is typically at index 2
            int inputHeight = inputShape[1]; // Height is typically at index 1

            // Prepare input tensor with proper error handling
            ByteBuffer inputBuffer;
            try {
                inputBuffer = ImageUtils.bitmapToByteBuffer(bitmap, inputWidth, inputHeight, 0f, 1f);
            } catch (Exception e) {
                Log.e(TAG, "Error converting bitmap to ByteBuffer: " + e.getMessage());
                bitmap.recycle();
                return;
            }

            // Get output shape and create output buffer
            int[] outputShape = tflite.getOutputTensor(0).shape();
            Log.d(TAG, "Output shape: " + Arrays.toString(outputShape));
            
            // For YOLOv8, output shape is typically [1, 84, 8400] or [1, 8400, 84]
            float[][][] output;
            if (outputShape.length == 3) {
                if (outputShape[1] == 84) {
                    output = new float[outputShape[0]][outputShape[1]][outputShape[2]]; // [1, 84, 8400]
                } else {
                    output = new float[outputShape[0]][outputShape[2]][outputShape[1]]; // [1, 8400, 84] -> transpose
                }
            } else {
                Log.e(TAG, "Unexpected output shape: " + Arrays.toString(outputShape));
                bitmap.recycle();
                return;
            }

            // Run inference
            try {
                long startTime = System.currentTimeMillis();
                tflite.run(inputBuffer, output);
                long endTime = System.currentTimeMillis();
                Log.d(TAG, "Inference time: " + (endTime - startTime) + "ms");
            } catch (Exception e) {
                Log.e(TAG, "Error during model inference: " + e.getMessage());
                bitmap.recycle();
                return;
            }

            // Process results
            processDetectionResults(output, bitmap, inputWidth, inputHeight);

        } catch (Exception e) {
            Log.e(TAG, "Detection error: " + e.getMessage(), e);
            if (bitmap != null && !bitmap.isRecycled()) {
                bitmap.recycle();
            }
        }
    }

    private void processDetectionResults(float[][][] output, Bitmap bitmap, int inputWidth, int inputHeight) {
        try {
            List<Detection> detections = new ArrayList<>();
            boolean personDetected = false;

            // YOLOv8 format: first 4 values are box coords, remaining 80 are class scores
            int numClasses = 80;
            int numDetections = output[0][0].length; // Should be 8400 for YOLOv8

            Log.d(TAG, "Processing " + numDetections + " detections");

            for (int i = 0; i < numDetections; i++) {
                // Find max confidence and class index among the class scores
                float maxConfidence = 0;
                int detectedClass = -1;

                for (int c = 0; c < numClasses; c++) {
                    float confidence = output[0][c + 4][i]; // Class scores start at index 4
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
                    float xScale = (float) bitmap.getWidth() / inputWidth;
                    float yScale = (float) bitmap.getHeight() / inputHeight;

                    float left = (x - w/2) * xScale;
                    float top = (y - h/2) * yScale;
                    float right = (x + w/2) * xScale;
                    float bottom = (y + h/2) * yScale;

                    RectF boundingBox = new RectF(left, top, right, bottom);
                    detections.add(new Detection("person", maxConfidence, boundingBox));

                    Log.d(TAG, "Person detected with confidence: " + maxConfidence);
                }
            }

            if (personDetected) {
                handlePersonDetection(detections, bitmap);
            } else {
                // No person detected, recycle bitmap
                bitmap.recycle();
            }

        } catch (Exception e) {
            Log.e(TAG, "Error processing detection results: " + e.getMessage(), e);
            if (bitmap != null && !bitmap.isRecycled()) {
                bitmap.recycle();
            }
        }
    }

    private void handlePersonDetection(List<Detection> detections, Bitmap bitmap) {
        lastDetectionTime = System.currentTimeMillis();

        // Create a copy of the bitmap to draw bounding boxes
        Bitmap finalBitmap = bitmap.copy(bitmap.getConfig(), true);
        Canvas canvas = new Canvas(finalBitmap);

        // Draw bounding boxes
        for (Detection detection : detections) {
            canvas.drawRect(detection.boundingBox, boxPaint);
        }

        // Count the number of persons detected
        int personCount = detections.size();
        
        // Call the method to report the detection to the host
        onPersonDetected(personCount);

        handler.post(() -> {
            // Only enter latency period if we're not already in it
            if (isInLatencyPeriod.compareAndSet(false, true)) {
                // Show toast immediately
                Toast.makeText(context, "Person detected! (" + personCount + " person" + (personCount > 1 ? "s" : "") + ")", Toast.LENGTH_SHORT).show();
                
                // Trigger person detected notification
                triggerPersonDetected(finalBitmap);

                if (detectionListener != null) {
                    detectionListener.onPersonDetected(finalBitmap);
                    detectionListener.onDetectionStatusChanged(true);
                }

                Log.d(TAG, "Person detected - pausing detection for " + detectionLatency + " ms");

                // Schedule resumption after user-defined latency period
                latencyHandler.removeCallbacks(resumeDetectionRunnable);
                latencyHandler.postDelayed(resumeDetectionRunnable, detectionLatency);
            }
        });

        // Recycle the original bitmap
        bitmap.recycle();
    }

    // In PersonDetection.java, modify triggerPersonDetected() method:
    private void triggerPersonDetected(Bitmap bitmap) {
        try {
            // Always show toast
            uiHandler.post(() -> Toast.makeText(context, "Person detected!", Toast.LENGTH_SHORT).show());

            // Check if notification should be created
            if (!NotificationSettings.isPersonNotificationsEnabled(context)) {
                Log.d(TAG, "Person notifications disabled, skipping notification creation");
                // Still save image locally but don't create notifications
                String sessionId = ((CameraActivity) context).getSessionId();
                if (sessionId != null) {
                    saveDetectionImage(bitmap, sessionId);
                }
                return;
            }

            // Report detection to host and save notifications
            if (webRTCClient != null) {
                // Count how many people were detected (could be multiple in one frame)
                int personCount = countDetectedPeople(bitmap);
                
                Log.d(TAG, "Reporting person detection to host: " + personCount + " person(s)");
                
                // Create enhanced detection data with all details needed for notification card
                Map<String, Object> detectionData = new HashMap<>();
                detectionData.put("personCount", personCount);
                detectionData.put("confidence", "high");
                detectionData.put("detectionMethod", "yolov8");
                detectionData.put("imageWidth", bitmap.getWidth());
                detectionData.put("imageHeight", bitmap.getHeight());
                detectionData.put("processingTime", System.currentTimeMillis()); // For performance tracking
                
                // Report person detection with enhanced data
                webRTCClient.reportDetectionEvent("person", detectionData);
            } else {
                Log.w(TAG, "WebRTC client is null, cannot report detection");
            }

            // Save image locally
            String sessionId = ((CameraActivity) context).getSessionId();
            if (sessionId != null) {
                saveDetectionImage(bitmap, sessionId);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error handling person detection", e);
        }
    }

    // Helper method to count detected people (implementation depends on your detection logic)
    private int countDetectedPeople(Bitmap bitmap) {
        // This is just a placeholder - use your actual detection count logic
        return 1; // Default to 1 if you don't track the count
    }

    private void saveDetectionImage(Bitmap bitmap, String sessionId) {
        // Don't block processing with image saving
        executor.execute(() -> {
            try {
                // Save a scaled-down version of the image for better performance
                Bitmap scaledBitmap = Bitmap.createScaledBitmap(bitmap, bitmap.getWidth()/2, bitmap.getHeight()/2, true);

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
                            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 85, out);
                            out.close();

                            uiHandler.post(() -> Toast.makeText(context,
                                    "Person detected - Image saved",
                                    Toast.LENGTH_SHORT).show());

                            Log.d(TAG, "Detection image saved to: " + newFile.getUri());
                            scaledBitmap.recycle();
                            return;
                        }
                    }                } else {
                    // Only prompt once for directory selection during app session
                    if (!hasPromptedForDirectory && directoryManager.shouldPromptForDirectory()) {
                        hasPromptedForDirectory = true;
                        directoryManager.setPromptedForDirectory(true);
                        uiHandler.post(() -> {
                            Toast.makeText(context, "Please select a folder to save detection data", Toast.LENGTH_SHORT).show();
                            if (context instanceof CameraActivity) {
                                ((CameraActivity) context).openDirectoryPicker();
                            }
                        });
                    }
                }

                // Fallback to app storage
                saveToAppStorage(scaledBitmap, sessionId);
                scaledBitmap.recycle();

            } catch (Exception e) {
                Log.e(TAG, "Error saving detection image", e);
            }
        });
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

    // In PersonDetection.java, inside your person detection callback

    // When a person is detected
    private void onPersonDetected(int personCount) {
        Log.d(TAG, "Person detection callback triggered with count: " + personCount);
        
        // Report using the method in RTCJoiner
        if (webRTCClient != null) {
            webRTCClient.reportPersonDetection(personCount);
        } else {
            Log.w(TAG, "Cannot report person detection - webRTCClient is null");
        }
    }
}
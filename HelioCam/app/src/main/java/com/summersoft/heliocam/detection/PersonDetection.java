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
    private static final String LABEL_FILE = "labels.txt";    private static final float CONFIDENCE_THRESHOLD = 0.3f; // Reasonable threshold for person detection
    private static final int INPUT_WIDTH = 640;
    private static final int INPUT_HEIGHT = 640;

    private static final int REQUEST_DIRECTORY_PICKER = 1001;
    private Uri savedDirectoryUri = null;

    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private final Context context;
    private final Handler handler;
    private final Executor executor;
    private final RTCJoiner webRTCClient;    private Interpreter tflite;
    private List<String> labels;
    
    // Cache model input/output dimensions for performance
    private int modelInputWidth = INPUT_WIDTH;
    private int modelInputHeight = INPUT_HEIGHT;
    private int[] cachedInputShape;
    private int[] cachedOutputShape;    // Use AtomicBoolean for thread-safe state management
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final AtomicBoolean isInLatencyPeriod = new AtomicBoolean(false);
    private final AtomicBoolean isModelLoaded = new AtomicBoolean(false);
    private final AtomicBoolean isProcessingFrame = new AtomicBoolean(false);
    private final AtomicInteger frameCounter = new AtomicInteger(0);
    private DetectionDirectoryManager directoryManager;    // Enhanced performance tracking and optimization
    private long lastFrameProcessTime = 0;
    private long averageInferenceTime = 50; // Start with reasonable estimate
    private int processedFrameCount = 0;
    
    // Frame tracking for debugging
    private final AtomicInteger totalFramesReceived = new AtomicInteger(0);
    private final AtomicInteger framesProcessedCount = new AtomicInteger(0);
    private long lastDebugLogTime = 0;
    private static final long DEBUG_LOG_INTERVAL = 5000; // Log every 5 seconds
    
    // Optimized frame skipping with adaptive behavior
    private int dynamicSkipFactor = 4; // Start with power-of-2 value
    private long lastSkipFactorUpdate = 0;
    private static final long SKIP_FACTOR_UPDATE_INTERVAL = 2000; // Update every 2 seconds

    private boolean hasPromptedForDirectory = false;

    private long lastDetectionTime = 0;
    private int lastDetectedPersonCount = 0; // Track the number of people detected in the last detection
    private DetectionListener detectionListener;
    private YuvConverter yuvConverter = new YuvConverter();
    private final Paint boxPaint;    // Detection latency variables
    private int detectionLatency = 2000; // Reduced to 2 seconds for faster testing
    private final Handler latencyHandler = new Handler(Looper.getMainLooper());
    private final Runnable resumeDetectionRunnable = new Runnable() {
        @Override
        public void run() {
            if (isInLatencyPeriod.compareAndSet(true, false)) {
                Log.i(TAG, "Detection latency period ended. Resuming detection.");
                if (detectionListener != null) {
                    detectionListener.onDetectionStatusChanged(false);
                }
            } else {
                Log.w(TAG, "Attempted to end latency period but was not in latency period");
            }
        }
    };

    // Video recording variables
    private int videoRecordingDuration = 5000; // Default 5 seconds (in milliseconds)
    private boolean autoRecordingEnabled = true; // Toggle for automatic recording on detection
    private final AtomicBoolean isRecordingVideo = new AtomicBoolean(false);
    private final Handler recordingHandler = new Handler(Looper.getMainLooper());
    private Runnable stopRecordingRunnable;    private void run() {
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
            labels = FileUtils.readLabels(context, LABEL_FILE);            // Ultra-optimized interpreter settings for maximum performance
            Interpreter.Options options = new Interpreter.Options();
            options.setUseXNNPACK(true);
            options.setNumThreads(Math.min(2, Runtime.getRuntime().availableProcessors())); // Reduced threads for lower latency
            
            // Aggressive performance optimizations
            options.setAllowFp16PrecisionForFp32(true); // Allow reduced precision for speed
            options.setAllowBufferHandleOutput(true);   // Enable buffer handle output
            options.setUseNNAPI(false); // Disable NNAPI for consistent performance
            
            // Additional optimizations for detection workload
            options.setCancellable(false); // Disable cancellation for better performance
              // Experimental: Enable CPU backend optimizations
            // Note: Using CPU backend by default (no GPU delegate added)
            // This ensures consistent performance across different devices

            tflite = new Interpreter(model, options);
            
            // Cache model input/output shapes for ultra-fast access
            cachedInputShape = tflite.getInputTensor(0).shape();
            cachedOutputShape = tflite.getOutputTensor(0).shape();
            
            // Update cached dimensions for optimal performance
            if (cachedInputShape.length >= 3) {
                modelInputWidth = cachedInputShape[2];
                modelInputHeight = cachedInputShape[1];
            }
            
            Log.d(TAG, "Model loaded with ultra-optimized settings:");
            Log.d(TAG, "  Input shape: " + Arrays.toString(cachedInputShape) + 
                  " -> " + modelInputWidth + "x" + modelInputHeight);
            Log.d(TAG, "  Output shape: " + Arrays.toString(cachedOutputShape));
            Log.d(TAG, "  Threads: " + Math.min(3, Runtime.getRuntime().availableProcessors()));
            
            isModelLoaded.set(true);
        } catch (IOException e) {
            Log.e(TAG, "Error loading model", e);
            handler.post(() -> Toast.makeText(context, "Failed to load detection model: " + e.getMessage(), Toast.LENGTH_LONG).show());
        }
    }public interface DetectionListener {
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
    }    public PersonDetection(Context context, RTCJoiner webRTCClient) {
        this.context = context;
        this.webRTCClient = webRTCClient;
        this.handler = new Handler(Looper.getMainLooper());
        
        // Create high-priority executor for detection processing
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "PersonDetection-Worker");
            t.setPriority(Thread.MAX_PRIORITY); // Highest priority for smooth detection
            return t;
        });
        
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
    }    public void start() {
        if (isRunning.compareAndSet(false, true)) {
            Log.i(TAG, "Person detection started - Model loaded: " + isModelLoaded.get());
            // Force reset the latency period when starting
            if (isInLatencyPeriod.compareAndSet(true, false)) {
                Log.i(TAG, "Forced reset of latency period on start");
                latencyHandler.removeCallbacks(resumeDetectionRunnable);
            }
            
            // Reset frame counters for fresh debug session
            totalFramesReceived.set(0);
            framesProcessedCount.set(0);
            lastDebugLogTime = System.currentTimeMillis();
            
            // Ensure we're not stuck in latency period
            latencyHandler.removeCallbacks(resumeDetectionRunnable);
        } else {
            Log.d(TAG, "Detection already running");
        }
    }public void stop() {
        if (isRunning.compareAndSet(true, false)) {
            Log.d(TAG, "Person detection stopped");
            isInLatencyPeriod.set(false);
            latencyHandler.removeCallbacks(resumeDetectionRunnable);
            
            // Stop any ongoing video recording
            if (isRecordingVideo.get()) {
                stopTimedVideoRecording();
            }

            // Don't close the interpreter here to avoid reloading it
            // Just stop the detection process
        }
    }    public void shutdown() {
        stop();
        
        // Clean up video recording resources
        if (stopRecordingRunnable != null) {
            recordingHandler.removeCallbacks(stopRecordingRunnable);
            stopRecordingRunnable = null;
        }
        
        // Clean up the bitmap pools to prevent memory leaks
        ImageUtils.clearBitmapPool();
        ImageUtils.clearDetectionBitmapPool();
        
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
    }    /**
     * Force exit from latency period and resume detection immediately
     */
    public void forceResumeDetection() {
        if (isInLatencyPeriod.compareAndSet(true, false)) {
            latencyHandler.removeCallbacks(resumeDetectionRunnable);
            Log.w(TAG, "Detection resumed forcefully");
            if (detectionListener != null) {
                detectionListener.onDetectionStatusChanged(false);
            }
        } else {
            Log.d(TAG, "Force resume called but not in latency period");
        }
    }/**
     * Set the video recording duration in milliseconds.
     * This defines how long to record video when a person is detected.
     * Duration is constrained between 3-30 seconds for optimal performance.
     *
     * @param milliseconds The recording duration in milliseconds (3000-30000ms)
     */
    public void setVideoRecordingDuration(int milliseconds) {
        // Enforce 3-30 seconds constraint to allow more flexibility
        int constrainedDuration = Math.max(3000, Math.min(30000, milliseconds));
        this.videoRecordingDuration = constrainedDuration;
        
        if (milliseconds != constrainedDuration) {
            Log.w(TAG, "Video recording duration constrained from " + milliseconds + "ms to " + constrainedDuration + "ms (3-30 seconds)");
        }
        
        Log.d(TAG, "Video recording duration set to " + constrainedDuration + " ms");
    }

    /**
     * Get the current video recording duration in milliseconds.
     *
     * @return The current video recording duration
     */
    public int getVideoRecordingDuration() {
        return videoRecordingDuration;
    }    /**
     * Check whether video recording is currently in progress.
     *
     * @return true if recording video, false otherwise
     */
    public boolean isRecordingVideo() {
        return isRecordingVideo.get();
    }

    /**
     * Set whether automatic recording should be triggered on person detection.
     *
     * @param enabled true to enable automatic recording, false to disable
     */
    public void setAutoRecordingEnabled(boolean enabled) {
        this.autoRecordingEnabled = enabled;
        Log.d(TAG, "Auto recording " + (enabled ? "enabled" : "disabled"));
    }    /**
     * Check whether automatic recording is enabled for person detection.
     *
     * @return true if auto recording is enabled, false otherwise
     */
    public boolean isAutoRecordingEnabled() {
        return autoRecordingEnabled;
    }

    /**
     * Test the detection system by forcing a mock detection
     * Useful for debugging latency period issues
     */
    public void testDetection() {
        Log.i(TAG, "Testing detection system...");
        Log.i(TAG, "Current state - Running: " + isRunning.get() + ", Model loaded: " + isModelLoaded.get() + 
                  ", In latency: " + isInLatencyPeriod.get());
        
        if (!isRunning.get()) {
            Log.w(TAG, "Detection not running - cannot test");
            return;
        }
        
        if (isInLatencyPeriod.get()) {
            Log.w(TAG, "Currently in latency period - forcing resume for test");
            forceResumeDetection();
        }
        
        // Simulate a person detection to test the latency mechanism
        lastDetectionTime = System.currentTimeMillis();
        lastDetectedPersonCount = 1;
        
        if (isInLatencyPeriod.compareAndSet(false, true)) {
            Log.i(TAG, "TEST: Entering latency period for " + detectionLatency + "ms");
            
            handler.post(() -> {
                Toast.makeText(context, "TEST: Person detected!", Toast.LENGTH_SHORT).show();
                if (detectionListener != null) {
                    detectionListener.onDetectionStatusChanged(true);
                }
            });

            // Schedule resumption
            try {
                latencyHandler.removeCallbacks(resumeDetectionRunnable);
                boolean scheduled = latencyHandler.postDelayed(resumeDetectionRunnable, detectionLatency);
                Log.i(TAG, "TEST: Latency resumption scheduled: " + scheduled);
            } catch (Exception e) {
                Log.e(TAG, "TEST: Failed to schedule resumption: " + e.getMessage());
                isInLatencyPeriod.set(false);
            }
        }
    }

    /**
     * Start video recording for the configured duration when person is detected
     */    private void startTimedVideoRecording() {
        if (isRecordingVideo.compareAndSet(false, true)) {
            try {
                // Cast context to CameraActivity to access recording methods
                CameraActivity cameraActivity = (CameraActivity) context;
                
                Log.d(TAG, "Starting timed video recording for " + videoRecordingDuration + "ms");
                
                // Start recording using existing CameraActivity method
                if (cameraActivity.startRecordingFromDetection("Person_Detected")) {
                    // Schedule automatic stop after configured duration
                    stopRecordingRunnable = new Runnable() {
                        @Override
                        public void run() {
                            stopTimedVideoRecording();
                        }
                    };
                    recordingHandler.postDelayed(stopRecordingRunnable, videoRecordingDuration);
                    
                    uiHandler.post(() -> Toast.makeText(context, 
                            "Person detected - Recording for " + (videoRecordingDuration / 1000) + " seconds", 
                            Toast.LENGTH_SHORT).show());
                } else {
                    // Failed to start recording, reset state
                    isRecordingVideo.set(false);
                    Log.w(TAG, "Failed to start video recording");
                }
            } catch (ClassCastException e) {
                Log.e(TAG, "Context is not CameraActivity, cannot start recording", e);
                isRecordingVideo.set(false);
            } catch (Exception e) {
                Log.e(TAG, "Error starting video recording", e);
                isRecordingVideo.set(false);
            }
        } else {
            Log.d(TAG, "Video recording already in progress, skipping");
        }
    }

    /**
     * Stop the timed video recording
     */
    private void stopTimedVideoRecording() {
        if (isRecordingVideo.compareAndSet(true, false)) {
            try {
                // Remove the scheduled stop callback
                if (stopRecordingRunnable != null) {
                    recordingHandler.removeCallbacks(stopRecordingRunnable);
                    stopRecordingRunnable = null;
                }
                
                // Cast context to CameraActivity to access recording methods
                CameraActivity cameraActivity = (CameraActivity) context;
                
                Log.d(TAG, "Stopping timed video recording");
                
                // Stop recording using existing CameraActivity method
                cameraActivity.stopRecordingFromDetection();
                
                uiHandler.post(() -> Toast.makeText(context, 
                        "Person detection video saved", 
                        Toast.LENGTH_SHORT).show());
                        
            } catch (ClassCastException e) {
                Log.e(TAG, "Context is not CameraActivity, cannot stop recording", e);
            } catch (Exception e) {
                Log.e(TAG, "Error stopping video recording", e);
            }
        }
    }

    private void loadModel() {
        executor.execute(this::run);
    }    @Override
    public void onFrame(VideoFrame frame) {
        // Track all frames received
        int totalFrames = totalFramesReceived.incrementAndGet();
          // Periodic debug logging
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastDebugLogTime > DEBUG_LOG_INTERVAL) {
            int processed = framesProcessedCount.get();
            Log.i(TAG, "Frame Stats - Received: " + totalFrames + ", Processed: " + processed + 
                      ", Model loaded: " + isModelLoaded.get() + ", Running: " + isRunning.get() + 
                      ", In latency: " + isInLatencyPeriod.get());
            
            // Additional debugging for latency period
            if (isInLatencyPeriod.get()) {
                Log.w(TAG, "STUCK IN LATENCY PERIOD - Last detection: " + (currentTime - lastDetectionTime) + "ms ago");
                // Auto-recovery mechanism: if stuck for too long, force reset
                if (currentTime - lastDetectionTime > detectionLatency * 3) { // 3x the latency period
                    Log.w(TAG, "FORCING LATENCY PERIOD RESET due to timeout");
                    forceResumeDetection();
                }
            }
            
            lastDebugLogTime = currentTime;
        }
        
        // Ultra-fast early exit checks with single atomic read
        if (!isRunning.get()) {
            Log.v(TAG, "Frame dropped - not running");
            return;
        }
        
        if (!isModelLoaded.get()) {
            Log.v(TAG, "Frame dropped - model not loaded");
            return;
        }
        
        if (isInLatencyPeriod.get()) {
            Log.v(TAG, "Frame dropped - in latency period");
            return;
        }

        // Highly optimized frame skipping - avoid expensive operations
        int currentFrame = frameCounter.getAndIncrement();
        if ((currentFrame & (getDynamicSkipFactor() - 1)) != 0) { // Use bitwise AND for power-of-2 skip factors
            return;
        }

        // Lock-free processing check - exit immediately if busy
        if (!isProcessingFrame.compareAndSet(false, true)) {
            Log.v(TAG, "Frame dropped - processing busy");
            return;
        }

        Log.d(TAG, "Processing frame " + totalFrames);

        // Don't retain frame - work with it immediately to reduce memory pressure
        
        // Process frame on dedicated high-priority executor
        executor.execute(() -> {
            Bitmap bitmap = null;
            
            try {
                framesProcessedCount.incrementAndGet();
                
                // Single ultra-fast conversion with early exit on failure
                bitmap = ImageUtils.videoFrameToBitmapUltraFast(frame);
                if (bitmap == null) {
                    Log.w(TAG, "Failed to convert frame to bitmap");
                    return;
                }
                
                Log.d(TAG, "Frame converted to bitmap: " + bitmap.getWidth() + "x" + bitmap.getHeight());
                  
                // Use optimized person detection
                detectPersonOptimized(bitmap, System.currentTimeMillis());
                
            } catch (Exception e) {
                Log.w(TAG, "Frame processing error: " + e.getMessage());
            } finally {
                // Immediate cleanup
                if (bitmap != null) {
                    ImageUtils.safeRecycleBitmap(bitmap);
                }
                isProcessingFrame.set(false);
            }
        });
    }
      /**
     * Get dynamic skip factor optimized for bitwise operations (power of 2)
     * Uses simplified performance-based adjustment
     */
    private int getDynamicSkipFactor() {
        long currentTime = System.currentTimeMillis();
        
        // Update skip factor every 2 seconds based on performance
        if (currentTime - lastSkipFactorUpdate > SKIP_FACTOR_UPDATE_INTERVAL) {
            lastSkipFactorUpdate = currentTime;
            
            if (averageInferenceTime > 100) {
                // Slow inference - increase skip factor (must be power of 2)
                dynamicSkipFactor = Math.min(16, Integer.highestOneBit(dynamicSkipFactor) << 1);
            } else if (averageInferenceTime < 30) {
                // Fast inference - decrease skip factor (must be power of 2)
                dynamicSkipFactor = Math.max(2, Integer.highestOneBit(dynamicSkipFactor) >> 1);
            }
            // Keep current skip factor if performance is acceptable (30-100ms)
        }
        
        // Ensure skip factor is always a power of 2 for efficient bitwise operations
        return Integer.highestOneBit(dynamicSkipFactor);
    }
    
    /**
     * Optimized person detection method with enhanced performance tracking
     */
    private void detectPersonOptimized(Bitmap bitmap, long frameStartTime) {
        if (!isRunning.get() || isInLatencyPeriod.get() || bitmap == null || bitmap.isRecycled()) {
            ImageUtils.safeRecycleBitmap(bitmap);
            return;
        }

        try {
            // Verify model availability
            if (tflite == null) {
                Log.e(TAG, "TensorFlow Lite interpreter is null");
                ImageUtils.safeRecycleBitmap(bitmap);
                return;
            }

            // Use cached model dimensions for maximum efficiency
            int inputWidth = modelInputWidth;
            int inputHeight = modelInputHeight;            // Optimized input preparation - use CHW format for YOLOv8
            ByteBuffer inputBuffer = ImageUtils.bitmapToByteBufferCHW(bitmap, inputWidth, inputHeight, 0f, 1f);
            if (inputBuffer == null) {
                ImageUtils.safeRecycleBitmap(bitmap);
                return;
            }

            // Pre-allocated output tensor using cached shape
            float[][][] output = createOutputTensor();
            if (output == null) {
                ImageUtils.safeRecycleBitmap(bitmap);
                return;
            }            // Run inference with performance tracking
            long inferenceStartTime = System.currentTimeMillis();
            Log.d(TAG, "Running inference on " + inputWidth + "x" + inputHeight + " input");
            try {
                tflite.run(inputBuffer, output);
                Log.d(TAG, "Inference successful");
            } catch (Exception e) {
                Log.e(TAG, "Inference error: " + e.getMessage(), e);
                ImageUtils.safeRecycleBitmap(bitmap);
                return;
            }
            
            long inferenceDuration = System.currentTimeMillis() - inferenceStartTime;
            Log.d(TAG, "Inference completed in " + inferenceDuration + "ms");
            updatePerformanceMetrics(inferenceDuration);

            // Process results efficiently
            Log.d(TAG, "Processing detection results...");
            processDetectionResultsOptimized(output, bitmap, inputWidth, inputHeight);

        } catch (Exception e) {
            Log.e(TAG, "Detection error: " + e.getMessage(), e);
            if (bitmap != null && !bitmap.isRecycled()) {
                ImageUtils.safeRecycleBitmap(bitmap);
            }
        }
    }
    
    /**
     * Create pre-allocated output tensor for best performance
     */
    private float[][][] createOutputTensor() {
        try {
            if (cachedOutputShape != null && cachedOutputShape.length == 3) {
                if (cachedOutputShape[1] == 84) {
                    return new float[cachedOutputShape[0]][cachedOutputShape[1]][cachedOutputShape[2]]; // [1, 84, 8400]
                } else {
                    return new float[cachedOutputShape[0]][cachedOutputShape[2]][cachedOutputShape[1]]; // [1, 8400, 84] -> transpose
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error creating output tensor: " + e.getMessage());
        }
        return null;
    }
      /**
     * Lightweight performance metrics update
     */
    private void updatePerformanceMetrics(long inferenceDuration) {
        // Simple rolling average without array allocation
        if (processedFrameCount < 10) {
            averageInferenceTime = (averageInferenceTime * processedFrameCount + inferenceDuration) / (processedFrameCount + 1);
        } else {
            // Weighted average favoring recent measurements
            averageInferenceTime = (averageInferenceTime * 9 + inferenceDuration) / 10;
        }
        
        processedFrameCount++;
        
        // Log slow inference for debugging (less frequently)
        if (inferenceDuration > 200 && processedFrameCount % 10 == 0) {
            Log.d(TAG, "Slow inference: " + inferenceDuration + "ms (avg: " + averageInferenceTime + "ms)");
        }
    }private void detectPerson(Bitmap bitmap) {
        if (!isRunning.get() || isInLatencyPeriod.get() || bitmap == null || bitmap.isRecycled()) {
            if (bitmap != null && !bitmap.isRecycled()) {
                // Return bitmap to pool instead of recycling
                ImageUtils.safeRecycleBitmap(bitmap);
            }
            return;
        }

        try {
            // Verify model is still loaded
            if (tflite == null) {
                Log.e(TAG, "TensorFlow Lite interpreter is null");
                ImageUtils.safeRecycleBitmap(bitmap);
                return;
            }

            // Use cached model input shape if available for better performance
            int inputWidth = modelInputWidth;
            int inputHeight = modelInputHeight;            // Prepare input tensor with proper error handling
            ByteBuffer inputBuffer;
            try {
                // Use CHW format conversion for YOLOv8 compatibility
                inputBuffer = ImageUtils.bitmapToByteBufferCHW(bitmap, inputWidth, inputHeight, 0f, 1f);
                if (inputBuffer == null) {
                    Log.e(TAG, "Failed to convert bitmap to ByteBuffer");
                    ImageUtils.safeRecycleBitmap(bitmap);
                    return;
                }
            } catch (Exception e) {
                Log.e(TAG, "Error converting bitmap to ByteBuffer: " + e.getMessage());
                ImageUtils.safeRecycleBitmap(bitmap);
                return;
            }

            // Use cached output shape information if available
            // For YOLOv8, output shape is typically [1, 84, 8400] or [1, 8400, 84]
            float[][][] output;
            try {
                if (cachedOutputShape.length == 3) {
                    if (cachedOutputShape[1] == 84) {
                        output = new float[cachedOutputShape[0]][cachedOutputShape[1]][cachedOutputShape[2]]; // [1, 84, 8400]
                    } else {
                        output = new float[cachedOutputShape[0]][cachedOutputShape[2]][cachedOutputShape[1]]; // [1, 8400, 84] -> transpose
                    }
                } else {
                    Log.e(TAG, "Unexpected cached output shape: " + Arrays.toString(cachedOutputShape));
                    ImageUtils.safeRecycleBitmap(bitmap);
                    return;
                }
            } catch (Exception e) {
                Log.e(TAG, "Error preparing output tensor: " + e.getMessage());
                ImageUtils.safeRecycleBitmap(bitmap);
                return;
            }

            // Run inference with timing for performance monitoring
            long inferenceStartTime = System.currentTimeMillis();
            try {
                tflite.run(inputBuffer, output);
            } catch (Exception e) {
                Log.e(TAG, "Error during model inference: " + e.getMessage());
                ImageUtils.safeRecycleBitmap(bitmap);
                return;
            }
            long inferenceDuration = System.currentTimeMillis() - inferenceStartTime;
            if (inferenceDuration > 100) {
                Log.d(TAG, "Slow inference: " + inferenceDuration + "ms");
            }            // Process results
            processDetectionResultsOptimized(output, bitmap, inputWidth, inputHeight);

        } catch (Exception e) {
            Log.e(TAG, "Detection error: " + e.getMessage(), e);
            if (bitmap != null && !bitmap.isRecycled()) {
                ImageUtils.safeRecycleBitmap(bitmap);
            }
        }
    }    /**
     * Highly optimized detection result processing
     */
    private void processDetectionResultsOptimized(float[][][] output, Bitmap bitmap, int inputWidth, int inputHeight) {
        try {
            boolean personDetected = false;
            int detectionCount = 0;

            // Log output shape for debugging
            Log.d(TAG, "Output shape: [" + output.length + ", " + output[0].length + ", " + output[0][0].length + "]");
            
            // Determine the correct output format based on shape
            int numDetections;
            boolean isTransposed = false;
            
            if (output[0].length == 84 && output[0][0].length > 1000) {
                // Format: [1, 84, 8400] - coordinates are in rows
                numDetections = output[0][0].length; // 8400 detections
                isTransposed = false;
                Log.d(TAG, "Using format [1, 84, 8400] with " + numDetections + " detections");
            } else if (output[0].length > 1000 && output[0][0].length == 84) {
                // Format: [1, 8400, 84] - coordinates are in columns
                numDetections = output[0].length; // 8400 detections
                isTransposed = true;
                Log.d(TAG, "Using format [1, 8400, 84] with " + numDetections + " detections");
            } else {
                Log.e(TAG, "Unexpected output format: [" + output.length + ", " + output[0].length + ", " + output[0][0].length + "]");
                ImageUtils.safeRecycleBitmap(bitmap);
                return;
            }
            
            // Pre-calculate scaling factors
            float xScale = (float) bitmap.getWidth() / inputWidth;
            float yScale = (float) bitmap.getHeight() / inputHeight;
            
            // Optimized detection loop with minimal object creation
            int maxLoggedDetections = Math.min(20, numDetections); // Log first 20 for debugging
            float maxPersonConfidence = 0f;
            
            for (int i = 0; i < numDetections && detectionCount < 10; i++) { // Limit to first 10 detections for performance
                float x, y, w, h, personConfidence;
                
                if (isTransposed) {
                    // Format: [1, 8400, 84] - each detection is a row
                    x = output[0][i][0]; // center x
                    y = output[0][i][1]; // center y
                    w = output[0][i][2]; // width
                    h = output[0][i][3]; // height
                    personConfidence = output[0][i][4]; // Person class confidence (index 0 in COCO + 4 coordinates)
                } else {
                    // Format: [1, 84, 8400] - coordinates are in separate rows
                    x = output[0][0][i]; // center x
                    y = output[0][1][i]; // center y
                    w = output[0][2][i]; // width
                    h = output[0][3][i]; // height
                    personConfidence = output[0][4][i]; // Person class confidence
                }
                
                // Track max confidence for debugging
                if (personConfidence > maxPersonConfidence) {
                    maxPersonConfidence = personConfidence;
                }
                
                // Log first few detections for debugging
                if (i < maxLoggedDetections) {
                    Log.v(TAG, "Detection " + i + ": conf=" + personConfidence + ", bbox=[" + x + "," + y + "," + w + "," + h + "]");
                }
                
                if (personConfidence > CONFIDENCE_THRESHOLD) {
                    personDetected = true;
                    detectionCount++;
                    
                    Log.d(TAG, "Person detected with confidence: " + personConfidence + " at [" + x + "," + y + "," + w + "," + h + "]");

                    // Minimal bounding box calculation (no object creation for performance)
                    // Convert from normalized coordinates to pixel coordinates
                    float left = (x - w/2) * xScale;
                    float top = (y - h/2) * yScale;
                    float right = (x + w/2) * xScale;
                    float bottom = (y + h/2) * yScale;

                    // Basic sanity check for valid bounding box
                    if (left >= 0 && top >= 0 && right <= bitmap.getWidth() && bottom <= bitmap.getHeight()) {
                        Log.d(TAG, "Valid person detection: confidence=" + personConfidence + 
                             ", bbox=[" + left + "," + top + "," + right + "," + bottom + "]");
                    } else {
                        Log.w(TAG, "Invalid bounding box detected");
                        detectionCount--; // Don't count invalid detections
                    }
                }
            }            Log.d(TAG, "Detection results - Person detected: " + personDetected + ", Count: " + detectionCount + 
                      ", Max person confidence: " + maxPersonConfidence + ", Threshold: " + CONFIDENCE_THRESHOLD);

            // Additional debugging - check if we're getting any meaningful values
            if (maxPersonConfidence < 0.01f) {
                Log.w(TAG, "Very low confidence values detected - possible model input/preprocessing issue");
                // Log first few raw values for debugging
                for (int i = 0; i < Math.min(5, numDetections); i++) {
                    if (isTransposed) {
                        Log.d(TAG, "Raw detection " + i + ": [" + output[0][i][0] + ", " + output[0][i][1] + 
                              ", " + output[0][i][2] + ", " + output[0][i][3] + ", " + output[0][i][4] + "]");
                    } else {
                        Log.d(TAG, "Raw detection " + i + ": [" + output[0][0][i] + ", " + output[0][1][i] + 
                              ", " + output[0][2][i] + ", " + output[0][3][i] + ", " + output[0][4][i] + "]");
                    }
                }
            } else if (maxPersonConfidence > 0.01f && !personDetected) {
                Log.i(TAG, "Model is working (max confidence: " + maxPersonConfidence + ") but below threshold (" + CONFIDENCE_THRESHOLD + ")");
            }

            if (personDetected && detectionCount > 0) {
                Log.i(TAG, "Handling person detection with count: " + detectionCount);
                handlePersonDetectionOptimized(detectionCount, bitmap);
            } else {
                Log.d(TAG, "No valid person detections found (max conf: " + maxPersonConfidence + ")");
                // Return bitmap to pool immediately
                ImageUtils.safeRecycleBitmap(bitmap);
            }

        } catch (Exception e) {
            Log.e(TAG, "Error processing detection results: " + e.getMessage());
            ImageUtils.safeRecycleBitmap(bitmap);
        }
    }    /**
     * Optimized person detection handler with minimal overhead
     */
    private void handlePersonDetectionOptimized(int personCount, Bitmap bitmap) {
        lastDetectionTime = System.currentTimeMillis();
        lastDetectedPersonCount = personCount;
        
        // Quick WebRTC reporting
        if (webRTCClient != null) {
            try {
                Map<String, Object> detectionData = new HashMap<>();
                detectionData.put("personCount", personCount);
                detectionData.put("confidence", "high");
                detectionData.put("timestamp", lastDetectionTime);
                webRTCClient.reportDetectionEvent("person", detectionData);
            } catch (Exception e) {
                Log.e(TAG, "Error reporting detection: " + e.getMessage());
            }
        }

        // Set latency period immediately to prevent multiple detections
        if (isInLatencyPeriod.compareAndSet(false, true)) {
            Log.d(TAG, "Person detected - entering latency period for " + detectionLatency + "ms");
            
            // Handle UI updates on main thread
            handler.post(() -> {
                // Quick toast notification
                Toast.makeText(context, "Person detected! (" + personCount + ")", Toast.LENGTH_SHORT).show();
                
                // Trigger additional actions
                triggerPersonDetectedOptimized();

                // Notify detection listener
                if (detectionListener != null) {
                    detectionListener.onDetectionStatusChanged(true);
                }
            });            // Schedule resumption on latency handler (ensure it's properly scheduled)
            try {
                latencyHandler.removeCallbacks(resumeDetectionRunnable);
                boolean scheduled = latencyHandler.postDelayed(resumeDetectionRunnable, detectionLatency);
                Log.i(TAG, "Latency resumption scheduled: " + scheduled + " (delay: " + detectionLatency + "ms)");
                
                if (!scheduled) {
                    Log.e(TAG, "Failed to schedule latency resumption - forcing immediate reset");
                    isInLatencyPeriod.set(false);
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to schedule latency resumption: " + e.getMessage());
                // Fallback: reset latency period immediately if scheduling fails
                isInLatencyPeriod.set(false);
            }
        } else {
            Log.d(TAG, "Already in latency period, skipping duplicate detection");
        }

        // Return bitmap to pool
        ImageUtils.safeRecycleBitmap(bitmap);
    }

    /**
     * Optimized person detection trigger with minimal overhead
     */
    private void triggerPersonDetectedOptimized() {
        try {
            // Start video recording only if auto recording is enabled
            if (autoRecordingEnabled) {
                startTimedVideoRecording();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in optimized person detection trigger: " + e.getMessage());
        }
    }private void handlePersonDetection(List<Detection> detections, Bitmap bitmap) {
        lastDetectionTime = System.currentTimeMillis();

        // Count the number of persons detected and update the tracker
        int personCount = detections.size();
        lastDetectedPersonCount = personCount; // Update the tracker
        
        // Call the method to report the detection to the host
        onPersonDetected(personCount);

        handler.post(() -> {
            // Only enter latency period if we're not already in it
            if (isInLatencyPeriod.compareAndSet(false, true)) {
                // Show toast immediately
                Toast.makeText(context, "Person detected! (" + personCount + " person" + (personCount > 1 ? "s" : "") + ")", Toast.LENGTH_SHORT).show();
                
                // Trigger person detected notification (recording only, no image capture)
                triggerPersonDetected();

                if (detectionListener != null) {
                    detectionListener.onDetectionStatusChanged(true);
                }

                Log.d(TAG, "Person detected - pausing detection for " + detectionLatency + " ms");

                // Schedule resumption after user-defined latency period
                latencyHandler.removeCallbacks(resumeDetectionRunnable);
                latencyHandler.postDelayed(resumeDetectionRunnable, detectionLatency);
            }
        });

        // Return bitmap to pool instead of directly recycling
        ImageUtils.safeRecycleBitmap(bitmap);
    }// In PersonDetection.java, modify triggerPersonDetected() method:
    private void triggerPersonDetected() {
        try {
            // Always show toast
            uiHandler.post(() -> Toast.makeText(context, "Person detected!", Toast.LENGTH_SHORT).show());

            // Always report detection to WebRTC host first (for live monitoring)
            if (webRTCClient != null) {
                // Count how many people were detected (could be multiple in one frame)
                int personCount = lastDetectedPersonCount > 0 ? lastDetectedPersonCount : 1;
                
                Log.d(TAG, "Reporting person detection to host: " + personCount + " person(s)");
                
                // Create enhanced detection data with all details needed for notification card
                Map<String, Object> detectionData = new HashMap<>();
                detectionData.put("personCount", personCount);
                detectionData.put("confidence", "high");
                detectionData.put("detectionMethod", "yolov8");
                detectionData.put("processingTime", System.currentTimeMillis()); // For performance tracking
                detectionData.put("recordingDuration", videoRecordingDuration); // Include video duration info
                
                // Report person detection with enhanced data
                webRTCClient.reportDetectionEvent("person", detectionData);
            } else {
                Log.w(TAG, "WebRTC client is null, cannot report detection");
            }

            // Start video recording only if auto recording is enabled
            if (autoRecordingEnabled) {
                startTimedVideoRecording();
                Log.d(TAG, "Auto recording enabled - starting video recording");
            } else {
                Log.d(TAG, "Auto recording disabled - skipping video recording");
            }
            
            // Check if notification should be created (this only affects local notifications, not live reporting)
            if (!NotificationSettings.isPersonNotificationsEnabled(context)) {
                Log.d(TAG, "Person notifications disabled, skipping notification creation");
                NotificationSettings.debugCurrentSettings(context); // Debug the settings
                return;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error handling person detection", e);
        }
    }// Helper method to count detected people (implementation depends on your detection logic)
    private int countDetectedPeople(Bitmap bitmap) {
        // This should track the actual count from the last detection
        // For now, return the last known person count from detection results
        return lastDetectedPersonCount > 0 ? lastDetectedPersonCount : 1;
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
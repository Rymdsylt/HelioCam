package com.summersoft.heliocam.utils;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.util.Log;

import org.webrtc.VideoFrame;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class ImageUtils {

    private static final String TAG = "ImageUtils";    public static Bitmap videoFrameToBitmap(VideoFrame frame) {
        if (frame == null) {
            Log.e(TAG, "VideoFrame is null");
            return null;
        }

        VideoFrame.Buffer buffer = frame.getBuffer();
        if (buffer == null) {
            Log.e(TAG, "VideoFrame buffer is null");
            return null;
        }

        // Don't retain here - the buffer is already valid when passed to us
        try {
            VideoFrame.I420Buffer i420Buffer = buffer.toI420();
            if (i420Buffer == null) {
                Log.e(TAG, "Failed to convert to I420Buffer");
                return null;
            }

            try {
                int width = i420Buffer.getWidth();
                int height = i420Buffer.getHeight();

                if (width <= 0 || height <= 0) {
                    Log.e(TAG, "Invalid frame dimensions: " + width + "x" + height);
                    return null;
                }

                // Reasonable downscaling for better performance but still good quality
                int targetWidth = Math.max(width / 2, 240);
                int targetHeight = Math.max(height / 2, 180);

                // Get YUV planes
                ByteBuffer yBuffer = i420Buffer.getDataY();
                ByteBuffer uBuffer = i420Buffer.getDataU();
                ByteBuffer vBuffer = i420Buffer.getDataV();

                if (yBuffer == null || uBuffer == null || vBuffer == null) {
                    Log.e(TAG, "YUV buffers are null");
                    return null;
                }

                // Use optimized conversion
                return createOptimizedBitmap(i420Buffer, targetWidth, targetHeight);
            } finally {
                // Don't manually release i420Buffer - let WebRTC handle it
            }
        } catch (Exception e) {
            Log.e(TAG, "Error converting VideoFrame to Bitmap: " + e.getMessage(), e);
            return null;
        }
        // Don't manually release buffer - let WebRTC handle it
    }// Bitmap pool to reduce memory allocation
    private static final Object poolLock = new Object();
    private static Bitmap cachedBitmap = null;
    private static int cachedBitmapWidth = 0;
    private static int cachedBitmapHeight = 0;
    
    /**
     * Fast bitmap conversion for real-time detection with minimal quality loss
     */
    public static Bitmap videoFrameToBitmapFast(VideoFrame frame) {
        if (frame == null) {
            return null;
        }

        VideoFrame.Buffer buffer = frame.getBuffer();
        if (buffer == null) {
            return null;
        }

        VideoFrame.I420Buffer i420Buffer = null;
        try {
            i420Buffer = buffer.toI420();
            if (i420Buffer == null) {
                return null;
            }

            int width = i420Buffer.getWidth();
            int height = i420Buffer.getHeight();

            if (width <= 0 || height <= 0) {
                return null;
            }

            // Use smaller target size for faster processing
            // More rectangular shape (keeping aspect ratio better) rather than square
            int targetWidth = 192;  // Optimized smaller size for detection
            int targetHeight = 144; // Maintains 4:3 aspect ratio for most cameras

            // Use bitmap from pool if possible to reduce allocations
            Bitmap resultBitmap = createOptimizedBitmap(i420Buffer, targetWidth, targetHeight);
            
            // The buffer is managed by WebRTC and doesn't need manual release
            return resultBitmap;
        } catch (Exception e) {
            Log.e(TAG, "Error in fast bitmap conversion: " + e.getMessage());
            return null;
        }
    }
      /**
     * Ultra-fast bitmap conversion specifically for object detection
     * Prioritizes speed over quality, suitable only for detection algorithms
     */
    public static Bitmap videoFrameToBitmapUltraFast(VideoFrame frame) {
        if (frame == null || frame.getBuffer() == null) {
            return null;
        }
        
        try {
            VideoFrame.Buffer buffer = frame.getBuffer();
            VideoFrame.I420Buffer i420Buffer = buffer.toI420();
            if (i420Buffer == null) {
                return null;
            }
            
            // Fixed small dimensions for maximum speed and consistency
            int targetWidth = 160;
            int targetHeight = 120;
            
            // Use ultra-optimized luminance-only processing
            return createUltraFastLuminanceBitmap(i420Buffer, targetWidth, targetHeight);
        } catch (Exception e) {
            Log.e(TAG, "Error in ultra-fast bitmap conversion: " + e.getMessage());
            return null;
        }
    }
      /**
     * Maximum performance luminance-only bitmap creation with pre-allocated buffers
     */
    private static Bitmap createUltraFastLuminanceBitmap(VideoFrame.I420Buffer i420Buffer, int targetWidth, int targetHeight) {
        ByteBuffer yBuffer = i420Buffer.getDataY();
        if (yBuffer == null) {
            return null;
        }
        
        int width = i420Buffer.getWidth();
        int height = i420Buffer.getHeight();
        int yStride = i420Buffer.getStrideY();
        
        // Get bitmap from dedicated detection pool for maximum performance
        Bitmap bitmap = getDetectionBitmap();
        
        // Use pre-allocated pixel array to minimize allocations
        int[] pixels = new int[targetWidth * targetHeight];
        
        // Ultra-fast scaling with simple integer math
        int xStep = Math.max(1, width / targetWidth);
        int yStep = Math.max(1, height / targetHeight);
        
        // Optimized luminance-only processing with minimal bounds checking
        for (int y = 0; y < targetHeight; y++) {
            int srcY = Math.min(y * yStep, height - 1);
            int yRowIndex = srcY * yStride;
            int pixelRowStart = y * targetWidth;
            
            for (int x = 0; x < targetWidth; x++) {
                int srcX = Math.min(x * xStep, width - 1);
                
                try {
                    // Direct Y value access with minimal processing
                    int yValue = yBuffer.get(yRowIndex + srcX) & 0xFF;
                    
                    // Ultra-fast grayscale conversion
                    pixels[pixelRowStart + x] = 0xFF000000 | (yValue << 16) | (yValue << 8) | yValue;
                } catch (Exception e) {
                    pixels[pixelRowStart + x] = 0xFF000000; // Black pixel fallback
                }
            }
        }
        
        // Single batch pixel update for maximum performance
        bitmap.setPixels(pixels, 0, targetWidth, 0, 0, targetWidth, targetHeight);
        return bitmap;
    }private static Bitmap createOptimizedBitmap(VideoFrame.I420Buffer i420Buffer, int targetWidth, int targetHeight) {
        // Get YUV data
        ByteBuffer yBuffer = i420Buffer.getDataY();
        ByteBuffer uBuffer = i420Buffer.getDataU();
        ByteBuffer vBuffer = i420Buffer.getDataV();
        
        if (yBuffer == null || uBuffer == null || vBuffer == null) {
            return null;
        }
        
        int width = i420Buffer.getWidth();
        int height = i420Buffer.getHeight();
        int yStride = i420Buffer.getStrideY();
        int uStride = i420Buffer.getStrideU();
        int vStride = i420Buffer.getStrideV();
        
        // Check if we can reuse an existing bitmap from the pool
        Bitmap bitmap = null;
        synchronized (poolLock) {
            if (cachedBitmap != null && !cachedBitmap.isRecycled() && 
                cachedBitmapWidth == targetWidth && cachedBitmapHeight == targetHeight) {
                bitmap = cachedBitmap;
                cachedBitmap = null; // Remove from pool
            }
        }
        
        // Create new bitmap if none available in pool
        if (bitmap == null) {
            bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888);
        }
        
        // Pre-allocate the pixel array for better performance
        int[] pixels = new int[targetWidth * targetHeight];
        
        // Use fixed-point arithmetic to speed up scaling
        final int FP_SHIFT = 16;
        final int xRatio = (int)((width << FP_SHIFT) / targetWidth);
        final int yRatio = (int)((height << FP_SHIFT) / targetHeight);
        
        // Process all pixels with optimized loops
        for (int y = 0; y < targetHeight; y++) {
            // Calculate source Y coordinate
            int srcY = (y * yRatio) >> FP_SHIFT;
            int yRowIndex = srcY * yStride;
            int uvRowIndex = (srcY >> 1) * uStride; // UV plane is half height
            
            for (int x = 0; x < targetWidth; x++) {
                // Calculate source X coordinate
                int srcX = (x * xRatio) >> FP_SHIFT;
                int pixelIndex = y * targetWidth + x;
                
                try {
                    // Get Y value (luminance)
                    int yValue = yBuffer.get(yRowIndex + srcX) & 0xFF;
                    
                    // Get U and V values (chrominance)
                    // UV planes are at half resolution
                    int uvX = srcX >> 1;
                    int uValue = uBuffer.get(uvRowIndex + uvX) & 0xFF;
                    int vValue = vBuffer.get(uvRowIndex + uvX) & 0xFF;
                    
                    // Convert YUV to RGB using optimized formula
                    pixels[pixelIndex] = YUVtoRGBFast(yValue, uValue, vValue);
                } catch (Exception e) {
                    // Handle boundary issues safely
                    pixels[pixelIndex] = 0xFF000000; // Black pixel
                }
            }
        }
        
        // Set pixels in the bitmap
        bitmap.setPixels(pixels, 0, targetWidth, 0, 0, targetWidth, targetHeight);
        return bitmap;
    }
      /**
     * Ultra-optimized method that creates a bitmap using only luminance (Y channel)
     * This is extremely fast but loses color information - good enough for many detection models
     */
    private static Bitmap createLuminanceOnlyBitmap(VideoFrame.I420Buffer i420Buffer, int targetWidth, int targetHeight) {
        ByteBuffer yBuffer = i420Buffer.getDataY();
        if (yBuffer == null) {
            return null;
        }
        
        int width = i420Buffer.getWidth();
        int height = i420Buffer.getHeight();
        int yStride = i420Buffer.getStrideY();
        
        // Check if we can reuse an existing bitmap from the pool
        Bitmap bitmap = null;
        synchronized (poolLock) {
            if (cachedBitmap != null && !cachedBitmap.isRecycled() && 
                cachedBitmapWidth == targetWidth && cachedBitmapHeight == targetHeight) {
                bitmap = cachedBitmap;
                cachedBitmap = null; // Remove from pool
            }
        }
        
        // Create new bitmap if none available in pool
        if (bitmap == null) {
            bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888);
        }
        
        // Pre-allocate pixel array
        int[] pixels = new int[targetWidth * targetHeight];
        
        // Use fixed-point arithmetic for faster scaling
        final int FP_SHIFT = 16;
        final int xRatio = (int)((width << FP_SHIFT) / targetWidth);
        final int yRatio = (int)((height << FP_SHIFT) / targetHeight);
        
        // We'll only use Y channel (grayscale) which is much faster
        for (int y = 0; y < targetHeight; y++) {
            int srcY = (y * yRatio) >> FP_SHIFT;
            int yRowIndex = srcY * yStride;
            
            for (int x = 0; x < targetWidth; x++) {
                int srcX = (x * xRatio) >> FP_SHIFT;
                int pixelIndex = y * targetWidth + x;
                
                try {
                    // Get Y value (luminance)
                    int yValue = yBuffer.get(yRowIndex + srcX) & 0xFF;
                    
                    // Convert Y (luminance) directly to grayscale RGB
                    // This is much faster than full YUV conversion
                    pixels[pixelIndex] = 0xFF000000 | (yValue << 16) | (yValue << 8) | yValue;
                } catch (Exception e) {
                    pixels[pixelIndex] = 0xFF000000; // Black pixel
                }
            }
        }
        
        bitmap.setPixels(pixels, 0, targetWidth, 0, 0, targetWidth, targetHeight);
        return bitmap;
    }
    
    /**
     * Optimized YUV to RGB conversion using pre-calculated lookup values
     * This version uses integer arithmetic for speed on older devices
     */
    private static int YUVtoRGBFast(int y, int u, int v) {
        // Faster YUV to RGB conversion using integer math
        // Avoid floating point operations for better performance
        y = Math.max(0, y - 16);
        u = u - 128;
        v = v - 128;
        
        // Constants pre-multiplied and converted to fixed-point
        int y1192 = 1192 * y;
        int r = (y1192 + 1634 * v) >> 10;
        int g = (y1192 - 833 * v - 400 * u) >> 10;
        int b = (y1192 + 2066 * u) >> 10;
        
        // Clamp using bit-masking for better performance
        r = Math.min(Math.max(r, 0), 255);
        g = Math.min(Math.max(g, 0), 255);
        b = Math.min(Math.max(b, 0), 255);
        
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }    /**
     * Enhanced bitmap pool management with ultra-fast pooling
     */
    public static boolean recycleBitmapToPool(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) {
            return false;
        }
        
        synchronized (poolLock) {
            // Only pool bitmaps of common detection sizes for efficiency
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            
            if ((width == 160 && height == 120) || (width == 192 && height == 144) || (width == 320 && height == 240)) {
                // Replace existing cached bitmap if available
                if (cachedBitmap != null && !cachedBitmap.isRecycled()) {
                    cachedBitmap.recycle();
                }
                
                cachedBitmap = bitmap;
                cachedBitmapWidth = width;
                cachedBitmapHeight = height;
                return true;
            }
        }
        
        // Not a poolable size, just recycle normally
        bitmap.recycle();
        return false;
    }
    
    /**
     * Enhanced bitmap pool clearing with proper cleanup
     */
    public static void clearBitmapPool() {
        synchronized (poolLock) {
            if (cachedBitmap != null && !cachedBitmap.isRecycled()) {
                cachedBitmap.recycle();
                cachedBitmap = null;
            }
            cachedBitmapWidth = 0;
            cachedBitmapHeight = 0;
        }
    }
      /**
     * Ultra-fast safe bitmap recycling with optimal pool management
     */
    public static void safeRecycleBitmap(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) {
            return;
        }
        
        // Check if it's a detection bitmap first (most common case)
        if (bitmap.getWidth() == DETECTION_WIDTH && bitmap.getHeight() == DETECTION_HEIGHT) {
            returnDetectionBitmap(bitmap);
            return;
        }
        
        // Try to pool other sizes, recycle if not poolable
        recycleBitmapToPool(bitmap);
    }private static int YUVtoRGB(int y, int u, int v) {
        // Improved YUV to RGB conversion - matches YUVtoRGBFast for consistency
        y = Math.max(0, y - 16);
        u = u - 128;
        v = v - 128;

        // Constants pre-multiplied and converted to fixed-point
        int y1192 = 1192 * y;
        int r = (y1192 + 1634 * v) >> 10;
        int g = (y1192 - 833 * v - 400 * u) >> 10;
        int b = (y1192 + 2066 * u) >> 10;
        
        // Clamp using bit-masking for better performance
        r = Math.min(Math.max(r, 0), 255);
        g = Math.min(Math.max(g, 0), 255);
        b = Math.min(Math.max(b, 0), 255);
        
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }    public static ByteBuffer bitmapToByteBuffer(Bitmap bitmap, int width, int height, float mean, float std) {
        Bitmap scaledBitmap = null;
        boolean recycleBitmap = false;
        
        try {
            if (bitmap.getWidth() != width || bitmap.getHeight() != height) {
                scaledBitmap = Bitmap.createScaledBitmap(bitmap, width, height, false); // Use false for faster scaling
                recycleBitmap = true; // We'll need to recycle this temporary bitmap
            } else {
                scaledBitmap = bitmap;
            }
    
            // Calculate buffer size based on model's expected format (float32)
            int bytesPerChannel = 4; // Float32 is 4 bytes
            int channels = 3; // RGB
            ByteBuffer inputBuffer = ByteBuffer.allocateDirect(bytesPerChannel * width * height * channels);
            inputBuffer.order(ByteOrder.nativeOrder());
    
            int[] intValues = new int[width * height];
            scaledBitmap.getPixels(intValues, 0, width, 0, 0, width, height);
    
            // Fix: Properly use mean and std parameters for normalization
            // YOLOv8 expects normalized values: (pixel_value / 255.0 - mean) / std
            float scale = 1.0f / 255.0f;
            
            // Debug logging for first few pixels to verify preprocessing
            if (width == 640 && height == 640) {
                Log.d(TAG, "Bitmap preprocessing: mean=" + mean + ", std=" + std + ", scale=" + scale);
                Log.d(TAG, "First few raw pixels: " + Integer.toHexString(intValues[0]) + ", " + 
                      Integer.toHexString(intValues[1]) + ", " + Integer.toHexString(intValues[2]));
            }
            
            // Process pixels in batches for better cache locality
            int batchSize = width;
            for (int i = 0; i < height; i++) {
                int offset = i * width;
                for (int j = 0; j < width; j += batchSize) {
                    int blockSize = Math.min(batchSize, width - j);
                    for (int k = 0; k < blockSize; k++) {
                        int pixel = intValues[offset + j + k];
                        
                        // Extract and properly normalize RGB values using mean and std
                        float r = (((pixel >> 16) & 0xFF) * scale - mean) / std;
                        float g = (((pixel >> 8) & 0xFF) * scale - mean) / std;
                        float b = ((pixel & 0xFF) * scale - mean) / std;
                        
                        inputBuffer.putFloat(r);
                        inputBuffer.putFloat(g);
                        inputBuffer.putFloat(b);
                        
                        // Debug logging for first few processed pixels
                        if ((i * width + j + k) < 3 && width == 640 && height == 640) {
                            Log.d(TAG, "Processed pixel " + (i * width + j + k) + ": R=" + r + ", G=" + g + ", B=" + b);
                        }
                    }
                }
            }
    
            // Recycle the scaled bitmap if we created a new one
            if (recycleBitmap && scaledBitmap != bitmap) {
                scaledBitmap.recycle();
            }
    
            inputBuffer.rewind();
            return inputBuffer;
        } catch (Exception e) {
            Log.e(TAG, "Error in bitmapToByteBuffer: " + e.getMessage());
            if (recycleBitmap && scaledBitmap != bitmap) {
                scaledBitmap.recycle();
            }
            return null;
        }
    }

    public static Bitmap rotateBitmap(Bitmap bitmap, int rotationDegrees) {
        if (bitmap == null) {
            return null;
        }

        Matrix matrix = new Matrix();
        matrix.postRotate(rotationDegrees);
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }

    public static Bitmap flipBitmap(Bitmap bitmap, boolean horizontal, boolean vertical) {
        if (bitmap == null) {
            return null;
        }

        Matrix matrix = new Matrix();
        matrix.preScale(horizontal ? -1 : 1, vertical ? -1 : 1);
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }

    public static Bitmap cropCenterSquare(Bitmap bitmap) {
        if (bitmap == null) {
            return null;
        }

        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int size = Math.min(width, height);

        int x = (width - size) / 2;
        int y = (height - size) / 2;

        return Bitmap.createBitmap(bitmap, x, y, size, size);
    }

    /**
     * Ultra-fast bitmap to ByteBuffer conversion for real-time detection
     * Optimized for small bitmaps (160x120) with minimal overhead
     */
    public static ByteBuffer bitmapToByteBufferFast(Bitmap bitmap, int width, int height) {
        if (bitmap == null || bitmap.isRecycled()) {
            return null;
        }
        
        try {
            // Assume bitmap is already the correct size to avoid scaling
            int channels = 3; // RGB
            ByteBuffer inputBuffer = ByteBuffer.allocateDirect(width * height * channels * 4); // Float32
            inputBuffer.order(ByteOrder.nativeOrder());
            
            // Direct pixel access with minimal allocations
            int[] pixels = new int[width * height];
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
            
            // Ultra-fast normalization with fixed-point arithmetic
            final int SCALE_FACTOR = 16; // 2^16 = 65536
            final int SCALE_VALUE = (int)(65536.0f / 255.0f); // Pre-calculated scale
            
            // Optimized pixel processing
            for (int i = 0; i < pixels.length; i++) {
                int pixel = pixels[i];
                
                // Extract RGB and normalize in one operation using bit shifts
                int r = (pixel >> 16) & 0xFF;
                int g = (pixel >> 8) & 0xFF;
                int b = pixel & 0xFF;
                
                // Convert to float using pre-calculated scale
                inputBuffer.putFloat((r * SCALE_VALUE) >> SCALE_FACTOR);
                inputBuffer.putFloat((g * SCALE_VALUE) >> SCALE_FACTOR);
                inputBuffer.putFloat((b * SCALE_VALUE) >> SCALE_FACTOR);
            }
            
            inputBuffer.rewind();
            return inputBuffer;
            
        } catch (Exception e) {
            Log.e(TAG, "Error in fast buffer conversion: " + e.getMessage());
            return null;
        }
    }

    /**
     * Convert bitmap to ByteBuffer in CHW format (Channel-Height-Width) for YOLOv8 models
     * Most YOLOv8 models expect CHW format rather than HWC format
     */
    public static ByteBuffer bitmapToByteBufferCHW(Bitmap bitmap, int width, int height, float mean, float std) {
        if (bitmap == null || bitmap.isRecycled()) {
            Log.e(TAG, "Invalid bitmap provided to bitmapToByteBufferCHW");
            return null;
        }

        Bitmap scaledBitmap = null;
        boolean recycleBitmap = false;
        
        try {
            if (bitmap.getWidth() != width || bitmap.getHeight() != height) {
                scaledBitmap = Bitmap.createScaledBitmap(bitmap, width, height, false); // Use false for faster scaling
                recycleBitmap = true; // We'll need to recycle this temporary bitmap
            } else {
                scaledBitmap = bitmap;
            }
    
            // Calculate buffer size based on model's expected format (float32)
            int bytesPerChannel = 4; // Float32 is 4 bytes
            int channels = 3; // RGB
            ByteBuffer inputBuffer = ByteBuffer.allocateDirect(bytesPerChannel * width * height * channels);
            inputBuffer.order(ByteOrder.nativeOrder());
    
            int[] intValues = new int[width * height];
            scaledBitmap.getPixels(intValues, 0, width, 0, 0, width, height);
    
            // YOLOv8 expects normalized values: (pixel_value / 255.0 - mean) / std
            float scale = 1.0f / 255.0f;
            
            // Debug logging for first few pixels to verify preprocessing
            Log.d(TAG, "CHW Bitmap preprocessing: mean=" + mean + ", std=" + std + ", scale=" + scale);
            Log.d(TAG, "CHW Input tensor shape should be [1, 3, " + height + ", " + width + "]");
            
            // CHW Format: Store all R values, then all G values, then all B values
            // This is the format most YOLOv8 models expect
            
            // Red channel first
            for (int i = 0; i < height; i++) {
                for (int j = 0; j < width; j++) {
                    int pixel = intValues[i * width + j];
                    float r = (((pixel >> 16) & 0xFF) * scale - mean) / std;
                    inputBuffer.putFloat(r);
                }
            }
            
            // Green channel second
            for (int i = 0; i < height; i++) {
                for (int j = 0; j < width; j++) {
                    int pixel = intValues[i * width + j];
                    float g = (((pixel >> 8) & 0xFF) * scale - mean) / std;
                    inputBuffer.putFloat(g);
                }
            }
            
            // Blue channel third
            for (int i = 0; i < height; i++) {
                for (int j = 0; j < width; j++) {
                    int pixel = intValues[i * width + j];
                    float b = ((pixel & 0xFF) * scale - mean) / std;
                    inputBuffer.putFloat(b);
                }
            }
            
            // Debug logging for processed values
            inputBuffer.rewind();
            float firstR = inputBuffer.getFloat(0);
            float firstG = inputBuffer.getFloat(width * height * 4); // G channel starts here
            float firstB = inputBuffer.getFloat(width * height * 8); // B channel starts here
            Log.d(TAG, "CHW First pixel values: R=" + firstR + ", G=" + firstG + ", B=" + firstB);
            inputBuffer.rewind();
    
            // Recycle the scaled bitmap if we created a new one
            if (recycleBitmap && scaledBitmap != bitmap) {
                scaledBitmap.recycle();
            }
    
            return inputBuffer;
        } catch (Exception e) {
            Log.e(TAG, "Error in bitmapToByteBufferCHW: " + e.getMessage(), e);
            if (recycleBitmap && scaledBitmap != bitmap) {
                scaledBitmap.recycle();
            }
            return null;
        }
    }

    // High-performance bitmap pool for detection
    private static final Object DETECTION_POOL_LOCK = new Object();
    private static Bitmap detectionBitmapPool = null;
    private static final int DETECTION_WIDTH = 160;
    private static final int DETECTION_HEIGHT = 120;
    
    /**
     * Get optimized bitmap from pool for detection (160x120)
     */
    public static Bitmap getDetectionBitmap() {
        synchronized (DETECTION_POOL_LOCK) {
            if (detectionBitmapPool != null && !detectionBitmapPool.isRecycled()) {
                Bitmap bitmap = detectionBitmapPool;
                detectionBitmapPool = null;
                return bitmap;
            }
        }
        return Bitmap.createBitmap(DETECTION_WIDTH, DETECTION_HEIGHT, Bitmap.Config.ARGB_8888);
    }
    
    /**
     * Return detection bitmap to pool
     */
    public static void returnDetectionBitmap(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled() || 
            bitmap.getWidth() != DETECTION_WIDTH || bitmap.getHeight() != DETECTION_HEIGHT) {
            return;
        }
        
        synchronized (DETECTION_POOL_LOCK) {
            if (detectionBitmapPool != null && !detectionBitmapPool.isRecycled()) {
                detectionBitmapPool.recycle();
            }
            detectionBitmapPool = bitmap;
        }
    }
    
    /**
     * Clear detection bitmap pool
     */
    public static void clearDetectionBitmapPool() {
        synchronized (DETECTION_POOL_LOCK) {
            if (detectionBitmapPool != null && !detectionBitmapPool.isRecycled()) {
                detectionBitmapPool.recycle();
            }
            detectionBitmapPool = null;
        }
    }
}
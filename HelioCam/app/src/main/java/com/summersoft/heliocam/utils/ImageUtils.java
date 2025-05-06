package com.summersoft.heliocam.utils;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.util.Log;

import org.webrtc.VideoFrame;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class ImageUtils {

    private static final String TAG = "ImageUtils";

    public static Bitmap videoFrameToBitmap(VideoFrame frame) {
        VideoFrame.Buffer buffer = frame.getBuffer();
        // Explicitly retain the buffer before conversion
        buffer.retain();

        try {
            VideoFrame.I420Buffer i420Buffer = buffer.toI420();
            try {
                int width = i420Buffer.getWidth();
                int height = i420Buffer.getHeight();

                // Downscale during conversion for detection purposes
                int targetWidth = width / 2;  // Reduce resolution by half
                int targetHeight = height / 2;

                // Get YUV planes
                ByteBuffer yBuffer = i420Buffer.getDataY();
                ByteBuffer uBuffer = i420Buffer.getDataU();
                ByteBuffer vBuffer = i420Buffer.getDataV();

                int[] argb = new int[targetWidth * targetHeight];
                convertYUV420ToARGB8888Downscaled(
                        yBuffer, uBuffer, vBuffer,
                        argb, width, height, targetWidth, targetHeight,
                        i420Buffer.getStrideY(),
                        i420Buffer.getStrideU(),
                        i420Buffer.getStrideV()
                );

                Bitmap bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888);
                bitmap.setPixels(argb, 0, targetWidth, 0, 0, targetWidth, targetHeight);
                return bitmap;
            } finally {
                i420Buffer.release(); // Release I420Buffer when done
            }
        } finally {
            buffer.release(); // Always release the original buffer
        }
    }

    private static void convertYUV420ToARGB8888Downscaled(
            ByteBuffer yBuffer, ByteBuffer uBuffer, ByteBuffer vBuffer,
            int[] output, int origWidth, int origHeight, int targetWidth, int targetHeight,
            int yStride, int uStride, int vStride
    ) {
        int yp = 0;
        for (int j = 0; j < targetHeight; j++) {
            int origJ = j * origHeight / targetHeight;
            int pY = yStride * origJ;
            int pU = uStride * (origJ / 2);
            int pV = vStride * (origJ / 2);

            for (int i = 0; i < targetWidth; i++) {
                int origI = i * origWidth / targetWidth;

                // Check bounds
                if (pY + origI >= yBuffer.capacity()) {
                    output[yp++] = 0xFF000000; // Black pixel
                    continue;
                }

                int uvIndex = (origI / 2);
                if (pU + uvIndex >= uBuffer.capacity() || pV + uvIndex >= vBuffer.capacity()) {
                    output[yp++] = 0xFF000000; // Black pixel
                    continue;
                }

                // Get YUV values
                int y = yBuffer.get(pY + origI) & 0xff;
                int u = uBuffer.get(pU + uvIndex) & 0xff;
                int v = vBuffer.get(pV + uvIndex) & 0xff;

                // Convert to RGB
                output[yp++] = YUVtoRGB(y, u, v);
            }
        }
    }

    private static int YUVtoRGB(int y, int u, int v) {
        // Convert YUV to RGB
        y = Math.max(0, y - 16);
        u = u - 128;
        v = v - 128;

        // Using BT.601 conversion formulas
        int r = (int)(1.164 * y + 1.596 * v);
        int g = (int)(1.164 * y - 0.392 * u - 0.813 * v);
        int b = (int)(1.164 * y + 2.017 * u);

        // Clamp values to 0-255
        r = Math.max(0, Math.min(255, r));
        g = Math.max(0, Math.min(255, g));
        b = Math.max(0, Math.min(255, b));

        return 0xff000000 | (r << 16) | (g << 8) | b;
    }

    public static ByteBuffer bitmapToByteBuffer(Bitmap bitmap, int width, int height, float mean, float std) {
        Bitmap scaledBitmap;
        if (bitmap.getWidth() != width || bitmap.getHeight() != height) {
            scaledBitmap = Bitmap.createScaledBitmap(bitmap, width, height, false); // Use false for faster scaling
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

        // More efficient loop
        int pixel = 0;
        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                final int val = intValues[pixel++];

                // YOLOv8 typically uses 0-1 normalized RGB values
                inputBuffer.putFloat(((val >> 16) & 0xFF) / 255.0f);
                inputBuffer.putFloat(((val >> 8) & 0xFF) / 255.0f);
                inputBuffer.putFloat((val & 0xFF) / 255.0f);
            }
        }

        if (scaledBitmap != bitmap) {
            scaledBitmap.recycle();
        }

        inputBuffer.rewind();
        return inputBuffer;
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
}
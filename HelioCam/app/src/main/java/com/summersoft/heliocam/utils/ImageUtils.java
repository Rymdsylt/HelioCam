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
        VideoFrame.I420Buffer i420Buffer = frame.getBuffer().toI420();
        try {
            int width = i420Buffer.getWidth();
            int height = i420Buffer.getHeight();

            // Get YUV planes
            ByteBuffer yBuffer = i420Buffer.getDataY();
            ByteBuffer uBuffer = i420Buffer.getDataU();
            ByteBuffer vBuffer = i420Buffer.getDataV();

            int[] argb = new int[width * height];
            convertYUV420ToARGB8888(
                    yBuffer, uBuffer, vBuffer,
                    argb, width, height,
                    i420Buffer.getStrideY(),
                    i420Buffer.getStrideU(),
                    i420Buffer.getStrideV()
            );

            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            bitmap.setPixels(argb, 0, width, 0, 0, width, height);
            return bitmap;
        } catch (Exception e) {
            Log.e(TAG, "Frame conversion error", e);
            return null;
        } finally {
            i420Buffer.release();
        }
    }

    private static void convertYUV420ToARGB8888(
            ByteBuffer yBuffer, ByteBuffer uBuffer, ByteBuffer vBuffer,
            int[] output, int width, int height,
            int yStride, int uStride, int vStride
    ) {
        int yp = 0;
        for (int j = 0; j < height; j++) {
            int pY = yStride * j;
            int pU = uStride * (j / 2);
            int pV = vStride * (j / 2);

            for (int i = 0; i < width; i++) {
                // Add bounds checking to prevent IndexOutOfBoundsException
                if (pY + i >= yBuffer.capacity()) {
                    output[yp++] = 0xFF000000; // Black pixel
                    continue;
                }

                // Calculate U and V indices - they're at half resolution
                int uvIndex = (i / 2);

                // Check U and V buffer bounds
                if (pU + uvIndex >= uBuffer.capacity() || pV + uvIndex >= vBuffer.capacity()) {
                    output[yp++] = 0xFF000000; // Black pixel
                    continue;
                }

                // Get YUV values
                int y = yBuffer.get(pY + i) & 0xff;
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
        Bitmap scaledBitmap = Bitmap.createScaledBitmap(bitmap, width, height, true);
        ByteBuffer inputBuffer = ByteBuffer.allocateDirect(4 * width * height * 3);
        inputBuffer.order(ByteOrder.nativeOrder());

        int[] intValues = new int[width * height];
        scaledBitmap.getPixels(intValues, 0, width, 0, 0, width, height);

        for (int pixelValue : intValues) {
            float r = (((pixelValue >> 16) & 0xFF) - mean) / std;
            float g = (((pixelValue >> 8) & 0xFF) - mean) / std;
            float b = ((pixelValue & 0xFF) - mean) / std;

            inputBuffer.putFloat(r);
            inputBuffer.putFloat(g);
            inputBuffer.putFloat(b);
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
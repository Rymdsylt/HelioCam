// FileUtils.java
package com.summersoft.heliocam.utils;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.util.Log;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

public class FileUtils {
    private static final String TAG = "FileUtils";

    public static MappedByteBuffer loadModelFile(Context context, String modelFile) throws IOException {
        AssetFileDescriptor fileDescriptor = context.getAssets().openFd(modelFile);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();

        Log.d(TAG, "Loading model: " + modelFile);
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    public static List<String> readLabels(Context context, String labelFile) throws IOException {
        List<String> labels = new ArrayList<>();
        try (InputStream is = context.getAssets().open(labelFile);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            String line;
            while ((line = reader.readLine()) != null) {
                labels.add(line.trim());
            }
            Log.d(TAG, "Loaded " + labels.size() + " labels");
        }
        return labels;
    }
}
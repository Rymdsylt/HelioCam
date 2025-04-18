package com.summersoft.heliocam.utils;


import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

import androidx.documentfile.provider.DocumentFile;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class DetectionDirectoryManager {
    private static final String TAG = "DetectionDirectoryManager";
    private static final String PREFS_NAME = "HelioCamPrefs";
    private static final String KEY_BASE_DIRECTORY_URI = "base_directory_uri";

    private final Context context;
    private Uri baseDirectoryUri;

    public DetectionDirectoryManager(Context context) {
        this.context = context;
        loadSavedDirectory();
    }

    private void loadSavedDirectory() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String savedDirString = prefs.getString(KEY_BASE_DIRECTORY_URI, null);

        if (savedDirString != null) {
            baseDirectoryUri = Uri.parse(savedDirString);
            if (!isValidDirectory(baseDirectoryUri)) {
                baseDirectoryUri = null;
            }
        }
    }

    public boolean hasValidDirectory() {
        return baseDirectoryUri != null && isValidDirectory(baseDirectoryUri);
    }

    public void setBaseDirectory(Uri uri) {
        if (uri != null) {
            try {
                // Take persistent permission
                int takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
                context.getContentResolver().takePersistableUriPermission(uri, takeFlags);

                // Save the URI
                baseDirectoryUri = uri;
                SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                prefs.edit().putString(KEY_BASE_DIRECTORY_URI, uri.toString()).apply();

                Toast.makeText(context, "Base directory set successfully", Toast.LENGTH_SHORT).show();

                // Ensure detection directories exist
                createDetectionSubdirectories();
            } catch (SecurityException e) {
                Log.e(TAG, "Failed to take persistent permission", e);
                Toast.makeText(context, "Failed to save directory permission", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void createDetectionSubdirectories() {
        if (hasValidDirectory()) {
            DocumentFile baseDir = DocumentFile.fromTreeUri(context, baseDirectoryUri);
            if (baseDir != null) {
                // Create subdirectories for different detection types
                ensureSubdirectory(baseDir, "Person_Detections");
                ensureSubdirectory(baseDir, "Sound_Detections");
                ensureSubdirectory(baseDir, "Video_Clips");
            }
        }
    }

    private DocumentFile ensureSubdirectory(DocumentFile parent, String dirName) {
        DocumentFile dir = parent.findFile(dirName);
        if (dir == null || !dir.exists()) {
            dir = parent.createDirectory(dirName);
            if (dir != null) {
                Log.d(TAG, "Created directory: " + dirName);
            } else {
                Log.e(TAG, "Failed to create directory: " + dirName);
            }
        }
        return dir;
    }

    public DocumentFile getPersonDetectionDirectory() {
        if (hasValidDirectory()) {
            DocumentFile baseDir = DocumentFile.fromTreeUri(context, baseDirectoryUri);
            if (baseDir != null) {
                DocumentFile dir = baseDir.findFile("Person_Detections");
                if (dir != null && dir.exists()) {
                    return dir;
                }
                return ensureSubdirectory(baseDir, "Person_Detections");
            }
        }
        return null;
    }

    public DocumentFile getSoundDetectionDirectory() {
        if (hasValidDirectory()) {
            DocumentFile baseDir = DocumentFile.fromTreeUri(context, baseDirectoryUri);
            if (baseDir != null) {
                DocumentFile dir = baseDir.findFile("Sound_Detections");
                if (dir != null && dir.exists()) {
                    return dir;
                }
                return ensureSubdirectory(baseDir, "Sound_Detections");
            }
        }
        return null;
    }

    public DocumentFile getVideoClipsDirectory() {
        if (hasValidDirectory()) {
            DocumentFile baseDir = DocumentFile.fromTreeUri(context, baseDirectoryUri);
            if (baseDir != null) {
                DocumentFile dir = baseDir.findFile("Video_Clips");
                if (dir != null && dir.exists()) {
                    return dir;
                }
                return ensureSubdirectory(baseDir, "Video_Clips");
            }
        }
        return null;
    }

    public File getAppStorageDirectory(String subDir) {
        File dir = new File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "HelioCam/" + subDir);
        dir.mkdirs();
        return dir;
    }

    private boolean isValidDirectory(Uri uri) {
        try {
            DocumentFile directory = DocumentFile.fromTreeUri(context, uri);
            return directory != null && directory.exists() && directory.isDirectory();
        } catch (Exception e) {
            Log.e(TAG, "Error validating directory", e);
            return false;
        }
    }

    public String generateTimestampedFilename(String prefix, String extension) {
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        return prefix + "_" + timestamp + extension;
    }
}
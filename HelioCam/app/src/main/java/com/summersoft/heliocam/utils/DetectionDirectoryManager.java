package com.summersoft.heliocam.utils;


import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;
import android.Manifest;

import androidx.core.content.ContextCompat;
import androidx.documentfile.provider.DocumentFile;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class DetectionDirectoryManager {
    private static final String TAG = "DetectionDirectoryManager";
    private static final String PREFS_NAME = "HelioCamPrefs";
    private static final String KEY_BASE_DIRECTORY_URI = "base_directory_uri";
    private static final String KEY_PROMPTED_FOR_DIRECTORY = "prompted_for_directory";

    private final Context context;
    private Uri baseDirectoryUri;
    private boolean promptedForDirectory;    public DetectionDirectoryManager(Context context) {
        this.context = context;
        loadSavedDirectory();
        initializeDefaultDirectory();
        
        // Always ensure we have working directories
        ensureAppDirectoriesExist();
    }

    private void loadSavedDirectory() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String savedDirString = prefs.getString(KEY_BASE_DIRECTORY_URI, null);
        promptedForDirectory = prefs.getBoolean(KEY_PROMPTED_FOR_DIRECTORY, false);

        if (savedDirString != null) {
            baseDirectoryUri = Uri.parse(savedDirString);
            if (!isValidDirectory(baseDirectoryUri)) {
                Log.w(TAG, "Saved directory is no longer valid, clearing");
                baseDirectoryUri = null;
                // Reset prompted flag if saved directory is invalid
                setPromptedForDirectory(false);
            }
        }
    }

    /**
     * Initialize default app-specific directory to ensure we always have a working directory
     */
    private void initializeDefaultDirectory() {
        // Always ensure we have fallback directories available
        ensureAppDirectoriesExist();
        Log.d(TAG, "Default app directories initialized");
    }
    
    /**
     * Ensure all app-specific directories exist
     */
    private void ensureAppDirectoriesExist() {
        File appDir = new File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "HelioCam");
        if (!appDir.exists()) {
            boolean created = appDir.mkdirs();
            Log.d(TAG, "Created app directory: " + created + " at " + appDir.getAbsolutePath());
        }
        
        // Create subdirectories
        createSubdirectoryIfNotExists(appDir, "Person_Detections");
        createSubdirectoryIfNotExists(appDir, "Sound_Detections");
        createSubdirectoryIfNotExists(appDir, "Video_Clips");
    }
    
    private void createSubdirectoryIfNotExists(File parent, String dirName) {
        File subDir = new File(parent, dirName);
        if (!subDir.exists()) {
            boolean created = subDir.mkdirs();
            Log.d(TAG, "Created subdirectory '" + dirName + "': " + created);
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
                context.getContentResolver().takePersistableUriPermission(uri, takeFlags);                // Save the URI
                baseDirectoryUri = uri;
                SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                prefs.edit()
                    .putString(KEY_BASE_DIRECTORY_URI, uri.toString())
                    .putBoolean(KEY_PROMPTED_FOR_DIRECTORY, true)
                    .apply();
                
                this.promptedForDirectory = true;
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
    }    public File getAppStorageDirectory(String subDir) {
        File dir = new File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "HelioCam/" + subDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    /**
     * Get the best available directory for saving files
     * Prioritizes user-selected directory, falls back to app storage
     */
    public File getBestAvailableDirectory(String subDir) {
        // Try user-selected directory first
        if (hasValidDirectory()) {
            DocumentFile userDir = null;
            if ("Person_Detections".equals(subDir)) {
                userDir = getPersonDetectionDirectory();
            } else if ("Sound_Detections".equals(subDir)) {
                userDir = getSoundDetectionDirectory();
            } else if ("Video_Clips".equals(subDir)) {
                userDir = getVideoClipsDirectory();
            }
            
            if (userDir != null && userDir.exists()) {
                // Return a File representation if possible for compatibility
                try {
                    return new File(userDir.getUri().getPath());
                } catch (Exception e) {
                    Log.w(TAG, "Could not convert DocumentFile to File, using app storage");
                }
            }
        }
        
        // Fallback to app storage
        return getAppStorageDirectory(subDir);
    }

    /**
     * Check if all required permissions are granted for external storage
     */
    public boolean hasStoragePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) ==
                    PackageManager.PERMISSION_GRANTED;
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                    PackageManager.PERMISSION_GRANTED;
        } else {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                    PackageManager.PERMISSION_GRANTED &&
                   ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) ==
                    PackageManager.PERMISSION_GRANTED;
        }
    }

    private boolean isValidDirectory(Uri uri) {
        try {
            DocumentFile directory = DocumentFile.fromTreeUri(context, uri);
            return directory != null && directory.exists() && directory.isDirectory();
        } catch (Exception e) {
            Log.e(TAG, "Error validating directory", e);
            return false;
        }
    }    public String generateTimestampedFilename(String prefix, String extension) {
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        return prefix + "_" + timestamp + extension;
    }
    
    /**
     * Check if the app should prompt the user to select a directory
     * This prevents repetitive prompting while allowing a single prompt per app launch
     * @return true if the app should prompt for directory selection, false otherwise
     */
    public boolean shouldPromptForDirectory() {
        return !promptedForDirectory;
    }
    
    /**
     * Set whether the app has prompted for directory selection
     * @param prompted true if prompted, false otherwise
     */
    public void setPromptedForDirectory(boolean prompted) {
        this.promptedForDirectory = prompted;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_PROMPTED_FOR_DIRECTORY, prompted).apply();
    }
    
    /**
     * Reset the prompted flag (useful when user wants to change directory)
     */
    public void resetPromptedFlag() {
        setPromptedForDirectory(false);
    }
    
    /**
     * Get the base directory URI (can be null if not set)
     */
    public Uri getBaseDirectoryUri() {
        return baseDirectoryUri;
    }
}
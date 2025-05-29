package com.summersoft.heliocam.utils;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

/**
 * Centralized permission manager for the HelioCam application.
 * Handles comprehensive permission checking and requesting for camera, microphone, and storage.
 */
public class PermissionManager {
    
    private static final String TAG = "PermissionManager";
    private static final String PREFS_NAME = "permission_settings";
    private static final String KEY_PERMISSIONS_GRANTED = "permissions_granted";
    
    // Permission request codes
    public static final int PERMISSION_REQUEST_CODE_ALL = 100;
    public static final int PERMISSION_REQUEST_CODE_CAMERA = 101;
    public static final int PERMISSION_REQUEST_CODE_MICROPHONE = 102;
    public static final int PERMISSION_REQUEST_CODE_STORAGE = 103;
    
    // Required permissions
    private static final String[] ESSENTIAL_PERMISSIONS = {
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO
    };
    
    private final Context context;
    
    public PermissionManager(Context context) {
        this.context = context;
    }
    
    /**
     * Interface for permission check callbacks
     */
    public interface PermissionCallback {
        void onPermissionsGranted();
        void onPermissionsDenied(List<String> deniedPermissions);
        void onPermissionsExplanationNeeded(List<String> permissions);
    }
    
    /**
     * Check if all essential permissions are granted
     */
    public boolean hasAllEssentialPermissions() {
        for (String permission : ESSENTIAL_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Missing permission: " + permission);
                return false;
            }
        }
        
        // Also check storage permissions based on Android version
        if (!hasStoragePermissions()) {
            Log.d(TAG, "Missing storage permissions");
            return false;
        }
        
        Log.d(TAG, "All essential permissions granted");
        return true;
    }
    
    /**
     * Check if storage permissions are granted based on Android version
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
    
    /**
     * Check if camera permission is granted
     */
    public boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }
    
    /**
     * Check if microphone permission is granted
     */
    public boolean hasMicrophonePermission() {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }
    
    /**
     * Get list of missing permissions
     */
    public List<String> getMissingPermissions() {
        List<String> missingPermissions = new ArrayList<>();
        
        // Check essential permissions
        for (String permission : ESSENTIAL_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                missingPermissions.add(permission);
            }
        }
        
        // Check storage permissions
        if (!hasStoragePermissions()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                missingPermissions.add(Manifest.permission.READ_MEDIA_VIDEO);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                missingPermissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            } else {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    missingPermissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
                }
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    missingPermissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);
                }
            }
        }
        
        return missingPermissions;
    }
    
    /**
     * Request all essential permissions
     */
    public void requestAllEssentialPermissions(Activity activity) {
        List<String> permissionsToRequest = getMissingPermissions();
        
        if (!permissionsToRequest.isEmpty()) {
            Log.d(TAG, "Requesting permissions: " + permissionsToRequest.toString());
            ActivityCompat.requestPermissions(
                activity,
                permissionsToRequest.toArray(new String[0]),
                PERMISSION_REQUEST_CODE_ALL
            );
        } else {
            Log.d(TAG, "All permissions already granted");
        }
    }
    
    /**
     * Request specific permission
     */
    public void requestPermission(Activity activity, String permission, int requestCode) {
        ActivityCompat.requestPermissions(activity, new String[]{permission}, requestCode);
    }
    
    /**
     * Show permission rationale dialog
     */
    public void showPermissionRationale(Activity activity, List<String> permissions, PermissionCallback callback) {
        StringBuilder message = new StringBuilder("HelioCam requires the following permissions to function properly:\n\n");
        
        for (String permission : permissions) {
            switch (permission) {
                case Manifest.permission.CAMERA:
                    message.append("• Camera: To capture video and images during surveillance\n");
                    break;
                case Manifest.permission.RECORD_AUDIO:
                    message.append("• Microphone: To record audio with video and detect sounds\n");
                    break;
                case Manifest.permission.WRITE_EXTERNAL_STORAGE:
                case Manifest.permission.READ_EXTERNAL_STORAGE:
                case Manifest.permission.READ_MEDIA_VIDEO:
                    message.append("• Storage: To save recorded videos and images\n");
                    break;
            }
        }
        
        message.append("\nWould you like to grant these permissions now?");
        
        new AlertDialog.Builder(activity)
            .setTitle("Permissions Required")
            .setMessage(message.toString())
            .setIcon(android.R.drawable.ic_dialog_info)
            .setPositiveButton("Grant Permissions", (dialog, which) -> {
                requestAllEssentialPermissions(activity);
                if (callback != null) {
                    callback.onPermissionsExplanationNeeded(permissions);
                }
            })
            .setNegativeButton("Not Now", (dialog, which) -> {
                if (callback != null) {
                    callback.onPermissionsDenied(permissions);
                }
            })
            .setCancelable(false)
            .show();
    }
    
    /**
     * Handle permission request results
     */
    public void handlePermissionResult(int requestCode, String[] permissions, int[] grantResults, PermissionCallback callback) {
        if (requestCode == PERMISSION_REQUEST_CODE_ALL) {
            List<String> deniedPermissions = new ArrayList<>();
            List<String> grantedPermissions = new ArrayList<>();
            
            for (int i = 0; i < permissions.length; i++) {
                if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                    grantedPermissions.add(permissions[i]);
                } else {
                    deniedPermissions.add(permissions[i]);
                }
            }
            
            Log.d(TAG, "Granted permissions: " + grantedPermissions.toString());
            Log.d(TAG, "Denied permissions: " + deniedPermissions.toString());
            
            if (deniedPermissions.isEmpty()) {
                // All permissions granted
                savePermissionStatus(true);
                if (callback != null) {
                    callback.onPermissionsGranted();
                }
            } else {
                // Some permissions denied
                savePermissionStatus(false);
                if (callback != null) {
                    callback.onPermissionsDenied(deniedPermissions);
                }
            }
        }
    }
    
    /**
     * Show comprehensive permission dialog that explains each permission
     */
    public void showComprehensivePermissionDialog(Activity activity, PermissionCallback callback) {
        List<String> missingPermissions = getMissingPermissions();
        
        if (missingPermissions.isEmpty()) {
            // All permissions already granted
            if (callback != null) {
                callback.onPermissionsGranted();
            }
            return;
        }
        
        StringBuilder message = new StringBuilder();
        message.append("Welcome to HelioCam! For the best experience, we need access to:\n\n");
        
        if (missingPermissions.contains(Manifest.permission.CAMERA)) {
            message.append("📹 Camera\n");
            message.append("   • Record surveillance videos\n");
            message.append("   • Capture images during detection events\n\n");
        }
        
        if (missingPermissions.contains(Manifest.permission.RECORD_AUDIO)) {
            message.append("🎤 Microphone\n");
            message.append("   • Record audio with video\n");
            message.append("   • Enable sound detection features\n\n");
        }
        
        if (missingPermissions.stream().anyMatch(p -> 
            p.equals(Manifest.permission.WRITE_EXTERNAL_STORAGE) ||
            p.equals(Manifest.permission.READ_EXTERNAL_STORAGE) ||
            p.equals(Manifest.permission.READ_MEDIA_VIDEO))) {
            message.append("💾 Storage\n");
            message.append("   • Save recorded videos and images\n");
            message.append("   • Access your custom storage locations\n\n");
        }
        
        message.append("You can change these permissions later in your device settings.");
        
        new AlertDialog.Builder(activity)
            .setTitle("Setup Required Permissions")
            .setMessage(message.toString())
            .setIcon(android.R.drawable.ic_dialog_info)
            .setPositiveButton("Continue", (dialog, which) -> {
                requestAllEssentialPermissions(activity);
            })
            .setNegativeButton("Skip for Now", (dialog, which) -> {
                savePermissionStatus(false);
                if (callback != null) {
                    callback.onPermissionsDenied(missingPermissions);
                }
            })
            .setCancelable(false)
            .show();
    }
    
    /**
     * Check if user should be shown permission rationale
     */
    public boolean shouldShowPermissionRationale(Activity activity, String permission) {
        return ActivityCompat.shouldShowRequestPermissionRationale(activity, permission);
    }
    
    /**
     * Save permission status to preferences
     */
    private void savePermissionStatus(boolean granted) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_PERMISSIONS_GRANTED, granted).apply();
        Log.d(TAG, "Permission status saved: " + granted);
    }
    
    /**
     * Check if permissions have been previously granted
     */
    public boolean hasPermissionsBeenGrantedBefore() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_PERMISSIONS_GRANTED, false);
    }
    
    /**
     * Get user-friendly permission name
     */
    public static String getPermissionDisplayName(String permission) {
        switch (permission) {
            case Manifest.permission.CAMERA:
                return "Camera";
            case Manifest.permission.RECORD_AUDIO:
                return "Microphone";
            case Manifest.permission.WRITE_EXTERNAL_STORAGE:
            case Manifest.permission.READ_EXTERNAL_STORAGE:
            case Manifest.permission.READ_MEDIA_VIDEO:
                return "Storage";
            default:
                return permission.substring(permission.lastIndexOf('.') + 1);
        }
    }
    
    /**
     * Static method to quickly check if all permissions are granted
     */
    public static boolean hasAllPermissions(Context context) {
        return new PermissionManager(context).hasAllEssentialPermissions();
    }
}

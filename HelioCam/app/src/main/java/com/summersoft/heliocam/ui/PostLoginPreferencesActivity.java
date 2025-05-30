package com.summersoft.heliocam.ui;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.summersoft.heliocam.R;
import com.summersoft.heliocam.utils.PermissionManager;

import java.util.List;

public class PostLoginPreferencesActivity extends AppCompatActivity implements PermissionManager.PermissionCallback {

    private static final String TAG = "PostLoginPreferences";
    private static final String PREFS_NAME = "notification_settings";
    private static final String KEY_ALL_NOTIFICATIONS = "all_notifications";
    private static final String KEY_SOUND_NOTIFICATIONS = "sound_notifications";
    private static final String KEY_PERSON_NOTIFICATIONS = "person_notifications";

    private MaterialSwitch allNotificationsSwitch;
    private MaterialSwitch soundNotificationsSwitch;
    private MaterialSwitch personNotificationsSwitch;
    private MaterialButton skipButton;
    private MaterialButton saveButton;
    
    private SharedPreferences sharedPreferences;
    private PermissionManager permissionManager;
    private boolean permissionsChecked = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_post_login_preferences);

        // Initialize SharedPreferences and PermissionManager
        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        permissionManager = new PermissionManager(this);

        // Initialize UI components
        allNotificationsSwitch = findViewById(R.id.notification_switch);
        soundNotificationsSwitch = findViewById(R.id.sound_notification_switch);
        personNotificationsSwitch = findViewById(R.id.person_notification_switch);
        skipButton = findViewById(R.id.skipButton);
        saveButton = findViewById(R.id.saveButton);

        // Set up listeners for the main notification switch
        allNotificationsSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            soundNotificationsSwitch.setEnabled(isChecked);
            personNotificationsSwitch.setEnabled(isChecked);
            
            if (!isChecked) {
                // If main switch is turned off, disable all other switches
                soundNotificationsSwitch.setChecked(false);
                personNotificationsSwitch.setChecked(false);
            }
        });

        // Set up button click listeners
        skipButton.setOnClickListener(v -> {
            // Check permissions before navigating
            checkPermissionsBeforeNavigation();
        });

        saveButton.setOnClickListener(v -> {
            // Save preferences
            savePreferences();
            Toast.makeText(this, "Preferences saved", Toast.LENGTH_SHORT).show();
            // Check permissions before navigating
            checkPermissionsBeforeNavigation();
        });

        // Check permissions immediately after login if not already checked
        if (!permissionsChecked) {
            checkPermissions();
        }
    }

    /**
     * Check if all required permissions are granted
     */
    private void checkPermissions() {
        Log.d(TAG, "Checking permissions after login");
        
        if (!permissionManager.hasAllEssentialPermissions()) {
            Log.d(TAG, "Missing permissions, showing permission dialog");
            // Show comprehensive permission dialog
            permissionManager.showComprehensivePermissionDialog(this, this);
        } else {
            Log.d(TAG, "All permissions already granted");
            permissionsChecked = true;
        }
    }

    /**
     * Check permissions before navigating to next screen
     */
    private void checkPermissionsBeforeNavigation() {
        if (!permissionManager.hasAllEssentialPermissions()) {
            // Show permission rationale before navigating
            List<String> missingPermissions = permissionManager.getMissingPermissions();
            permissionManager.showPermissionRationale(this, missingPermissions, new PermissionManager.PermissionCallback() {
                @Override
                public void onPermissionsGranted() {
                    navigateToHome();
                }

                @Override
                public void onPermissionsDenied(List<String> deniedPermissions) {
                    // Still navigate but show warning
                    Toast.makeText(PostLoginPreferencesActivity.this, 
                        "Some features may not work without permissions", Toast.LENGTH_LONG).show();
                    navigateToHome();
                }

                @Override
                public void onPermissionsExplanationNeeded(List<String> permissions) {
                    // Permissions dialog was shown, wait for user response
                }
            });
        } else {
            // All permissions granted, proceed to navigation
            navigateToHome();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        // Handle permission results using PermissionManager
        permissionManager.handlePermissionResult(requestCode, permissions, grantResults, this);
    }

    // PermissionManager.PermissionCallback implementation
    @Override
    public void onPermissionsGranted() {
        Log.d(TAG, "All permissions granted");
        permissionsChecked = true;
        Toast.makeText(this, "Permissions granted successfully!", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onPermissionsDenied(List<String> deniedPermissions) {
        Log.w(TAG, "Some permissions denied: " + deniedPermissions.toString());
        permissionsChecked = true;
        
        StringBuilder message = new StringBuilder("The following permissions were denied:\n");
        for (String permission : deniedPermissions) {
            message.append("• ").append(PermissionManager.getPermissionDisplayName(permission)).append("\n");
        }
        message.append("\nSome features may not work properly. You can grant these permissions later in Settings.");
        
        Toast.makeText(this, message.toString(), Toast.LENGTH_LONG).show();
    }

    @Override
    public void onPermissionsExplanationNeeded(List<String> permissions) {
        Log.d(TAG, "Permission explanation needed for: " + permissions.toString());
        // The PermissionManager handles showing the explanation dialog
    }

    private void savePreferences() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        
        boolean allEnabled = allNotificationsSwitch.isChecked();
        boolean soundEnabled = soundNotificationsSwitch.isChecked();
        boolean personEnabled = personNotificationsSwitch.isChecked();
        
        editor.putBoolean(KEY_ALL_NOTIFICATIONS, allEnabled);
        editor.putBoolean(KEY_SOUND_NOTIFICATIONS, soundEnabled);
        editor.putBoolean(KEY_PERSON_NOTIFICATIONS, personEnabled);
        
        editor.apply();
    }
    
    private void navigateToHome() {
        // After post-login preferences, we should always go to role selection
        // Clear any existing role preference to ensure fresh role selection
        UserRoleSelectionActivity.clearUserRole(this);
        
        // Navigate to role selection activity with clear task flags
        Intent intent = new Intent(this, UserRoleSelectionActivity.class);
        // Add flag to indicate this is coming from post-login preferences
        intent.putExtra("from_post_login", true);
        // Clear the task stack to prevent going back to preferences
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish(); // Close this activity so user can't go back to it with back button
    }
}
package com.summersoft.heliocam.ui;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.summersoft.heliocam.R;

public class PostLoginPreferencesActivity extends AppCompatActivity {

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_post_login_preferences);

        // Initialize SharedPreferences
        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

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
            // Skip preferences and go to home screen
            navigateToHome();
        });

        saveButton.setOnClickListener(v -> {
            // Save preferences
            savePreferences();
            Toast.makeText(this, "Preferences saved", Toast.LENGTH_SHORT).show();
            navigateToHome();
        });
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
    }    private void navigateToHome() {
        // Check if user already has a saved role
        String savedRole = UserRoleSelectionActivity.getUserRole(this);
        
        if (savedRole != null && !savedRole.isEmpty()) {
            // User has a saved role, navigate directly to appropriate home activity
            Intent intent;
            if (UserRoleSelectionActivity.ROLE_HOST.equals(savedRole)) {
                intent = new Intent(this, HomeActivity.class);
            } else {
                intent = new Intent(this, JoinerHomeActivity.class);
            }
            startActivity(intent);
        } else {
            // No saved role, go to role selection for first time setup
            Intent intent = new Intent(this, UserRoleSelectionActivity.class);
            startActivity(intent);
        }
        finish(); // Close this activity so user can't go back to it with back button
    }
}
package com.summersoft.heliocam.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.materialswitch.MaterialSwitch;
import com.summersoft.heliocam.R;

public class NotificationSettings extends Fragment {
    private static final String TAG = "NotificationSettings";
    private static final String PREFS_NAME = "notification_settings";
    private static final String KEY_ALL_NOTIFICATIONS = "all_notifications";
    private static final String KEY_EMAIL_NOTIFICATIONS = "email_notifications";
    private static final String KEY_SOUND_NOTIFICATIONS = "sound_notifications";
    private static final String KEY_PERSON_NOTIFICATIONS = "person_notifications";
    private static final String KEY_IN_APP_NOTIFICATIONS = "in_app_notifications";

    // UI Components
    private MaterialSwitch allNotificationsSwitch;
    private MaterialSwitch emailNotificationsSwitch;
    private MaterialSwitch soundNotificationsSwitch;
    private MaterialSwitch personNotificationsSwitch;
    private MaterialSwitch inAppNotificationsSwitch;

    // Shared Preferences
    private SharedPreferences sharedPreferences;

    public NotificationSettings() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_notification_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Initialize SharedPreferences
        sharedPreferences = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        // Initialize UI components
        initializeViews(view);

        // Load saved preferences
        loadSavedPreferences();

        // Setup switch listeners
        setupSwitchListeners();

        // Handle window inset padding (system bar)
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), systemBars.top,
                    v.getPaddingRight(), v.getPaddingBottom());
            return insets;
        });
    }

    private void initializeViews(View view) {
        allNotificationsSwitch = view.findViewById(R.id.notification_switch);
        emailNotificationsSwitch = view.findViewById(R.id.email_notification_switch);
        soundNotificationsSwitch = view.findViewById(R.id.sound_detection_switch);
        personNotificationsSwitch = view.findViewById(R.id.person_detection_switch);
        inAppNotificationsSwitch = view.findViewById(R.id.in_app_notification_switch);
    }

    private void loadSavedPreferences() {
        boolean allEnabled = sharedPreferences.getBoolean(KEY_ALL_NOTIFICATIONS, true);
        boolean emailEnabled = sharedPreferences.getBoolean(KEY_EMAIL_NOTIFICATIONS, true);
        boolean soundEnabled = sharedPreferences.getBoolean(KEY_SOUND_NOTIFICATIONS, true);
        boolean personEnabled = sharedPreferences.getBoolean(KEY_PERSON_NOTIFICATIONS, true);
        boolean inAppEnabled = sharedPreferences.getBoolean(KEY_IN_APP_NOTIFICATIONS, true);

        // Set switches to saved values
        allNotificationsSwitch.setChecked(allEnabled);
        emailNotificationsSwitch.setChecked(emailEnabled);
        soundNotificationsSwitch.setChecked(soundEnabled);
        personNotificationsSwitch.setChecked(personEnabled);
        inAppNotificationsSwitch.setChecked(inAppEnabled);

        // Update enabled state based on master switch
        updateSwitchesEnabledState(allEnabled);
    }

    private void setupSwitchListeners() {
        // Master switch for all notifications
        allNotificationsSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            // Update all switches when master is toggled
            updateAllSwitches(isChecked);
            // Save the preference
            savePreference(KEY_ALL_NOTIFICATIONS, isChecked);
            // Update UI enabled state
            updateSwitchesEnabledState(isChecked);
            // Show feedback to user
            showToast(isChecked ? "All notifications enabled" : "All notifications disabled");
        });

        // Email notifications switch
        emailNotificationsSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            savePreference(KEY_EMAIL_NOTIFICATIONS, isChecked);
            showToast(isChecked ? "Email notifications enabled" : "Email notifications disabled");
        });

        // Sound detection notifications switch
        soundNotificationsSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            savePreference(KEY_SOUND_NOTIFICATIONS, isChecked);
            showToast(isChecked ? "Sound detection notifications enabled" :
                    "Sound detection notifications disabled");
        });

        // Person detection notifications switch
        personNotificationsSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            savePreference(KEY_PERSON_NOTIFICATIONS, isChecked);
            showToast(isChecked ? "Person detection notifications enabled" :
                    "Person detection notifications disabled");
        });

        // In-app notifications switch
        inAppNotificationsSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            savePreference(KEY_IN_APP_NOTIFICATIONS, isChecked);
            showToast(isChecked ? "In-app notifications enabled" : "In-app notifications disabled");
        });
    }

    private void updateAllSwitches(boolean isChecked) {
        // Only update if different to avoid triggering listeners
        if (emailNotificationsSwitch.isChecked() != isChecked) {
            emailNotificationsSwitch.setChecked(isChecked);
        }
        if (soundNotificationsSwitch.isChecked() != isChecked) {
            soundNotificationsSwitch.setChecked(isChecked);
        }
        if (personNotificationsSwitch.isChecked() != isChecked) {
            personNotificationsSwitch.setChecked(isChecked);
        }
        if (inAppNotificationsSwitch.isChecked() != isChecked) {
            inAppNotificationsSwitch.setChecked(isChecked);
        }

        // Save all preferences at once
        saveAllPreferences(isChecked);
    }

    private void updateSwitchesEnabledState(boolean enabled) {
        emailNotificationsSwitch.setEnabled(enabled);
        soundNotificationsSwitch.setEnabled(enabled);
        personNotificationsSwitch.setEnabled(enabled);
        inAppNotificationsSwitch.setEnabled(enabled);
    }

    private void savePreference(String key, boolean value) {
        sharedPreferences.edit().putBoolean(key, value).apply();
        Log.d(TAG, "Saved preference: " + key + " = " + value);
    }

    private void saveAllPreferences(boolean value) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(KEY_EMAIL_NOTIFICATIONS, value);
        editor.putBoolean(KEY_SOUND_NOTIFICATIONS, value);
        editor.putBoolean(KEY_PERSON_NOTIFICATIONS, value);
        editor.putBoolean(KEY_IN_APP_NOTIFICATIONS, value);
        editor.apply();

        Log.d(TAG, "Saved all notification preferences: " + value);
    }

    private void showToast(String message) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
    }

    /**
     * Public static methods to check notification settings from other classes
     */

    public static boolean isNotificationTypeEnabled(Context context, String notificationType) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        // First check if all notifications are disabled
        boolean allEnabled = prefs.getBoolean(KEY_ALL_NOTIFICATIONS, true);
        if (!allEnabled) {
            return false;
        }

        // Otherwise, check the specific notification type
        switch (notificationType) {
            case "email":
                return prefs.getBoolean(KEY_EMAIL_NOTIFICATIONS, true);
            case "sound":
                return prefs.getBoolean(KEY_SOUND_NOTIFICATIONS, true);
            case "person":
                return prefs.getBoolean(KEY_PERSON_NOTIFICATIONS, true);
            case "in_app":
                return prefs.getBoolean(KEY_IN_APP_NOTIFICATIONS, true);
            default:
                return true;
        }
    }

    /**
     * Helper method to check if a specific notification type is enabled
     */
    public static boolean isEmailNotificationsEnabled(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean allEnabled = prefs.getBoolean(KEY_ALL_NOTIFICATIONS, true);
        boolean emailEnabled = prefs.getBoolean(KEY_EMAIL_NOTIFICATIONS, true);
        return allEnabled && emailEnabled;
    }

    public static boolean isSoundNotificationsEnabled(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean allEnabled = prefs.getBoolean(KEY_ALL_NOTIFICATIONS, true);
        boolean soundEnabled = prefs.getBoolean(KEY_SOUND_NOTIFICATIONS, true);
        return allEnabled && soundEnabled;
    }

    public static boolean isPersonNotificationsEnabled(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean allEnabled = prefs.getBoolean(KEY_ALL_NOTIFICATIONS, true);
        boolean personEnabled = prefs.getBoolean(KEY_PERSON_NOTIFICATIONS, true);
        return allEnabled && personEnabled;
    }

    public static boolean isInAppNotificationsEnabled(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean allEnabled = prefs.getBoolean(KEY_ALL_NOTIFICATIONS, true);
        boolean inAppEnabled = prefs.getBoolean(KEY_IN_APP_NOTIFICATIONS, true);
        return allEnabled && inAppEnabled;
    }
}
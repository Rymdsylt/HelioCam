package com.summersoft.heliocam.ui;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.summersoft.heliocam.R;
import com.summersoft.heliocam.notifs.SoundNotifListener;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.summersoft.heliocam.notifs.SoundNotifListener;
import com.summersoft.heliocam.notifs.SoundNotifService;

public class HomeActivity extends AppCompatActivity {

    private int currentFragmentIndex = 0; // Track the current fragment index
    private final int[] fragmentOrder = { // Define the order of fragments
            R.id.bottom_home,
            R.id.bottom_notifications,
            R.id.bottom_history,
            R.id.bottom_settings,
            R.id.bottom_profile
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        // Check for notification permission
        checkNotificationPermission();

        // Set up Bottom Navigation
        BottomNavigationView bottomNavigationView = findViewById(R.id.bottomNavigation);
        bottomNavigationView.setSelectedItemId(R.id.bottom_home);

        bottomNavigationView.setOnItemSelectedListener(item -> {
            int selectedFragmentIndex = getFragmentIndex(item.getItemId());
            if (selectedFragmentIndex == -1) return false;

            // Check if the selected fragment is already displayed
            if (selectedFragmentIndex == currentFragmentIndex) {
                return false; // Do nothing if the same fragment is selected
            }

            Fragment selectedFragment = getFragmentById(item.getItemId());
            if (selectedFragment != null) {
                int enterAnim, exitAnim;

                // Determine animation direction based on fragment index
                if (selectedFragmentIndex > currentFragmentIndex) {
                    // Slide right (next fragment)
                    enterAnim = R.anim.slide_in_right;
                    exitAnim = R.anim.slide_out_left;
                } else {
                    // Slide left (previous fragment)
                    enterAnim = R.anim.slide_in_left;
                    exitAnim = R.anim.slide_out_right;
                }

                getSupportFragmentManager().beginTransaction()
                        .setCustomAnimations(
                                enterAnim, // Enter animation
                                exitAnim  // Exit animation
                        )
                        .replace(R.id.fragment_container, selectedFragment)
                        .commit();

                currentFragmentIndex = selectedFragmentIndex; // Update current index
            }
            return true;
        });

        // Set the default fragment
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, new HomeFragment())
                    .commit();
        }





        Intent serviceIntent = new Intent(this, SoundNotifService.class);
        this.startService(serviceIntent);


    }

    // Helper method to get the index of a fragment based on its menu ID
    private int getFragmentIndex(int itemId) {
        for (int i = 0; i < fragmentOrder.length; i++) {
            if (fragmentOrder[i] == itemId) {
                return i;
            }
        }
        return -1;
    }

    // Helper method to return the fragment instance for a given menu ID
    private Fragment getFragmentById(int itemId) {
        switch (itemId) {
            case R.id.bottom_home:
                return new HomeFragment();
            case R.id.bottom_notifications:
                return new NotificationFragment();
            case R.id.bottom_history:
                return new HistoryFragment();
            case R.id.bottom_settings:
                return new SettingsFragment();
            case R.id.bottom_profile:
                return new ProfileFragment();
            default:
                return null;
        }
    }

    /**
     * Check and request notification permission if necessary
     */
    private void checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Check if notification permission is granted
            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (notificationManager != null && !notificationManager.areNotificationsEnabled()) {
                // Request permission if notifications are not enabled
                requestNotificationPermission();
            }
        }
    }

    /**
     * Request notification permission for Android 13 and above
     */
    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{android.Manifest.permission.POST_NOTIFICATIONS},
                        1); // 1 is the request code
            }
        }
    }
}

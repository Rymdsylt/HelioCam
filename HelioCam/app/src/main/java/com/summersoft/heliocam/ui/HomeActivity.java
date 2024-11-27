package com.summersoft.heliocam.ui;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.summersoft.heliocam.R;

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
}

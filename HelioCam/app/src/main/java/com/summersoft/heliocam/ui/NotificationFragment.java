package com.summersoft.heliocam.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.ImageButton;

import androidx.fragment.app.Fragment;

import com.summersoft.heliocam.R;
import com.summersoft.heliocam.notifs.PopulateNotifs;

import java.util.Set;

public class NotificationFragment extends Fragment {

    private static final String TAG = "NotificationFragment";

    public NotificationFragment() {
        // Required empty public constructor
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        Log.d(TAG, "NotificationFragment: onCreateView started");
        View view = inflater.inflate(R.layout.fragment_notification, container, false);

        ViewGroup notificationContainer = view.findViewById(R.id.notifcation_card_container);
        
        // Set up refresh button
        com.google.android.material.button.MaterialButton refreshButton = view.findViewById(R.id.refreshButton);
        if (refreshButton != null) {
            refreshButton.setOnClickListener(v -> refreshNotifications());
        }
        
        // Add Clear All button functionality
        com.google.android.material.button.MaterialButton clearAllButton = view.findViewById(R.id.clear_all_button);
        if (clearAllButton != null) {
            // Set it visible by default
            clearAllButton.setVisibility(View.VISIBLE);
            
            clearAllButton.setOnClickListener(v -> {
                if (getContext() != null && notificationContainer != null) {
                    // Clear all notifications from preferences
                    SharedPreferences prefs = getContext().getSharedPreferences(
                            "NotificationPrefs", Context.MODE_PRIVATE);
                    
                    // Get existing notifications and mark all as deleted
                    PopulateNotifs populator = PopulateNotifs.getInstance();
                    Set<String> notificationIds = populator.getNotificationIds();
                    
                    if (notificationIds != null && !notificationIds.isEmpty()) {
                        prefs.edit()
                             .putStringSet("deleted_notifs", notificationIds)
                             .apply();
                             
                        // Refresh the UI
                        refreshNotifications();
                        
                        // Hide the button after clearing
                        clearAllButton.setVisibility(View.GONE);
                    }
                }
            });
        } else {
            Log.e(TAG, "Clear all button not found in layout");
        }

        // Only attempt to populate notifications if container exists
        if (notificationContainer != null && getContext() != null) {
            Log.d(TAG, "Starting notification population");
            PopulateNotifs populator = PopulateNotifs.getInstance();
            populator.startPopulatingNotifs(getContext(), notificationContainer);
        } else {
            Log.e(TAG, "Cannot populate notifications: container or context is null");
        }

        return view;
    }
    // In NotificationFragment.java - fixed refreshNotifications method:
    public static void refreshNotifications() {
        if (activeInstance == null) {
            Log.d(TAG, "refreshNotifications: No active instance available");
            return;
        }

        // First check if the fragment is added to an activity
        if (!activeInstance.isAdded()) {
            Log.d(TAG, "refreshNotifications: Fragment not attached to activity");
            return;
        }

        // Now check for context and view
        if (activeInstance.getContext() == null) {
            Log.d(TAG, "refreshNotifications: Context is null");
            return;
        }

        View view = activeInstance.getView();
        if (view == null) {
            Log.d(TAG, "refreshNotifications: View is null");
            return;
        }

        ViewGroup container = view.findViewById(R.id.notifcation_card_container);
        if (container == null) {
            Log.d(TAG, "refreshNotifications: Container not found");
            return;
        }

        // Force a complete refresh from Firebase
        container.removeAllViews();

        // Create a loading indicator to show while fetching
        TextView loadingText = new TextView(activeInstance.getContext());
        loadingText.setText("Loading notifications...");
        loadingText.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        loadingText.setPadding(0, 50, 0, 50);
        container.addView(loadingText);

        // Using final reference to avoid lambda capturing potentially changing activeInstance
        final Fragment currentFragment = activeInstance;

        // Delay fetch slightly to ensure the database write completes
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            // Double-check that fragment is still valid when the delayed code runs
            if (currentFragment.isAdded() && currentFragment.getContext() != null) {
                PopulateNotifs.getInstance().startPopulatingNotifs(
                        currentFragment.getContext(), container);
            } else {
                Log.d(TAG, "Delayed refresh aborted: fragment no longer valid");
            }
        }, 500);
    }

    // Keep track of the active instance
    public static NotificationFragment activeInstance;


    @Override
    public void onResume() {
        super.onResume();
        activeInstance = this;
    }

    @Override
    public void onPause() {
        super.onPause();
        if (activeInstance == this) {
            activeInstance = null;
        }
    }

    // Add this inside your displayNotifications method where you handle card creation
    public void setUpViewDetailsButton(View innerLayout, View detailsButton) {
        // Find metadata container
        View metadataContainer = innerLayout.findViewById(R.id.metadata_container);
        
        // Set initial text and icon based on visibility state
        boolean isVisible = metadataContainer.getVisibility() == View.VISIBLE;
        
        // Check which type of button we're dealing with and update accordingly
        if (detailsButton instanceof com.google.android.material.button.MaterialButton) {
            updateDetailsButton((com.google.android.material.button.MaterialButton) detailsButton, isVisible);
        }
        
        detailsButton.setOnClickListener(v -> {
            // Toggle metadata container visibility with animation
            boolean currentlyVisible = metadataContainer.getVisibility() == View.VISIBLE;
            
            if (currentlyVisible) {
                // Hide with animation
                metadataContainer.animate()
                    .alpha(0f)
                    .setDuration(200)
                    .withEndAction(() -> {
                        metadataContainer.setVisibility(View.GONE);
                        if (detailsButton instanceof com.google.android.material.button.MaterialButton) {
                            updateDetailsButton((com.google.android.material.button.MaterialButton) detailsButton, false);
                        }
                    })
                    .start();
            } else {
                // Show with animation
                metadataContainer.setAlpha(0f);
                metadataContainer.setVisibility(View.VISIBLE);
                metadataContainer.animate()
                    .alpha(1f)
                    .setDuration(200)
                    .start();
                if (detailsButton instanceof com.google.android.material.button.MaterialButton) {
                    updateDetailsButton((com.google.android.material.button.MaterialButton) detailsButton, true);
                }
            }
        });
    }

    private void updateDetailsButton(com.google.android.material.button.MaterialButton button, boolean expanded) {
        if (expanded) {
            button.setText("Hide Details");
            button.setIconResource(R.drawable.ic_visibility_off);
        } else {
            button.setText("Details");
            button.setIconResource(R.drawable.ic_visibility);
        }
    }
}

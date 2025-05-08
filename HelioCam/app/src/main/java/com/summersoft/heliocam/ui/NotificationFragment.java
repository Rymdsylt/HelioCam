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
        
        // Add null check before using notificationContainer
        if (notificationContainer == null) {
            Log.e(TAG, "Could not find notification container view!");
            // Create the container if it doesn't exist
            ScrollView scrollView = view.findViewById(R.id.mainContent);
            if (scrollView != null) {
                // Find the proper parent for our container by traversing the view hierarchy
                ViewGroup parentLayout = (ViewGroup) scrollView.getChildAt(0);
                if (parentLayout != null) {
                    // Find the MaterialCardView
                    for (int i = 0; i < parentLayout.getChildCount(); i++) {
                        View child = parentLayout.getChildAt(i);
                        if (child instanceof androidx.cardview.widget.CardView || 
                            child instanceof com.google.android.material.card.MaterialCardView) {
                            // Found card view, now get its content layout
                            ViewGroup cardContent = (ViewGroup) ((ViewGroup) child).getChildAt(0);
                            if (cardContent != null) {
                                // Create a new container
                                LinearLayout newContainer = new LinearLayout(requireContext());
                                newContainer.setId(R.id.notifcation_card_container);
                                newContainer.setOrientation(LinearLayout.VERTICAL);
                                cardContent.addView(newContainer);
                                
                                // Update our reference
                                notificationContainer = newContainer;
                                break;
                            }
                        }
                    }
                }
            }
        }
        
        // Create a final reference to the container for use in lambda
        final ViewGroup finalNotificationContainer = notificationContainer;
        
        // Add Clear All button functionality with proper null check
        Button clearAllButton = view.findViewById(R.id.clear_all_button);
        if (clearAllButton != null) {
            // Set it visible by default
            clearAllButton.setVisibility(View.VISIBLE);
            
            clearAllButton.setOnClickListener(v -> {
                if (getContext() != null && finalNotificationContainer != null) {
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
        if (finalNotificationContainer != null && getContext() != null) {
            Log.d(TAG, "Starting notification population");
            PopulateNotifs populator = PopulateNotifs.getInstance();
            populator.debugFirebaseData(getContext());
            populator.startPopulatingNotifs(getContext(), finalNotificationContainer);
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

        // Force refresh with a small delay to ensure Firebase data is available
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            refreshNotifications();
        }, 300);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (activeInstance == this) {
            activeInstance = null;
        }
    }

    // Add this inside your displayNotifications method where you handle card creation
    private void setUpViewDetailsButton(View innerLayout, ImageButton viewDetailsButton) {
        viewDetailsButton.setOnClickListener(v -> {
            // Toggle metadata container visibility
            View metadataContainer = innerLayout.findViewById(R.id.metadata_container);
            if (metadataContainer != null) {
                boolean isVisible = metadataContainer.getVisibility() == View.VISIBLE;
                metadataContainer.setVisibility(isVisible ? View.GONE : View.VISIBLE);
                viewDetailsButton.setImageResource(isVisible ?
                        R.drawable.ic_visibility : R.drawable.ic_visibility_off);
            }
        });
    }
}

package com.summersoft.heliocam.ui;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import com.summersoft.heliocam.R;
import com.summersoft.heliocam.notifs.PopulateNotifs;

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

        if (notificationContainer == null) {
            Log.e(TAG, "Could not find notification container view!");
        } else {
            Log.d(TAG, "Found notification container view");
        }


        if (getContext() != null) {
            Log.d(TAG, "Starting notification population");
            PopulateNotifs populator = PopulateNotifs.getInstance();

            // Add this debug call
            populator.debugFirebaseData(getContext());

            populator.startPopulatingNotifs(getContext(), notificationContainer);
        }

        return view;
    }
    // In NotificationFragment.java - completely replace the refreshNotifications method:
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
}

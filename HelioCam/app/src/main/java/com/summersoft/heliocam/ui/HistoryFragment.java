package com.summersoft.heliocam.ui;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.fragment.app.Fragment;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.summersoft.heliocam.R;
import com.summersoft.heliocam.status.LoginStatus;
import com.summersoft.heliocam.status.SessionLoader;

public class HistoryFragment extends Fragment {

    private static final String TAG = "HistoryFragment";
    private FirebaseAuth mAuth;
    private Handler handler = new Handler();
    private Runnable loadSessionsRunnable;
    private LinearLayout sessionCardContainer;
    private View noSessionsPlaceholder;
    private MaterialButton refreshButton;
    private MaterialButton clearAllButton; // New button
    private MaterialButton createFirstSessionButton;
    private SessionLoader sessionLoader;

    public HistoryFragment() {
        // Required empty public constructor
    }

    @SuppressLint("WrongViewCast")
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                            Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View rootView = inflater.inflate(R.layout.fragment_history, container, false);
        
        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        
        // Initialize views
        sessionCardContainer = rootView.findViewById(R.id.sessions_container); // Updated ID
        noSessionsPlaceholder = rootView.findViewById(R.id.empty_sessions_container); // Updated ID
        refreshButton = rootView.findViewById(R.id.filterButton); // Using filter button instead
        clearAllButton = rootView.findViewById(R.id.clear_all_button); // New button
        createFirstSessionButton = rootView.findViewById(R.id.create_session_button); // Updated ID
        
        // Set initial visibility - make sure placeholder is visible by default
        if (noSessionsPlaceholder != null) {
            noSessionsPlaceholder.setVisibility(View.VISIBLE);
        }
        
        // Set up click listeners
        if (refreshButton != null) {
            refreshButton.setOnClickListener(v -> refreshSessions());
        }
        
        if (clearAllButton != null) {
            clearAllButton.setOnClickListener(v -> confirmClearAllSessions());
        }
        
        if (createFirstSessionButton != null) {
            createFirstSessionButton.setOnClickListener(v -> {
                Intent intent = new Intent(getActivity(), HostSession.class);
                startActivity(intent);
            });
        }
        
        // Initialize LoginStatus
        LoginStatus.checkLoginStatus(requireContext());
        
        // Create custom session loader only if container is found
        if (sessionCardContainer != null) {
            sessionLoader = new CustomSessionLoader(
                    (HomeActivity) getActivity(),
                    sessionCardContainer);
            sessionLoader.setSessionChangeListener(() -> updateVisibility());
        } else {
            Log.e("HistoryFragment", "Session container view not found");
        }
        
        // Runnable for loading user sessions periodically
        loadSessionsRunnable = new Runnable() {
            @Override
            public void run() {
                if (getActivity() != null && isAdded()) {
                    // Only proceed if fragment is added and activity is available
                    sessionLoader.loadUserSessions();
                    updateVisibility();
                }
                handler.postDelayed(this, 2000); // Repeat every 2 seconds
            }
        };
        
        return rootView;
    }
    
    /**
     * Update visibility of UI elements based on session count
     */
    private void updateVisibility() {
        if (sessionCardContainer != null && noSessionsPlaceholder != null && clearAllButton != null) {
            boolean hasSessionItems = sessionCardContainer.getChildCount() > 0;
            Log.d("HistoryFragment", "Session count: " + sessionCardContainer.getChildCount());
            
            // Show placeholder if there are no sessions
            noSessionsPlaceholder.setVisibility(hasSessionItems ? View.GONE : View.VISIBLE);
            
            // Show/hide clear all button based on session count
            clearAllButton.setVisibility(hasSessionItems ? View.VISIBLE : View.GONE);
            
            // Log the visibility state for debugging
            Log.d("HistoryFragment", "Placeholder visibility: " + 
                  (noSessionsPlaceholder.getVisibility() == View.VISIBLE ? "VISIBLE" : "GONE"));
        } else {
            Log.e("HistoryFragment", "Container, placeholder, or clear button is null");
        }
    }
    
    /**
     * Confirm before clearing all sessions
     */
    private void confirmClearAllSessions() {
        if (getActivity() != null) {
            new AlertDialog.Builder(getActivity())
                .setTitle("Clear All Sessions")
                .setMessage("Are you sure you want to delete all sessions? This action cannot be undone.")
                .setPositiveButton("Clear All", (dialog, which) -> {
                    clearAllSessions();
                })
                .setNegativeButton("Cancel", null)
                .show();
        }
    }
    
    /**
     * Clear all sessions from the database
     */
    private void clearAllSessions() {
        if (mAuth.getCurrentUser() != null) {
            String userId = mAuth.getCurrentUser().getUid();
            DatabaseReference userSessionsRef = FirebaseDatabase.getInstance()
                .getReference("users")
                .child(userId)
                .child("sessions");
                
            // Remove all sessions
            userSessionsRef.removeValue()
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(getActivity(), "All sessions cleared", Toast.LENGTH_SHORT).show();
                    // Clear the UI
                    sessionCardContainer.removeAllViews();
                    updateVisibility();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(getActivity(), "Failed to clear sessions: " + e.getMessage(), 
                        Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "Failed to clear sessions", e);
                });
        }
    }
    
    /**
     * Manually refresh sessions
     */
    private void refreshSessions() {
        if (getActivity() != null) {
            Toast.makeText(getActivity(), "Refreshing sessions...", Toast.LENGTH_SHORT).show();
            sessionLoader.loadUserSessions();
            updateVisibility();
        }
    }
    
    @Override
    public void onStart() {
        super.onStart();
        // Start session loading when fragment becomes visible
        handler.post(loadSessionsRunnable);
    }
    
    @Override
    public void onStop() {
        super.onStop();
        // Stop loading sessions when fragment is not visible
        handler.removeCallbacks(loadSessionsRunnable);
    }
    
    /**
     * Custom extension of SessionLoader to handle visibility updates
     */
    private class CustomSessionLoader extends SessionLoader {
        public CustomSessionLoader(HomeActivity homeActivity, LinearLayout container) {
            super(homeActivity, container);
        }
        
        @Override
        public void loadUserSessions() {
            super.loadUserSessions();
            
            // After sessions are loaded, check if we need to update visibility
            handler.postDelayed(() -> {
                if (getActivity() != null && isAdded()) {
                    updateVisibility();
                }
            }, 300);
        }
    }
}

package com.summersoft.heliocam.ui;

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

import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
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
    private MaterialButton newSessionButton;
    private MaterialButton createFirstSessionButton;
    private SessionLoader sessionLoader;

    public HistoryFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                            Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View rootView = inflater.inflate(R.layout.fragment_history, container, false);
        
        mAuth = FirebaseAuth.getInstance();
        
        // Initialize UI components
        sessionCardContainer = rootView.findViewById(R.id.sessionCardContainer);
        noSessionsPlaceholder = rootView.findViewById(R.id.noSessionsPlaceholder);
        refreshButton = rootView.findViewById(R.id.refreshButton);
        newSessionButton = rootView.findViewById(R.id.newSessionButton);
        createFirstSessionButton = rootView.findViewById(R.id.createFirstSessionButton);
        
        // Set up click listeners
        refreshButton.setOnClickListener(v -> refreshSessions());
        
        newSessionButton.setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), HostSession.class);
            startActivity(intent);
        });
        
        createFirstSessionButton.setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), HostSession.class);
            startActivity(intent);
        });
        
        // Initialize LoginStatus
        LoginStatus.checkLoginStatus(requireContext());
        
        // Create custom session loader only if container is found
        if (sessionCardContainer != null) {
            sessionLoader = new CustomSessionLoader(
                    (HomeActivity) getActivity(),
                    sessionCardContainer);
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
        if (sessionCardContainer != null && noSessionsPlaceholder != null) {
            boolean hasSessionItems = sessionCardContainer.getChildCount() > 0;
            noSessionsPlaceholder.setVisibility(hasSessionItems ? View.GONE : View.VISIBLE);
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

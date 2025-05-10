package com.summersoft.heliocam.ui;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Button;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.card.MaterialCardView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.summersoft.heliocam.R;
import com.summersoft.heliocam.status.LoginStatus;
import com.summersoft.heliocam.status.LogoutUser;
import com.summersoft.heliocam.status.SessionLoader;

public class HomeFragment extends Fragment {

    private static final String TAG = "HomeFragment";
    private FirebaseAuth mAuth;
    private Handler handler = new Handler();
    private Runnable checkLoginStatusRunnable;
    private Runnable loadSessionsRunnable;
    private LinearLayout sessionCardContainer;
    
    // UI Components for the tips feature
    private TextView tipTextView;
    private Button nextTipButton;
    private int currentTipIndex = 0;
    private String[] helioCamTips;

    public HomeFragment() {
        // Required empty public constructor
    }

    @SuppressLint("MissingInflatedId")
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View rootView = inflater.inflate(R.layout.fragment_home, container, false);

        mAuth = FirebaseAuth.getInstance();


        
        // Initialize tips array and UI components
        initializeTips();
        setupTipsCarousel(rootView);
        
        // Setup quick action buttons
        setupQuickActions(rootView);
        
        // Setup getting started section
        setupGettingStarted(rootView);

        // Set padding for window insets
        ViewCompat.setOnApplyWindowInsetsListener(rootView.findViewById(R.id.mainpage), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Register appLogo as the menu button
        View menuButton = rootView.findViewById(R.id.appLogo);
        registerForContextMenu(menuButton);

        menuButton.setOnClickListener(v -> {
            Log.d(TAG, "App logo clicked for menu!");
            v.showContextMenu();
        });

        // OnClickListener for "addSession" button
        View addSessionButton = rootView.findViewById(R.id.addSession);
        addSessionButton.setOnClickListener(v -> {
            showSessionOptionsDialog();
        });

        /* // Also add click listener for FAB
        View fabCreateSession = rootView.findViewById(R.id.fragment_fab_create_session);
        fabCreateSession.setOnClickListener(v -> {
            showSessionOptionsDialog();
        }); */
        
        // Update welcome text if user is logged in
        updateWelcomeText(rootView);

        // Create a custom SessionLoader that updates the placeholder button
        SessionLoader sessionLoader = new SessionLoader(
                (HomeActivity) getActivity(),
                sessionCardContainer) {
            @Override
            public void loadUserSessions() {
                super.loadUserSessions();

                // Add listener for the add_new_session_button in the placeholder
                // This needs to happen after sessions are loaded
                handler.postDelayed(() -> {
                    // We need to check if the placeholder exists in the container
                    if (sessionCardContainer != null) {
                        for (int i = 0; i < sessionCardContainer.getChildCount(); i++) {
                            View child = sessionCardContainer.getChildAt(i);
                            View addButton = child.findViewById(R.id.add_new_session_button);
                            if (addButton != null) {
                                addButton.setOnClickListener(v -> showSessionOptionsDialog());
                                break;
                            }
                        }
                    }
                }, 200); // Small delay to ensure views are inflated
            }
        };

        // Runnable for checking login status
        checkLoginStatusRunnable = new Runnable() {
            @Override
            public void run() {
                if (getActivity() != null && isAdded()) {
                    LoginStatus.checkLoginStatus(getActivity());
                    updateWelcomeText(rootView);
                }
                handler.postDelayed(this, 1500); // Repeat every 1.5 seconds
            }
        };

        // Runnable for loading user sessions every 1.5 seconds
        loadSessionsRunnable = new Runnable() {
            @Override
            public void run() {
                if (getActivity() != null && isAdded()) {
                    // Only proceed if fragment is added and activity is available
                    sessionLoader.loadUserSessions();  // Load sessions
                }
                handler.postDelayed(this, 1500); // Repeat every 1.5 seconds
            }
        };

        return rootView;
    }
    
    /**
     * Initialize tips for the carousel
     */
    private void initializeTips() {
        helioCamTips = new String[] {
            "Enable motion detection to receive alerts when movement is detected in your camera view.",
            "Position your camera in a well-lit area for better image quality and clearer recordings.",
            "Use a strong, unique passkey for each session to enhance security and control access.",
            "Check your internet connection speed before starting a session for optimal streaming quality.",
            "Angle your camera away from windows to avoid glare and backlight issues in your recordings."
        };
    }
    
    /**
     * Setup the tips carousel with next tip button functionality
     */
    private void setupTipsCarousel(View rootView) {
        tipTextView = rootView.findViewById(R.id.tip_text);
        nextTipButton = rootView.findViewById(R.id.next_tip_button);
        
        if (tipTextView != null && nextTipButton != null) {
            // Set initial tip
            tipTextView.setText(helioCamTips[0]);
            
            // Setup next tip button
            nextTipButton.setOnClickListener(v -> {
                showNextTip();
            });
        }
    }
    
    /**
     * Show the next tip in the carousel with animation
     */
    private void showNextTip() {
        // Animation for tip transition
        tipTextView.animate()
            .alpha(0f)
            .setDuration(200)
            .withEndAction(() -> {
                // Update to next tip
                currentTipIndex = (currentTipIndex + 1) % helioCamTips.length;
                
                // Set the new tip text
                tipTextView.setText(helioCamTips[currentTipIndex]);
                
                // Fade back in
                tipTextView.animate()
                    .alpha(1f)
                    .setDuration(200)
                    .start();
            }).start();
    }
    
    /**
     * Setup quick action buttons
     */
    private void setupQuickActions(View rootView) {
        // Setup Start New Session button
        MaterialCardView createSessionCard = rootView.findViewById(R.id.btn_quick_create_session);
        if (createSessionCard != null) {
            createSessionCard.setOnClickListener(v -> {
                Intent intent = new Intent(getActivity(), HostSession.class);
                startActivity(intent);
            });
        }
        
        // Setup View Sessions button
        MaterialCardView viewSessionsCard = rootView.findViewById(R.id.btn_quick_view_sessions);
        if (viewSessionsCard != null) {
            viewSessionsCard.setOnClickListener(v -> {
                // Navigate to History fragment
                if (getActivity() instanceof HomeActivity) {
                    HomeActivity activity = (HomeActivity) getActivity();
                    activity.getBottomNavigationView().setSelectedItemId(R.id.bottom_history);
                }
            });
        }
    }
    
    /**
     * Setup getting started section
     */
    private void setupGettingStarted(View rootView) {
        Button startMonitoringButton = rootView.findViewById(R.id.start_monitoring_button);
        if (startMonitoringButton != null) {
            startMonitoringButton.setOnClickListener(v -> {
                Intent intent = new Intent(getActivity(), HostSession.class);
                startActivity(intent);
            });
        }
    }
    
    /**
     * Update welcome text with user name if logged in
     */
    private void updateWelcomeText(View rootView) {
        TextView welcomeHeaderText = rootView.findViewById(R.id.welcome_text);
        TextView welcomeSubtitleText = rootView.findViewById(R.id.welcome_subtitle);
        
        if (welcomeHeaderText != null) {
            FirebaseUser currentUser = mAuth.getCurrentUser();
            if (currentUser != null && currentUser.getDisplayName() != null && !currentUser.getDisplayName().isEmpty()) {
                welcomeHeaderText.setText("Welcome, " + currentUser.getDisplayName());
            } else {
                welcomeHeaderText.setText("Welcome to HelioCam");
            }
        }
    }

    /**
     * Shows a dialog with options to create or join a session
     */
    private void showSessionOptionsDialog() {
        if (getActivity() == null) return;

        // Inflate the dialog layout
        View dialogView = LayoutInflater.from(getActivity()).inflate(R.layout.dialog_session_options, null);

        // Create dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setView(dialogView);

        AlertDialog dialog = builder.create();
        dialog.show();

        // Set up button clicks
        dialogView.findViewById(R.id.btn_create_session).setOnClickListener(v -> {
            // Start AddSessionActivity for creating a new session
            Intent intent = new Intent(getActivity(), HostSession.class);
            startActivity(intent);
            dialog.dismiss();
        });

        dialogView.findViewById(R.id.btn_join_session).setOnClickListener(v -> {
            // Start WatchSessionActivity for joining an existing session
            Intent intent = new Intent(getActivity(), AddSessionActivity.class);
            startActivity(intent);
            dialog.dismiss();
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        // Start login check after 1.5 seconds
        handler.postDelayed(checkLoginStatusRunnable, 1500);
        // Start loading sessions after 1.5 seconds
        handler.postDelayed(loadSessionsRunnable, 1500);
    }

    @Override
    public void onStop() {
        super.onStop();
        handler.removeCallbacks(checkLoginStatusRunnable); // Stop login check
        handler.removeCallbacks(loadSessionsRunnable); // Stop session loading
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);

        if (v.getId() == R.id.appLogo) {
            MenuInflater inflater = getActivity().getMenuInflater();
            inflater.inflate(R.menu.context_menu, menu);
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_logout:
                LogoutUser.logoutUser();  // Logout user
                LoginStatus.checkLoginStatus(getActivity()); // Check login status
                return true;
            default:
                return super.onContextItemSelected(item);
        }
    }
}
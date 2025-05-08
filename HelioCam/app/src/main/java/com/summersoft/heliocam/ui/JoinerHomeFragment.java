package com.summersoft.heliocam.ui;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.fragment.app.Fragment;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.summersoft.heliocam.R;
import com.summersoft.heliocam.status.LoginStatus;

public class JoinerHomeFragment extends Fragment {
    private static final String TAG = "JoinerHomeFragment";
    private FirebaseAuth mAuth;
    private Handler handler = new Handler();

    // UI Components for the tips feature
    private TextView tipTextView;
    private Button nextTipButton;
    private int currentTipIndex = 0;
    private String[] viewerTips;

    public JoinerHomeFragment() {
        // Required empty public constructor
    }

    @SuppressLint("MissingInflatedId")
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View rootView = inflater.inflate(R.layout.fragment_joiner_home, container, false);

        mAuth = FirebaseAuth.getInstance();

        // Initialize tips array and UI components
        initializeTips();
        setupTipsCarousel(rootView);

        // Setup quick action buttons
        setupQuickActions(rootView);

        // Update welcome text if user is logged in
        updateWelcomeText(rootView);

        // Set padding for window insets
        ViewCompat.setOnApplyWindowInsetsListener(rootView.findViewById(R.id.mainpage), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Join session button listener
        Button joinSessionButton = rootView.findViewById(R.id.join_session_button);
        if (joinSessionButton != null) {
            joinSessionButton.setOnClickListener(v -> {
                // Navigate to join session activity
                Intent intent = new Intent(getActivity(), AddSessionActivity.class);
                startActivity(intent);
            });
        }

        // Set up joinSession button in top bar
        MaterialButton joinSessionTopBar = rootView.findViewById(R.id.joinSession);
        if (joinSessionTopBar != null) {
            joinSessionTopBar.setOnClickListener(v -> {
                Intent intent = new Intent(getActivity(), AddSessionActivity.class);
                startActivity(intent);
            });
        }

        return rootView;
    }

    /**
     * Initialize tips for the carousel
     */
    private void initializeTips() {
        viewerTips = new String[] {
            "Make sure you have the correct session ID and passkey before attempting to join a camera stream.",
            "Always check the connection quality indicator to ensure optimal viewing experience.",
            "Use the live chat feature to communicate with the session host for any assistance.",
            "You can bookmark frequently accessed sessions for quick access in the future.",
            "Enable notifications to get alerts when your favorite camera streams go live."
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
            tipTextView.setText(viewerTips[0]);

            // Setup next tip button
            nextTipButton.setOnClickListener(v -> {
                showNextTip();
            });
        }
    }

    /**
     * Show the next tip in the carousel
     */
    private void showNextTip() {
        currentTipIndex = (currentTipIndex + 1) % viewerTips.length;
        if (tipTextView != null) {
            tipTextView.setText(viewerTips[currentTipIndex]);
        }
    }

    /**
     * Setup quick action buttons
     */
    private void setupQuickActions(View rootView) {
        // Setup Join Session button
        MaterialCardView joinSessionCard = rootView.findViewById(R.id.btn_quick_join_session);
        if (joinSessionCard != null) {
            joinSessionCard.setOnClickListener(v -> {
                Intent intent = new Intent(getActivity(), AddSessionActivity.class);
                startActivity(intent);
            });
        }

        // Setup Recent Sessions button

    }

    /**
     * Update welcome text with user name if logged in
     */
    private void updateWelcomeText(View rootView) {
        TextView welcomeHeaderText = rootView.findViewById(R.id.welcome_text);

        if (welcomeHeaderText != null) {
            if (mAuth.getCurrentUser() != null && mAuth.getCurrentUser().getDisplayName() != null &&
                !mAuth.getCurrentUser().getDisplayName().isEmpty()) {
                welcomeHeaderText.setText("Welcome, " + mAuth.getCurrentUser().getDisplayName());
            } else {
                welcomeHeaderText.setText("Welcome to HelioCam Viewer");
            }
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        // Check login status when fragment starts
        handler.postDelayed(() -> {
            if (getActivity() != null && isAdded()) {
                LoginStatus.checkLoginStatus(getActivity());
                updateWelcomeText(getView());
            }
        }, 1500);
    }

    @Override
    public void onStop() {
        super.onStop();
        handler.removeCallbacksAndMessages(null);
    }
}

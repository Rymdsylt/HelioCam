package com.summersoft.heliocam.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.summersoft.heliocam.R;
import com.summersoft.heliocam.webrtc.SfuManager;
import com.summersoft.heliocam.webrtc_utils.RTCJoiner;

import org.webrtc.PeerConnectionFactory;
import org.webrtc.SurfaceViewRenderer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SessionPreviewActivity extends AppCompatActivity {
    private static final String TAG = "SessionPreviewActivity";

    public SfuManager sfuManager;

    // Firebase related variables
    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;
    private String sessionId;
    private String sessionName;
    private String hostEmail;

    // UI components
    private TextView connectionStatus;
    private TextView connectionBadge;
    private Button joinButton;
    private SurfaceViewRenderer previewRenderer;
    private TextView sessionNameText;

    // WebRTC components
    private RTCJoiner rtcJoiner;

    // Track if resources have been released
    private boolean resourcesReleased = false;
    private ValueEventListener sessionValueListener;
    private ValueEventListener availableSessionsListener;

    // Handler for UI updates
    private Handler uiHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_session_preview);

        // Initialize handler for UI updates
        uiHandler = new Handler(Looper.getMainLooper());

        // Make UI fullscreen
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();

        // Check authentication
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "Please sign in to continue", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Initialize UI
        initializeUI();

        // Get session information from intent
        sessionId = getIntent().getStringExtra("session_id");
        sessionName = getIntent().getStringExtra("session_name");
        hostEmail = getIntent().getStringExtra("host_email");

        // Log received values
        Log.d(TAG, "Received session ID: " + sessionId);
        Log.d(TAG, "Received session name: " + sessionName);
        Log.d(TAG, "Received host email: " + hostEmail);


        if (sessionId == null || sessionId.isEmpty()) {
            // If no valid session ID is provided, fetch available sessions
            Log.d(TAG, "No session ID provided. Looking for available sessions...");
            connectionStatus.setText("Looking for available sessions...");
            fetchAvailableSessions();
        } else {
            // If session name is not provided, use a default
            if (sessionName == null || sessionName.isEmpty()) {
                sessionName = "Unknown Session";
            }
            sessionNameText.setText(sessionName);

            // Setup WebRTC preview and fetch session details for the provided session
            setupCameraPreview();
            fetchSessionDetails();
        }
    }


    private void initializeUI() {
        // Initialize UI components
        sessionNameText = findViewById(R.id.session_name_text);
        connectionStatus = findViewById(R.id.connection_status);
        connectionBadge = findViewById(R.id.connection_badge);
        Button cancelButton = findViewById(R.id.cancel_button);
        joinButton = findViewById(R.id.join_button);
        ImageButton backButton = findViewById(R.id.back_button);
        previewRenderer = findViewById(R.id.preview_renderer);

        // Only set properties that don't require initialization
        previewRenderer.setZOrderMediaOverlay(true);

        // Set default text
        sessionNameText.setText("Connecting...");

        // Initially disable join button until connection is established
        joinButton.setEnabled(false);

        // Set click listeners
        backButton.setOnClickListener(v -> finish());
        cancelButton.setOnClickListener(v -> finish());
        joinButton.setOnClickListener(v -> joinSession());
    }

    private void fetchAvailableSessions() {
        connectionBadge.setText("Searching");

        // Remove any existing listener
        if (availableSessionsListener != null) {
            mDatabase.removeEventListener(availableSessionsListener);
        }

        String userEmail = mAuth.getCurrentUser().getEmail().replace(".", "_");

        availableSessionsListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (isFinishing() || resourcesReleased) return;

                List<SessionInfo> availableSessions = new ArrayList<>();

                // Loop through the current user's sessions only
                for (DataSnapshot sessionSnapshot : dataSnapshot.getChildren()) {
                    String sessionId = sessionSnapshot.getKey();
                    String sessionName = sessionSnapshot.child("session_name").getValue(String.class);

                    if (sessionId != null && !sessionId.isEmpty()) {
                        availableSessions.add(new SessionInfo(sessionId, sessionName, userEmail));
                    }
                }

                if (availableSessions.isEmpty()) {
                    connectionStatus.setText("No active sessions found");
                    connectionBadge.setText("Not found");
                } else {
                    SessionInfo firstSession = availableSessions.get(0);
                    hostEmail = firstSession.hostEmail;
                    sessionId = firstSession.sessionId;
                    sessionName = firstSession.sessionName != null ? firstSession.sessionName : "Unknown Session";

                    sessionNameText.setText(sessionName);
                    Log.d(TAG, "Found session: " + sessionId + " with name: " + sessionName + " from host: " + hostEmail);

                    setupCameraPreview();
                    fetchSessionDetailsFromHost();
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.e(TAG, "Firebase error: " + databaseError.getMessage());
                connectionStatus.setText("Error finding sessions: " + databaseError.getMessage());
                connectionBadge.setText("Error");
            }
        };

        // Only fetch sessions for the logged-in user
        mDatabase.child("users").child(userEmail).child("sessions").addListenerForSingleValueEvent(availableSessionsListener);
    }

    // Java
    // Java
    private void setupCameraPreview() {
        if (sessionId == null || sessionId.isEmpty()) {
            Log.e(TAG, "Cannot setup camera preview: sessionId is null or empty");
            return;
        }

        // Create RTCJoiner without initializing the renderer yet
        rtcJoiner = new RTCJoiner(this, sessionId, previewRenderer, mDatabase);

        // Start camera after a short delay to ensure the renderer is ready
        previewRenderer.post(() -> {
            if (rtcJoiner != null && !isFinishing()) {
                rtcJoiner.startCamera(this, true);
                Log.d(TAG, "Camera preview setup completed successfully");
            }
        });
    }

    private void fetchSessionDetailsFromHost() {
        if (sessionId == null || hostEmail == null) {
            Log.e(TAG, "Cannot fetch session details: sessionId or hostEmail is null");
            return;
        }

        Log.d(TAG, "Fetching session details for session: " + sessionId + " from host: " + hostEmail);

        // Update UI to show connecting state
        connectionStatus.setText("Connecting to host session...");
        connectionBadge.setText("Connecting");

        // Reference to the host's session
        DatabaseReference sessionRef = mDatabase.child("users")
                .child(hostEmail)
                .child("sessions")
                .child(sessionId);

        try {
            // Remove previous listener if exists
            if (sessionValueListener != null) {
                sessionRef.removeEventListener(sessionValueListener);
                sessionValueListener = null;
            }

            sessionValueListener = new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot dataSnapshot) {
                    if (isFinishing() || resourcesReleased) return;

                    if (dataSnapshot.exists()) {
                        // Session exists on the host side
                        String name = dataSnapshot.child("session_name").getValue(String.class);
                        if (name != null && !name.isEmpty()) {
                            sessionName = name;
                            sessionNameText.setText(sessionName);
                        }

                        // Enable join button and update UI
                        connectionStatus.setText("Ready to join session");
                        connectionBadge.setText("Ready");
                        connectionBadge.setBackgroundTintList(
                                ContextCompat.getColorStateList(SessionPreviewActivity.this, R.color.green));
                        joinButton.setEnabled(true);

                        // Initialize RTCJoiner with connection to the host
                        if (rtcJoiner != null) {
                            rtcJoiner.initiateSessionJoin(sessionId, hostEmail);
                        }
                    } else {
                        // Session doesn't exist
                        connectionStatus.setText("Session not found");
                        connectionBadge.setText("Not found");
                        connectionBadge.setBackgroundTintList(
                                ContextCompat.getColorStateList(SessionPreviewActivity.this, R.color.red));
                    }
                }

                @Override
                public void onCancelled(DatabaseError databaseError) {
                    Log.e(TAG, "Database error: " + databaseError.getMessage());
                    connectionStatus.setText("Connection error");
                    connectionBadge.setText("Error");
                    connectionBadge.setBackgroundTintList(
                            ContextCompat.getColorStateList(SessionPreviewActivity.this, R.color.red));
                }
            };

            // Add the listener
            sessionRef.addValueEventListener(sessionValueListener);
        } catch (Exception e) {
            Log.e(TAG, "Error adding Firebase listener", e);
            connectionStatus.setText("Failed to connect to session");
            connectionBadge.setText("Error");
            Toast.makeText(this, "Connection error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void fetchSessionDetails() {
        // First verify authentication
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null || currentUser.getEmail() == null) {
            Log.e(TAG, "User not authenticated or email is null");
            Toast.makeText(this, "Authentication error", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Update UI to show connecting state
        connectionStatus.setText("Connecting to session...");
        connectionBadge.setText("Connecting");

        // Get the user's email key for Firebase
        String userEmail = mAuth.getCurrentUser().getEmail().replace(".", "_");
        DatabaseReference sessionRef = mDatabase.child("users")
                .child(userEmail)
                .child("sessions")
                .child(sessionId);

        try {
            // Remove previous listener if exists
            if (sessionValueListener != null) {
                sessionRef.removeEventListener(sessionValueListener);
                sessionValueListener = null;
            }

            sessionValueListener = new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot dataSnapshot) {
                    if (isFinishing() || resourcesReleased) {
                        return;
                    }

                    if (dataSnapshot.exists()) {
                        // Session exists, update connection status
                        connectionStatus.setText("Connection established successfully");
                        connectionBadge.setText("Ready");
                        connectionBadge.setBackground(ContextCompat.getDrawable(
                                SessionPreviewActivity.this,
                                R.drawable.rounded_background));
                        joinButton.setEnabled(true);

                        // Update session name if it was not provided
                        if ((sessionName == null || sessionName.equals("Unknown Session")) &&
                                dataSnapshot.child("session_name").exists()) {
                            sessionName = dataSnapshot.child("session_name").getValue(String.class);
                            sessionNameText.setText(sessionName);
                        }

                        // Show successful connection with visual feedback after a short delay
                        uiHandler.postDelayed(() -> {
                            if (!isFinishing() && !resourcesReleased && joinButton.isEnabled()) {
                                connectionBadge.setBackgroundTintList(
                                        ContextCompat.getColorStateList(
                                                SessionPreviewActivity.this,
                                                android.R.color.holo_green_dark));
                            }
                        }, 1000);
                    } else {
                        // Session not found in current user's data, try to find it in other users
                        Log.d(TAG, "Session not found in current user's data. Searching other users...");
                        fetchAvailableSessions();
                    }
                }

                @Override
                public void onCancelled(DatabaseError databaseError) {
                    Log.e(TAG, "Firebase error: " + databaseError.getMessage());
                    if (!isFinishing() && !resourcesReleased) {
                        connectionStatus.setText("Connection error: " + databaseError.getMessage());
                        connectionBadge.setText("Error");
                        joinButton.setEnabled(false);

                        Toast.makeText(SessionPreviewActivity.this,
                                "Failed to connect: " + databaseError.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                }
            };

            // Add the listener
            sessionRef.addValueEventListener(sessionValueListener);
        } catch (Exception e) {
            Log.e(TAG, "Error adding Firebase listener", e);
            connectionStatus.setText("Failed to connect to session");
            connectionBadge.setText("Error");
            Toast.makeText(this, "Connection error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void joinSession() {
        if (mAuth.getCurrentUser() == null) {
            Toast.makeText(this, "Authentication error. Please sign in again.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Make sure we have a valid host email before proceeding
        if (sessionId == null || sessionId.isEmpty()) {
            Toast.makeText(this, "Invalid session ID", Toast.LENGTH_SHORT).show();
            return;
        }

        if (hostEmail == null || hostEmail.isEmpty()) {
            // Try to extract host email from session ID if not explicitly provided
            String[] parts = sessionId.split("_");
            if (parts.length > 0) {
                hostEmail = parts[0].replace("_", ".");
            }

            if (hostEmail == null || hostEmail.isEmpty()) {
                Toast.makeText(this, "Host information not available", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        Log.d(TAG, "Joining session: " + sessionId + " from host: " + hostEmail);

        // Update UI
        connectionStatus.setText("Joining session...");
        connectionBadge.setText("Joining");
        joinButton.setEnabled(false);

        try {
            // Ensure RTCJoiner is initialized
            if (rtcJoiner != null) {
                // Connect to session with the host's email
                rtcJoiner.initiateSessionJoin(sessionId, hostEmail);

                // Transition to the session viewing activity
                Intent intent = new Intent(SessionPreviewActivity.this, CameraActivity.class);
                intent.putExtra("session_id", sessionId);
                intent.putExtra("session_name", sessionName);
                intent.putExtra("host_email", hostEmail);
                startActivity(intent);

                // Mark as released and finish this activity
                resourcesReleased = true;
                finish();
            } else {
                Log.e(TAG, "RTCJoiner is null, cannot connect to session");
                Toast.makeText(this, "Connection error: RTCJoiner not initialized", Toast.LENGTH_SHORT).show();
                connectionStatus.setText("Connection failed");
                connectionBadge.setText("Error");
                joinButton.setEnabled(true);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error joining session: " + e.getMessage(), e);
            Toast.makeText(this, "Error joining session: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            connectionStatus.setText("Connection failed");
            connectionBadge.setText("Error");
            joinButton.setEnabled(true);
        }
    }



    @Override
    protected void onPause() {
        super.onPause();
        // RTCJoiner handles camera lifecycle
    }

    @Override
    protected void onResume() {
        super.onResume();
        // RTCJoiner handles camera lifecycle
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy called, resourcesReleased=" + resourcesReleased);

        // Only clean up if not already released
        if (!resourcesReleased) {
            cleanupResources();
            resourcesReleased = true;
        }

        super.onDestroy();
    }

    private void cleanupResources() {
        Log.d(TAG, "Cleaning up resources");

        // Cancel any pending UI updates
        if (uiHandler != null) {
            uiHandler.removeCallbacksAndMessages(null);
        }

        // Dispose RTCJoiner (handles all WebRTC cleanup)
        if (rtcJoiner != null) {
            rtcJoiner.dispose();
            rtcJoiner = null;
        }

        // Clean up Firebase listeners
        if (sessionValueListener != null) {
            try {
                if (hostEmail != null) {
                    mDatabase.child("users")
                            .child(hostEmail)
                            .child("sessions")
                            .child(sessionId)
                            .removeEventListener(sessionValueListener);
                } else if (mAuth.getCurrentUser() != null && mAuth.getCurrentUser().getEmail() != null) {
                    String userEmail = mAuth.getCurrentUser().getEmail().replace(".", "_");
                    mDatabase.child("users")
                            .child(userEmail)
                            .child("sessions")
                            .child(sessionId)
                            .removeEventListener(sessionValueListener);
                }
                sessionValueListener = null;
            } catch (Exception e) {
                Log.e(TAG, "Error removing session listener", e);
            }
        }

        if (availableSessionsListener != null) {
            try {
                mDatabase.child("users").removeEventListener(availableSessionsListener);
                availableSessionsListener = null;
            } catch (Exception e) {
                Log.e(TAG, "Error removing available sessions listener", e);
            }
        }
    }

    // Helper class to store session information
    public class SessionInfo {
        private String sessionId;
        private String sessionName;
        private String hostEmail;

        public SessionInfo(String sessionId, String sessionName, String hostEmail) {
            this.sessionId = sessionId;
            this.sessionName = sessionName;
            this.hostEmail = hostEmail;
        }

        public String getSessionId() {
            return sessionId;
        }

        public void setSessionId(String sessionId) {
            this.sessionId = sessionId;
        }

        public String getSessionName() {
            return sessionName;
        }

        public void setSessionName(String sessionName) {
            this.sessionName = sessionName;
        }

        public String getHostEmail() {
            return hostEmail;
        }

        public void setHostEmail(String hostEmail) {
            this.hostEmail = hostEmail;
        }
    }

}
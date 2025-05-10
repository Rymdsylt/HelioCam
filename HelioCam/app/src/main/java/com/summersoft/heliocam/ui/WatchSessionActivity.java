package com.summersoft.heliocam.ui;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.tabs.TabLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.summersoft.heliocam.R;
import com.summersoft.heliocam.webrtc_utils.RTCHost;

import org.webrtc.EglBase;
import org.webrtc.SurfaceViewRenderer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class WatchSessionActivity extends AppCompatActivity {
    private static final String TAG = "WatchSessionActivity";

    // Firebase components
    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;

    // Session information
    private String sessionId;
    private String sessionName;

    // UI components
    private TextView sessionTitle; // Changed from sessionNameText 
    private TextView participantsCount; // Changed from connectionStatus
    private SurfaceViewRenderer feedView1; // Primary feed view
    private SurfaceViewRenderer feedView2; // Secondary feed views
    private SurfaceViewRenderer feedView3;
    private SurfaceViewRenderer feedView4;
    private View gridLayout; // Changed to match XML

    // WebRTC components
    private RTCHost rtcHost;

    // UI state
    private boolean isAudioEnabled = true;
    private boolean ignoreJoinRequests = false;
    private ValueEventListener joinRequestsListener;

    // Camera status views
    private Map<String, TextView> cameraDisabledMessages = new HashMap<>();
    private Map<String, TextView> micStatusMessages = new HashMap<>();
    private View joinRequestNotification;

    // Participant counts
    private int totalConnectedCameras = 0;
    private int activeCamerasCount = 0;

    // Add this as a class field
    private Map<SurfaceViewRenderer, Boolean> initializedRenderers = new HashMap<>();

    // Add this to WatchSessionActivity.java class variables
    private int focusedCamera = -1; // -1 means no focus (grid view)

    // Add these to your class variables
    private Map<String, TextView> cameraNumberLabels = new HashMap<>();
    private Map<String, TextView> cameraTimestamps = new HashMap<>();
    private Handler timestampUpdateHandler = new Handler(Looper.getMainLooper());
    private Runnable timestampUpdateRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_watch_session);

        // Initialize Firebase components
        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();

        // Get session info from intent
        sessionId = getIntent().getStringExtra("session_id");
        sessionName = getIntent().getStringExtra("session_name");

        if (sessionId == null || sessionName == null) {
            Toast.makeText(this, "Invalid session information", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        Log.d(TAG, "Session ID: " + sessionId);
        Log.d(TAG, "Session name: " + sessionName);

        // Initialize UI components
        sessionTitle = findViewById(R.id.session_title);
        participantsCount = findViewById(R.id.participants_count);

        // Set session name as title
        sessionTitle.setText(sessionName);

        // Initialize video renderers
        feedView1 = findViewById(R.id.feed_view_1);
        feedView2 = findViewById(R.id.feed_view_2);
        feedView3 = findViewById(R.id.feed_view_3);
        feedView4 = findViewById(R.id.feed_view_4);
        gridLayout = findViewById(R.id.grid_layout);

        // Important: Safety check for renderer initialization
        try {
            // Release all renderers before starting
            safeReleaseRenderer(feedView1);
            safeReleaseRenderer(feedView2);
            safeReleaseRenderer(feedView3);
            safeReleaseRenderer(feedView4);
        } catch (Exception e) {
            Log.e(TAG, "Error releasing views", e);
        }

        // Now initialize WebRTC components
        try {
            rtcHost = new RTCHost(this, feedView1, mDatabase);

            // Setup UI components, listeners, etc.
            setupUI();
            setupSessionListeners();
        } catch (IllegalStateException e) {
            Log.e(TAG, "Error initializing WebRTC", e);
            Toast.makeText(this, "Failed to initialize video. Please restart the app.", Toast.LENGTH_LONG).show();
        }

        // Initialize participant counter
        participantsCount = findViewById(R.id.participants_count);

        // Set initial value
        updateParticipantCount(0);
    }

    // Helper method to safely release a renderer
    private void safeReleaseRenderer(SurfaceViewRenderer renderer) {
        if (renderer != null) {
            try {
                renderer.release();
                if (initializedRenderers != null) {
                    initializedRenderers.put(renderer, false);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error releasing renderer: " + e.getMessage());
            }
        }
    }

    @Override
    protected void onDestroy() {
        if (timestampUpdateRunnable != null) {
            timestampUpdateHandler.removeCallbacks(timestampUpdateRunnable);
        }
        super.onDestroy();

        // Clean up resources
        if (rtcHost != null) {
            rtcHost.dispose();
        }

        // Release all renderers
        safeReleaseRenderer(feedView1);
        safeReleaseRenderer(feedView2);
        safeReleaseRenderer(feedView3);
        safeReleaseRenderer(feedView4);
    }

    /**
     * Set up the UI components and their click listeners
     */
    @SuppressLint("ClickableViewAccessibility")
    private void setupUI() {
        // Initialize UI components
        MaterialButton endSessionButton = findViewById(R.id.end_session_button);
        MaterialButton micButton = findViewById(R.id.microphone_button);
        joinRequestNotification = findViewById(R.id.join_request_notification);
        FloatingActionButton settingsButton = findViewById(R.id.settings_button);

        // Initially hide the join request notification
        if (joinRequestNotification != null) {
            joinRequestNotification.setVisibility(View.GONE);
        }

        // Find all container views ONCE at the beginning
        View feedContainer1 = findViewById(R.id.feed_container_1);
        View feedContainer2 = findViewById(R.id.feed_container_2);
        View feedContainer3 = findViewById(R.id.feed_container_3);
        View feedContainer4 = findViewById(R.id.feed_container_4);

        // End session button
        if (endSessionButton != null) {
            endSessionButton.setOnClickListener(v -> {
                showEndSessionConfirmation();
            });
        }

        // Mic button to toggle audio
        if (micButton != null) {
            // Set initial state
            micButton.setIconResource(isAudioEnabled ? 
                    R.drawable.ic_baseline_mic_24 : R.drawable.ic_baseline_mic_off_24);
            
            // Set click listener to toggle mic state
            micButton.setOnClickListener(v -> {
                toggleAudio();
                
                // Update button icon based on new state
                micButton.setIconResource(isAudioEnabled ? 
                        R.drawable.ic_baseline_mic_24 : R.drawable.ic_baseline_mic_off_24);
                
                // Show feedback to the user
                Toast.makeText(WatchSessionActivity.this, 
                        isAudioEnabled ? "Microphone unmuted" : "Microphone muted", 
                        Toast.LENGTH_SHORT).show();
            });
        }

        // Settings button to show session info
        if (settingsButton != null) {
            settingsButton.setOnClickListener(v -> {
                showSessionInfoDialog();
            });
        }

        // Set up notification for join requests
        if (joinRequestNotification != null) {
            joinRequestNotification.setOnClickListener(v -> {
                showJoinRequestDialog();
            });
        }

        // Initialize camera disabled message views
        cameraDisabledMessages.clear();
        micStatusMessages.clear();

        // Add camera off message views for each feed - using the EXISTING container variables
        if (feedContainer1 != null) {
            TextView offMsg1 = feedContainer1.findViewById(R.id.camera_off_message_1);
            TextView micStatus1 = feedContainer1.findViewById(R.id.mic_status_1);
            if (offMsg1 != null) cameraDisabledMessages.put("feed1", offMsg1);
            if (micStatus1 != null) micStatusMessages.put("feed1", micStatus1);
        }

        if (feedContainer2 != null) {
            TextView offMsg2 = feedContainer2.findViewById(R.id.camera_off_message_2);
            TextView micStatus2 = feedContainer2.findViewById(R.id.mic_status_2);
            if (offMsg2 != null) cameraDisabledMessages.put("feed2", offMsg2);
            if (micStatus2 != null) micStatusMessages.put("feed2", micStatus2);
        }

        if (feedContainer3 != null) {
            TextView offMsg3 = feedContainer3.findViewById(R.id.camera_off_message_3);
            TextView micStatus3 = feedContainer3.findViewById(R.id.mic_status_3);
            if (offMsg3 != null) cameraDisabledMessages.put("feed3", offMsg3);
            if (micStatus3 != null) micStatusMessages.put("feed3", micStatus3);
        }

        if (feedContainer4 != null) {
            TextView offMsg4 = feedContainer4.findViewById(R.id.camera_off_message_4);
            TextView micStatus4 = feedContainer4.findViewById(R.id.mic_status_4);
            if (offMsg4 != null) cameraDisabledMessages.put("feed4", offMsg4);
            if (micStatus4 != null) micStatusMessages.put("feed4", micStatus4);
        }

        // Hide all camera disabled messages initially
        for (TextView msgView : cameraDisabledMessages.values()) {
            if (msgView != null) {
                msgView.setVisibility(View.GONE);
            }
        }

        // Hide all mic status messages initially
        for (TextView micView : micStatusMessages.values()) {
            if (micView != null) {
                micView.setVisibility(View.GONE);
            }
        }

        // Initialize camera number labels and timestamps
        cameraNumberLabels.clear();
        cameraTimestamps.clear();

        // Add camera number and timestamp views for each feed - again using the EXISTING variables
        if (feedContainer1 != null) {
            TextView cameraNumber1 = feedContainer1.findViewById(R.id.camera_number_1);
            TextView timestamp1 = feedContainer1.findViewById(R.id.camera_timestamp_1);
            if (cameraNumber1 != null) cameraNumberLabels.put("feed1", cameraNumber1);
            if (timestamp1 != null) cameraTimestamps.put("feed1", timestamp1);
        }

        if (feedContainer2 != null) {
            TextView cameraNumber2 = feedContainer2.findViewById(R.id.camera_number_2);
            TextView timestamp2 = feedContainer2.findViewById(R.id.camera_timestamp_2);
            if (cameraNumber2 != null) cameraNumberLabels.put("feed2", cameraNumber2);
            if (timestamp2 != null) cameraTimestamps.put("feed2", timestamp2);
        }

        if (feedContainer3 != null) {
            TextView cameraNumber3 = feedContainer3.findViewById(R.id.camera_number_3);
            TextView timestamp3 = feedContainer3.findViewById(R.id.camera_timestamp_3);
            if (cameraNumber3 != null) cameraNumberLabels.put("feed3", cameraNumber3);
            if (timestamp3 != null) cameraTimestamps.put("feed3", timestamp3);
        }

        if (feedContainer4 != null) {
            TextView cameraNumber4 = feedContainer4.findViewById(R.id.camera_number_4);
            TextView timestamp4 = feedContainer4.findViewById(R.id.camera_timestamp_4);
            if (cameraNumber4 != null) cameraNumberLabels.put("feed4", cameraNumber4);
            if (timestamp4 != null) cameraTimestamps.put("feed4", timestamp4);
        }

        // Start the timestamp update scheduler
        startTimestampUpdates();

        participantsCount.setOnClickListener(v -> {
            // Show detailed participant list when count is clicked
            showParticipantListDialog();
        });

        // Initialize click-to-focus behavior for camera feeds
        // REMOVE THESE LINES:
        // View feedContainer1 = findViewById(R.id.feed_container_1);
        // View feedContainer2 = findViewById(R.id.feed_container_2);
        // View feedContainer3 = findViewById(R.id.feed_container_3);
        // View feedContainer4 = findViewById(R.id.feed_container_4);

        // Gesture detector for tracking single vs double clicks
        GestureDetector gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                // Single tap enters focus mode
                return false;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                // Double tap exits focus mode
                if (focusedCamera != -1) {
                    focusedCamera = -1;
                    updateGridLayout(totalConnectedCameras);
                    return true;
                }
                return false;
            }
        });

        // Set click listeners for each feed container - using the EXISTING containers 
        if (feedContainer1 != null) {
            feedContainer1.setOnTouchListener((v, event) -> {
                if (gestureDetector.onTouchEvent(event)) {
                    return true;
                }
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    if (focusedCamera == -1 && feedContainer1.getVisibility() == View.VISIBLE) {
                        focusedCamera = 0;
                        updateGridLayout(totalConnectedCameras);
                        showFocusHint(feedContainer1);
                        return true;
                    }
                }
                return false;
            });
        }

        if (feedContainer2 != null) {
            feedContainer2.setOnTouchListener((v, event) -> {
                if (gestureDetector.onTouchEvent(event)) {
                    return true;
                }
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    if (focusedCamera == -1 && feedContainer2.getVisibility() == View.VISIBLE) {
                        focusedCamera = 1;
                        updateGridLayout(totalConnectedCameras);
                        showFocusHint(feedContainer2);
                        return true;
                    }
                }
                return false;
            });
        }

        if (feedContainer3 != null) {
            feedContainer3.setOnTouchListener((v, event) -> {
                if (gestureDetector.onTouchEvent(event)) {
                    return true;
                }
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    if (focusedCamera == -1 && feedContainer3.getVisibility() == View.VISIBLE) {
                        focusedCamera = 2;
                        updateGridLayout(totalConnectedCameras);
                        showFocusHint(feedContainer3);
                        return true;
                    }
                }
                return false;
            });
        }

        if (feedContainer4 != null) {
            feedContainer4.setOnTouchListener((v, event) -> {
                if (gestureDetector.onTouchEvent(event)) {
                    return true;
                }
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    if (focusedCamera == -1 && feedContainer4.getVisibility() == View.VISIBLE) {
                        focusedCamera = 3;
                        updateGridLayout(totalConnectedCameras);
                        showFocusHint(feedContainer4);
                        return true;
                    }
                }
                return false;
            });
        }
    }

    /**
     * Show dialog with participant details
     */
    private void showParticipantListDialog() {
        String userEmail = mAuth.getCurrentUser().getEmail().replace(".", "_");

        mDatabase.child("users").child(userEmail).child("sessions").child(sessionId)
                .child("connected_cameras").get().addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(this);
                        builder.setTitle("Connected Participants");

                        // Create list view
                        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_participants_list, null);
                        LinearLayout participantsList = dialogView.findViewById(R.id.participants_list);

                        if (participantsList != null) {
                            if (task.getResult().getChildrenCount() > 0) {
                                for (DataSnapshot participant : task.getResult().getChildren()) {
                                    // Create participant entry
                                    View entry = LayoutInflater.from(this).inflate(R.layout.item_participant, null);
                                    TextView nameView = entry.findViewById(R.id.participant_name);
                                    TextView statusView = entry.findViewById(R.id.participant_status);

                                    // Get participant data
                                    String email = participant.child("email").getValue(String.class);
                                    Boolean isActive = participant.child("active").getValue(Boolean.class);

                                    // Set values
                                    if (nameView != null)
                                        nameView.setText(email != null ? email : "Unknown");
                                    if (statusView != null) {
                                        statusView.setText(isActive != null && isActive ? "Active" : "Connected");
                                        statusView.setTextColor(ContextCompat.getColor(this,
                                                isActive != null && isActive ? R.color.green : R.color.gray));
                                    }

                                    // Add to list
                                    participantsList.addView(entry);
                                }
                            } else {
                                TextView noParticipants = new TextView(this);
                                noParticipants.setText("No participants connected");
                                noParticipants.setPadding(20, 20, 20, 20);
                                participantsList.addView(noParticipants);
                            }
                        }

                        builder.setView(dialogView);
                        builder.setPositiveButton("Close", (dialog, id) -> dialog.dismiss());
                        builder.create().show();
                    } else {
                        Toast.makeText(this, "Failed to fetch participants", Toast.LENGTH_SHORT).show();
                    }
                });
    }


    /**
     * Set up Firebase listeners for session events
     */
    private void setupSessionListeners() {
        // Set up join request listener
        setupJoinRequestListener();

        // Set up session status listener
        setupSessionStatusListener();

        // Track connected cameras (total count)
        setupConnectedCamerasListener();

        // Track active cameras (those currently streaming)
        setupActiveCamerasListener();

        // Tell RTCHost to initialize the session with this session ID
        if (rtcHost != null) {
            rtcHost.initializeSession(sessionId);
        }

        // Track accepted join requests to manage active cameras display
        setupAcceptedCamerasListener();
    }

    /**
     * Set up a Firebase listener for join requests
     */
    private void setupJoinRequestListener() {
        // Clean up any existing listener
        if (joinRequestsListener != null) {
            String userEmail = mAuth.getCurrentUser().getEmail().replace(".", "_");
            mDatabase.child("users").child(userEmail).child("sessions").child(sessionId)
                    .child("join_requests").removeEventListener(joinRequestsListener);
        }

        // Create a new listener
        joinRequestsListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                boolean hasJoinRequests = false;
                long pendingRequestCount = 0;
                
                for (DataSnapshot requestSnapshot : dataSnapshot.getChildren()) {
                    String status = requestSnapshot.child("status").getValue(String.class);
                    // Only count pending requests (not accepted or rejected)
                    if (status == null || !status.equals("accepted")) {
                        hasJoinRequests = true;
                        pendingRequestCount++;
                    }
                }
                
                // Update UI based on whether we have pending join requests
                if (joinRequestNotification != null) {
                    joinRequestNotification.setVisibility(hasJoinRequests && !ignoreJoinRequests ? 
                            View.VISIBLE : View.GONE);
                }
                
                // Update notification count badge
                View notificationCount = findViewById(R.id.notification_count);
                TextView notificationCountText = findViewById(R.id.notification_count_text);
                
                if (notificationCount != null && notificationCountText != null) {
                    if (pendingRequestCount > 0) {
                        notificationCount.setVisibility(View.VISIBLE);
                        notificationCountText.setText(String.valueOf(pendingRequestCount));
                    } else {
                        notificationCount.setVisibility(View.GONE);
                    }
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.e(TAG, "Join requests listener cancelled", databaseError.toException());
            }
        };

        // Add the listener to Firebase
        String userEmail = mAuth.getCurrentUser().getEmail().replace(".", "_");
        mDatabase.child("users").child(userEmail).child("sessions").child(sessionId)
                .child("join_requests").addValueEventListener(joinRequestsListener);
    }

    /**
     * Set up a Firebase listener for session status updates
     */
    private void setupSessionStatusListener() {
        String userEmail = mAuth.getCurrentUser().getEmail().replace(".", "_");

        // Listen for connected cameras
        mDatabase.child("users").child(userEmail).child("sessions").child(sessionId)
                .child("connected_cameras").addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        long count = dataSnapshot.getChildrenCount();
                        updateParticipantCount((int) count);
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {
                        Log.e(TAG, "Session status listener cancelled", databaseError.toException());
                    }
                });

        // Listen for camera status changes (camera on/off)
        mDatabase.child("users").child(userEmail).child("sessions").child(sessionId)
                .child("camera_status").addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        for (DataSnapshot cameraSnapshot : dataSnapshot.getChildren()) {
                            String cameraId = cameraSnapshot.getKey();
                            Boolean isCameraOff = cameraSnapshot.child("camera_off").getValue(Boolean.class);

                            // Update UI based on camera status
                            updateCameraOffStatus(cameraId, isCameraOff != null && isCameraOff);
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {
                        Log.e(TAG, "Camera status listener cancelled", databaseError.toException());
                    }
                });
    }

    /**
     * Listen for connected cameras (total count)
     */
    private void setupConnectedCamerasListener() {
        String userEmail = mAuth.getCurrentUser().getEmail().replace(".", "_");

        mDatabase.child("users").child(userEmail).child("sessions").child(sessionId)
                .child("connected_cameras").addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        long count = dataSnapshot.getChildrenCount();
                        totalConnectedCameras = (int) count;
                        updateParticipantCount(totalConnectedCameras);
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {
                        Log.e(TAG, "Connected cameras listener cancelled", databaseError.toException());
                    }
                });
    }

    /**
     * Listen for active cameras (currently streaming)
     */
    private void setupActiveCamerasListener() {
        String userEmail = mAuth.getCurrentUser().getEmail().replace(".", "_");

        mDatabase.child("users").child(userEmail).child("sessions").child(sessionId)
                .child("active_cameras").addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        long count = dataSnapshot.getChildrenCount();
                        activeCamerasCount = (int) count;

                        // Update status message to show active vs. total
                        updateDetailedParticipantCount(activeCamerasCount, totalConnectedCameras);
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {
                        Log.e(TAG, "Active cameras listener cancelled", databaseError.toException());
                    }
                });
    }

    /**
     * Listen for accepted cameras to update the UI
     */
    private void setupAcceptedCamerasListener() {
        String userEmail = mAuth.getCurrentUser().getEmail().replace(".", "_");

        mDatabase.child("users").child(userEmail).child("sessions").child(sessionId)
                .child("join_requests").addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        int acceptedCount = 0;
                        Map<String, String> acceptedJoiners = new HashMap<>();

                        // Count accepted cameras and track their IDs
                        for (DataSnapshot requestSnapshot : dataSnapshot.getChildren()) {
                            String status = requestSnapshot.child("status").getValue(String.class);
                            if ("accepted".equals(status)) {
                                String joinerId = requestSnapshot.getKey();
                                String joinerEmail = requestSnapshot.child("email").getValue(String.class);
                                acceptedCount++;

                                if (joinerId != null && joinerEmail != null) {
                                    acceptedJoiners.put(joinerId, joinerEmail);
                                }
                            }
                        }

                        // Update the active camera count
                        activeCamerasCount = acceptedCount;
                        updateDetailedParticipantCount(activeCamerasCount, totalConnectedCameras);

                        // Update the grid layout
                        updateGridLayout(acceptedCount);

                        // Ensure all accepted cameras have renderers assigned
                        for (Map.Entry<String, String> entry : acceptedJoiners.entrySet()) {
                            String joinerId = entry.getKey();
                            String joinerEmail = entry.getValue();

                            // Check if this camera needs a renderer assignment
                            if (rtcHost != null && rtcHost.needsRendererAssignment(joinerId)) {
                                Log.d(TAG, "Assigning renderer to camera: " + joinerId);
                                assignCameraToRenderer(joinerId, joinerEmail);
                            }
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {
                        Log.e(TAG, "Accepted cameras listener cancelled", databaseError.toException());
                    }
                });
    }

    /**
     * Update the participant count display with more details
     */
    private void updateParticipantCount(int count) {
        if (participantsCount != null) {
            StringBuilder countText = new StringBuilder();

            // Format the count display
            countText.append(count)
                    .append(" camera")
                    .append(count != 1 ? "s" : "")
                    .append(" connected");

            // Add maximum participant limit info
            countText.append(" (max 4)");

            // Update the UI
            participantsCount.setText(countText.toString());

            // Optionally change text color based on count
            int color = count >= 4 ? ContextCompat.getColor(this, R.color.red) :
                    ContextCompat.getColor(this, R.color.green);
            participantsCount.setTextColor(color);

            // Log the count for debugging
            Log.d(TAG, "Updated participant count: " + count);
        }
    }

    /**
     * Update participant count with active vs. total information
     */
    private void updateDetailedParticipantCount(int active, int total) {
        if (participantsCount != null) {
            String countText = active + " active / " + total + " connected (max 4)";
            participantsCount.setText(countText);
        }
    }

    /**
     * Update the camera disabled message visibility
     */
    private void updateCameraOffStatus(String cameraId, boolean isOff) {
        // Map the camera ID to the appropriate message view (you'll need to define this mapping)
        String viewKey = mapCameraIdToViewKey(cameraId);

        TextView msgView = cameraDisabledMessages.get(viewKey);
        if (msgView != null) {
            msgView.setVisibility(isOff ? View.VISIBLE : View.GONE);
        }
    }

    /**
     * Map a camera ID to a view key
     */
    private String mapCameraIdToViewKey(String cameraId) {
        // This is a placeholder - you'll need to implement proper mapping
        // based on how rtcHost assigns cameras to views
        return "feed1"; // Default to first feed
    }

    /**
     * Toggle audio mute/unmute
     */
    private void toggleAudio() {
        isAudioEnabled = !isAudioEnabled;

        // Update RTCHost audio state
        if (rtcHost != null) {
            if (isAudioEnabled) {
                rtcHost.unmuteMic();
            } else {
                rtcHost.muteMic();
            }
        }
        
        // Log the state change
        Log.d(TAG, "Audio toggled to: " + (isAudioEnabled ? "enabled" : "disabled"));
    }

    /**
     * Show confirmation dialog for ending the session
     */
    private void showEndSessionConfirmation() {
        new AlertDialog.Builder(this)
                .setTitle("End Session")
                .setMessage("Are you sure you want to end this session? All connections will be terminated.")
                .setPositiveButton("End Session", (dialog, which) -> {
                    endSession();
                })
                .setNegativeButton("Cancel", (dialog, which) -> {
                    dialog.dismiss();
                })
                .setCancelable(true)
                .create()
                .show();
    }

    /**
     * End the current session
     */
    private void endSession() {
        // Clean up RTCHost
        if (rtcHost != null) {
            rtcHost.dispose();
        }

        // Delete the session from Firebase
        String userEmail = mAuth.getCurrentUser().getEmail().replace(".", "_");
        mDatabase.child("users").child(userEmail).child("sessions").child(sessionId)
                .removeValue()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "Session deleted successfully");
                        finish();
                    } else {
                        Log.e(TAG, "Failed to delete session", task.getException());
                        Toast.makeText(this, "Failed to end session", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    /**
     * Show dialog for handling join requests
     */
    private void showJoinRequestDialog() {
        // Get the current user's email
        String userEmail = mAuth.getCurrentUser().getEmail().replace(".", "_");

        // Add debugging to see what's happening
        Log.d(TAG, "showJoinRequestDialog called, checking join requests");

        // Query join requests
        mDatabase.child("users").child(userEmail).child("sessions").child(sessionId)
                .child("join_requests").get().addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        // More detailed debug info
                        Log.d(TAG, "Join requests query successful, found: " +
                                (task.getResult().exists() ? task.getResult().getChildrenCount() + " requests" : "no requests"));

                        if (task.getResult().exists() && task.getResult().getChildrenCount() > 0) {
                            // Create dialog
                            AlertDialog.Builder builder = new AlertDialog.Builder(this);
                            builder.setTitle("Join Requests");

                            // Create custom view
                            View customView = LayoutInflater.from(this).inflate(R.layout.dialog_join_requests, null);
                            GridLayout requestsGrid = customView.findViewById(R.id.requests_grid);
                            CheckBox ignoreCheckbox = customView.findViewById(R.id.ignore_checkbox);

                            if (ignoreCheckbox != null) {
                                ignoreCheckbox.setChecked(ignoreJoinRequests);
                            }

                            // Add join requests to the grid
                            if (requestsGrid != null) {
                                boolean hasValidRequests = false;

                                // Track unique emails to prevent duplicates
                                Set<String> uniqueEmails = new HashSet<>();

                                for (DataSnapshot requestSnapshot : task.getResult().getChildren()) {
                                    String requestId = requestSnapshot.getKey();
                                    String requestEmail = requestSnapshot.child("email").getValue(String.class);

                                    // Skip requests that already have status "accepted"
                                    String status = requestSnapshot.child("status").getValue(String.class);
                                    if ("accepted".equals(status)) continue;

                                    // Skip duplicate emails
                                    if (uniqueEmails.contains(requestEmail)) continue;
                                    uniqueEmails.add(requestEmail);

                                    // Add request entry to the grid
                                    addRequestToGrid(requestsGrid, requestId, requestEmail != null ? requestEmail : "Unknown");
                                    hasValidRequests = true;
                                }

                                if (!hasValidRequests) {
                                    TextView noRequestsText = new TextView(this);
                                    noRequestsText.setText("No pending join requests");
                                    noRequestsText.setPadding(20, 20, 20, 20);
                                    requestsGrid.addView(noRequestsText);
                                }
                            }

                            // Set the view to the dialog
                            builder.setView(customView);

                            // Add close button
                            builder.setPositiveButton("Close", (dialog, which) -> {
                                // Update ignore preference
                                if (ignoreCheckbox != null) {
                                    ignoreJoinRequests = ignoreCheckbox.isChecked();
                                }

                                dialog.dismiss();
                                joinRequestDialog = null;
                            });

                            // Show dialog
                            joinRequestDialog = builder.create();
                            joinRequestDialog.show();
                        } else {
                            Toast.makeText(this, "No join requests found", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Log.e(TAG, "Failed to query join requests", task.getException());
                        Toast.makeText(this, "Error checking for join requests", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    /**
     * Add a join request to the grid view
     */
    private void addRequestToGrid(GridLayout grid, String requestId, String email) {
        // Create request entry view
        View requestView = LayoutInflater.from(this).inflate(R.layout.item_join_request, null);
        TextView emailText = requestView.findViewById(R.id.request_email);

        // Add debug to confirm the layout is properly loaded
        if (requestView == null) {
            Log.e(TAG, "Failed to inflate join request layout");
            return;
        }

        // Get buttons with null checks
        View acceptBtn = requestView.findViewById(R.id.accept_button);
        View rejectBtn = requestView.findViewById(R.id.reject_button);

        if (emailText != null) {
            emailText.setText(email);
        }

        if (acceptBtn != null) {
            acceptBtn.setOnClickListener(v -> {
                Log.d(TAG, "Accept button clicked for joiner: " + email + " with ID: " + requestId);
                acceptJoinRequest(requestId);
            });
        } else {
            Log.e(TAG, "Accept button not found in join request layout");
        }

        if (rejectBtn != null) {
            rejectBtn.setOnClickListener(v -> {
                Log.d(TAG, "Reject button clicked for joiner: " + email);
                rejectJoinRequest(requestId);
            });
        }

        // Add to grid with proper layout params
        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = GridLayout.LayoutParams.MATCH_PARENT;
        params.height = GridLayout.LayoutParams.WRAP_CONTENT;
        params.setMargins(8, 8, 8, 8);

        requestView.setLayoutParams(params);
        grid.addView(requestView);
    }

    /**
     * Accept a join request
     */
    private void acceptJoinRequest(String requestId) {
        Log.d(TAG, "Accepting join request: " + requestId);

        String userEmail = mAuth.getCurrentUser().getEmail().replace(".", "_");
        DatabaseReference requestRef = mDatabase.child("users")
                .child(userEmail)
                .child("sessions")
                .child(sessionId)
                .child("join_requests")
                .child(requestId);

        // First get the joiner's email and device information
        requestRef.get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult() != null) {
                String joinerEmail = task.getResult().child("email").getValue(String.class);
                String deviceId = task.getResult().child("deviceId").getValue(String.class);
                String deviceName = task.getResult().child("deviceName").getValue(String.class);
                
                if (joinerEmail != null) {
                    // Use a combination of email and device ID to create a unique identifier
                    String uniqueJoinerId = deviceId != null ? joinerEmail + "_" + deviceId : joinerEmail;
                    
                    Log.d(TAG, "Accepting join request from email: " + joinerEmail + 
                          ", device: " + (deviceName != null ? deviceName : "Unknown") + 
                          ", with unique ID: " + uniqueJoinerId);
                    
                    // Set the request as accepted in Firebase
                    requestRef.child("status").setValue("accepted");
                    
                    // Store device info with request for better identification
                    if (deviceId != null) requestRef.child("deviceId").setValue(deviceId);
                    if (deviceName != null) requestRef.child("deviceName").setValue(deviceName);
                    
                    // Create connection for this joiner, passing device info
                    if (rtcHost != null) {
                        rtcHost.acceptJoinRequest(requestId, joinerEmail, deviceName, deviceId);
                    }
                    
                    // Assign camera to a renderer
                    assignCameraToRenderer(requestId, joinerEmail, deviceId, deviceName);
                    
                    // Hide the join request dialog
                    dismissJoinRequestDialog();
                    
                    Toast.makeText(WatchSessionActivity.this, 
                            "Join request accepted: " + joinerEmail + 
                            (deviceName != null ? " (" + deviceName + ")" : ""), 
                            Toast.LENGTH_SHORT).show();
                }
            } else {
                Log.e(TAG, "Failed to get join request data", 
                      task.getException() != null ? task.getException() : new Exception("Unknown error"));
            }
        });
    }

    // Update assignCameraToRenderer method signature to include device information
    private void assignCameraToRenderer(String joinerId, String joinerEmail, String deviceId, String deviceName) {
        Log.d(TAG, "Starting to assign joiner " + joinerId + " (email: " + joinerEmail + 
              ", device: " + (deviceName != null ? deviceName : "Unknown") + ") to a renderer");
        
        // Rest of your method remains mostly the same, but use the deviceId to ensure uniqueness
        // ...
    }

    /**
     * Dismiss the join request dialog if it's currently showing
     */
    private AlertDialog joinRequestDialog;

    private void dismissJoinRequestDialog() {
        if (joinRequestDialog != null && joinRequestDialog.isShowing()) {
            joinRequestDialog.dismiss();
            joinRequestDialog = null;
        }
    }

    /**
     * Assign a camera to an available renderer
     */
    private void assignCameraToRenderer(String joinerId, String joinerEmail) {
        Log.d(TAG, "Starting to assign joiner " + joinerId + " to a renderer");

        if (rtcHost == null) {
            Log.e(TAG, "rtcHost is null, cannot assign renderer");
            return;
        }

        // Get all active joiners
        String[] activeJoiners = rtcHost.getActiveJoiners();
        int activeCount = activeJoiners != null ? activeJoiners.length : 0;
        Log.d(TAG, "Active joiners count: " + activeCount);

        // First, check if this joiner already has a renderer assigned
        SurfaceViewRenderer existingRenderer = null;
        if (rtcHost.isRendererAssignedToJoiner(joinerId, feedView1)) {
            existingRenderer = feedView1;
        } else if (rtcHost.isRendererAssignedToJoiner(joinerId, feedView2)) {
            existingRenderer = feedView2;
        } else if (rtcHost.isRendererAssignedToJoiner(joinerId, feedView3)) {
            existingRenderer = feedView3;
        } else if (rtcHost.isRendererAssignedToJoiner(joinerId, feedView4)) {
            existingRenderer = feedView4;
        }

        if (existingRenderer != null) {
            Log.d(TAG, "Joiner " + joinerId + " already has a renderer assigned, skipping reassignment");
            return;
        }

        // Now find which position this joiner should get
        final int position;  // Make this final or effectively final for lambda

        // Check which renderers are already assigned
        boolean[] rendererAssigned = new boolean[4];

        // Check if each renderer is already assigned to any joiner
        for (String activeJoiner : activeJoiners) {
            if (rtcHost.isRendererAssignedToJoiner(activeJoiner, feedView1)) {
                rendererAssigned[0] = true;
            } else if (rtcHost.isRendererAssignedToJoiner(activeJoiner, feedView2)) {
                rendererAssigned[1] = true;
            } else if (rtcHost.isRendererAssignedToJoiner(activeJoiner, feedView3)) {
                rendererAssigned[2] = true;
            } else if (rtcHost.isRendererAssignedToJoiner(activeJoiner, feedView4)) {
                rendererAssigned[3] = true;
            }
        }

        // Find the first unassigned position
        int posTemp = -1;
        for (int i = 0; i < 4; i++) {
            if (!rendererAssigned[i]) {
                posTemp = i;
                break;
            }
        }

        // If all renderers are assigned (shouldn't happen), use position based on count
        if (posTemp == -1) {
            posTemp = Math.min(activeCount, 3); // Max 4 renderers (0-3)
        }

        position = posTemp;  // Assign to final position

        Log.d(TAG, "Assigning joiner " + joinerId + " to position " + position);

        // Now set the assigned_camera value in Firebase
        String userEmail = mAuth.getCurrentUser().getEmail().replace(".", "_");
        DatabaseReference requestRef = mDatabase.child("users")
                .child(userEmail)
                .child("sessions")
                .child(sessionId)
                .child("join_requests")
                .child(joinerId);

        // Send the camera position+1 to the joiner
        requestRef.child("assigned_camera").setValue(position + 1); // Position 0-3 to camera 1-4

        // Update grid layout based on the number of active cameras
        updateGridLayout(Math.max(activeCount, position + 1));

        // Assign renderer based on position
        SurfaceViewRenderer rendererToUse = null;
        View container = null;

        switch (position) {
            case 0:
                rendererToUse = feedView1;
                container = findViewById(R.id.feed_container_1);
                break;
            case 1:
                rendererToUse = feedView2;
                container = findViewById(R.id.feed_container_2);
                break;
            case 2:
                rendererToUse = feedView3;
                container = findViewById(R.id.feed_container_3);
                break;
            case 3:
                rendererToUse = feedView4;
                container = findViewById(R.id.feed_container_4);
                break;
        }

        if (rendererToUse != null && container != null) {
            // Ensure container is visible
            container.setVisibility(View.VISIBLE);

            // Initialize and assign renderer
            try {
                // First make sure renderer is released and reinitialized
                try {
                    rendererToUse.release();
                } catch (Exception e) {
                    // Ignore errors when releasing
                }

                // Initialize with EGL context from RTCHost
                if (rtcHost.getEglBaseContext() != null) {
                    rendererToUse.init(rtcHost.getEglBaseContext(), null);
                    rendererToUse.setEnableHardwareScaler(true);
                    rendererToUse.setMirror(false);
                    Log.d(TAG, "Renderer initialized successfully for position " + position);

                    // Assign renderer to joiner
                    rtcHost.assignRendererToJoiner(joinerId, rendererToUse);
                    initializedRenderers.put(rendererToUse, true);

                    Log.d(TAG, "Successfully assigned renderer at position " + position +
                            " to joiner " + joinerId);
                } else {
                    Log.e(TAG, "Failed to get EGL context from RTCHost");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error initializing renderer: " + e.getMessage(), e);
            }
        } else {
            Log.e(TAG, "No renderer available for position " + position);
        }

        // Add to the end of assignCameraToRenderer method
        if (rtcHost != null) {
            rtcHost.logRendererAssignments();
        }

        // After assigning the renderer, also update the camera number label
        switch (position) {
            case 0:
                TextView cameraNumber1 = cameraNumberLabels.get("feed1");
                if (cameraNumber1 != null) {
                    cameraNumber1.setText("Camera 1: " + joinerEmail);
                    cameraNumber1.setVisibility(View.VISIBLE);
                }
                TextView timestamp1 = cameraTimestamps.get("feed1");
                if (timestamp1 != null) {
                    timestamp1.setVisibility(View.VISIBLE);
                }
                break;
            case 1:
                TextView cameraNumber2 = cameraNumberLabels.get("feed2");
                if (cameraNumber2 != null) {
                    cameraNumber2.setText("Camera 2: " + joinerEmail);
                    cameraNumber2.setVisibility(View.VISIBLE);
                }
                TextView timestamp2 = cameraTimestamps.get("feed2");
                if (timestamp2 != null) {
                    timestamp2.setVisibility(View.VISIBLE);
                }
                break;
            case 2:
                TextView cameraNumber3 = cameraNumberLabels.get("feed3");
                if (cameraNumber3 != null) {
                    cameraNumber3.setText("Camera 3: " + joinerEmail);
                    cameraNumber3.setVisibility(View.VISIBLE);
                }
                TextView timestamp3 = cameraTimestamps.get("feed3");
                if (timestamp3 != null) {
                    timestamp3.setVisibility(View.VISIBLE);
                }
                break;
            case 3:
                TextView cameraNumber4 = cameraNumberLabels.get("feed4");
                if (cameraNumber4 != null) {
                    cameraNumber4.setText("Camera 4: " + joinerEmail);
                    cameraNumber4.setVisibility(View.VISIBLE);
                }
                TextView timestamp4 = cameraTimestamps.get("feed4");
                if (timestamp4 != null) {
                    timestamp4.setVisibility(View.VISIBLE);
                }
                break;
        }

        // In assignCameraToRenderer method in WatchSessionActivity
        String deviceName = rtcHost.getJoinerDeviceName(joinerId);
        String displayText = "Camera " + (position + 1) + ": " + joinerEmail;
        if (deviceName != null && !deviceName.isEmpty()) {
            displayText += " (" + deviceName + ")";
        }

        TextView cameraNumber = cameraNumberLabels.get("feed" + (position + 1));
        if (cameraNumber != null) {
            cameraNumber.setText(displayText);
            cameraNumber.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Find which renderer is assigned to a specific joiner
     */
    private SurfaceViewRenderer findRendererForJoiner(String joinerId) {
        // Check each renderer to see if it's assigned to this joiner
        if (rtcHost.isRendererAssignedToJoiner(joinerId, feedView1)) return feedView1;
        if (rtcHost.isRendererAssignedToJoiner(joinerId, feedView2)) return feedView2;
        if (rtcHost.isRendererAssignedToJoiner(joinerId, feedView3)) return feedView3;
        if (rtcHost.isRendererAssignedToJoiner(joinerId, feedView4)) return feedView4;
        return null;
    }

    // Helper method to ensure renderer container is visible
    private void ensureRendererVisible(String rendererKey) {
        View container = null;
        switch (rendererKey) {
            case "feed1":
                container = findViewById(R.id.feed_container_1);
                break;
            case "feed2":
                container = findViewById(R.id.feed_container_2);
                break;
            case "feed3":
                container = findViewById(R.id.feed_container_3);
                break;
            case "feed4":
                container = findViewById(R.id.feed_container_4);
                break;
        }

        if (container != null && container.getVisibility() != View.VISIBLE) {
            container.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Update the grid layout based on the number of active cameras
     */
    private void updateGridLayout(int cameraCount) {
        Log.d(TAG, "Updating grid layout for " + cameraCount + " cameras");

        // Get all feed containers
        View feedContainer1 = findViewById(R.id.feed_container_1);
        View feedContainer2 = findViewById(R.id.feed_container_2);
        View feedContainer3 = findViewById(R.id.feed_container_3);
        View feedContainer4 = findViewById(R.id.feed_container_4);

        if (feedContainer1 == null || feedContainer2 == null ||
                feedContainer3 == null || feedContainer4 == null) {
            Log.e(TAG, "One or more feed containers are null - layout problem");
            return;
        }

        // Get the constraint layout
        ConstraintLayout gridLayout = findViewById(R.id.grid_layout);
        if (gridLayout == null) {
            Log.e(TAG, "Grid layout not found");
            return;
        }

        // Create a new constraint set to establish layout rules
        ConstraintSet constraintSet = new ConstraintSet();
        constraintSet.clone(gridLayout);

        // First clear all existing constraints for the containers
        constraintSet.clear(R.id.feed_container_1);
        constraintSet.clear(R.id.feed_container_2);
        constraintSet.clear(R.id.feed_container_3);
        constraintSet.clear(R.id.feed_container_4);

        // Clear all click listeners before setting new ones
        feedContainer1.setOnClickListener(null);
        feedContainer2.setOnClickListener(null);
        feedContainer3.setOnClickListener(null);
        feedContainer4.setOnClickListener(null);

        // Check if we're in focused mode
        if (focusedCamera != -1 && focusedCamera < cameraCount) {
            // Show only the focused camera, hide others
            feedContainer1.setVisibility(focusedCamera == 0 ? View.VISIBLE : View.GONE);
            feedContainer2.setVisibility(focusedCamera == 1 ? View.VISIBLE : View.GONE);
            feedContainer3.setVisibility(focusedCamera == 2 ? View.VISIBLE : View.GONE);
            feedContainer4.setVisibility(focusedCamera == 3 ? View.VISIBLE : View.GONE);

            // Get the focused container
            View focusedContainer = null;
            switch (focusedCamera) {
                case 0:
                    focusedContainer = feedContainer1;
                    break;
                case 1:
                    focusedContainer = feedContainer2;
                    break;
                case 2:
                    focusedContainer = feedContainer3;
                    break;
                case 3:
                    focusedContainer = feedContainer4;
                    break;
            }

            if (focusedContainer != null) {
                // Full screen constraints for focused camera
                constraintSet.connect(focusedContainer.getId(), ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START);
                constraintSet.connect(focusedContainer.getId(), ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP);
                constraintSet.connect(focusedContainer.getId(), ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END);
                constraintSet.connect(focusedContainer.getId(), ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM);
            }
        } else {
            // Set visibility for normal grid display
            feedContainer1.setVisibility(cameraCount >= 1 ? View.VISIBLE : View.GONE);
            feedContainer2.setVisibility(cameraCount >= 2 ? View.VISIBLE : View.GONE);
            feedContainer3.setVisibility(cameraCount >= 3 ? View.VISIBLE : View.GONE);
            feedContainer4.setVisibility(cameraCount >= 4 ? View.VISIBLE : View.GONE);

            Log.d(TAG, "Container visibility set: " +
                    "1=" + (feedContainer1.getVisibility() == View.VISIBLE ? "visible" : "gone") + ", " +
                    "2=" + (feedContainer2.getVisibility() == View.VISIBLE ? "visible" : "gone") + ", " +
                    "3=" + (feedContainer3.getVisibility() == View.VISIBLE ? "visible" : "gone") + ", " +
                    "4=" + (feedContainer4.getVisibility() == View.VISIBLE ? "visible" : "gone"));

            // Create constraints based on camera count
            switch (cameraCount) {
                case 1:
                    // Single camera - full screen
                    constraintSet.connect(R.id.feed_container_1, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START);
                    constraintSet.connect(R.id.feed_container_1, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP);
                    constraintSet.connect(R.id.feed_container_1, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END);
                    constraintSet.connect(R.id.feed_container_1, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM);

                    // Add click listener for focus mode
                    feedContainer1.setOnClickListener(v -> {
                        showFocusHint(feedContainer1);
                        focusedCamera = 0;
                        updateGridLayout(cameraCount);
                    });
                    break;

                case 2:
                    // Two cameras - top and bottom grid (vertical layout)

                    // Set up container 1 (top)
                    constraintSet.connect(R.id.feed_container_1, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START);
                    constraintSet.connect(R.id.feed_container_1, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP);
                    constraintSet.connect(R.id.feed_container_1, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END);
                    constraintSet.connect(R.id.feed_container_1, ConstraintSet.BOTTOM, R.id.feed_container_2, ConstraintSet.TOP);

                    // Set up container 2 (bottom)
                    constraintSet.connect(R.id.feed_container_2, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START);
                    constraintSet.connect(R.id.feed_container_2, ConstraintSet.TOP, R.id.feed_container_1, ConstraintSet.BOTTOM);
                    constraintSet.connect(R.id.feed_container_2, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END);
                    constraintSet.connect(R.id.feed_container_2, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM);

                    // Make them equal height
                    constraintSet.setVerticalWeight(R.id.feed_container_1, 1);
                    constraintSet.setVerticalWeight(R.id.feed_container_2, 1);

                    // Add click listeners for focus mode
                    feedContainer1.setOnClickListener(v -> {
                        showFocusHint(feedContainer1);
                        focusedCamera = 0;
                        updateGridLayout(cameraCount);
                    });

                    feedContainer2.setOnClickListener(v -> {
                        showFocusHint(feedContainer2);
                        focusedCamera = 1;
                        updateGridLayout(cameraCount);
                    });
                    break;

                case 3:
                    // Three cameras - 2x2 grid with one larger cell

                    // Top left
                    constraintSet.connect(R.id.feed_container_1, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START);
                    constraintSet.connect(R.id.feed_container_1, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP);
                    constraintSet.connect(R.id.feed_container_1, ConstraintSet.END, R.id.feed_container_2, ConstraintSet.START);
                    constraintSet.connect(R.id.feed_container_1, ConstraintSet.BOTTOM, R.id.feed_container_3, ConstraintSet.TOP);

                    // Top right
                    constraintSet.connect(R.id.feed_container_2, ConstraintSet.START, R.id.feed_container_1, ConstraintSet.END);
                    constraintSet.connect(R.id.feed_container_2, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP);
                    constraintSet.connect(R.id.feed_container_2, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END);
                    constraintSet.connect(R.id.feed_container_2, ConstraintSet.BOTTOM, R.id.feed_container_3, ConstraintSet.TOP);

                    // Bottom (full width)
                    constraintSet.connect(R.id.feed_container_3, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START);
                    constraintSet.connect(R.id.feed_container_3, ConstraintSet.TOP, R.id.feed_container_1, ConstraintSet.BOTTOM);
                    constraintSet.connect(R.id.feed_container_3, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END);
                    constraintSet.connect(R.id.feed_container_3, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM);

                    // Make the top two equal width
                    constraintSet.createHorizontalChain(
                            ConstraintSet.PARENT_ID, ConstraintSet.LEFT,
                            ConstraintSet.PARENT_ID, ConstraintSet.RIGHT,
                            new int[]{R.id.feed_container_1, R.id.feed_container_2},
                            new float[]{1, 1},
                            ConstraintSet.CHAIN_PACKED
                    );

                    // Equal height distribution (50% top row, 50% bottom row)
                    constraintSet.setVerticalWeight(R.id.feed_container_1, 1);
                    constraintSet.setVerticalWeight(R.id.feed_container_3, 1);

                    // Add click listeners for focus mode
                    feedContainer1.setOnClickListener(v -> {
                        showFocusHint(feedContainer1);
                        focusedCamera = 0;
                        updateGridLayout(cameraCount);
                    });

                    feedContainer2.setOnClickListener(v -> {
                        showFocusHint(feedContainer2);
                        focusedCamera = 1;
                        updateGridLayout(cameraCount);
                    });

                    feedContainer3.setOnClickListener(v -> {
                        showFocusHint(feedContainer3);
                        focusedCamera = 2;
                        updateGridLayout(cameraCount);
                    });
                    break;

                case 4:
                    // Four cameras - 2x2 grid

                    // Top left
                    constraintSet.connect(R.id.feed_container_1, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START);
                    constraintSet.connect(R.id.feed_container_1, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP);
                    constraintSet.connect(R.id.feed_container_1, ConstraintSet.END, R.id.feed_container_2, ConstraintSet.START);
                    constraintSet.connect(R.id.feed_container_1, ConstraintSet.BOTTOM, R.id.feed_container_3, ConstraintSet.TOP);

                    // Top right
                    constraintSet.connect(R.id.feed_container_2, ConstraintSet.START, R.id.feed_container_1, ConstraintSet.END);
                    constraintSet.connect(R.id.feed_container_2, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP);
                    constraintSet.connect(R.id.feed_container_2, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END);
                    constraintSet.connect(R.id.feed_container_2, ConstraintSet.BOTTOM, R.id.feed_container_4, ConstraintSet.TOP);

                    // Bottom left
                    constraintSet.connect(R.id.feed_container_3, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START);
                    constraintSet.connect(R.id.feed_container_3, ConstraintSet.TOP, R.id.feed_container_1, ConstraintSet.BOTTOM);
                    constraintSet.connect(R.id.feed_container_3, ConstraintSet.END, R.id.feed_container_4, ConstraintSet.START);
                    constraintSet.connect(R.id.feed_container_3, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM);

                    // Bottom right
                    constraintSet.connect(R.id.feed_container_4, ConstraintSet.START, R.id.feed_container_3, ConstraintSet.END);
                    constraintSet.connect(R.id.feed_container_4, ConstraintSet.TOP, R.id.feed_container_2, ConstraintSet.BOTTOM);
                    constraintSet.connect(R.id.feed_container_4, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END);
                    constraintSet.connect(R.id.feed_container_4, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM);

                    // Equal width for both columns
                    constraintSet.createHorizontalChain(
                            ConstraintSet.PARENT_ID, ConstraintSet.LEFT,
                            ConstraintSet.PARENT_ID, ConstraintSet.RIGHT,
                            new int[]{R.id.feed_container_1, R.id.feed_container_2},
                            new float[]{1, 1},
                            ConstraintSet.CHAIN_PACKED
                    );

                    constraintSet.createHorizontalChain(
                            ConstraintSet.PARENT_ID, ConstraintSet.LEFT,
                            ConstraintSet.PARENT_ID, ConstraintSet.RIGHT,
                            new int[]{R.id.feed_container_3, R.id.feed_container_4},
                            new float[]{1, 1},
                            ConstraintSet.CHAIN_PACKED
                    );

                    // Equal height for both rows
                    constraintSet.setVerticalWeight(R.id.feed_container_1, 1);
                    constraintSet.setVerticalWeight(R.id.feed_container_3, 1);

                    // Add click listeners for focus mode
                    feedContainer1.setOnClickListener(v -> {
                        showFocusHint(feedContainer1);
                        focusedCamera = 0;
                        updateGridLayout(cameraCount);
                    });

                    feedContainer2.setOnClickListener(v -> {
                        showFocusHint(feedContainer2);
                        focusedCamera = 1;
                        updateGridLayout(cameraCount);
                    });

                    feedContainer3.setOnClickListener(v -> {
                        showFocusHint(feedContainer3);
                        focusedCamera = 2;
                        updateGridLayout(cameraCount);
                    });

                    feedContainer4.setOnClickListener(v -> {
                        showFocusHint(feedContainer4);
                        focusedCamera = 3;
                        updateGridLayout(cameraCount);
                    });
                    break;
            }
        }

        // Show "No cameras connected" message if needed
        View noCamerasMessage = findViewById(R.id.no_cameras_message);
        if (noCamerasMessage != null) {
            noCamerasMessage.setVisibility(cameraCount == 0 ? View.VISIBLE : View.GONE);
        }

        // Add margins between containers (8dp)
        int margin = (int) (8 * getResources().getDisplayMetrics().density);
        constraintSet.setMargin(R.id.feed_container_1, ConstraintSet.START, margin);
        constraintSet.setMargin(R.id.feed_container_1, ConstraintSet.TOP, margin);
        constraintSet.setMargin(R.id.feed_container_1, ConstraintSet.END, margin);
        constraintSet.setMargin(R.id.feed_container_1, ConstraintSet.BOTTOM, margin);

        constraintSet.setMargin(R.id.feed_container_2, ConstraintSet.START, margin);
        constraintSet.setMargin(R.id.feed_container_2, ConstraintSet.TOP, margin);
        constraintSet.setMargin(R.id.feed_container_2, ConstraintSet.END, margin);
        constraintSet.setMargin(R.id.feed_container_2, ConstraintSet.BOTTOM, margin);

        constraintSet.setMargin(R.id.feed_container_3, ConstraintSet.START, margin);
        constraintSet.setMargin(R.id.feed_container_3, ConstraintSet.TOP, margin);
        constraintSet.setMargin(R.id.feed_container_3, ConstraintSet.END, margin);
        constraintSet.setMargin(R.id.feed_container_3, ConstraintSet.BOTTOM, margin);

        constraintSet.setMargin(R.id.feed_container_4, ConstraintSet.START, margin);
        constraintSet.setMargin(R.id.feed_container_4, ConstraintSet.TOP, margin);
        constraintSet.setMargin(R.id.feed_container_4, ConstraintSet.END, margin);
        constraintSet.setMargin(R.id.feed_container_4, ConstraintSet.BOTTOM, margin);

        // Apply the constraints
        constraintSet.applyTo(gridLayout);

        // Make sure to initialize renderers that should be visible
        if ((focusedCamera == 0 || focusedCamera == -1) && cameraCount >= 1) {
            initializeRenderer(feedView1);
        }
        if ((focusedCamera == 1 || focusedCamera == -1) && cameraCount >= 2) {
            initializeRenderer(feedView2);
        }
        if ((focusedCamera == 2 || focusedCamera == -1) && cameraCount >= 3) {
            initializeRenderer(feedView3);
        }
        if ((focusedCamera == 3 || focusedCamera == -1) && cameraCount >= 4) {
            initializeRenderer(feedView4);
        }

        // Show/hide back button based on focus state
        FloatingActionButton backButton = findViewById(R.id.back_to_grid_button);
        if (backButton != null) {
            backButton.setVisibility(focusedCamera != -1 ? View.VISIBLE : View.GONE);
            backButton.setOnClickListener(v -> {
                // Exit focus mode
                focusedCamera = -1;
                updateGridLayout(totalConnectedCameras);
            });
        }

        Log.d(TAG, "Grid layout updated for " + cameraCount + " cameras with focus mode: " +
                (focusedCamera == -1 ? "none" : focusedCamera));
    }

    // Then modify the initializeRenderer method
    private void initializeRenderer(SurfaceViewRenderer renderer) {
        if (renderer != null && rtcHost != null) {
            try {
                // Make sure we have an EglBase context
                EglBase.Context eglContext = rtcHost.getEglBaseContext();
                if (eglContext == null) {
                    Log.e(TAG, "EglBase context is null, cannot initialize renderer");
                    return;
                }

                // First check if already initialized to avoid re-initialization
                try {
                    // Use release and re-init pattern to ensure clean state
                    renderer.release();
                    Log.d(TAG, "Released renderer before initialization");
                } catch (Exception e) {
                    // Ignore release errors - might not be initialized yet
                    Log.d(TAG, "Renderer was not previously initialized or error releasing: " + e.getMessage());
                }

                // Initialize with EglBase context
                renderer.init(eglContext, null);
                renderer.setEnableHardwareScaler(true);
                renderer.setMirror(false);

                // Mark as initialized
                initializedRenderers.put(renderer, true);
                Log.d(TAG, "Renderer initialized successfully with context: " + eglContext);
            } catch (Exception e) {
                Log.e(TAG, "Error initializing renderer: " + e.getMessage(), e);
                initializedRenderers.put(renderer, false);
            }
        } else {
            Log.e(TAG, "Cannot initialize null renderer or rtcHost is null");
        }
    }

    /**
     * Reject a join request
     */
    private void rejectJoinRequest(String requestId) {
        String userEmail = mAuth.getCurrentUser().getEmail().replace(".", "_");
        mDatabase.child("users").child(userEmail).child("sessions").child(sessionId)
                .child("join_requests").child(requestId).removeValue()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(this, "Request rejected", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Failed to reject request", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    /**
     * Show a focus animation hint
     */
    private void showFocusHint(View container) {
        // Create a temporary overlay for the focus animation
        View focusOverlay = new View(this);
        focusOverlay.setBackgroundColor(ContextCompat.getColor(this, R.color.white));
        focusOverlay.setAlpha(0.3f);

        if (container instanceof FrameLayout) {
            // Add the overlay to the container
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
            );
            ((FrameLayout) container).addView(focusOverlay, params);

            // Animate the overlay
            focusOverlay.animate()
                    .alpha(0f)
                    .setDuration(300)
                    .withEndAction(() -> {
                        // Remove the overlay when animation is done
                        ((FrameLayout) container).removeView(focusOverlay);
                    })
                    .start();
        }
    }

    // Add this method to start regular timestamp updates
    private void startTimestampUpdates() {
        // Stop any existing updates
        if (timestampUpdateRunnable != null) {
            timestampUpdateHandler.removeCallbacks(timestampUpdateRunnable);
        }

        // Create a new update runnable
        timestampUpdateRunnable = new Runnable() {
            @Override
            public void run() {
                updateAllTimestamps();
                // Schedule next update in 1 second
                timestampUpdateHandler.postDelayed(this, 1000);
            }
        };

        // Start updates
        timestampUpdateHandler.post(timestampUpdateRunnable);
    }

    // Method to update all timestamps
    private void updateAllTimestamps() {
        String timeString = getFormattedTime();
        for (TextView timestampView : cameraTimestamps.values()) {
            if (timestampView != null && timestampView.getVisibility() == View.VISIBLE) {
                timestampView.setText(timeString);
            }
        }
    }

    // Helper method to get formatted time
    private String getFormattedTime() {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault());
        return sdf.format(new java.util.Date());
    }

    /**
     * Show session information dialog
     */
    private void showSessionInfoDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_session_info, null);
        builder.setView(dialogView);
        
        // Find views in the dialog
        TextView sessionTitleView = dialogView.findViewById(R.id.dialog_session_title);
        TextView sessionPasskeyView = dialogView.findViewById(R.id.dialog_session_passkey);
        LinearLayout connectedCamerasContainer = dialogView.findViewById(R.id.connected_cameras_container);
        Button copyPasskeyButton = dialogView.findViewById(R.id.copy_passkey_button);
        Button closeButton = dialogView.findViewById(R.id.close_button);
        
        // Get the tab layout and panels
        TabLayout tabLayout = dialogView.findViewById(R.id.tab_layout);
        View sessionInfoPanel = dialogView.findViewById(R.id.session_info_panel);
        View camerasPanel = dialogView.findViewById(R.id.cameras_panel);
        TextView cameraCountBadge = dialogView.findViewById(R.id.camera_count_badge);
        TextView sessionCameraCount = dialogView.findViewById(R.id.session_camera_count);
        TextView noCamerasMessage = dialogView.findViewById(R.id.no_cameras_message);
        TextView sessionDuration = dialogView.findViewById(R.id.session_duration);
        
        // Set session name as title
        sessionTitleView.setText(sessionName);
        
        // Calculate session duration
        String userEmail = mAuth.getCurrentUser().getEmail().replace(".", "_");
        mDatabase.child("users").child(userEmail).child("sessions").child(sessionId)
                .child("created_at").get().addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null && task.getResult().exists()) {
                        Long createdAt = task.getResult().getValue(Long.class);
                        if (createdAt != null) {
                            long duration = (System.currentTimeMillis() - createdAt) / 1000; // in seconds
                            long minutes = duration / 60;
                            long seconds = duration % 60;
                            sessionDuration.setText(String.format("%02d:%02d", minutes, seconds));
                        }
                    }
                });
        
        // Set up tab selection listener
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                if (tab.getPosition() == 0) {
                    // Show session info panel
                    sessionInfoPanel.setVisibility(View.VISIBLE);
                    camerasPanel.setVisibility(View.GONE);
                } else {
                    // Show cameras panel
                    sessionInfoPanel.setVisibility(View.GONE);
                    camerasPanel.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
                // Not needed
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                // Not needed
            }
        });
        
        // Find the passkey section container
        LinearLayout passkeySection = dialogView.findViewById(R.id.passkey_section);
        
        // Fetch session passkey and connected cameras from Firebase
        mDatabase.child("users").child(userEmail).child("sessions").child(sessionId)
                .get().addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        // Get session passkey
                        String passkey = task.getResult().child("session_code").getValue(String.class);
                        
                        // Debug the passkey value
                        Log.d(TAG, "Retrieved session passkey: " + (passkey != null ? passkey : "null"));
                        
                        if (passkey != null && !passkey.isEmpty()) {
                            sessionPasskeyView.setText(passkey);
                            passkeySection.setVisibility(View.VISIBLE);
                            
                            // Set up copy button
                            copyPasskeyButton.setOnClickListener(v -> {
                                // Copy to clipboard
                                android.content.ClipboardManager clipboard = 
                                    (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                                android.content.ClipData clip = 
                                    android.content.ClipData.newPlainText("Session Passkey", passkey);
                                clipboard.setPrimaryClip(clip);
                                Toast.makeText(WatchSessionActivity.this, 
                                    "Session code copied to clipboard", Toast.LENGTH_SHORT).show();
                            });
                        } else {
                            // Hide the entire passkey section if no passkey is available
                            passkeySection.setVisibility(View.GONE);
                        }
                        
                        // Get connected cameras
                        DataSnapshot connectedCamerasSnapshot = task.getResult().child("join_requests");
                        int cameraCount = 0;
                        
                        if (connectedCamerasSnapshot.exists()) {
                            connectedCamerasContainer.removeAllViews(); // Clear existing views
                            
                            for (DataSnapshot cameraSnapshot : connectedCamerasSnapshot.getChildren()) {
                                String status = cameraSnapshot.child("status").getValue(String.class);
                                if ("accepted".equals(status)) {
                                    cameraCount++;
                                    String email = cameraSnapshot.child("email").getValue(String.class);
                                    String deviceName = cameraSnapshot.child("deviceName").getValue(String.class);
                                    
                                    if (email != null) {
                                        // Create a view for this camera
                                        View cameraItemView = LayoutInflater.from(WatchSessionActivity.this)
                                            .inflate(R.layout.item_connected_camera, null);
                                        
                                        TextView cameraEmailView = cameraItemView.findViewById(R.id.camera_email);
                                        TextView cameraDeviceView = cameraItemView.findViewById(R.id.camera_device);
                                        
                                        cameraEmailView.setText(email);
                                        if (deviceName != null && !deviceName.isEmpty()) {
                                            cameraDeviceView.setText(deviceName);
                                            cameraDeviceView.setVisibility(View.VISIBLE);
                                        } else {
                                            cameraDeviceView.setVisibility(View.GONE);
                                        }
                                        
                                        connectedCamerasContainer.addView(cameraItemView);
                                    }
                                }
                            }
                            
                            // Update camera count
                            sessionCameraCount.setText(String.valueOf(cameraCount));
                            cameraCountBadge.setText(String.valueOf(cameraCount));
                            
                            // Show empty state if needed
                            if (cameraCount == 0) {
                                noCamerasMessage.setVisibility(View.VISIBLE);
                                connectedCamerasContainer.setVisibility(View.GONE);
                            } else {
                                noCamerasMessage.setVisibility(View.GONE);
                                connectedCamerasContainer.setVisibility(View.VISIBLE);
                            }
                        } else {
                            // No cameras connected
                            sessionCameraCount.setText("0");
                            cameraCountBadge.setText("0");
                            noCamerasMessage.setVisibility(View.VISIBLE);
                            connectedCamerasContainer.setVisibility(View.GONE);
                        }
                    }
                });
    
        AlertDialog dialog = builder.create();
    
        // Set up close button
        closeButton.setOnClickListener(v -> dialog.dismiss());
    
        // Show the dialog
        dialog.show();
    }
}
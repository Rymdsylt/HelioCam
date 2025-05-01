package com.summersoft.heliocam.ui;

import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
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
import java.util.Map;

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
    private void setupUI() {
        // Initialize UI components
        MaterialButton endSessionButton = findViewById(R.id.end_session_button);
        MaterialButton micButton = findViewById(R.id.microphone_button);
        joinRequestNotification = findViewById(R.id.join_request_notification);
        
        // Initially hide the join request notification
        if (joinRequestNotification != null) {
            joinRequestNotification.setVisibility(View.GONE);
        }

        // End session button
        if (endSessionButton != null) {
            endSessionButton.setOnClickListener(v -> {
                showEndSessionConfirmation();
            });
        }
        
        // Mic button to toggle audio
        if (micButton != null) {
            micButton.setOnClickListener(v -> {
                toggleAudio();
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
        
        // Add camera off message views for each feed
        View feedContainer1 = findViewById(R.id.feed_container_1);
        if (feedContainer1 != null) {
            TextView offMsg1 = feedContainer1.findViewById(R.id.camera_off_message_1);
            TextView micStatus1 = feedContainer1.findViewById(R.id.mic_status_1);
            if (offMsg1 != null) cameraDisabledMessages.put("feed1", offMsg1);
            if (micStatus1 != null) micStatusMessages.put("feed1", micStatus1);
        }
        
        // Repeat for other feed containers
        View feedContainer2 = findViewById(R.id.feed_container_2);
        if (feedContainer2 != null) {
            TextView offMsg2 = feedContainer2.findViewById(R.id.camera_off_message_2);
            TextView micStatus2 = feedContainer2.findViewById(R.id.mic_status_2);
            if (offMsg2 != null) cameraDisabledMessages.put("feed2", offMsg2);
            if (micStatus2 != null) micStatusMessages.put("feed2", micStatus2);
        }
        
        View feedContainer3 = findViewById(R.id.feed_container_3);
        if (feedContainer3 != null) {
            TextView offMsg3 = feedContainer3.findViewById(R.id.camera_off_message_3);
            TextView micStatus3 = feedContainer3.findViewById(R.id.mic_status_3);
            if (offMsg3 != null) cameraDisabledMessages.put("feed3", offMsg3);
            if (micStatus3 != null) micStatusMessages.put("feed3", micStatus3);
        }
        
        View feedContainer4 = findViewById(R.id.feed_container_4);
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

        participantsCount.setOnClickListener(v -> {
            // Show detailed participant list when count is clicked
            showParticipantListDialog();
        });
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
                                    if (nameView != null) nameView.setText(email != null ? email : "Unknown");
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
                // Show notification if there are pending requests
                boolean hasRequests = dataSnapshot.exists() && dataSnapshot.getChildrenCount() > 0;
                if (joinRequestNotification != null) {
                    joinRequestNotification.setVisibility(hasRequests && !ignoreJoinRequests ? View.VISIBLE : View.GONE);
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
                        updateParticipantCount((int)count);
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
                        totalConnectedCameras = (int)count;
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
                        activeCamerasCount = (int)count;
                        
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
        
        // Update button appearance
        MaterialButton micButton = findViewById(R.id.microphone_button);
        if (micButton != null) {
            micButton.setIconResource(isAudioEnabled ?
                    R.drawable.ic_baseline_mic_24 : R.drawable.ic_baseline_mic_off_24);
        }
        
        // Update RTCHost
        if (rtcHost != null) {
            if (isAudioEnabled) {
                rtcHost.unmuteMic();
            } else {
                rtcHost.muteMic();
            }
        }
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
                                
                                for (DataSnapshot requestSnapshot : task.getResult().getChildren()) {
                                    String requestId = requestSnapshot.getKey();
                                    String requestEmail = requestSnapshot.child("email").getValue(String.class);
                                    
                                    // Skip requests that already have status "accepted"
                                    String status = requestSnapshot.child("status").getValue(String.class);
                                    if ("accepted".equals(status)) continue;
                                    
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
                
        // First get the joiner's email
        requestRef.child("email").get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult() != null) {
                String joinerEmail = task.getResult().getValue(String.class);
                
                if (joinerEmail != null) {
                    Log.d(TAG, "Found joiner email: " + joinerEmail + " for request: " + requestId);
                    
                    // Create a map for the update with all needed fields
                    Map<String, Object> updates = new HashMap<>();
                    updates.put("status", "accepted");
                    updates.put("accepted_at", System.currentTimeMillis());
                    
                    // Update the request status
                    requestRef.updateChildren(updates)
                        .addOnCompleteListener(updateTask -> {
                            if (updateTask.isSuccessful()) {
                                Log.d(TAG, "Join request successfully accepted");
                                Toast.makeText(WatchSessionActivity.this, 
                                    "Request accepted. Camera joining...", Toast.LENGTH_SHORT).show();
                                
                                // Create peer connection AND assign to a renderer based on active count
                                if (rtcHost != null) {
                                    rtcHost.createPeerConnection(requestId);
                                    Log.d(TAG, "Created peer connection for joiner: " + requestId);
                                    
                                    // Get active joiners and assign renderer based on count
                                    assignCameraToRenderer(requestId, joinerEmail);
                                }
                                
                                // Close the dialog if open
                                dismissJoinRequestDialog();
                            } else {
                                Log.e(TAG, "Failed to accept join request", updateTask.getException());
                                Toast.makeText(WatchSessionActivity.this, 
                                    "Failed to accept request", Toast.LENGTH_SHORT).show();
                            }
                        });
                } else {
                    Log.e(TAG, "Join request email is null");
                    Toast.makeText(WatchSessionActivity.this, 
                        "Failed to get joiner information", Toast.LENGTH_SHORT).show();
                }
            } else {
                Log.e(TAG, "Failed to get joiner email", task.getException());
                Toast.makeText(WatchSessionActivity.this, 
                    "Failed to get joiner information", Toast.LENGTH_SHORT).show();
            }
        });
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
        int position = -1;
        
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
        for (int i = 0; i < 4; i++) {
            if (!rendererAssigned[i]) {
                position = i;
                break;
            }
        }
        
        // If all renderers are assigned (shouldn't happen), use position based on count
        if (position == -1) {
            position = Math.min(activeCount, 3); // Max 4 renderers (0-3)
        }
        
        Log.d(TAG, "Assigning joiner " + joinerId + " to position " + position);
        
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
        
        // Set visibility for all containers
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
                break;
                
            case 2:
                // Two cameras - side by side (horizontal layout)
                
                // First camera - left half
                constraintSet.connect(R.id.feed_container_1, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START);
                constraintSet.connect(R.id.feed_container_1, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP);
                constraintSet.connect(R.id.feed_container_1, ConstraintSet.END, R.id.feed_container_2, ConstraintSet.START);
                constraintSet.connect(R.id.feed_container_1, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM);
                
                // Second camera - right half
                constraintSet.connect(R.id.feed_container_2, ConstraintSet.START, R.id.feed_container_1, ConstraintSet.END);
                constraintSet.connect(R.id.feed_container_2, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP);
                constraintSet.connect(R.id.feed_container_2, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END);
                constraintSet.connect(R.id.feed_container_2, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM);
                
                // Make them equal width
                constraintSet.createHorizontalChain(
                    ConstraintSet.PARENT_ID, ConstraintSet.LEFT,
                    ConstraintSet.PARENT_ID, ConstraintSet.RIGHT,
                    new int[]{R.id.feed_container_1, R.id.feed_container_2},
                    new float[]{1, 1},
                    ConstraintSet.CHAIN_PACKED
                );
                break;
                
            case 3:
                // Three cameras - 2 on top, 1 on bottom
                
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
                
                // Bottom (centered)
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
                
                // Height distribution - top row 50%, bottom row 50%
                constraintSet.setVerticalWeight(R.id.feed_container_1, 1);
                constraintSet.setVerticalWeight(R.id.feed_container_3, 1);
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
                
                // Make rows and columns equal
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
                
                // Equal height for rows
                constraintSet.setVerticalWeight(R.id.feed_container_1, 1);
                constraintSet.setVerticalWeight(R.id.feed_container_3, 1);
                break;
        }

        // Add this inside the updateGridLayout method, after the switch statement
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
        if (cameraCount >= 1) {
            initializeRenderer(feedView1);
        }
        if (cameraCount >= 2) {
            initializeRenderer(feedView2);
        }
        if (cameraCount >= 3) {
            initializeRenderer(feedView3);
        }
        if (cameraCount >= 4) {
            initializeRenderer(feedView4);
        }
        
        Log.d(TAG, "Grid layout updated for " + cameraCount + " cameras");
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


}
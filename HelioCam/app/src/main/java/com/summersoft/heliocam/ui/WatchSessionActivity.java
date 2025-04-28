package com.summersoft.heliocam.ui;

import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.summersoft.heliocam.R;
import com.summersoft.heliocam.webrtc.SfuManager;
import com.summersoft.heliocam.webrtc_utils.RTCHost;

import org.webrtc.EglBase;
import org.webrtc.PeerConnection;
import org.webrtc.SurfaceViewRenderer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WatchSessionActivity extends AppCompatActivity {

    private static final String TAG = "WatchSessionActivity";

    // UI Components
    private TextView cameraDisabledMessage;
    private TextView participantsCountTextView;
    private ConstraintLayout gridLayout;
    private FloatingActionButton settingsButton;
    private MaterialButton micButton;

    // SurfaceViewRenderers for up to 4 participants
    private SurfaceViewRenderer feedView1;
    private SurfaceViewRenderer feedView2;
    private SurfaceViewRenderer feedView3;
    private SurfaceViewRenderer feedView4;
    private List<SurfaceViewRenderer> renderers = new ArrayList<>();

    // WebRTC
    private EglBase eglBase;
    private RTCHost rtcHost;
    private Map<String, RTCHost> participantHosts = new HashMap<>();
    private DatabaseReference mDatabase;

    // Session info
    private String sessionKey;
    private String sessionName;

    // State
    private boolean isMicOn = true;
    private boolean ignoreRequests = false;
    private int currentParticipantCount = 0;
    private AlertDialog dialog;

    private Handler handler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_watch_session);

        // Initialize UI components and SurfaceViewRenderers first
        initializeUIWithoutRenderers();

        // Now it's safe to use feedView1 and others
        eglBase = EglBase.create();
        feedView1.init(eglBase.getEglBaseContext(), null);
        feedView1.setMirror(false);
        Log.d(TAG, "EglBase created successfully");

        // Get session information from intent
        sessionKey = getIntent().getStringExtra("SESSION_KEY");
        sessionName = getIntent().getStringExtra("SESSION_NAME");
        Log.d(TAG, "Starting session: " + sessionName + " with key: " + sessionKey);

        // Initialize UI components
        initializeUIWithoutRenderers();
        Log.d(TAG, "UI initialized without renderers");

        // Initialize renderers with EglBase context
        for (SurfaceViewRenderer renderer : renderers) {
            try {
                renderer.init(eglBase.getEglBaseContext(), null);
                renderer.setEnableHardwareScaler(true);
                renderer.setMirror(false);
                Log.d(TAG, "Renderer initialized successfully");
            } catch (IllegalStateException e) {
                Log.d(TAG, "Renderer already initialized: " + e.getMessage());
            }
        }

        // Initialize database reference
        mDatabase = FirebaseDatabase.getInstance().getReference();

        // Create the RTCHost instance with the first renderer
        rtcHost = new RTCHost(this, sessionKey, feedView1, mDatabase);
        Log.d(TAG, "RTCHost created for session: " + sessionKey);

        // Add remote track listener with more robust handling
        rtcHost.addRemoteTrackListener(videoTrack -> {
            Log.d(TAG, "Remote track received in WatchSessionActivity listener");
            runOnUiThread(() -> {
                if (feedView1 != null && videoTrack != null) {
                    Log.d(TAG, "Attaching remote track to feedView1");
                    // Force renderer to be visible
                    feedView1.setVisibility(View.VISIBLE);
                    // Make sure track has a sink
                    videoTrack.addSink(feedView1);
                    // Force layout update
                    gridLayout.requestLayout();
                    feedView1.requestLayout();
                } else {
                    Log.e(TAG, "Failed to attach track: feedView1=" + (feedView1 != null) +
                            ", videoTrack=" + (videoTrack != null));
                }
            });
        });

        // Create and set up the WebRTC session
        rtcHost.createSession();
        Log.d(TAG, "WebRTC session created");

        // Explicitly update renderer settings for better visibility
        feedView1.setZOrderMediaOverlay(false);
        feedView1.setEnableHardwareScaler(true);

        // Set up event listeners
        setupEventListeners();
        Log.d(TAG, "Event listeners set up");

        // When scheduling a delayed task
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                checkConnectionState();
                handler.postDelayed(this, 10000);
            }
        }, 10000);

    }

    // Add at the end of onCreate() or in onResume()
    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume called");

        // Make sure the renderer is explicitly visible
        if (feedView1 != null) {
            feedView1.setVisibility(View.VISIBLE);
        }

        // Force the peer connection to be created if it doesn't exist yet
        if (rtcHost != null) {
            if (rtcHost.sfuManager != null && rtcHost.sfuManager.peerConnection == null) {
                Log.d(TAG, "Re-creating peer connection in onResume");
                rtcHost.createSession();
            }
            rtcHost.ensureRemoteVideoTrackIsAttached(feedView1);
        }
    }
    private void initializeUIWithoutRenderers() {
        // Initialize main UI components
        cameraDisabledMessage = findViewById(R.id.camera_disabled_message);
        participantsCountTextView = findViewById(R.id.participants_count);
        gridLayout = findViewById(R.id.grid_layout);
        micButton = (MaterialButton) findViewById(R.id.microphone_button);
        settingsButton = findViewById(R.id.settings_button);

        // Set the title if available
        TextView sessionTitleView = findViewById(R.id.session_title);
        if (sessionTitleView != null && sessionName != null) {
            sessionTitleView.setText(sessionName);
        }

        // Find SurfaceViewRenderers
        feedView1 = findViewById(R.id.feed_view_1);
        feedView2 = findViewById(R.id.feed_view_2);
        feedView3 = findViewById(R.id.feed_view_3);
        feedView4 = findViewById(R.id.feed_view_4);

        // Initialize renderers list
        renderers.add(feedView1);
        renderers.add(feedView2);
        renderers.add(feedView3);
        renderers.add(feedView4);
    }

    private void initializeRenderers() {
        // Make the first view visible initially
        feedView1.setVisibility(View.VISIBLE);
        for (int i = 1; i < renderers.size(); i++) {
            renderers.get(i).setVisibility(View.GONE);
        }

        // Set the initial grid layout
        updateGridLayout(1);
    }

    private void initializeUI() {
        // Initialize main UI components
        cameraDisabledMessage = findViewById(R.id.camera_disabled_message);
        participantsCountTextView = findViewById(R.id.participants_count);
        gridLayout = findViewById(R.id.grid_layout);
        micButton = (MaterialButton) findViewById(R.id.microphone_button);
        settingsButton = findViewById(R.id.settings_button);

        // Set the title if available
        TextView sessionTitleView = findViewById(R.id.session_title);
        if (sessionTitleView != null && sessionName != null) {
            sessionTitleView.setText(sessionName);
        }

        // Initialize SurfaceViewRenderers
        feedView1 = findViewById(R.id.feed_view_1);
        feedView2 = findViewById(R.id.feed_view_2);
        feedView3 = findViewById(R.id.feed_view_3);
        feedView4 = findViewById(R.id.feed_view_4);

        // Initialize renderers list
        renderers.add(feedView1);
        renderers.add(feedView2);
        renderers.add(feedView3);
        renderers.add(feedView4);


        // Initialize each renderer with the EGL context
        for (SurfaceViewRenderer renderer : renderers) {
            renderer.init(eglBase.getEglBaseContext(), null);
            renderer.setEnableHardwareScaler(true);
            renderer.setMirror(false);
            renderer.setZOrderMediaOverlay(false);
        }

        // Make the first view visible initially
        feedView1.setVisibility(View.VISIBLE);
        for (int i = 1; i < renderers.size(); i++) {
            renderers.get(i).setVisibility(View.GONE);
        }

        // Set the initial grid layout
        updateGridLayout(1);
    }

    private void setupEventListeners() {
        // Microphone button listener
        micButton.setOnClickListener(v -> toggleMic());

        // Settings button listener
        settingsButton.setOnClickListener(v -> showSettingsDialog());

        // Firebase listeners
        listenForCameraStatus(sessionKey);
        listenForMicStatus(sessionKey);
        listenForJoinRequests(sessionKey);
        listenForParticipantCount(sessionKey);
    }

    private void listenForParticipantCount(String sessionKey) {
        String userEmail = FirebaseAuth.getInstance().getCurrentUser().getEmail().replace(".", "_");

        mDatabase.child("users").child(userEmail).child("sessions").child(sessionKey)
                .child("participants")
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        if (dataSnapshot.exists()) {
                            int count = (int) dataSnapshot.getChildrenCount();
                            updateParticipantCount(count);
                        } else {
                            updateParticipantCount(0);
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {
                        Log.e(TAG, "Failed to listen for participants: " + databaseError.getMessage());
                    }
                });
    }

    private void updateParticipantCount(int count) {
        // Limit to maximum 4 participants
        int actualCount = Math.min(count, 4);
        currentParticipantCount = actualCount;

        // Update the UI
        runOnUiThread(() -> {
            participantsCountTextView.setText(getString(R.string.participants_count, actualCount));
            updateGridLayout(actualCount);

            // Create additional RTCHost instances for each participant as needed
            for (int i = 1; i < actualCount; i++) {
                final int index = i;
                if (!participantHosts.containsKey("participant_" + i)) {
                    RTCHost newHost = new RTCHost(
                            WatchSessionActivity.this,
                            sessionKey + "_" + i,
                            renderers.get(i),
                            mDatabase
                    );
                    participantHosts.put("participant_" + i, newHost);
                    newHost.createSession();
                }
            }
        });
    }

    private void updateGridLayout(int participantCount) {
        // Hide all views first
        for (SurfaceViewRenderer renderer : renderers) {
            renderer.setVisibility(View.GONE);
        }

        // Show only the required number of views
        for (int i = 0; i < participantCount && i < renderers.size(); i++) {
            renderers.get(i).setVisibility(View.VISIBLE);
        }

        // Update layout constraints based on count
        ConstraintLayout.LayoutParams params1, params2, params3, params4;

        switch (participantCount) {
            case 1:
                // Single view takes up full space
                params1 = (ConstraintLayout.LayoutParams) feedView1.getLayoutParams();
                params1.width = ConstraintLayout.LayoutParams.MATCH_PARENT;
                params1.height = ConstraintLayout.LayoutParams.MATCH_PARENT;
                params1.topToTop = ConstraintLayout.LayoutParams.PARENT_ID;
                params1.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID;
                params1.startToStart = ConstraintLayout.LayoutParams.PARENT_ID;
                params1.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID;
                feedView1.setLayoutParams(params1);
                break;

            case 2:
                // Two views split vertically
                params1 = (ConstraintLayout.LayoutParams) feedView1.getLayoutParams();
                params1.width = ConstraintLayout.LayoutParams.MATCH_PARENT;
                params1.height = 0;
                params1.topToTop = ConstraintLayout.LayoutParams.PARENT_ID;
                params1.bottomToTop = R.id.feed_view_2;
                params1.startToStart = ConstraintLayout.LayoutParams.PARENT_ID;
                params1.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID;
                feedView1.setLayoutParams(params1);

                params2 = (ConstraintLayout.LayoutParams) feedView2.getLayoutParams();
                params2.width = ConstraintLayout.LayoutParams.MATCH_PARENT;
                params2.height = 0;
                params2.topToBottom = R.id.feed_view_1;
                params2.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID;
                params2.startToStart = ConstraintLayout.LayoutParams.PARENT_ID;
                params2.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID;
                feedView2.setLayoutParams(params2);
                break;

            case 3:
                // First view on top, two below in columns
                params1 = (ConstraintLayout.LayoutParams) feedView1.getLayoutParams();
                params1.width = ConstraintLayout.LayoutParams.MATCH_PARENT;
                params1.height = 0;
                params1.topToTop = ConstraintLayout.LayoutParams.PARENT_ID;
                params1.bottomToTop = R.id.feed_view_2;
                params1.startToStart = ConstraintLayout.LayoutParams.PARENT_ID;
                params1.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID;
                feedView1.setLayoutParams(params1);

                params2 = (ConstraintLayout.LayoutParams) feedView2.getLayoutParams();
                params2.width = 0;
                params2.height = 0;
                params2.topToBottom = R.id.feed_view_1;
                params2.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID;
                params2.startToStart = ConstraintLayout.LayoutParams.PARENT_ID;
                params2.endToStart = R.id.feed_view_3;
                feedView2.setLayoutParams(params2);

                params3 = (ConstraintLayout.LayoutParams) feedView3.getLayoutParams();
                params3.width = 0;
                params3.height = 0;
                params3.topToBottom = R.id.feed_view_1;
                params3.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID;
                params3.startToEnd = R.id.feed_view_2;
                params3.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID;
                feedView3.setLayoutParams(params3);
                break;

            case 4:
                // Grid of four views
                params1 = (ConstraintLayout.LayoutParams) feedView1.getLayoutParams();
                params1.width = 0;
                params1.height = 0;
                params1.topToTop = ConstraintLayout.LayoutParams.PARENT_ID;
                params1.bottomToTop = R.id.feed_view_3;
                params1.startToStart = ConstraintLayout.LayoutParams.PARENT_ID;
                params1.endToStart = R.id.feed_view_2;
                feedView1.setLayoutParams(params1);

                params2 = (ConstraintLayout.LayoutParams) feedView2.getLayoutParams();
                params2.width = 0;
                params2.height = 0;
                params2.topToTop = ConstraintLayout.LayoutParams.PARENT_ID;
                params2.bottomToTop = R.id.feed_view_4;
                params2.startToEnd = R.id.feed_view_1;
                params2.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID;
                feedView2.setLayoutParams(params2);

                params3 = (ConstraintLayout.LayoutParams) feedView3.getLayoutParams();
                params3.width = 0;
                params3.height = 0;
                params3.topToBottom = R.id.feed_view_1;
                params3.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID;
                params3.startToStart = ConstraintLayout.LayoutParams.PARENT_ID;
                params3.endToStart = R.id.feed_view_4;
                feedView3.setLayoutParams(params3);

                params4 = (ConstraintLayout.LayoutParams) feedView4.getLayoutParams();
                params4.width = 0;
                params4.height = 0;
                params4.topToBottom = R.id.feed_view_2;
                params4.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID;
                params4.startToEnd = R.id.feed_view_3;
                params4.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID;
                feedView4.setLayoutParams(params4);
                break;
        }

        // Notify rtcHost of layout change if needed
        if (rtcHost != null) {
            rtcHost.updateRendererCount(participantCount, renderers);
        }
    }

    private void toggleMic() {
        isMicOn = !isMicOn;

        if (isMicOn) {
            rtcHost.unmuteMic();
            // Use setIcon instead of setImageDrawable for MaterialButton
            micButton.setIcon(ContextCompat.getDrawable(this, R.drawable.ic_baseline_mic_24));
        } else {
            rtcHost.muteMic();
            // Use setIcon instead of setImageDrawable for MaterialButton
            micButton.setIcon(ContextCompat.getDrawable(this, R.drawable.ic_baseline_mic_off_24));
        }
    }

    private void showSettingsDialog() {
        // Create and show Material Design bottom sheet dialog for settings
        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_session_settings, null);
        bottomSheetDialog.setContentView(dialogView);

        // Set up dialog buttons
        dialogView.findViewById(R.id.btn_mute_all).setOnClickListener(v -> {
            toggleMuteAllParticipants();
            bottomSheetDialog.dismiss();
        });

        dialogView.findViewById(R.id.btn_end_session).setOnClickListener(v -> {
            confirmEndSession();
            bottomSheetDialog.dismiss();
        });

        dialogView.findViewById(R.id.btn_record).setOnClickListener(v -> {
            toggleRecording();
            bottomSheetDialog.dismiss();
        });

        bottomSheetDialog.show();
    }

    private void confirmEndSession() {
        new AlertDialog.Builder(this)
                .setTitle("End Session")
                .setMessage("Are you sure you want to end this session for all participants?")
                .setPositiveButton("End Session", (dialog, which) -> endSession())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void endSession() {
        String userEmail = FirebaseAuth.getInstance().getCurrentUser().getEmail().replace(".", "_");

        mDatabase.child("users").child(userEmail).child("sessions").child(sessionKey)
                .child("end_session").setValue(1)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(this, "Session ended", Toast.LENGTH_SHORT).show();
                        finish();
                    } else {
                        Toast.makeText(this, "Failed to end session", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void toggleMuteAllParticipants() {
        String userEmail = FirebaseAuth.getInstance().getCurrentUser().getEmail().replace(".", "_");

        mDatabase.child("users").child(userEmail).child("sessions").child(sessionKey)
                .child("mute_all").setValue(1)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(this, "All participants muted", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Failed to mute participants", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void toggleRecording() {
        // This would connect to your recording functionality
        Toast.makeText(this, "Recording feature not implemented", Toast.LENGTH_SHORT).show();
    }

    private void listenForCameraStatus(String sessionKey) {
        String userEmail = FirebaseAuth.getInstance().getCurrentUser().getEmail().replace(".", "_");

        mDatabase.child("users").child(userEmail).child("sessions").child(sessionKey)
                .child("camera_enabled")
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        if (dataSnapshot.exists()) {
                            boolean cameraEnabled = dataSnapshot.getValue(Integer.class) == 1;
                            runOnUiThread(() -> {
                                cameraDisabledMessage.setVisibility(cameraEnabled ? View.GONE : View.VISIBLE);
                            });
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {
                        Log.e(TAG, "Failed to listen for camera status: " + databaseError.getMessage());
                    }
                });
    }

    private void listenForMicStatus(String sessionKey) {
        String userEmail = FirebaseAuth.getInstance().getCurrentUser().getEmail().replace(".", "_");

        mDatabase.child("users").child(userEmail).child("sessions").child(sessionKey)
                .child("mic_enabled")
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        if (dataSnapshot.exists()) {
                            boolean micEnabled = dataSnapshot.getValue(Integer.class) == 1;
                            TextView micStatusMessage = findViewById(R.id.mic_status_message);
                            runOnUiThread(() -> {
                                micStatusMessage.setVisibility(micEnabled ? View.GONE : View.VISIBLE);
                            });
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {
                        Log.e(TAG, "Failed to listen for mic status: " + databaseError.getMessage());
                    }
                });
    }


    private void listenForJoinRequests(String sessionKey) {
        String userEmail = FirebaseAuth.getInstance().getCurrentUser().getEmail().replace(".", "_");

        mDatabase.child("users").child(userEmail).child("sessions").child(sessionKey)
                .child("want_join")
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        // Only show join request dialog if not already showing and not ignoring requests
                        if (dataSnapshot.exists() && dataSnapshot.getValue(Integer.class) == 1
                                && !ignoreRequests && (dialog == null || !dialog.isShowing())) {
                            showJoinRequestDialog(sessionKey);
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {
                        Log.e(TAG, "Failed to listen for join requests: " + databaseError.getMessage());
                    }
                });

        // Also listen for active connections to update renderer attachments
        mDatabase.child("users").child(userEmail).child("sessions").child(sessionKey)
                .child("active_connections")
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        if (dataSnapshot.exists()) {
                            // Get list of active connections
                            int index = 0;
                            for (DataSnapshot connection : dataSnapshot.getChildren()) {
                                if (index < renderers.size()) {
                                    String connectionId = connection.getKey();
                                    ensureRendererForConnection(connectionId, index);
                                    index++;
                                }
                            }
                            updateGridLayout(Math.max(1, index));
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {
                        Log.e(TAG, "Failed to listen for active connections: " + databaseError.getMessage());
                    }
                });
    }
    // Add this new method to properly set up renderers for connections:
    private void ensureRendererForConnection(String connectionId, int index) {
        SurfaceViewRenderer renderer = renderers.get(index);

        // Make sure renderer is initialized with EGL context
        try {
            renderer.init(eglBase.getEglBaseContext(), null);
            renderer.setEnableHardwareScaler(true);
            renderer.setMirror(false);
            renderer.setZOrderMediaOverlay(false);
        } catch (IllegalStateException e) {
            // Already initialized - just log and continue
            Log.d(TAG, "Renderer already initialized: " + e.getMessage());
        }

        // Create host for this connection if it doesn't exist
        if (!participantHosts.containsKey(connectionId)) {
            RTCHost newHost = new RTCHost(
                    WatchSessionActivity.this,
                    sessionKey + "_" + connectionId,
                    renderer,
                    mDatabase
            );
            participantHosts.put(connectionId, newHost);
            newHost.createSession();

            // Make sure we attach remote track listeners
            newHost.addRemoteTrackListener(videoTrack -> {
                runOnUiThread(() -> {
                    videoTrack.addSink(renderer);
                });
            });
        }
    }

    private void showJoinRequestDialog(String sessionKey) {
        if (isFinishing()) return;

        runOnUiThread(() -> {
            View dialogView = getLayoutInflater().inflate(R.layout.dialog_join_request, null);
            CheckBox ignoreCheckbox = dialogView.findViewById(R.id.ignore_checkbox);

            dialog = new AlertDialog.Builder(this)
                    .setTitle("Join Request")
                    .setMessage("Someone wants to join your session.")
                    .setView(dialogView)
                    .setPositiveButton("Allow", (d, which) -> {
                        allowJoinRequest(sessionKey);
                        if (ignoreCheckbox.isChecked()) {
                            ignoreRequests = true;
                            resetIgnoreFlagAfterDelay();
                        }
                        deleteWantJoinStatus();
                    })
                    .setNegativeButton("Deny", (d, which) -> {
                        if (ignoreCheckbox.isChecked()) {
                            ignoreRequests = true;
                            resetIgnoreFlagAfterDelay();
                        }
                        deleteWantJoinStatus();
                    })
                    .setCancelable(false)
                    .create();

            dialog.show();
        });
    }

    // Then modify allowJoinRequest to track connections:
    private void allowJoinRequest(String sessionKey) {
        String userEmail = FirebaseAuth.getInstance().getCurrentUser().getEmail().replace(".", "_");

        // Set the join request as approved
        mDatabase.child("users").child(userEmail).child("sessions").child(sessionKey)
                .child("join_approved").setValue(1);

        // Add connection to active_connections list with timestamp
        String connectionId = "connection_" + System.currentTimeMillis();
        mDatabase.child("users").child(userEmail).child("sessions").child(sessionKey)
                .child("active_connections").child(connectionId).setValue(System.currentTimeMillis());

        Toast.makeText(this, "Join request approved", Toast.LENGTH_SHORT).show();
    }

    private void deleteWantJoinStatus() {
        String userEmail = FirebaseAuth.getInstance().getCurrentUser().getEmail().replace(".", "_");

        if (sessionKey != null) {
            mDatabase.child("users").child(userEmail).child("sessions").child(sessionKey)
                    .child("want_join").removeValue();
        }
    }

    private void resetIgnoreFlagAfterDelay() {
        new Handler().postDelayed(() -> {
            ignoreRequests = false;
            if (dialog != null && dialog.isShowing()) {
                dialog.dismiss();
            }
        }, 60000); // 1 minute
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Clean up renderers
        for (SurfaceViewRenderer renderer : renderers) {
            renderer.release();
        }

        // Dispose of RTCHost
        if (rtcHost != null) {
            rtcHost.dispose();
        }

        // Dispose all participant hosts
        for (RTCHost host : participantHosts.values()) {
            if (host != null) {
                host.dispose();
            }
        }
        participantHosts.clear();

        // Release EGL context
        if (eglBase != null) {
            eglBase.release();
        }

        // Clean up Firebase
        deleteWantJoinStatus();
    }
    // Add to WatchSessionActivity.java
    private void checkConnectionState() {
        if (rtcHost != null && rtcHost.sfuManager != null && rtcHost.sfuManager.peerConnection != null) {
            PeerConnection.IceConnectionState state = rtcHost.sfuManager.peerConnection.iceConnectionState();
            Log.d(TAG, "ICE Connection state: " + state);

            // If we're in a disconnected state for too long, try reconnecting
            if (state == PeerConnection.IceConnectionState.DISCONNECTED ||
                    state == PeerConnection.IceConnectionState.FAILED) {
                Log.d(TAG, "Connection appears to be failing, attempting to recreate session");
                rtcHost.createSession();
            }
        } else {
            Log.d(TAG, "PeerConnection not yet established");
        }
    }

}
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
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_watch_session);
        
        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();
        
        // Initialize UI components
        initializeUI();
        
        // Get session information from intent
        sessionId = getIntent().getStringExtra("session_id");
        sessionName = getIntent().getStringExtra("session_name");
        
        if (sessionId == null || sessionId.isEmpty()) {
            Toast.makeText(this, "Invalid session ID", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        sessionTitle.setText(sessionName != null ? sessionName : "Session");
        
        // Initialize RTCHost with main feed view
        rtcHost = new RTCHost(this, feedView1, mDatabase);
        
        // Create the session to receive camera connections
        if (sessionId != null) {
            // Use existing session ID if provided
            rtcHost.createSession(sessionName != null ? sessionName : "Viewing Session");
        } else {
            // Create a new session with a generated ID
            sessionId = rtcHost.createSession(sessionName != null ? sessionName : "Viewing Session");
        }
        
        // Set window insets
        ViewCompat.setOnApplyWindowInsetsListener(gridLayout, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        
        // Listen for join requests
        listenForJoinRequests();
        
        // Periodically update the UI based on connected cameras
        updateConnectedCamerasUI();
        
        // Update connection status
        participantsCount.setText("Participants: 0");
    }

    private void initializeUI() {
        // Initialize UI components
        sessionTitle = findViewById(R.id.session_title);
        participantsCount = findViewById(R.id.participants_count);
        feedView1 = findViewById(R.id.feed_view_1);
        feedView2 = findViewById(R.id.feed_view_2);
        feedView3 = findViewById(R.id.feed_view_3);
        feedView4 = findViewById(R.id.feed_view_4);
        gridLayout = findViewById(R.id.grid_layout);
        joinRequestNotification = findViewById(R.id.join_request_notification);
        
        // Initialize the feed views
        EglBase eglBase = EglBase.create();
        
        // Initialize primary feed view
        feedView1.init(eglBase.getEglBaseContext(), null);
        feedView1.setMirror(false);
        
        // Initialize secondary feed views (initially invisible)
        feedView2.init(eglBase.getEglBaseContext(), null);
        feedView2.setMirror(false);
        feedView2.setVisibility(View.GONE);
        
        feedView3.init(eglBase.getEglBaseContext(), null);
        feedView3.setMirror(false);
        feedView3.setVisibility(View.GONE);
        
        feedView4.init(eglBase.getEglBaseContext(), null);
        feedView4.setMirror(false);
        feedView4.setVisibility(View.GONE);
        
        // Initialize audio button
        MaterialButton microphoneButton = findViewById(R.id.microphone_button);
        microphoneButton.setOnClickListener(v -> toggleAudio());
        
        // Initialize settings button
        FloatingActionButton settingsButton = findViewById(R.id.settings_button);
        settingsButton.setOnClickListener(v -> showSettingsMenu());
        
        // Initialize camera status message
        TextView cameraDisabledMessage = findViewById(R.id.camera_disabled_message);
        cameraDisabledMessage.setVisibility(View.GONE);
        
        // Initialize microphone status message
        TextView micStatusMessage = findViewById(R.id.mic_status_message);
        micStatusMessage.setVisibility(View.GONE);
        
        // Hide join request notification initially
        joinRequestNotification.setVisibility(View.GONE);
    }
    
    private void showSettingsMenu() {
        // Create settings menu options
        String[] options = {"End Session", "Show Session Info", "View Statistics"};
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Settings");
        builder.setItems(options, (dialog, which) -> {
            switch (which) {
                case 0: // End Session
                    confirmEndSession();
                    break;
                case 1: // Show Session Info
                    showSessionInfo();
                    break;
                case 2: // View Statistics
                    showStatistics();
                    break;
            }
        });
        builder.show();
    }
    
    private void confirmEndSession() {
        new AlertDialog.Builder(this)
                .setTitle("End Session")
                .setMessage("Closing will end the session for all cameras. Continue?")
                .setPositiveButton("Yes", (dialog, which) -> {
                    cleanup();
                    finish();
                })
                .setNegativeButton("No", (dialog, which) -> dialog.dismiss())
                .setCancelable(true)
                .create()
                .show();
    }
    
    private void showSessionInfo() {
        String info = "Session ID: " + sessionId + "\n" +
                "Session Name: " + sessionName + "\n" +
                "Your Email: " + mAuth.getCurrentUser().getEmail();
                
        new AlertDialog.Builder(this)
                .setTitle("Session Information")
                .setMessage(info)
                .setPositiveButton("Copy ID", (dialog, which) -> {
                    // Copy session ID to clipboard
                    android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    android.content.ClipData clip = android.content.ClipData.newPlainText("Session ID", sessionId);
                    clipboard.setPrimaryClip(clip);
                    Toast.makeText(this, "Session ID copied to clipboard", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Close", (dialog, which) -> dialog.dismiss())
                .show();
    }
    
    private void showStatistics() {
        String[] activeJoiners = rtcHost != null ? rtcHost.getActiveJoiners() : new String[0];
        
        String stats = "Connected cameras: " + activeJoiners.length + "\n" +
                "Session duration: " + getSessionDuration() + "\n" +
                "Network status: Good";
                
        new AlertDialog.Builder(this)
                .setTitle("Session Statistics")
                .setMessage(stats)
                .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
                .show();
    }
    
    private String getSessionDuration() {
        // Calculate session duration logic here
        return "00:30:45"; // Placeholder
    }

    private void toggleAudio() {
        if (rtcHost != null) {
            isAudioEnabled = !isAudioEnabled;
            rtcHost.toggleAudio();
            
            // Update UI
            MaterialButton microphoneButton = findViewById(R.id.microphone_button);
            microphoneButton.setIconTint(getColorStateList(
                    isAudioEnabled ? R.color.white : R.color.red));
            
            Toast.makeText(this, isAudioEnabled ? "Audio enabled" : "Audio disabled", Toast.LENGTH_SHORT).show();
        }
    }

    private void listenForJoinRequests() {
        String userEmail = mAuth.getCurrentUser().getEmail().replace(".", "_");
        
        joinRequestsListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (ignoreJoinRequests) return;
                
                if (dataSnapshot.exists() && dataSnapshot.getChildrenCount() > 0) {
                    // Show join request notification
                    showJoinRequestNotification(dataSnapshot);
                } else {
                    // Hide join request notification
                    joinRequestNotification.setVisibility(View.GONE);
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.e(TAG, "Failed to listen for join requests: " + databaseError.getMessage());
            }
        };
        
        mDatabase.child("users")
                .child(userEmail)
                .child("sessions")
                .child(sessionId)
                .child("join_requests")
                .addValueEventListener(joinRequestsListener);
    }
    
    private void showJoinRequestNotification(DataSnapshot joinRequests) {
        // Get the first join request
        DataSnapshot firstRequest = joinRequests.getChildren().iterator().next();
        String joinerId = firstRequest.getKey();
        String joinerEmail = firstRequest.child("email").getValue(String.class);
        
        if (joinerId != null && joinerEmail != null) {
            joinRequestNotification.setVisibility(View.VISIBLE);
            
            MaterialButton allowButton = joinRequestNotification.findViewById(R.id.allow_join_button);
            allowButton.setOnClickListener(v -> {
                acceptJoinRequest(joinerId, joinerEmail);
                joinRequestNotification.setVisibility(View.GONE);
            });
        }
    }

    private void showJoinRequestDialog(String joinerId, String joinerEmail) {
        if (isFinishing()) return;

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_join_request, null);
        CheckBox ignoreCheckbox = dialogView.findViewById(R.id.ignore_checkbox);
        
        new AlertDialog.Builder(this)
                .setTitle("Camera Join Request")
                .setMessage("Camera " + joinerEmail + " wants to join. Accept?")
                .setView(dialogView)
                .setPositiveButton("Accept", (dialog, which) -> {
                    // Accept the camera join request
                    acceptJoinRequest(joinerId, joinerEmail);
                    
                    // Set ignore flag if checked
                    if (ignoreCheckbox.isChecked()) {
                        ignoreJoinRequests = true;
                        resetIgnoreFlagAfterDelay();
                    }
                })
                .setNegativeButton("Decline", (dialog, which) -> {
                    // Decline the camera join request
                    declineJoinRequest(joinerId);
                    
                    // Set ignore flag if checked
                    if (ignoreCheckbox.isChecked()) {
                        ignoreJoinRequests = true;
                        resetIgnoreFlagAfterDelay();
                    }
                })
                .setCancelable(false)
                .show();
    }

    private void acceptJoinRequest(String joinerId, String joinerEmail) {
        // Get number of active connections
        String[] activeJoiners = rtcHost.getActiveJoiners();
        int activeCount = activeJoiners.length;
        
        // Determine which feed view to use
        SurfaceViewRenderer targetView;
        switch (activeCount) {
            case 0:
                targetView = feedView1;
                break;
            case 1:
                targetView = feedView2;
                feedView2.setVisibility(View.VISIBLE);
                break;
            case 2:
                targetView = feedView3;
                feedView3.setVisibility(View.VISIBLE);
                break;
            case 3:
                targetView = feedView4;
                feedView4.setVisibility(View.VISIBLE);
                break;
            default:
                Toast.makeText(this, "Maximum cameras reached (4)", Toast.LENGTH_SHORT).show();
                return;
        }
        
        // Assign the renderer to the joiner
        rtcHost.assignRendererToJoiner(joinerId, targetView);
        
        // Listen for camera status
        listenForCameraStatus(joinerId, joinerEmail);
        
        // Update join request status in Firebase
        String userEmail = mAuth.getCurrentUser().getEmail().replace(".", "_");
        
        mDatabase.child("users")
                .child(userEmail)
                .child("sessions")
                .child(sessionId)
                .child("join_requests")
                .child(joinerId)
                .child("accepted")
                .setValue(true);
                
        // Update connection status
        updateConnectionStatus();
    }

    private void declineJoinRequest(String joinerId) {
        // Remove join request from Firebase
        String userEmail = mAuth.getCurrentUser().getEmail().replace(".", "_");
        
        mDatabase.child("users")
                .child(userEmail)
                .child("sessions")
                .child(sessionId)
                .child("join_requests")
                .child(joinerId)
                .removeValue();
    }

    private void listenForCameraStatus(String cameraId, String cameraEmail) {
        // Format email for Firebase path
        String formattedEmail = cameraEmail.replace(".", "_");
        
        // Listen for camera_off status
        mDatabase.child("users")
                .child(formattedEmail)
                .child("sessions")
                .child(sessionId)
                .child("camera_off")
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        if (dataSnapshot.exists()) {
                            Integer cameraOffFlag = dataSnapshot.getValue(Integer.class);
                            if (cameraOffFlag != null && cameraOffFlag == 1) {
                                // Camera is off
                                TextView cameraOffMessage = cameraDisabledMessages.get(cameraId);
                                if (cameraOffMessage != null) {
                                    cameraOffMessage.setVisibility(View.VISIBLE);
                                }
                            }
                        } else {
                            // Camera is on
                            TextView cameraOffMessage = cameraDisabledMessages.get(cameraId);
                            if (cameraOffMessage != null) {
                                cameraOffMessage.setVisibility(View.GONE);
                            }
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {
                        Log.e(TAG, "Failed to listen for camera status: " + databaseError.getMessage());
                    }
                });
                
        // Listen for mic_on status
        mDatabase.child("users")
                .child(formattedEmail)
                .child("sessions")
                .child(sessionId)
                .child("mic_on")
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        if (dataSnapshot.exists()) {
                            Integer micOnFlag = dataSnapshot.getValue(Integer.class);
                            if (micOnFlag != null && micOnFlag == 0) {
                                // Mic is off
                                TextView micOffMessage = micStatusMessages.get(cameraId);
                                if (micOffMessage != null) {
                                    micOffMessage.setVisibility(View.VISIBLE);
                                }
                            } else {
                                // Mic is on
                                TextView micOffMessage = micStatusMessages.get(cameraId);
                                if (micOffMessage != null) {
                                    micOffMessage.setVisibility(View.GONE);
                                }
                            }
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {
                        Log.e(TAG, "Failed to listen for mic status: " + databaseError.getMessage());
                    }
                });
    }

    private void updateConnectedCamerasUI() {
        Handler handler = new Handler(Looper.getMainLooper());
        Runnable updateRunnable = new Runnable() {
            @Override
            public void run() {
                if (isFinishing() || rtcHost == null) return;
                
                // Get active joiners from RTCHost
                String[] activeJoiners = rtcHost.getActiveJoiners();
                
                // Update connection status
                updateConnectionStatus(activeJoiners.length);
                
                // Schedule next update
                handler.postDelayed(this, 5000);
            }
        };
        
        // Start periodic updates
        handler.postDelayed(updateRunnable, 2000);
    }

    private void updateConnectionStatus() {
        String[] activeJoiners = rtcHost != null ? rtcHost.getActiveJoiners() : new String[0];
        updateConnectionStatus(activeJoiners.length);
    }
    
    private void updateConnectionStatus(int cameraCount) {
        if (participantsCount != null) {
            participantsCount.setText("Participants: " + cameraCount);
        }
    }

    private void resetIgnoreFlagAfterDelay() {
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            ignoreJoinRequests = false;
        }, 60000); // Reset ignore flag after 60 seconds
    }



    @Override
    protected void onDestroy() {
        cleanup();
        super.onDestroy();
    }
    
    private void cleanup() {
        // Remove Firebase listeners
        if (joinRequestsListener != null) {
            String userEmail = mAuth.getCurrentUser().getEmail().replace(".", "_");
            mDatabase.child("users")
                    .child(userEmail)
                    .child("sessions")
                    .child(sessionId)
                    .child("join_requests")
                    .removeEventListener(joinRequestsListener);
        }
        
        // Clean up RTCHost
        if (rtcHost != null) {
            rtcHost.dispose();
            rtcHost = null;
        }
    }
    

}
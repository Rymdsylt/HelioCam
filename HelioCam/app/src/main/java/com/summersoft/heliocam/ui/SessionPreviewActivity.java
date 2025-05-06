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

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.summersoft.heliocam.R;
import com.summersoft.heliocam.webrtc_utils.RTCJoiner;

import org.webrtc.SurfaceViewRenderer;

public class SessionPreviewActivity extends AppCompatActivity {
    private static final String TAG = "SessionPreviewActivity";

    // Firebase components
    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;
    
    // Session information
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
    
    // Listeners
    private ValueEventListener sessionListener;
    
    // State tracking
    private boolean resourcesReleased = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_session_preview);
        
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
        
        Log.d(TAG, "Session ID: " + sessionId);
        Log.d(TAG, "Session Name: " + sessionName);
        Log.d(TAG, "Host Email: " + hostEmail);
        
        // Update session name in UI
        if (sessionName != null && !sessionName.isEmpty()) {
            sessionNameText.setText(sessionName);
        } else {
            sessionNameText.setText("Unnamed Session");
        }
        
        // Initialize WebRTC preview - now as a camera (joiner)
        initializeCameraPreview();
        
        // Verify session exists
        verifySession();
    }
    
    private void initializeUI() {
        // Initialize UI components
        sessionNameText = findViewById(R.id.session_name_text);
        connectionStatus = findViewById(R.id.connection_status);
        connectionBadge = findViewById(R.id.connection_badge);
        joinButton = findViewById(R.id.join_button);
        ImageButton backButton = findViewById(R.id.back_button);
        previewRenderer = findViewById(R.id.preview_renderer);
        
        // Initially disable join button until connection is established
        joinButton.setEnabled(false);
        
        // Set click listeners
        backButton.setOnClickListener(v -> finish());
        joinButton.setOnClickListener(v -> joinSessionAsCamera());
    }
    
    private void initializeCameraPreview() {
        if (sessionId == null || sessionId.isEmpty() || hostEmail == null || hostEmail.isEmpty()) {
            Log.e(TAG, "Cannot initialize preview: missing session information");
            connectionStatus.setText("Missing session information");
            connectionBadge.setText("Error");
            connectionBadge.setBackgroundTintList(
                    ContextCompat.getColorStateList(this, R.color.red));
            return;
        }
        
        // Create RTCJoiner for camera preview
        rtcJoiner = new RTCJoiner(this, previewRenderer, mDatabase);
        
        // Start local camera to show preview
        rtcJoiner.startCamera(true);
        
        connectionStatus.setText("Camera preview initialized");
    }
    
    private void verifySession() {
        if (sessionId == null || sessionId.isEmpty() || hostEmail == null || hostEmail.isEmpty()) {
            Log.e(TAG, "Cannot verify session: missing session information");
            return;
        }
        
        // Update UI to show connecting state
        connectionStatus.setText("Verifying session...");
        connectionBadge.setText("Checking");
        
        // Format host email for Firebase path
        String formattedHostEmail = hostEmail.replace(".", "_");
        
        // Reference to the host's session
        DatabaseReference sessionRef = mDatabase.child("users")
                .child(formattedHostEmail)
                .child("sessions")
                .child(sessionId);
                
        // Listen for session data
        sessionListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (isFinishing() || resourcesReleased) return;
                
                if (dataSnapshot.exists()) {
                    // Session exists
                    String name = dataSnapshot.child("session_name").getValue(String.class);
                    if (name != null && !name.isEmpty()) {
                        sessionName = name;
                        sessionNameText.setText(name);
                    }
                    
                    // Check if session is active
                    Boolean isActive = dataSnapshot.child("active").getValue(Boolean.class);
                    if (isActive != null && isActive) {
                        // Session is active, enable join button
                        connectionStatus.setText("Session is active and ready to join");
                        connectionBadge.setText("Ready");
                        connectionBadge.setBackgroundTintList(
                                ContextCompat.getColorStateList(SessionPreviewActivity.this, R.color.green));
                        joinButton.setEnabled(true);
                    } else {
                        // Session is not active
                        connectionStatus.setText("Session is not active");
                        connectionBadge.setText("Inactive");
                        connectionBadge.setBackgroundTintList(
                                ContextCompat.getColorStateList(SessionPreviewActivity.this, R.color.orange));
                        joinButton.setEnabled(false);
                    }
                } else {
                    // Session doesn't exist
                    connectionStatus.setText("Session not found");
                    connectionBadge.setText("Not found");
                    connectionBadge.setBackgroundTintList(
                            ContextCompat.getColorStateList(SessionPreviewActivity.this, R.color.red));
                    joinButton.setEnabled(false);
                }
            }
            
            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.e(TAG, "Database error: " + databaseError.getMessage());
                connectionStatus.setText("Connection error");
                connectionBadge.setText("Error");
                connectionBadge.setBackgroundTintList(
                        ContextCompat.getColorStateList(SessionPreviewActivity.this, R.color.red));
                joinButton.setEnabled(false);
            }
        };
        
        // Add the listener
        sessionRef.addValueEventListener(sessionListener);
    }
    
    private void joinSessionAsCamera() {
        if (hostEmail == null || sessionId == null) {
            Toast.makeText(this, "Missing session information", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Update UI
        connectionStatus.setText("Joining session as camera...");
        connectionBadge.setText("Joining");
        joinButton.setEnabled(false);
        
        // Join the session by sending a join request to the host
        String userEmail = mAuth.getCurrentUser().getEmail();
        String formattedUserEmail = userEmail.replace(".", "_");
        String formattedHostEmail = hostEmail.replace(".", "_");
        
        DatabaseReference joinRequestRef = mDatabase.child("users")
                .child(formattedHostEmail)
                .child("sessions")
                .child(sessionId)
                .child("join_requests")
                .push();
                
        joinRequestRef.child("email").setValue(userEmail);
        joinRequestRef.child("timestamp").setValue(System.currentTimeMillis())
                .addOnSuccessListener(aVoid -> {
                    // Store join request ID for later reference
                    String joinRequestId = joinRequestRef.getKey();
                    
                    // Start CameraActivity
                    Intent intent = new Intent(SessionPreviewActivity.this, CameraActivity.class);
                    intent.putExtra("session_id", sessionId);
                    intent.putExtra("host_email", hostEmail);
                    intent.putExtra("session_name", sessionName);
                    intent.putExtra("join_request_id", joinRequestId);
                    startActivity(intent);
                    
                    // Mark as released and finish this activity
                    resourcesReleased = true;
                    finish();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to send join request: " + e.getMessage());
                    Toast.makeText(this, "Failed to join session: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    connectionStatus.setText("Failed to join session");
                    connectionBadge.setText("Error");
                    connectionBadge.setBackgroundTintList(
                            ContextCompat.getColorStateList(this, R.color.red));
                    joinButton.setEnabled(true);
                });
    }
    
    @Override
    protected void onDestroy() {
        // Clean up resources
        if (!resourcesReleased) {
            cleanupResources();
            resourcesReleased = true;
        }
        
        super.onDestroy();
    }
    
    private void cleanupResources() {
        // Remove Firebase listeners
        if (sessionListener != null && hostEmail != null && sessionId != null) {
            String formattedHostEmail = hostEmail.replace(".", "_");
            mDatabase.child("users")
                    .child(formattedHostEmail)
                    .child("sessions")
                    .child(sessionId)
                    .removeEventListener(sessionListener);
            sessionListener = null;
        }
        
        // Dispose RTCJoiner
        if (rtcJoiner != null) {
            rtcJoiner.dispose();
            rtcJoiner = null;
        }
    }
}
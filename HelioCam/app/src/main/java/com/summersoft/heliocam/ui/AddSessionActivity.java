package com.summersoft.heliocam.ui;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.summersoft.heliocam.R;
import com.summersoft.heliocam.webrtc_utils.RTCJoiner;

import java.util.Map;

public class AddSessionActivity extends AppCompatActivity {
    private static final String TAG = "AddSessionActivity";
    private FirebaseAuth mAuth;
    private TextInputEditText sessionInput;
    private DatabaseReference mDatabase;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_session);

        // Initialize Firebase components
        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();
        
        // Initialize UI components
        sessionInput = findViewById(R.id.add_session_input);
        
        // Setup buttons
        setupButtons();
    }
    
    private void setupButtons() {
        // Join session button - now joining a camera session as a viewer
        MaterialButton addSessionConfirm = findViewById(R.id.add_session_confirm);
        addSessionConfirm.setOnClickListener(v -> joinCameraSession());
        
        // Host session button - now creating a viewer session
        MaterialButton hostInsteadButton = findViewById(R.id.buttonHostInstead);
        hostInsteadButton.setOnClickListener(v -> {
            Intent intent = new Intent(AddSessionActivity.this, HostSession.class);
            startActivity(intent);
        });
        
        // Cancel button
        MaterialButton cancelButton = findViewById(R.id.buttonCancel);
        cancelButton.setOnClickListener(v -> finish());
    }
    
    private void joinCameraSession() {
        String sessionCode = sessionInput.getText().toString().trim();
        
        // Validate input
        if (sessionCode.isEmpty()) {
            showToast("Please enter a session code");
            return;
        }
        
        // Check if user is authenticated
        if (mAuth.getCurrentUser() == null) {
            showToast("You must be logged in to join a session");
            return;
        }
        
        // Show loading indicator
        showLoading(true);
        
        // Use RTCJoiner instead of RTCHost to find the session
        RTCJoiner.findSessionByCode(sessionCode, new RTCJoiner.SessionFoundCallback() {
            @Override
            public void onSessionFound(String sessionId, String hostEmail) {
                // Hide loading indicator
                showLoading(false);
                
                // Retrieve session name and connect
                mDatabase.child("users")
                        .child(hostEmail.replace(".", "_"))
                        .child("sessions")
                        .child(sessionId)
                        .child("session_name")
                        .get()
                        .addOnSuccessListener(snapshot -> {
                            String sessionName = snapshot.getValue(String.class);
                            if (sessionName == null) sessionName = "Unnamed Session";
                            connectToSession(sessionId, hostEmail, sessionName);
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Failed to get session name", e);
                            connectToSession(sessionId, hostEmail, "Unnamed Session");
                        });
            }

            @Override
            public void onSessionNotFound() {
                // Hide loading indicator
                showLoading(false);
                showToast("No session found with that code");
            }

            @Override
            public void onError(String message, Exception e) {
                // Hide loading indicator
                showLoading(false);
                Log.e(TAG, message, e);
                showToast("Error searching for session: " + message);
            }
        });
    }
    
    private void connectToSession(String sessionId, String hostEmail, String sessionName) {
        Log.d(TAG, "Connecting to session: " + sessionId + " hosted by: " + hostEmail);
        
        // Get device info for this connection
        String deviceInfo = Build.MANUFACTURER + " " + Build.MODEL;
        
        // Send join request to Firebase
        String userEmail = mAuth.getCurrentUser().getEmail().replace(".", "_");
        String formattedHostEmail = hostEmail.replace(".", "_");
        
        mDatabase.child("users")
                .child(formattedHostEmail)
                .child("sessions")
                .child(sessionId)
                .child("joiner_info")
                .child(userEmail)
                .child("device_info")
                .setValue(deviceInfo);
                
        // Also store the session info in the user's own data
        mDatabase.child("users")
                .child(userEmail)
                .child("joined_sessions")
                .child(sessionId)
                .child("host_email")
                .setValue(hostEmail);
                
        mDatabase.child("users")
                .child(userEmail)
                .child("joined_sessions")
                .child(sessionId)
                .child("session_name")
                .setValue(sessionName);
                
        // Start SessionPreviewActivity
        Intent intent = new Intent(AddSessionActivity.this, SessionPreviewActivity.class);
        intent.putExtra("session_id", sessionId);
        intent.putExtra("host_email", hostEmail);
        intent.putExtra("session_name", sessionName);
        startActivity(intent);
        
        finish();
    }
    
    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    // Add this helper method
    private void showLoading(boolean isLoading) {
        // Update UI to show/hide loading indicator
        // This is just a stub - implement with your actual loading UI
    }
}
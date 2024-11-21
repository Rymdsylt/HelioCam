package com.summersoft.heliocam.ui;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.EditText;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.summersoft.heliocam.R;

import java.util.Map;

public class AddSessionActivity extends AppCompatActivity {

    private static final String TAG = "AddSession";
    private FirebaseAuth mAuth;
    private EditText sessionInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_session);

        mAuth = FirebaseAuth.getInstance();
        sessionInput = findViewById(R.id.add_session_input);

        Button addSessionConfirm = findViewById(R.id.add_session_confirm);
        addSessionConfirm.setOnClickListener(v -> addSession());



        // Find the ImageButton and set the click listener
        ImageButton imageButtonPhoneCamera = findViewById(R.id.imageButtonPhoneCamera);
        imageButtonPhoneCamera.setOnClickListener(v -> {
            // Navigate to UsePhoneActivity
            Intent intent = new Intent(AddSessionActivity.this, UsePhoneActivity.class);
            startActivity(intent);
        });
    }

    private void addSession() {
        String passkey = sessionInput.getText().toString().trim();

        // Validate passkey input
        if (passkey.isEmpty()) {
            showToast("Please enter a session passkey.");
            return;
        }

        // Check if the user is logged in
        if (mAuth.getCurrentUser() == null) {
            showToast("User not logged in. Please log in and try again.");
            Log.e(TAG, "Current user is null.");
            return;
        }

        String userEmail = mAuth.getCurrentUser().getEmail();
        if (userEmail == null || userEmail.isEmpty()) {
            showToast("Unable to retrieve user email. Please log in again.");
            Log.e(TAG, "User email is null or empty.");
            return;
        }

        // Prepare email for Firebase compatibility (replace '.' with '_')
        String finalUserEmail = userEmail.replace(".", "_");
        Log.d(TAG, "User email: " + finalUserEmail);
        Log.d(TAG, "Searching for passkey: " + passkey);

        // Get the current device information
        String deviceIdentifier = Build.MANUFACTURER + " " + Build.DEVICE;
        Log.d(TAG, "Device identifier: " + deviceIdentifier);

        // Query Firebase for user sessions
        DatabaseReference userRef = FirebaseDatabase.getInstance()
                .getReference("users")
                .child(finalUserEmail)
                .child("sessions");

        userRef.get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                DataSnapshot dataSnapshot = task.getResult();

                if (dataSnapshot != null && dataSnapshot.exists()) {
                    handleSessionData(dataSnapshot, finalUserEmail, passkey, deviceIdentifier);
                } else {
                    Log.d(TAG, "No sessions found.");
                    showToast("No sessions found.");
                }
            } else {
                String errorMessage = task.getException() != null ? task.getException().getMessage() : "Unknown error";
                Log.e(TAG, "Error retrieving sessions: " + errorMessage);
                showToast("Error retrieving sessions. Please try again.");
            }
        });
    }

    private void handleSessionData(DataSnapshot dataSnapshot, String userEmail, String passkey, String deviceIdentifier) {
        boolean passkeyFound = false;

        // Iterate over the sessions and check for the passkey
        for (DataSnapshot sessionSnapshot : dataSnapshot.getChildren()) {
            String sessionPasskey = getSessionPasskey(sessionSnapshot);

            if (passkey.equals(sessionPasskey)) {
                Log.d(TAG, "Passkey found in session: " + sessionSnapshot.getKey());

                // Query for the login info based on device info
                handleLoginInfo(userEmail, deviceIdentifier, sessionSnapshot);
                passkeyFound = true;
                break;
            }
        }

        if (!passkeyFound) {
            Log.d(TAG, "Passkey not found in any session.");
            showToast("Passkey not found.");
        }
    }


    private String getSessionPasskey(DataSnapshot sessionSnapshot) {
        Object sessionPasskeyObject = sessionSnapshot.child("passkey").getValue();
        return sessionPasskeyObject != null ? String.valueOf(sessionPasskeyObject) : "";
    }

    private void handleLoginInfo(String userEmail, String deviceIdentifier, DataSnapshot sessionSnapshot) {
        DatabaseReference loginInfoRef = FirebaseDatabase.getInstance()
                .getReference("users")
                .child(userEmail);

        loginInfoRef.get().addOnCompleteListener(loginTask -> {
            if (loginTask.isSuccessful()) {
                DataSnapshot loginInfoSnapshot = loginTask.getResult();
                boolean deviceFound = false;

                for (DataSnapshot loginInfo : loginInfoSnapshot.getChildren()) {
                    String deviceName = loginInfo.child("deviceName").getValue(String.class);
                    if (deviceName != null && deviceName.equals(deviceIdentifier)) {
                        addSessionToUser(loginInfo, sessionSnapshot);

                        // Call cleanUpSessionFields after adding the session
                        cleanUpSessionFields(userEmail, loginInfo.getKey());  // Pass the login info key as the device key
                        deviceFound = true;
                        break;
                    }
                }

                if (!deviceFound) {
                    Log.d(TAG, "Device not found in login info.");
                    showToast("Device not found in login info.");
                }
            } else {
                Log.e(TAG, "Error retrieving login info: " + loginTask.getException());
                showToast("Error retrieving login info.");
            }
        });
    }


    private void addSessionToUser(DataSnapshot loginInfo, DataSnapshot sessionSnapshot) {
        // Retrieve all session data dynamically
        String sessionId = sessionSnapshot.getKey();  // Use the session ID from the original sessions node

        // Reference to the user's logininfo node, using the original session ID
        DatabaseReference sessionAddedRef = FirebaseDatabase.getInstance()
                .getReference("users")
                .child(mAuth.getCurrentUser().getEmail().replace(".", "_"))
                .child(loginInfo.getKey())  // loginInfo is a device
                .child("sessions_added")
                .child(sessionId);  // Use the session ID from sessions node instead of push()

        // Iterate through all child nodes of sessionSnapshot to dynamically add all fields
        for (DataSnapshot childSnapshot : sessionSnapshot.getChildren()) {
            String key = childSnapshot.getKey();
            Object value = childSnapshot.getValue();

            // Set each key-value pair dynamically
            if (key != null && value != null) {
                sessionAddedRef.child(key).setValue(value);
            }
        }

        showToast("Session added successfully.");
        finish();
    }
    private void cleanUpSessionFields(String userEmail, String deviceKey) {
        // Get reference to sessions and sessions_added for the user and specific device
        DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("users")
                .child(userEmail.replace(".", "_"));  // Replace '.' with '_' for Firebase compatibility

        // Get the sessions and sessions_added data
        DatabaseReference sessionsRef = userRef.child("sessions");
        DatabaseReference loginInfoRef = userRef.child(deviceKey); // e.g., logininfo_5

        sessionsRef.get().addOnCompleteListener(sessionsTask -> {
            if (sessionsTask.isSuccessful()) {
                DataSnapshot sessionsSnapshot = sessionsTask.getResult();
                if (sessionsSnapshot != null && sessionsSnapshot.exists()) {
                    // Get the sessions_added for the specific device
                    loginInfoRef.child("sessions_added").get().addOnCompleteListener(sessionsAddedTask -> {
                        if (sessionsAddedTask.isSuccessful()) {
                            DataSnapshot sessionsAddedSnapshot = sessionsAddedTask.getResult();
                            if (sessionsAddedSnapshot != null && sessionsAddedSnapshot.exists()) {
                                // Iterate through each session in sessions_added
                                for (DataSnapshot sessionAddedSnapshot : sessionsAddedSnapshot.getChildren()) {
                                    String sessionId = sessionAddedSnapshot.getKey();
                                    if (sessionsSnapshot.hasChild(sessionId)) {
                                        // Get the fields from the original session in sessions
                                        DataSnapshot sessionSnapshot = sessionsSnapshot.child(sessionId);

                                        // Compare the fields of sessions_added and sessions
                                        Map<String, Object> sessionAddedData = (Map<String, Object>) sessionAddedSnapshot.getValue();
                                        Map<String, Object> sessionData = (Map<String, Object>) sessionSnapshot.getValue();

                                        // Iterate through each field in sessions_added and remove those not in sessions
                                        if (sessionAddedData != null) {
                                            for (Map.Entry<String, Object> entry : sessionAddedData.entrySet()) {
                                                String fieldKey = entry.getKey();
                                                if (!sessionData.containsKey(fieldKey)) {
                                                    // Remove the field if it's not in the original session data
                                                    sessionAddedSnapshot.getRef().child(fieldKey).removeValue();
                                                    Log.d(TAG, "Removed extra field: " + fieldKey);
                                                }
                                            }
                                        }
                                    }
                                }
                                showToast("Extra fields removed successfully.");
                            }
                        } else {
                            Log.e(TAG, "Error retrieving sessions_added: " + sessionsAddedTask.getException());
                        }
                    });
                }
            } else {
                Log.e(TAG, "Error retrieving sessions: " + sessionsTask.getException());
            }
        });
    }



    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}

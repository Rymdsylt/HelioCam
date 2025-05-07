package com.summersoft.heliocam.ui;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.summersoft.heliocam.R;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;

public class HostSession extends AppCompatActivity {

    public static final String EXTRA_SESSION_ID = "session_id";
    public static final String EXTRA_SESSION_NAME = "session_name";

    private EditText passkeyInput, sessionNameInput;
    private SecureRandom random;
    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_host_session);

        passkeyInput = findViewById(R.id.passkey_input);
        sessionNameInput = findViewById(R.id.session_name_input);
        random = new SecureRandom();
        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.mainpage), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Handle recreate intent
        boolean isRecreating = getIntent().getBooleanExtra("RECREATE", false);
        if (isRecreating) {
            String sessionName = getIntent().getStringExtra("SESSION_NAME");
            String sessionKey = getIntent().getStringExtra("SESSION_KEY");
            
            if (sessionName != null) {
                sessionNameInput.setText(sessionName);
            }
            
            // If we have a session key, fetch the passkey
            if (sessionKey != null) {
                String userEmail = mAuth.getCurrentUser().getEmail().replace(".", "_");
                mDatabase.child("users")
                        .child(userEmail)
                        .child("sessions")
                        .child(sessionKey)
                        .child("passkey")
                        .get()
                        .addOnSuccessListener(snapshot -> {
                            String passkey = snapshot.getValue(String.class);
                            if (passkey != null) {
                                passkeyInput.setText(passkey);
                            }
                        })
                        .addOnFailureListener(e -> {
                            Toast.makeText(this, "Failed to load session details", Toast.LENGTH_SHORT).show();
                        });
            }
        }

        findViewById(R.id.generateButton).setOnClickListener(view -> generateRandomPasskey());
        findViewById(R.id.cancelButton).setOnClickListener(view -> onBackPressed());
        findViewById(R.id.addButton).setOnClickListener(view -> addSession());
    }

    private void generateRandomPasskey() {
        String passkey = generateRandomString(6);
        passkeyInput.setText(passkey);
        Toast.makeText(this, "Passkey generated", Toast.LENGTH_SHORT).show();
    }

    private String generateRandomString(int length) {
        String characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder passkey = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            int index = random.nextInt(characters.length());
            passkey.append(characters.charAt(index));
        }
        return passkey.toString();
    }

    private void addSession() {
        String sessionName = sessionNameInput.getText().toString().trim();
        String passkey = passkeyInput.getText().toString().trim();

        if (sessionName.isEmpty()) {
            Toast.makeText(this, "Session name cannot be empty", Toast.LENGTH_SHORT).show();
            return;
        }

        if (passkey.isEmpty()) {
            Toast.makeText(this, "Passkey cannot be empty", Toast.LENGTH_SHORT).show();
            return;
        }

        // No need to check for camera permission since the host is only a viewer
        proceedWithAddingSession(sessionName, passkey);
    }

    private void proceedWithAddingSession(String sessionName, String passkey) {
        // Get the current user's email
        String userEmail = mAuth.getCurrentUser().getEmail().replace(".", "_");
        String originalEmail = mAuth.getCurrentUser().getEmail();

        // Create a unique session ID
        String sessionId = "session_" + (System.currentTimeMillis() / 1000);

        // Create the session data - MODIFIED HERE
        Map<String, Object> sessionData = new HashMap<>();
        sessionData.put("passkey", passkey);
        sessionData.put("session_name", sessionName);
        sessionData.put("active", true);  // Add this line
        sessionData.put("created_at", System.currentTimeMillis());  // Optional: add creation timestamp

        // Save the session under the current user's sessions node - MODIFIED
        mDatabase.child("users").child(userEmail).child("sessions").child(sessionId).setValue(sessionData)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        // IMPORTANT: Also save a reference to the session in a lookup table
                        Map<String, Object> sessionCodeData = new HashMap<>();
                        sessionCodeData.put("session_id", sessionId);
                        sessionCodeData.put("host_email", originalEmail);
                        
                        mDatabase.child("session_codes").child(passkey).setValue(sessionCodeData)
                                .addOnCompleteListener(codeTask -> {
                                    if (codeTask.isSuccessful()) {
                                        Toast.makeText(this, "Session added successfully", Toast.LENGTH_SHORT).show();
                                        
                                        // After adding the session, open WatchSessionActivity
                                        Intent intent = new Intent(HostSession.this, WatchSessionActivity.class);
                                        intent.putExtra(EXTRA_SESSION_ID, sessionId);
                                        intent.putExtra(EXTRA_SESSION_NAME, sessionName);
                                        startActivity(intent);
                                        finish(); // Close this activity
                                    } else {
                                        Toast.makeText(this, "Failed to register session code", Toast.LENGTH_SHORT).show();
                                    }
                                });
                    } else {
                        Toast.makeText(this, "Failed to add session", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // Session data model
    public static class Session {
        public String passkey;
        public String session_name;

        public Session(String passkey, String session_name) {
            this.passkey = passkey;
            this.session_name = session_name;
        }
    }
}
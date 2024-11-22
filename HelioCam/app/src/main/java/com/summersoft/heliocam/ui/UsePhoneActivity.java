package com.summersoft.heliocam.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
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

public class UsePhoneActivity extends AppCompatActivity {

    private EditText passkeyInput, sessionNameInput;
    private SecureRandom random;
    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_use_phone);

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

        // Get the current user's email
        String userEmail = mAuth.getCurrentUser().getEmail().replace(".", "_");

        // Create a unique session ID (e.g., session_1, session_2, etc.)
        String sessionId = "session_" + (System.currentTimeMillis() / 1000);

        // Create the session data
        Session session = new Session(passkey, sessionName);

        // Save the session under the current user's sessions node
        mDatabase.child("users").child(userEmail).child("sessions").child(sessionId).setValue(session)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(this, "Session added successfully", Toast.LENGTH_SHORT).show();

                        // After adding the session, open CameraActivity and pass session details
                        Intent intent = new Intent(UsePhoneActivity.this, CameraActivity.class);
                        intent.putExtra("session_name", sessionName); // Pass session name
                        intent.putExtra("session_id", sessionId); // Pass session ID (session_(n))
                        intent.putExtra("passkey", passkey); // Pass passkey
                        startActivity(intent);
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

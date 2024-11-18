package com.summersoft.heliocam.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.summersoft.heliocam.R;

public class LoginActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private EditText mUsername, mPassword;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        mAuth = FirebaseAuth.getInstance();

        mUsername = findViewById(R.id.username);
        mPassword = findViewById(R.id.password);

        findViewById(R.id.enterBtn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                loginUser();
            }
        });

        findViewById(R.id.signupLink).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(LoginActivity.this, SignupActivity.class); // Create an intent to open SignUpActivity
                startActivity(intent);
            }
        });
    }

    private void loginUser() {
        String email = mUsername.getText().toString().trim();
        String password = mPassword.getText().toString().trim();

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please enter both email and password", Toast.LENGTH_SHORT).show();
            return;
        }

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();

                        // Check if email is verified
                        if (user != null && user.isEmailVerified()) {
                            // Proceed to home activity if email is verified
                            Intent intent = new Intent(LoginActivity.this, HomeActivity.class);
                            startActivity(intent);
                            finish();
                        } else {
                            // Show a message if email is not verified
                            Toast.makeText(LoginActivity.this, "Please verify your email before logging in.", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(LoginActivity.this, "Authentication failed. Check your credentials.", Toast.LENGTH_SHORT).show();
                    }
                });
    }
}

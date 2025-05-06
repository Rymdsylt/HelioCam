package com.summersoft.heliocam.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.summersoft.heliocam.R;

public class ActivityForgotPassword extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private MaterialButton sendResetLinkBtn;
    private TextInputEditText emailInput;
    private TextView loginLink;
    private CountDownTimer cooldownTimer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_forgot_password);

        mAuth = FirebaseAuth.getInstance();
        
        // Initialize views with updated IDs
        sendResetLinkBtn = findViewById(R.id.sendResetLinkBtn);
        emailInput = findViewById(R.id.email);
        loginLink = findViewById(R.id.loginLink);

        // Apply window insets to the root view
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.forgotPasswordScrollView), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Set click listener for send reset link button
        sendResetLinkBtn.setOnClickListener(v -> sendPasswordReset());
        
        // Set click listener for login link
        loginLink.setOnClickListener(v -> {
            Intent intent = new Intent(ActivityForgotPassword.this, LoginActivity.class);
            startActivity(intent);
            finish();
        });
        
        // Add decorative elements programmatically (optional)
        addDecorativeElements();
    }

    private void addDecorativeElements() {
        // You could add code here to animate the decorations if needed
        // For example: ObjectAnimator.ofFloat(findViewById(R.id.topDecoration1), "alpha", 0f, 0.6f).start();
    }

    private void sendPasswordReset() {
        String email = emailInput.getText().toString().trim();

        if (email.isEmpty()) {
            Toast.makeText(ActivityForgotPassword.this, "Please enter your registered email.", Toast.LENGTH_SHORT).show();
            return;
        }

        sendResetLinkBtn.setEnabled(false);
        
        mAuth.sendPasswordResetEmail(email)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(ActivityForgotPassword.this, "Reset link sent to email.", Toast.LENGTH_SHORT).show();
                        startCooldown();
                    } else {
                        if (task.getException() instanceof FirebaseAuthInvalidUserException) {
                            Toast.makeText(ActivityForgotPassword.this, "Account doesn't exist.", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(ActivityForgotPassword.this, "Error sending reset email.", Toast.LENGTH_SHORT).show();
                        }
                        sendResetLinkBtn.setEnabled(true);
                    }
                });
    }

    private void startCooldown() {
        cooldownTimer = new CountDownTimer(60000, 1000) {
            public void onTick(long millisUntilFinished) {
                sendResetLinkBtn.setText(String.format("Try again in %ds", millisUntilFinished / 1000));
            }

            public void onFinish() {
                sendResetLinkBtn.setEnabled(true);
                sendResetLinkBtn.setText("Send Reset Link");
            }
        }.start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cooldownTimer != null) {
            cooldownTimer.cancel();
        }
    }
}

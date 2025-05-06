package com.summersoft.heliocam.ui;

import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.summersoft.heliocam.R;

public class ActivityForgotPassword extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private Button sendResetLinkBtn;
    private CountDownTimer cooldownTimer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_forgot_password);

        mAuth = FirebaseAuth.getInstance();
        sendResetLinkBtn = findViewById(R.id.sendResetLinkBtn);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        sendResetLinkBtn.setOnClickListener(v -> sendPasswordReset());
    }

    private void sendPasswordReset() {
        String email = ((EditText) findViewById(R.id.email)).getText().toString().trim();

        if (!email.isEmpty()) {
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
                        }
                    });
        } else {
            Toast.makeText(ActivityForgotPassword.this, "Please enter your registered email.", Toast.LENGTH_SHORT).show();
        }
    }

    private void startCooldown() {
        sendResetLinkBtn.setEnabled(false);
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

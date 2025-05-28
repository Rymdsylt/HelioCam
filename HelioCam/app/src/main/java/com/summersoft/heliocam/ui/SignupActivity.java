package com.summersoft.heliocam.ui;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.content.Intent;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.android.material.textfield.TextInputEditText;
import com.summersoft.heliocam.R;
import com.summersoft.heliocam.utils.UserModel;
import androidx.appcompat.app.AlertDialog;
import android.widget.Toast;

public class SignupActivity extends AppCompatActivity {

    private static final String TAG = "SignupActivity";
    private TextInputEditText fullname, username, contact, email, password, confirmPassword;
    private FirebaseAuth mAuth;
    private DatabaseReference databaseRef;@Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);

        mAuth = FirebaseAuth.getInstance();
        databaseRef = FirebaseDatabase.getInstance().getReference("users");  // Store all users here, no role-based separation.
          fullname = findViewById(R.id.fullname);
        username = findViewById(R.id.username);
        contact = findViewById(R.id.contact);
        email = findViewById(R.id.email);
        password = findViewById(R.id.password);
        confirmPassword = findViewById(R.id.confirmPassword);

        findViewById(R.id.enterBtn).setOnClickListener(v -> signUpUser());
        
        // Add click listener for login link to navigate to LoginActivity
        findViewById(R.id.loginLink).setOnClickListener(v -> {
            Intent intent = new Intent(SignupActivity.this, LoginActivity.class);
            startActivity(intent);
            finish(); // Close signup activity
        });
    }    private void signUpUser() {
        // Add null checks to identify which field is causing the issue
        if (email == null) {
            showErrorDialog("Field Error", "Email field not found");
            return;
        }
        if (password == null) {
            showErrorDialog("Field Error", "Password field not found");
            return;
        }
        if (confirmPassword == null) {
            showErrorDialog("Field Error", "Confirm Password field not found");
            return;
        }
        if (fullname == null) {
            showErrorDialog("Field Error", "Full name field not found");
            return;
        }
        if (username == null) {
            showErrorDialog("Field Error", "Username field not found");
            return;
        }
        if (contact == null) {
            showErrorDialog("Field Error", "Contact field not found");
            return;
        }
        
        String emailText = email.getText().toString().trim();
        String passwordText = password.getText().toString().trim();
        String confirmPasswordText = confirmPassword.getText().toString().trim();
        String fullnameText = fullname.getText().toString().trim();
        String usernameText = username.getText().toString().trim();
        String contactText = contact.getText().toString().trim();        if (emailText.isEmpty() || passwordText.isEmpty() || confirmPasswordText.isEmpty() || fullnameText.isEmpty() || usernameText.isEmpty() || contactText.isEmpty()) {
            showWarningDialog("Missing Information", "Please fill all fields");
            return;
        }

        if (!passwordText.equals(confirmPasswordText)) {
            showErrorDialog("Password Mismatch", "Passwords do not match");
            return;
        }
        
        // Directly create user instead of checking if email exists
        // Firebase Auth will handle duplicate email errors automatically
        createUserWithEmail(emailText, fullnameText, usernameText, contactText, passwordText);    }    private void createUserWithEmail(String email, String fullname, String username, String contact, String password) {
        mAuth.createUserWithEmailAndPassword(email, password)                .addOnCompleteListener(this, task -> {                    if (task.isSuccessful()) {
                        // Authentication successful - directly send verification email (no dialog)
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            user.sendEmailVerification()
                                    .addOnCompleteListener(task1 -> {
                                        if (task1.isSuccessful()) {
                                            // Email verification sent successfully - show only this dialog
                                            showSuccessDialog("Email Verification Sent", 
                                                "Verification email sent successfully. Please check your inbox and click the verification link to complete your registration.");
                                            storeUserData(email, fullname, username, contact);  // Store user data after sending verification
                                        } else {
                                            // Email verification failed
                                            String errorMsg = "Account created but failed to send verification email.";
                                            if (task1.getException() != null) {
                                                errorMsg += " Error: " + task1.getException().getMessage();
                                            }
                                            showErrorDialog("Email Verification Failed", errorMsg);
                                            // Still store user data even if verification email fails
                                            storeUserData(email, fullname, username, contact);
                                        }
                                    });
                        }
                    } else {
                        // Authentication failed - show detailed error dialog
                        String errorMessage = "Failed to create account";
                        if (task.getException() != null) {
                            String exceptionMsg = task.getException().getMessage();
                            if (exceptionMsg != null) {
                                if (exceptionMsg.contains("email address is already in use")) {
                                    errorMessage = "This email address is already registered. Please use a different email or try signing in.";
                                } else if (exceptionMsg.contains("weak password")) {
                                    errorMessage = "Password is too weak. Please use at least 6 characters with a mix of letters and numbers.";
                                } else if (exceptionMsg.contains("invalid email")) {
                                    errorMessage = "Please enter a valid email address.";
                                } else {
                                    errorMessage += ": " + exceptionMsg;
                                }
                            }                        }
                        showErrorDialog("Authentication Failed", errorMessage);
                    }});
    }    private void storeUserData(String email, String fullname, String username, String contact) {
        String sanitizedEmail = email.replaceAll("[.#$\\[\\]]", "_");
        
        Log.d(TAG, "Storing user data for: " + email);
        Log.d(TAG, "Sanitized email: " + sanitizedEmail);

        UserModel user = new UserModel(fullname, username, contact, email);        databaseRef.child(sanitizedEmail).setValue(user)                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "User data stored successfully");
                        // Don't show dialog here - already shown in email verification
                    } else {
                        Log.e(TAG, "Failed to store user data", task.getException());
                        String errorMessage = "Failed to store user data";
                        if (task.getException() != null) {
                            errorMessage += ": " + task.getException().getMessage();
                        }
                        showErrorDialog("Database Error", errorMessage);
                    }
                });
    }

    // Helper methods for showing dialogs
    private void showSuccessDialog(String title, String message) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setIcon(android.R.drawable.ic_dialog_info)
                .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
                .setCancelable(true)
                .show();
    }

    private void showErrorDialog(String title, String message) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
                .setCancelable(true)
                .show();
    }

    private void showWarningDialog(String title, String message) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setIcon(android.R.drawable.stat_sys_warning)
                .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
                .setCancelable(true)
                .show();
    }
}

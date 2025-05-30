package com.summersoft.heliocam.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.summersoft.heliocam.R;
import com.summersoft.heliocam.status.LoginStatus;
import com.summersoft.heliocam.status.LoginUser;
import androidx.appcompat.app.AlertDialog;

public class LoginActivity extends AppCompatActivity {

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;
    private EditText mUsername, mPassword;
    private Handler handler = new Handler();
    private Runnable loginStatusRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        mUsername = findViewById(R.id.username);
        mPassword = findViewById(R.id.password);

        findViewById(R.id.enterBtn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (checkLocationPermissions()) {
                    loginUser();
                } else {
                    // Request location permissions if not granted
                    requestLocationPermissions();
                }
            }
        });

        findViewById(R.id.signupLink).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(LoginActivity.this, SignupActivity.class); // Create an intent to open SignUpActivity
                startActivity(intent);
            }
        });

        findViewById(R.id.forgotPasswordLink).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(LoginActivity.this, ActivityForgotPassword.class); // Create an intent to open ActivityForgotPassword
                startActivity(intent);
            }
        });

    }

    private boolean checkLocationPermissions() {
        // Check if both ACCESS_FINE_LOCATION and ACCESS_COARSE_LOCATION permissions are granted
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestLocationPermissions() {
        // Request the necessary location permissions
        ActivityCompat.requestPermissions(this, new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
        }, LOCATION_PERMISSION_REQUEST_CODE);
    }    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            // If permissions are granted, attempt to login
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loginUser();            } else {
                // Show a message if permissions are denied
                showWarningDialog("Permission Required", "Location permissions are required to continue.");
            }
        }
    }    private void loginUser() {
        String email = mUsername.getText().toString().trim();
        String password = mPassword.getText().toString().trim();        if (email.isEmpty() || password.isEmpty()) {
            showWarningDialog("Missing Information", "Please enter both email and password");
            return;
        }

        // Use LoginUser class to handle Firebase login
        new LoginUser(this).loginUser(email, password);

        // Start checking login status every second after a successful login attempt
        loginStatusRunnable = new Runnable() {
            @Override
            public void run() {
                LoginStatus.checkLoginStatus(LoginActivity.this);
                handler.postDelayed(this, 1000); // Re-run this task every second
            }
        };
        handler.postDelayed(loginStatusRunnable, 0); // Start the periodic check immediately
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Remove the periodic task when the activity is no longer visible
        if (loginStatusRunnable != null) {
            handler.removeCallbacks(loginStatusRunnable);
        }
    }

    // Helper methods for showing dialogs
    private void showWarningDialog(String title, String message) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setIcon(android.R.drawable.stat_sys_warning)
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

    private void showSuccessDialog(String title, String message) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setIcon(android.R.drawable.ic_dialog_info)
                .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
                .setCancelable(true)
                .show();
    }
}

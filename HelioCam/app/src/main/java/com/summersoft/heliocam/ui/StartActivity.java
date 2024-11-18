package com.summersoft.heliocam.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.summersoft.heliocam.R;

public class StartActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_start);

        // Initialize Firebase Auth
        mAuth = FirebaseAuth.getInstance();

        // Delay of 3 seconds to show splash screen
        new Handler().postDelayed(() -> {
            boolean isLoggedIn = checkUserLoginStatus();

            Intent intent = new Intent(StartActivity.this,
                    isLoggedIn ? HomeActivity.class : LoginActivity.class);
            startActivity(intent);
            finish();
        }, 3000);
    }

    private boolean checkUserLoginStatus() {
        // Check if a user is currently signed in using Firebase Auth
        FirebaseUser currentUser = mAuth.getCurrentUser();
        return currentUser != null; // If the user is not null, they are logged in
    }
}

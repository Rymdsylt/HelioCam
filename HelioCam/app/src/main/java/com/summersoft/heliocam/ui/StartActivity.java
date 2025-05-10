package com.summersoft.heliocam.ui;

import android.animation.ObjectAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.AnticipateInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityOptionsCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.summersoft.heliocam.R;

public class StartActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private ImageView logoImage;
    private TextView appName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_start);

        // Initialize Firebase Auth
        mAuth = FirebaseAuth.getInstance();
        
        // Find views
        logoImage = findViewById(R.id.logo);
        appName = findViewById(R.id.app_name);
        
        // Start animations
        startSplashAnimations();

        // Delay to show splash screen
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            boolean isLoggedIn = checkUserLoginStatus();
            
            Intent intent = new Intent(StartActivity.this,
                    isLoggedIn ? HomeActivity.class : LoginActivity.class);
                    
            // Create animation bundle for smooth transition
            ActivityOptionsCompat options = ActivityOptionsCompat.makeSceneTransitionAnimation(
                    StartActivity.this, logoImage, "logo_transition");
                    
            startActivity(intent, options.toBundle());
            
            // Delayed finish to allow transition to complete
            new Handler(Looper.getMainLooper()).postDelayed(this::finish, 1000);
        }, 3000);
    }

    private void startSplashAnimations() {
        // Fade in logo
        logoImage.setAlpha(0f);
        logoImage.animate()
                .alpha(1f)
                .setDuration(800)
                .setInterpolator(new AnticipateInterpolator())
                .start();
                
        // Slide up app name
        appName.setTranslationY(50f);
        appName.setAlpha(0f);
        appName.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(800)
                .setStartDelay(300)
                .start();
    }

    private boolean checkUserLoginStatus() {
        // Check if a user is currently signed in using Firebase Auth
        FirebaseUser currentUser = mAuth.getCurrentUser();
        return currentUser != null; // If the user is not null, they are logged in
    }
}

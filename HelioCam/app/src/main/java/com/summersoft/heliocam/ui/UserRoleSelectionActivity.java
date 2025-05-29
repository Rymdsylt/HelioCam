package com.summersoft.heliocam.ui;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.summersoft.heliocam.R;

public class UserRoleSelectionActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "UserRolePrefs";
    private static final String KEY_USER_ROLE = "user_role";
    public static final String ROLE_HOST = "host";
    public static final String ROLE_JOINER = "joiner";

    private MaterialCardView hostCard;
    private MaterialCardView joinerCard;
    private MaterialButton continueButton;
    private String selectedRole = ROLE_HOST; // Default selection

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_role_selection);

        // Initialize UI components
        hostCard = findViewById(R.id.hostCard);
        joinerCard = findViewById(R.id.joinerCard);
        continueButton = findViewById(R.id.continueButton);

        // Set up click listeners for the cards
        hostCard.setOnClickListener(v -> selectRole(ROLE_HOST));
        joinerCard.setOnClickListener(v -> selectRole(ROLE_JOINER));        // Continue button click listener
        continueButton.setOnClickListener(v -> {
            saveUserRole();
            navigateToNextScreen();
        });

        // Check if coming from post-login preferences
        boolean fromPostLogin = getIntent().getBooleanExtra("from_post_login", false);
        
        // Check if user already has a saved role and pre-select it (only if not from post-login)
        String currentRole = null;
        if (!fromPostLogin) {
            currentRole = getUserRole(this);
        }
        
        // If no role is saved (returns null or empty) or coming from post-login, default to HOST
        if (currentRole == null || currentRole.isEmpty()) {
            currentRole = ROLE_HOST;
        }
        selectRole(currentRole);
    }

    private void selectRole(String role) {
        selectedRole = role;
        
        // Update UI to reflect selection
        if (ROLE_HOST.equals(role)) {
            // Visual updates for Host selection
            hostCard.setCardElevation(4f);
            hostCard.setStrokeColor(ContextCompat.getColor(this, R.color.orange));
            hostCard.setStrokeWidth(2);
            joinerCard.setCardElevation(1f);
            joinerCard.setStrokeColor(ContextCompat.getColor(this, android.R.color.transparent));
            joinerCard.setStrokeWidth(0);
            
            // Update button text
            continueButton.setText("Continue as Host");
        } else {
            // Visual updates for Joiner selection
            joinerCard.setCardElevation(4f);
            joinerCard.setStrokeColor(ContextCompat.getColor(this, R.color.orange));
            joinerCard.setStrokeWidth(2);
            hostCard.setCardElevation(1f);
            hostCard.setStrokeColor(ContextCompat.getColor(this, android.R.color.transparent));
            hostCard.setStrokeWidth(0);
            
            // Update button text
            continueButton.setText("Continue as Joiner");
        }
    }

    private void saveUserRole() {
        SharedPreferences preferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(KEY_USER_ROLE, selectedRole);
        editor.apply();
    }

    private void navigateToNextScreen() {
        // Navigate to the appropriate activity based on selected role
        Intent intent;
        
        if (ROLE_HOST.equals(selectedRole)) {
            // User selected HOST role, navigate to HomeActivity
            intent = new Intent(this, HomeActivity.class);
        } else {
            // User selected JOINER role, navigate to JoinerHomeActivity
            intent = new Intent(this, JoinerHomeActivity.class);
        }
        
        startActivity(intent);
        finish();
    }    /**
     * Utility method to get the user role from anywhere in the app
     * Returns null if no role is saved
     */
    public static String getUserRole(Context context) {
        SharedPreferences preferences = 
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return preferences.getString(KEY_USER_ROLE, null);
    }    /**
     * Utility method to check if user is a host
     * Returns true by default if no role is saved
     */
    public static boolean isUserHost(Context context) {
        String role = getUserRole(context);
        // Default to HOST if no role is saved
        return role == null || ROLE_HOST.equals(role);
    }

    /**
     * Utility method to clear the saved user role (useful for testing or role reset)
     */
    public static void clearUserRole(Context context) {
        SharedPreferences preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.remove(KEY_USER_ROLE);
        editor.apply();
    }
}
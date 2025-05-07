package com.summersoft.heliocam.ui;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

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
        joinerCard.setOnClickListener(v -> selectRole(ROLE_JOINER));

        // Continue button click listener
        continueButton.setOnClickListener(v -> {
            saveUserRole();
            navigateToNextScreen();
        });

        // Set initial selection
        selectRole(ROLE_HOST);
    }

    private void selectRole(String role) {
        selectedRole = role;
        
        // Update UI to reflect selection
        if (ROLE_HOST.equals(role)) {
            hostCard.setStrokeColor(getResources().getColor(android.R.color.holo_orange_light));
            joinerCard.setStrokeColor(getResources().getColor(android.R.color.darker_gray));
        } else {
            hostCard.setStrokeColor(getResources().getColor(android.R.color.darker_gray));
            joinerCard.setStrokeColor(getResources().getColor(android.R.color.holo_orange_light));
        }
    }

    private void saveUserRole() {
        SharedPreferences preferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(KEY_USER_ROLE, selectedRole);
        editor.apply();
        
        Toast.makeText(this, "You selected: " + 
                (ROLE_HOST.equals(selectedRole) ? "HOST" : "JOINER"), 
                Toast.LENGTH_SHORT).show();
    }

    private void navigateToNextScreen() {
        // Navigate to notification preferences or home activity based on role
        Intent intent = new Intent(this, HomeActivity.class);
        startActivity(intent);
        finish();
    }

    /**
     * Utility method to get the user role from anywhere in the app
     */
    public static String getUserRole(Context context) {
        SharedPreferences preferences = 
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return preferences.getString(KEY_USER_ROLE, ROLE_HOST);
    }

    /**
     * Utility method to check if user is a host
     */
    public static boolean isUserHost(Context context) {
        return ROLE_HOST.equals(getUserRole(context));
    }
}
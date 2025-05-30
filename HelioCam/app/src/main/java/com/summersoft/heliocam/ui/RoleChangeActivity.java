package com.summersoft.heliocam.ui;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.summersoft.heliocam.R;

public class RoleChangeActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "UserRolePrefs";
    private static final String KEY_USER_ROLE = "user_role";
    private static final String ROLE_HOST = UserRoleSelectionActivity.ROLE_HOST;
    private static final String ROLE_JOINER = UserRoleSelectionActivity.ROLE_JOINER;

    private MaterialCardView hostCard;
    private MaterialCardView joinerCard;
    private MaterialButton saveButton;
    private String selectedRole;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_change_role);

        // Set up toolbar with back button
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        // Initialize UI components
        hostCard = findViewById(R.id.hostCard);
        joinerCard = findViewById(R.id.joinerCard);
        saveButton = findViewById(R.id.saveButton);        // Get current role
        selectedRole = UserRoleSelectionActivity.getUserRole(this);
        // If no role is saved, default to HOST
        if (selectedRole == null || selectedRole.isEmpty()) {
            selectedRole = ROLE_HOST;
        }

        // Set up click listeners for the cards
        hostCard.setOnClickListener(v -> selectRole(ROLE_HOST));
        joinerCard.setOnClickListener(v -> selectRole(ROLE_JOINER));

        // Save button click listener
        saveButton.setOnClickListener(v -> {
            saveUserRole();
            navigateToRoleScreen(); // New method to navigate based on role
        });

        // Set initial selection based on current role
        selectRole(selectedRole);
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
        
        Toast.makeText(this, "Role changed to: " + 
                (ROLE_HOST.equals(selectedRole) ? "HOST" : "JOINER"), 
                Toast.LENGTH_SHORT).show();
    }

    /**
     * Navigate to the appropriate screen based on selected role
     */
    private void navigateToRoleScreen() {
        Intent intent;
        
        if (ROLE_HOST.equals(selectedRole)) {
            // User selected HOST role, navigate to HomeActivity
            intent = new Intent(this, HomeActivity.class);
        } else {
            // User selected JOINER role, navigate to JoinerHomeActivity 
            intent = new Intent(this, JoinerHomeActivity.class);
        }
        
        // Clear back stack so user can't navigate back to previous role's screens
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
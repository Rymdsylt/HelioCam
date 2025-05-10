package com.summersoft.heliocam.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.summersoft.heliocam.R;

import java.util.HashMap;
import java.util.Map;

public class SettingsFragment extends Fragment {

    public SettingsFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View rootView = inflater.inflate(R.layout.fragment_settings, container, false);

        // Get reference to the buttons in the layout
        Button btnNotification = rootView.findViewById(R.id.btnNotification);
        Button btDevicesLogin = rootView.findViewById(R.id.btDevicesLogin);
        Button btnAccountSettings = rootView.findViewById(R.id.btnAccountSetting);
        Button btnRoleChange = rootView.findViewById(R.id.btnRoleChange); // New button
        Button btnAbout = rootView.findViewById(R.id.btnAbout);
        Button btnLogout = rootView.findViewById(R.id.btnLogout);

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null && currentUser.getEmail() != null) {
            ensureUserHasSettingsKey(currentUser.getEmail());
        }

        // Set OnClickListener for btnNotification
        btnNotification.setOnClickListener(v -> {
            navigateToFragment(new NotificationSettings());
        });

        // Set OnClickListener for btDevicesLogin
        btDevicesLogin.setOnClickListener(v -> {
            navigateToFragment(new DevicesLoginFragment());
        });

        // Set OnClickListener for btnAccountSettings
        btnAccountSettings.setOnClickListener(v -> {
            navigateToFragment(new AccountSettingsFragment());
        });
        
        // Set OnClickListener for btnRoleChange
        btnRoleChange.setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), RoleChangeActivity.class);
            startActivity(intent);
        });

        // Set OnClickListener for btnAbout
        btnAbout.setOnClickListener(v -> {
            navigateToFragment(new AboutFragment());
        });
        
        // Set OnClickListener for btnLogout
        btnLogout.setOnClickListener(v -> {
            // Show confirmation dialog
            new AlertDialog.Builder(requireContext())
                .setTitle("Logout")
                .setMessage("Are you sure you want to logout?")
                .setPositiveButton("Yes", (dialog, which) -> {
                    // Use the LogoutUser utility class to handle logout
                    com.summersoft.heliocam.status.LogoutUser.logoutUser();
                    
                    // Show toast message
                    Toast.makeText(requireContext(), "Logged out successfully", Toast.LENGTH_SHORT).show();
                    
                    // Check login status to redirect to login screen
                    com.summersoft.heliocam.status.LoginStatus.checkLoginStatus(requireContext());
                    
                    // If using the HomeActivity, can also finish it
                    if (getActivity() instanceof HomeActivity) {
                        getActivity().finish();
                    }
                })
                .setNegativeButton("No", null)
                .show();
        });

        return rootView;
    }
    
    /**
     * Helper method to navigate to different fragments
     */
    private void navigateToFragment(Fragment fragment) {
        if (getFragmentManager() != null) {
            FragmentTransaction transaction = getFragmentManager().beginTransaction();
            transaction.setCustomAnimations(R.anim.slide_in_right, 0);
            transaction.replace(R.id.fragment_container, fragment);
            transaction.addToBackStack(null);
            transaction.commit();
        }
    }

    private void ensureUserHasSettingsKey(String userEmail) {
        String emailKey = userEmail.replace(".", "_");
        DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("users").child(emailKey);

        userRef.child("settingsKey").get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult() != null && task.getResult().exists() &&
                    task.getResult().getValue() != null) {
                // Key already exists, no action needed
            } else {
                // Generate a random settings key (alphanumeric, 8 characters)
                String newKey = generateRandomKey(8);

                // Save the key to Firebase
                userRef.child("settingsKey").setValue(newKey)
                        .addOnSuccessListener(unused -> {
                            // Key created successfully
                        })
                        .addOnFailureListener(e -> {
                            Toast.makeText(requireContext(),
                                    "Failed to create settings key: " + e.getMessage(),
                                    Toast.LENGTH_SHORT).show();
                        });
            }
        });
    }

    private String generateRandomKey(int length) {
        String alphaNumeric = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            int index = (int) (Math.random() * alphaNumeric.length());
            sb.append(alphaNumeric.charAt(index));
        }
        return sb.toString();
    }
}
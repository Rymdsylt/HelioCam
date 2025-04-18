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
        Button btnAbout = rootView.findViewById(R.id.btnAbout);
        Button btnSession = rootView.findViewById(R.id.btnSessionSetting);


        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null && currentUser.getEmail() != null) {
            ensureUserHasSettingsKey(currentUser.getEmail());
        }
        // Set OnClickListener for btnNotification
        btnNotification.setOnClickListener(v -> {
            // Create an instance of DevicesLoginFragment
            NotificationSettings notificationSettings = new NotificationSettings();

            // Begin a fragment transaction
            FragmentTransaction transaction = getFragmentManager().beginTransaction();

            // Set the custom animations
            transaction.setCustomAnimations(
                    R.anim.slide_in_right,   // Enter animation
                    0
            );

            // Replace the current fragment with DevicesLoginFragment
            transaction.replace(R.id.fragment_container, notificationSettings);

            // Optionally add to back stack
            transaction.addToBackStack(null);

            // Commit the transaction
            transaction.commit();
        });

        // Set OnClickListener for btDevicesLogin
        btDevicesLogin.setOnClickListener(v -> {
            // Create an instance of DevicesLoginFragment
            DevicesLoginFragment devicesLoginFragment = new DevicesLoginFragment();

            // Begin a fragment transaction
            FragmentTransaction transaction = getFragmentManager().beginTransaction();

            // Set the custom animations
            transaction.setCustomAnimations(
                    R.anim.slide_in_right,   // Enter animation
                    0
            );

            // Replace the current fragment with DevicesLoginFragment
            transaction.replace(R.id.fragment_container, devicesLoginFragment);

            // Optionally add to back stack
            transaction.addToBackStack(null);

            // Commit the transaction
            transaction.commit();
        });

         btnAccountSettings.setOnClickListener(v -> {
             // Direct navigation to account settings without key verification
             AccountSettingsFragment accountSettingsFragment = new AccountSettingsFragment();
             FragmentTransaction transaction = getFragmentManager().beginTransaction();
             transaction.setCustomAnimations(R.anim.slide_in_right, 0);
             transaction.replace(R.id.fragment_container, accountSettingsFragment);
             transaction.addToBackStack(null);
             transaction.commit();

             /*
            LayoutInflater dialogInflater = LayoutInflater.from(requireContext());
            View dialogView = dialogInflater.inflate(R.layout.dialog_account_settings, null);


            AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
            builder.setView(dialogView);


            EditText etSettingsKey = dialogView.findViewById(R.id.etSettingsKey);
            Button btnSendKey = dialogView.findViewById(R.id.btnSendKey);
            Button btnConfirm = dialogView.findViewById(R.id.btnConfirm);


            btnConfirm.setOnClickListener(confirmView -> {
                String settingsKey = etSettingsKey.getText().toString().trim();

                if (settingsKey.isEmpty()) {
                    Toast.makeText(requireContext(), "Please enter a key!", Toast.LENGTH_SHORT).show();
                    return;
                }

                String currentUserEmail = FirebaseAuth.getInstance().getCurrentUser().getEmail();
                if (currentUserEmail == null) {
                    Toast.makeText(requireContext(), "User not logged in!", Toast.LENGTH_SHORT).show();
                    return;
                }

                // Replace "@" and "." with valid Firebase path characters
                String emailKey = currentUserEmail.replace(".", "_");


                // Reference to Firebase Database
                DatabaseReference databaseReference = FirebaseDatabase.getInstance().getReference("users").child(emailKey);

                // Fetch the key value from Firebase
                databaseReference.child("settingsKey").get().addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        String storedKey = task.getResult().getValue(String.class);

                        if (settingsKey.equals(storedKey)) {
                            // Navigate to AccountSettingsFragment if key matches
                            AccountSettingsFragment accountSettingsFragment = new AccountSettingsFragment();
                            FragmentTransaction transaction = getFragmentManager().beginTransaction();
                            transaction.setCustomAnimations(R.anim.slide_in_right, 0);
                            transaction.replace(R.id.fragment_container, accountSettingsFragment);
                            transaction.addToBackStack(null);
                            transaction.commit();
                        } else {
                            Toast.makeText(requireContext(), "Invalid key! Please try again.", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(requireContext(), "Error fetching data. Please try again.", Toast.LENGTH_SHORT).show();
                    }
                });
            });


            btnSendKey.setOnClickListener(sendKeyView -> {
                // Get the logged-in user's email
                String currentUserEmail = FirebaseAuth.getInstance().getCurrentUser().getEmail();

                if (currentUserEmail == null || currentUserEmail.isEmpty()) {
                    Toast.makeText(requireContext(), "User not logged in or email not available.", Toast.LENGTH_SHORT).show();
                    return;
                }

                // Replace "." with valid Firebase path character "_"
                String emailKey = currentUserEmail.replace(".", "_");

                // Reference to the Firebase database
                DatabaseReference databaseReference = FirebaseDatabase.getInstance().getReference("users").child(emailKey);

                // Fetch the key value from Firebase
                databaseReference.child("settingsKey").get().addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null && task.getResult().getValue() != null) {
                        String settingsKey = task.getResult().getValue(String.class);

                        // Create an email task in Firebase
                        DatabaseReference emailTasksRef = FirebaseDatabase.getInstance()
                                .getReference("email_tasks").push();

                        // Data to send to Firebase
                        Map<String, Object> emailData = new HashMap<>();
                        emailData.put("to", currentUserEmail);
                        emailData.put("subject", "Your HelioCam Settings Key");
                        emailData.put("message", "Here is your settings key: " + settingsKey);
                        emailData.put("timestamp", ServerValue.TIMESTAMP);

                        // Save the email task to Firebase
                        emailTasksRef.setValue(emailData)
                                .addOnSuccessListener(unused -> {
                                    Toast.makeText(requireContext(),
                                            "Settings key will be sent to your email shortly",
                                            Toast.LENGTH_LONG).show();
                                })
                                .addOnFailureListener(e -> {
                                    Toast.makeText(requireContext(),
                                            "Failed to send settings key: " + e.getMessage(),
                                            Toast.LENGTH_LONG).show();
                                });
                    } else {
                        Toast.makeText(requireContext(), "Failed to retrieve settings key from Firebase.",
                                Toast.LENGTH_SHORT).show();
                    }
                });
            });



            // Create and show the dialog
            AlertDialog dialog = builder.create();
            dialog.show();

              */
        });



        // Set OnClickListener for btDevicesLogin
        btnAbout.setOnClickListener(v -> {
            // Create an instance of DevicesLoginFragment
            AboutFragment aboutFragment = new AboutFragment();

            // Begin a fragment transaction
            FragmentTransaction transaction = getFragmentManager().beginTransaction();

            // Set the custom animations
            transaction.setCustomAnimations(
                    R.anim.slide_in_right,   // Enter animation
                    0
            );

            // Replace the current fragment with DevicesLoginFragment
            transaction.replace(R.id.fragment_container, aboutFragment);

            // Optionally add to back stack
            transaction.addToBackStack(null);

            // Commit the transaction
            transaction.commit();
        });

        // Set OnClickListener for btnNotification
        btnSession.setOnClickListener(v -> {
            // Create an instance of DevicesLoginFragment
            SessionSettingsFragment sessionSettingsFragment = new SessionSettingsFragment();

            // Begin a fragment transaction
            FragmentTransaction transaction = getFragmentManager().beginTransaction();

            // Set the custom animations
            transaction.setCustomAnimations(
                    R.anim.slide_in_right,   // Enter animation
                    0
            );

            // Replace the current fragment with DevicesLoginFragment
            transaction.replace(R.id.fragment_container, sessionSettingsFragment);

            // Optionally add to back stack
            transaction.addToBackStack(null);

            // Commit the transaction
            transaction.commit();
        });

        return rootView;
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
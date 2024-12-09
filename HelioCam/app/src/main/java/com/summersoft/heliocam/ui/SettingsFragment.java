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
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.summersoft.heliocam.R;

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
                databaseReference.child("key").get().addOnCompleteListener(task -> {
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
                String email= currentUserEmail;

                // Reference to the Firebase database
                DatabaseReference databaseReference = FirebaseDatabase.getInstance().getReference("users").child(emailKey).child("key");

                // Fetch the key value from Firebase
                databaseReference.get().addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        String settingsKey = task.getResult().getValue(String.class);

                        if (settingsKey != null && !settingsKey.isEmpty()) {
                            Intent emailIntent = new Intent(Intent.ACTION_SEND);
                            emailIntent.setType("message/rfc822");
                            emailIntent.putExtra(Intent.EXTRA_EMAIL, new String[]{currentUserEmail});
                            emailIntent.putExtra(Intent.EXTRA_SUBJECT, "Your Settings Key");
                            emailIntent.putExtra(Intent.EXTRA_TEXT, "Here is your settings key: " + settingsKey);

                            try {
                                // Launch email client
                                startActivity(Intent.createChooser(emailIntent, "Send email using..."));
                                Toast.makeText(requireContext(), "Email client opened. Please send the email!", Toast.LENGTH_SHORT).show();
                            } catch (android.content.ActivityNotFoundException ex) {
                                Toast.makeText(requireContext(), "No email clients installed.", Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            Toast.makeText(requireContext(), "Failed to retrieve the settings key.", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(requireContext(), "Error fetching settings key from Firebase.", Toast.LENGTH_SHORT).show();
                    }
                });
            });



            // Create and show the dialog
            AlertDialog dialog = builder.create();
            dialog.show();
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
}

package com.summersoft.heliocam.status;

import static androidx.constraintlayout.helper.widget.MotionEffect.TAG;

import android.os.Build;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.auth.FirebaseUser;

import java.util.HashMap;
import java.util.Map;

public class LogoutUser {
    public static void logoutUser() {
        FirebaseAuth mAuth = FirebaseAuth.getInstance();
        FirebaseUser currentUser = mAuth.getCurrentUser();

        if (currentUser != null) {
            String emailKey = currentUser.getEmail().replace(".", "_");

            // Get the reference to the user's data in the database
            FirebaseDatabase database = FirebaseDatabase.getInstance();
            DatabaseReference usersRef = database.getReference("users");

            // Get the device name for the current device
            String deviceName = Build.MANUFACTURER + " " + Build.DEVICE;

            // Loop through logininfo_<n> entries to find the corresponding entry for the device
            usersRef.child(emailKey).get().addOnCompleteListener(task -> {
                if (task.isSuccessful() && task.getResult() != null) {
                    boolean deviceFound = false;

                    // Iterate over all logininfo_<n> entries
                    for (DataSnapshot snapshot : task.getResult().getChildren()) {
                        String key = snapshot.getKey();
                        if (key != null && key.startsWith("logininfo_")) {
                            // Get the device name from the snapshot
                            String savedDeviceName = snapshot.child("deviceName").getValue(String.class);

                            // Check if the device names match
                            if (deviceName.equals(savedDeviceName)) {
                                deviceFound = true;

                                // Prepare data to set login_status to 0
                                Map<String, Object> deviceData = new HashMap<>();
                                deviceData.put("login_status", 0);  // Set login_status to 0

                                // Update the login_status to 0 for this entry
                                usersRef.child(emailKey).child(key).updateChildren(deviceData)
                                        .addOnSuccessListener(aVoid -> {
                                            Log.d(TAG, "User's login_status set to 0 for device: " + deviceName);
                                        })
                                        .addOnFailureListener(e -> {
                                            Log.e(TAG, "Failed to update login_status: " + e.getMessage());
                                        });
                                break; // Exit the loop once the device is found and updated
                            }
                        }
                    }

                    // If no matching device was found, log the user out and update database if needed
                    if (!deviceFound) {
                        Log.d(TAG, "No matching device found for: " + deviceName);
                    }
                } else {
                    Log.e(TAG, "Failed to retrieve user data: " + (task.getException() != null ? task.getException().getMessage() : "Unknown error"));
                }
            });
        }

        // Sign out the user
        mAuth.signOut();
        Log.d(TAG, "User logged out.");
    }

    // This 'finish' method seems unused and can be removed if not needed.
    private void finish() {
    }
}

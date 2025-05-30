package com.summersoft.heliocam.status;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.widget.Toast;


import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;
import com.summersoft.heliocam.R;
import com.summersoft.heliocam.ui.HomeActivity;
import com.summersoft.heliocam.ui.PostLoginPreferencesActivity;

import android.util.Log; // Import for logging
import java.util.Map;
import java.util.HashMap;


public class LoginUser {

    private static final String TAG = "LoginUser"; // Define a tag for Logcat
    private Context context;
    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;

    public LoginUser(Context context) {
        this.context = context;
        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference(); // Get reference to Firebase DB
    }

    public void loginUser(String email, String password) {
        Log.d(TAG, "Attempting login for email: " + email);
        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "Authentication successful for email: " + email);
                        FirebaseUser user = mAuth.getCurrentUser();

                        // Check if email is verified
                        if (user != null && user.isEmailVerified()) {
                            Log.d(TAG, "Email verified for user: " + email);

                            // Proceed to preferences activity if email is verified
                            Intent intent = new Intent(context, PostLoginPreferencesActivity.class);
                            context.startActivity(intent);

                            // Format the email by replacing "." with "_"
                            String formattedEmail = email.replace(".", "_");
                            Log.d(TAG, "Formatted email: " + formattedEmail);

                            // Get the device name
                            String deviceName = Build.MANUFACTURER + " " + Build.DEVICE;
                            Log.d(TAG, "Device name: " + deviceName);

                            // Get the reference to the user's email in Firebase
                            DatabaseReference userRef = mDatabase.child("users").child(formattedEmail);

                            userRef.addListenerForSingleValueEvent(new ValueEventListener() {
                                @Override
                                public void onDataChange(DataSnapshot dataSnapshot) {
                                    boolean deviceFound = false;
                                    long maxIndex = 0;

                                    // Loop through all logininfo_(n) entries
                                    for (DataSnapshot loginInfoSnapshot : dataSnapshot.getChildren()) {
                                        if (loginInfoSnapshot.getKey().startsWith("logininfo_")) {
                                            // Retrieve deviceName from the login info entry
                                            String storedDeviceName = loginInfoSnapshot.child("deviceName").getValue(String.class);

                                            // Check if the device names match
                                            if (storedDeviceName != null && storedDeviceName.equals(deviceName)) {
                                                // Check current login_status value
                                                Long currentLoginStatus = loginInfoSnapshot.child("login_status").getValue(Long.class);

                                                if (currentLoginStatus == null || currentLoginStatus != 1) {
                                                    // Update login_status to 1 only if it's not already set
                                                    loginInfoSnapshot.getRef().child("login_status").setValue(1)
                                                            .addOnCompleteListener(task -> {
                                                                if (task.isSuccessful()) {
                                                                    Toast.makeText(context, "Login status updated successfully.", Toast.LENGTH_SHORT).show();
                                                                } else {
                                                                    Toast.makeText(context, "Failed to update login status.", Toast.LENGTH_SHORT).show();
                                                                }
                                                            });
                                                } else {
                                                    Toast.makeText(context, "Login status already set.", Toast.LENGTH_SHORT).show();
                                                }

                                                deviceFound = true;
                                                break; // Stop checking further login info once found and updated
                                            }
                                        }

                                        // Find the maximum index of logininfo_(n) entries
                                        if (loginInfoSnapshot.getKey().startsWith("logininfo_")) {
                                            String key = loginInfoSnapshot.getKey();
                                            try {
                                                long index = Long.parseLong(key.substring("logininfo_".length()));
                                                maxIndex = Math.max(maxIndex, index);
                                            } catch (NumberFormatException e) {
                                                e.printStackTrace(); // Ignore invalid keys
                                            }
                                        }
                                    }

                                    // If device not found, create a new logininfo_(n) entry
                                    if (!deviceFound) {
                                        // Create new logininfo entry with incremented index
                                        String newKey = "logininfo_" + (maxIndex + 1);
                                        Map<String, Object> deviceData = new HashMap<>();
                                        deviceData.put("deviceName", deviceName);
                                        deviceData.put("deviceOS", "Android " + Build.VERSION.RELEASE);
                                        deviceData.put("location", "Unknown Location"); // Add appropriate location
                                        deviceData.put("lastActive", System.currentTimeMillis());
                                        deviceData.put("login_status", 1);

                                        // Save new device info to Firebase
                                        userRef.child(newKey).setValue(deviceData)
                                                .addOnSuccessListener(aVoid -> {
                                                    Toast.makeText(context, "New device info saved to Realtime Database.", Toast.LENGTH_SHORT).show();
                                                })
                                                .addOnFailureListener(e -> {
                                                    Log.e(TAG, "Failed to save new device info.", e);
                                                    Toast.makeText(context, "Failed to save new device info.", Toast.LENGTH_SHORT).show();
                                                });
                                    }
                                }

                                @Override
                                public void onCancelled(DatabaseError databaseError) {
                                    Log.e(TAG, "Error retrieving login information.", databaseError.toException());
                                    Toast.makeText(context, "Error retrieving login information.", Toast.LENGTH_SHORT).show();
                                }
                            });
                        } else {
                            Log.d(TAG, "Email not verified for user: " + email);
                            Toast.makeText(context, "Please verify your email before logging in.", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Log.e(TAG, "Authentication failed for email: " + email, task.getException());
                        Toast.makeText(context, "Authentication failed. Check your credentials.", Toast.LENGTH_SHORT).show();
                    }
                });
    }
}

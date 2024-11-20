package com.summersoft.heliocam.status;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.provider.Settings;
import android.content.ContentResolver;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.summersoft.heliocam.ui.LoginActivity;

import java.util.HashMap;
import java.util.Map;

public class LoginStatus {
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;
    private static FusedLocationProviderClient fusedLocationClient;

    public static void checkLoginStatus(Context context) {
        FirebaseAuth mAuth = FirebaseAuth.getInstance();
        FirebaseUser currentUser = mAuth.getCurrentUser();

        if (currentUser == null) {
            Intent intent = new Intent(context, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            context.startActivity(intent);
        } else {
            fusedLocationClient = LocationServices.getFusedLocationProviderClient(context);
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

                // Request location permissions if not granted
                if (context instanceof AppCompatActivity) {
                    ActivityCompat.requestPermissions(
                            (AppCompatActivity) context,
                            new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                            LOCATION_PERMISSION_REQUEST_CODE
                    );
                }

                saveDeviceInfo(currentUser, "Unknown Location");
            } else {

                retrieveAndSaveLocation(context, currentUser);
            }
        }
    }

    public static String getUniqueDeviceId(Context context) {
        return Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
    }

    private static void retrieveAndSaveLocation(Context context, FirebaseUser currentUser) {
        try {
            fusedLocationClient.getLastLocation()
                    .addOnSuccessListener(location -> {
                        String locationString = "Unknown Location";
                        if (location != null) {
                            locationString = "Lat: " + location.getLatitude() + ", Lng: " + location.getLongitude();
                        }
                        saveDeviceInfo(currentUser, locationString);
                    })
                    .addOnFailureListener(e -> {
                        e.printStackTrace();
                        saveDeviceInfo(currentUser, "Unknown Location");
                    });
        } catch (SecurityException e) {
            e.printStackTrace();
            saveDeviceInfo(currentUser, "Unknown Location");
        }
    }

    private static void saveDeviceInfo(FirebaseUser currentUser, String deviceLocation) {
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        DatabaseReference usersRef = database.getReference("users");

        String emailKey = currentUser.getEmail().replace(".", "_");

        String deviceName = Build.MANUFACTURER + " " + Build.DEVICE;
        String deviceOS = "Android " + Build.VERSION.RELEASE;

        // Prepare device data to be updated
        Map<String, Object> deviceData = new HashMap<>();
        deviceData.put("deviceName", deviceName);
        deviceData.put("deviceOS", deviceOS);
        deviceData.put("location", deviceLocation);
        deviceData.put("lastActive", System.currentTimeMillis());
        deviceData.put("login_status", 1);  // Assuming the status should be 1 when logging in.

        DatabaseReference emailRef = usersRef.child(emailKey);

        // Fetch all existing logininfo_<n> keys
        emailRef.get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult() != null) {
                boolean isExistingDevice = false;
                long maxIndex = 0;
                String matchingKey = null;

                // Loop through all logininfo_<n> entries
                for (DataSnapshot snapshot : task.getResult().getChildren()) {
                    String key = snapshot.getKey();
                    if (key != null && key.startsWith("logininfo_")) {
                        try {
                            // Extract the number from the key
                            long index = Long.parseLong(key.substring("logininfo_".length()));
                            maxIndex = Math.max(maxIndex, index);

                            // Check if this entry matches the current device name
                            String savedDeviceName = snapshot.child("deviceName").getValue(String.class);
                            Integer savedLoginStatus = snapshot.child("login_status").getValue(Integer.class);

                            if (deviceName.equals(savedDeviceName)) {
                                isExistingDevice = true;
                                matchingKey = key;

                                // Check if login_status is 0 and log out the user if it is
                                if (savedLoginStatus != null && savedLoginStatus == 0) {
                                    // Logout user if status is 0
                                    LogoutUser.logoutUser();
                                }
                            }
                        } catch (NumberFormatException e) {
                            e.printStackTrace(); // Ignore invalid keys
                        }
                    }
                }

                if (isExistingDevice && matchingKey != null) {
                    // Update the existing logininfo_<n> entry without overwriting existing fields
                    emailRef.child(matchingKey).updateChildren(deviceData)
                            .addOnSuccessListener(aVoid -> {
                                System.out.println("Existing device info updated in Realtime Database.");
                            })
                            .addOnFailureListener(e -> {
                                e.printStackTrace();
                            });
                } else {
                    // Create a new logininfo_<n> entry
                    String newKey = "logininfo_" + (maxIndex + 1);
                    emailRef.child(newKey).setValue(deviceData)
                            .addOnSuccessListener(aVoid -> {
                                System.out.println("New device info saved to Realtime Database.");
                            })
                            .addOnFailureListener(e -> {
                                e.printStackTrace();
                            });
                }
            } else {
                System.err.println("Failed to retrieve user data: " + (task.getException() != null ? task.getException().getMessage() : "Unknown error"));
            }
        });
    }







    public static void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults, Context context) {
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
                if (currentUser != null) {
                    retrieveAndSaveLocation(context, currentUser);
                }
            } else {
                FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
                if (currentUser != null) {
                    saveDeviceInfo(currentUser, "Unknown Location");
                }
            }
        }
    }
}
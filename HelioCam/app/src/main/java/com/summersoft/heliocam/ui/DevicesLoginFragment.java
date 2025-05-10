package com.summersoft.heliocam.ui;

import android.os.Build;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.summersoft.heliocam.R;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import android.location.Address;
import android.location.Geocoder;

public class DevicesLoginFragment extends Fragment {

    private static final String TAG = "DevicesLoginFragment";
    private DatabaseReference databaseReference;
    private LinearLayout currentlyLoggedInContainer;
    private LinearLayout otherDevicesContainer;

    public DevicesLoginFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the fragment layout
        View rootView = inflater.inflate(R.layout.fragment_devices_login, container, false);

        // Initialize Firebase Database reference
        databaseReference = FirebaseDatabase.getInstance().getReference("users");

        // Get containers for adding device cards
        currentlyLoggedInContainer = rootView.findViewById(R.id.currently_logged_in_container);
        otherDevicesContainer = rootView.findViewById(R.id.other_devices_container);

        // Get the logged-in user's email from Firebase Authentication
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();

        if (currentUser != null) {
            String userEmail = currentUser.getEmail();
            if (userEmail != null) {
                String sanitizedEmail = userEmail.replace(".", "_");

                // Fetch data from Firebase for the logged-in user
                fetchDevicesForUser(sanitizedEmail);
            } else {
                Log.e(TAG, "Logged-in user email is null.");
            }
        } else {
            Log.e(TAG, "No logged-in user found.");
        }

        return rootView;
    }

    private void fetchDevicesForUser(String sanitizedEmail) {
        databaseReference.child(sanitizedEmail).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot userSnapshot) {
                // Clear the containers to prevent duplicate entries
                currentlyLoggedInContainer.removeAllViews();
                otherDevicesContainer.removeAllViews();

                // Current device name for comparison
                String currentDeviceName = Build.MANUFACTURER + " " + Build.DEVICE;
                
                // Store devices to process them
                List<DeviceData> devices = new ArrayList<>();

                if (userSnapshot.exists()) {
                    // Loop through the child nodes
                    for (DataSnapshot childSnapshot : userSnapshot.getChildren()) {
                        if (childSnapshot.getKey() != null && childSnapshot.getKey().startsWith("logininfo_")) {
                            // Get device name - handle both String and HashMap cases
                            String deviceName = null;
                            Object deviceNameObj = childSnapshot.child("deviceName").getValue();
                            if (deviceNameObj instanceof String) {
                                deviceName = (String) deviceNameObj;
                            } else if (deviceNameObj instanceof HashMap) {
                                // Extract name from HashMap or create a descriptive name
                                HashMap<String, Object> deviceMap = (HashMap<String, Object>) deviceNameObj;
                                if (deviceMap.containsKey("model")) {
                                    deviceName = deviceMap.get("model").toString();
                                } else if (deviceMap.containsKey("name")) {
                                    deviceName = deviceMap.get("name").toString();
                                } else {
                                    deviceName = "Device " + childSnapshot.getKey().substring(10); // Use part of the key
                                }
                            }

                            // Get location - handle both String and HashMap cases
                            String location = null;
                            Object locationObj = childSnapshot.child("location").getValue();
                            if (locationObj instanceof String) {
                                location = (String) locationObj;
                            } else if (locationObj instanceof HashMap) {
                                HashMap<String, Object> locationMap = (HashMap<String, Object>) locationObj;
                                if (locationMap.containsKey("latitude") && locationMap.containsKey("longitude")) {
                                    location = "latitude:" + locationMap.get("latitude") + ", longitude:" + locationMap.get("longitude");
                                } else {
                                    location = "Unknown Location";
                                }
                            } else {
                                location = "Unknown Location";
                            }

                            // Get last active timestamp
                            Long lastActiveTimestamp = null;
                            Object timestampObj = childSnapshot.child("lastActive").getValue();
                            if (timestampObj instanceof Long) {
                                lastActiveTimestamp = (Long) timestampObj;
                            } else if (timestampObj instanceof Integer) {
                                lastActiveTimestamp = ((Integer) timestampObj).longValue();
                            } else if (timestampObj instanceof HashMap) {
                                // Some Firebase timestamps are represented as server values
                                lastActiveTimestamp = System.currentTimeMillis(); // Fallback to current time
                            } else {
                                lastActiveTimestamp = System.currentTimeMillis(); // Fallback to current time
                            }

                            String deviceKey = childSnapshot.getKey();

                            // Populate the card only if we could extract the required data
                            if (deviceName != null && location != null && lastActiveTimestamp != null) {
                                DeviceData device = new DeviceData(deviceName, location, lastActiveTimestamp, deviceKey);
                                devices.add(device);
                            }
                        }
                    }
                    
                    // Process devices
                    processDevices(devices, currentDeviceName);
                    
                } else {
                    Log.w(TAG, "User data not found for: " + sanitizedEmail);
                }

                // Add this for debugging
                for (DataSnapshot snapshot : userSnapshot.getChildren()) {
                    if (snapshot.getKey() != null && snapshot.getKey().startsWith("logininfo_")) {
                        Log.d(TAG, "Device key: " + snapshot.getKey());
                        for (DataSnapshot field : snapshot.getChildren()) {
                            Log.d(TAG, "  Field: " + field.getKey() + " = " + field.getValue() 
                                + " (type: " + (field.getValue() != null ? field.getValue().getClass().getSimpleName() : "null") + ")");
                        }
                    }
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Log.e(TAG, "Error fetching devices: " + error.getMessage());
            }
        });
    }
    
    private void processDevices(List<DeviceData> devices, String currentDeviceName) {
        // Sort devices by last active time (most recent first)
        devices.sort((d1, d2) -> Long.compare(d2.lastActiveTimestamp, d1.lastActiveTimestamp));
        
        boolean currentDeviceFound = false;
        
        for (int i = 0; i < devices.size(); i++) {
            DeviceData device = devices.get(i);
            
            // Check if this is the current device
            boolean isCurrentDevice = device.deviceName.equals(currentDeviceName);
            
            if (isCurrentDevice) {
                currentDeviceFound = true;
                // Add to the current device container
                addDeviceCard(device, currentlyLoggedInContainer, isCurrentDevice, i == devices.size() - 1);
            } else {
                // Add to the other devices container
                addDeviceCard(device, otherDevicesContainer, false, i == devices.size() - 1);
            }
        }
        
        // If no current device was found in the list
        if (!currentDeviceFound) {
            // Show empty state for current device
            View emptyView = getLayoutInflater().inflate(R.layout.empty_device_state, currentlyLoggedInContainer, false);
            currentlyLoggedInContainer.addView(emptyView);
        }
        
        // If no other devices
        if (otherDevicesContainer.getChildCount() == 0) {
            // Show empty state for other devices
            View emptyView = getLayoutInflater().inflate(R.layout.empty_device_state, otherDevicesContainer, false);
            TextView emptyText = emptyView.findViewById(R.id.empty_state_text);
            emptyText.setText("No other devices found");
            otherDevicesContainer.addView(emptyView);
        }
    }

    private void addDeviceCard(DeviceData device, LinearLayout container, boolean isCurrentDevice, boolean isLastDevice) {
        // Inflate the device_info_card layout
        View deviceCardView = getLayoutInflater().inflate(R.layout.device_info_card, container, false);

        // Set the device name
        TextView deviceNameTextView = deviceCardView.findViewById(R.id.device_1_name);
        ImageView deviceIcon = deviceCardView.findViewById(R.id.device_icon);
        
        if (isCurrentDevice) {
            deviceNameTextView.setText("This Phone");
            deviceNameTextView.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
            deviceIcon.setImageResource(R.drawable.baseline_smartphone_24);
        } else {
            deviceNameTextView.setText(device.deviceName);
            
            // Set appropriate icon based on device name
            if (device.deviceName.toLowerCase().contains("phone") || 
                device.deviceName.toLowerCase().contains("pixel") ||
                device.deviceName.toLowerCase().contains("samsung") ||
                device.deviceName.toLowerCase().contains("xiaomi")) {
                deviceIcon.setImageResource(R.drawable.baseline_smartphone_24);
            } else if (device.deviceName.toLowerCase().contains("tablet") ||
                       device.deviceName.toLowerCase().contains("ipad") ||
                       device.deviceName.toLowerCase().contains("tab")) {
                deviceIcon.setImageResource(R.drawable.baseline_tablet_24);
            } else {
                deviceIcon.setImageResource(R.drawable.baseline_device_24);
            }
        }

        // Get the latitude and longitude from the location string
        String[] locationParts = device.location.split(",");
        if (locationParts.length == 2) {
            try {
                double latitude = Double.parseDouble(locationParts[0].split(":")[1].trim());
                double longitude = Double.parseDouble(locationParts[1].split(":")[1].trim());
                String cityAndCountry = getCityAndCountryFromLatLng(latitude, longitude);

                // Set the device location (city and country)
                TextView deviceLocationTextView = deviceCardView.findViewById(R.id.device_1_location);
                deviceLocationTextView.setText(cityAndCountry);
            } catch (NumberFormatException e) {
                Log.e(TAG, "Error parsing latitude and longitude: " + e.getMessage());
            }
        }

        // Format and display the last active time
        TextView lastActiveTextView = deviceCardView.findViewById(R.id.device_1_last_active);
        String formattedDate = formatTimestamp(device.lastActiveTimestamp);
        lastActiveTextView.setText(formattedDate);
        
        // Hide divider for last item
        if (isLastDevice) {
            deviceCardView.findViewById(R.id.divider).setVisibility(View.GONE);
        }
        
        // Setup action button
        ImageButton actionButton = deviceCardView.findViewById(R.id.device_action_button);
        actionButton.setOnClickListener(v -> {
            showDeviceActions(device, isCurrentDevice);
        });

        // Add the card to the container
        container.addView(deviceCardView);
    }
    
    private void showDeviceActions(DeviceData device, boolean isCurrentDevice) {
        String[] options;
        if (isCurrentDevice) {
            options = new String[]{"Refresh Status"};
        } else {
            options = new String[]{"Log Out Device", "Device Details"};
        }
        
        new AlertDialog.Builder(requireContext())
            .setTitle(isCurrentDevice ? "This Device" : device.deviceName)
            .setItems(options, (dialog, which) -> {
                if (isCurrentDevice) {
                    // Refresh status option
                    if (which == 0) {
                        Toast.makeText(requireContext(), "Refreshing device status...", Toast.LENGTH_SHORT).show();
                        // Refresh logic here
                    }
                } else {
                    // Other device options
                    if (which == 0) {
                        // Log out device option
                        confirmLogoutDevice(device);
                    } else if (which == 1) {
                        // Device details option
                        showDeviceDetails(device);
                    }
                }
            })
            .show();
    }
    
    private void confirmLogoutDevice(DeviceData device) {
        new AlertDialog.Builder(requireContext())
            .setTitle("Log Out Device")
            .setMessage("Are you sure you want to log out this device?\n\n" + device.deviceName)
            .setPositiveButton("Log Out", (dialog, which) -> {
                // Implement the logout logic here
                FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
                if (currentUser != null && currentUser.getEmail() != null) {
                    String sanitizedEmail = currentUser.getEmail().replace(".", "_");
                    
                    // Remove the device login info
                    databaseReference.child(sanitizedEmail).child(device.deviceKey).removeValue()
                        .addOnSuccessListener(aVoid -> {
                            Toast.makeText(requireContext(), 
                                "Device logged out successfully", Toast.LENGTH_SHORT).show();
                        })
                        .addOnFailureListener(e -> {
                            Toast.makeText(requireContext(),
                                "Failed to log out device: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        });
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }
    
    private void showDeviceDetails(DeviceData device) {
        // Implement showing detailed device info
        // This could be another dialog or fragment with more information
        Toast.makeText(requireContext(), "Showing details for " + device.deviceName, Toast.LENGTH_SHORT).show();
    }

    private String getCityAndCountryFromLatLng(double latitude, double longitude) {
        Geocoder geocoder = new Geocoder(getContext(), Locale.getDefault());
        try {
            List<Address> addresses = geocoder.getFromLocation(latitude, longitude, 1);
            if (addresses != null && !addresses.isEmpty()) {
                Address address = addresses.get(0);
                String city = address.getLocality();
                String country = address.getCountryName();
                return city + ", " + country;
            }
        } catch (IOException e) {
            Log.e(TAG, "Error fetching address: " + e.getMessage());
        }
        return "Unknown Location";
    }

    private String formatTimestamp(Long timestamp) {
        if (timestamp == null) return "Unknown";
        
        // Get current time
        long currentTime = System.currentTimeMillis();
        long diff = currentTime - timestamp;
        
        // If less than a minute ago
        if (diff < 60 * 1000) {
            return "Just now";
        }
        // If less than an hour ago
        else if (diff < 60 * 60 * 1000) {
            int minutes = (int) (diff / (60 * 1000));
            return minutes + " minute" + (minutes > 1 ? "s" : "") + " ago";
        }
        // If less than a day ago
        else if (diff < 24 * 60 * 60 * 1000) {
            int hours = (int) (diff / (60 * 60 * 1000));
            return hours + " hour" + (hours > 1 ? "s" : "") + " ago";
        }
        // Otherwise show the date
        else {
            Date date = new Date(timestamp);
            return DateFormat.format("MMM dd, yyyy • hh:mm a", date).toString();
        }
    }
    
    // Helper class to store device data
    private static class DeviceData {
        String deviceName;
        String location;
        long lastActiveTimestamp;
        String deviceKey;
        
        DeviceData(String deviceName, String location, long lastActiveTimestamp, String deviceKey) {
            this.deviceName = deviceName;
            this.location = location;
            this.lastActiveTimestamp = lastActiveTimestamp;
            this.deviceKey = deviceKey;
        }
    }
}

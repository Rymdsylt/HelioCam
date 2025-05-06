package com.summersoft.heliocam.ui;

import android.os.Build;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.fragment.app.Fragment;
import androidx.cardview.widget.CardView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.summersoft.heliocam.R;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import android.location.Address;
import android.location.Geocoder;

public class DevicesLoginFragment extends Fragment {

    private static final String TAG = "DevicesLoginFragment";
    private DatabaseReference databaseReference;
    private LinearLayout currentlyLoggedInContainer;

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

        // Get container for adding device cards
        currentlyLoggedInContainer = rootView.findViewById(R.id.currently_logged_in_container);

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
                // Clear the container to prevent duplicate entries
                currentlyLoggedInContainer.removeAllViews();

                if (userSnapshot.exists()) {
                    // Loop through the child nodes
                    for (DataSnapshot childSnapshot : userSnapshot.getChildren()) {
                        if (childSnapshot.getKey() != null && childSnapshot.getKey().startsWith("logininfo_")) {
                            String deviceName = childSnapshot.child("deviceName").getValue(String.class);
                            String location = childSnapshot.child("location").getValue(String.class);
                            Long lastActiveTimestamp = childSnapshot.child("lastActive").getValue(Long.class);

                            // Populate the card only if all required data is available
                            if (deviceName != null && location != null && lastActiveTimestamp != null) {
                                addDeviceCard(deviceName, location, lastActiveTimestamp);
                            } else {
                                Log.w(TAG, "Missing data (deviceName, location, or lastActive) for a login session.");
                            }
                        }
                    }
                } else {
                    Log.w(TAG, "User data not found for: " + sanitizedEmail);
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Log.e(TAG, "Error fetching devices: " + error.getMessage());
            }
        });
    }

    private void addDeviceCard(String deviceName, String location, Long lastActiveTimestamp) {
        // Inflate the device_info_card layout
        CardView deviceCardView = (CardView) getLayoutInflater().inflate(R.layout.device_info_card, currentlyLoggedInContainer, false);

        // Set the device name
        TextView deviceNameTextView = deviceCardView.findViewById(R.id.device_1_name);

        // Check if the device name matches Build.MANUFACTURER + Build.DEVICE
        String currentDeviceName = Build.MANUFACTURER + " " + Build.DEVICE;

        // Check if the current device matches the device in the database
        if (deviceName.equals(currentDeviceName)) {
            deviceNameTextView.setText("This Phone");
            deviceNameTextView.setTextColor(getResources().getColor(android.R.color.holo_green_dark)); // Green color

            // Add the card to the top of the container
            currentlyLoggedInContainer.addView(deviceCardView, 0); // Add at index 0 (top most)
        } else {
            deviceNameTextView.setText(deviceName); // Display the name of the other device

            // Add the card at the bottom of the container (default behavior)
            currentlyLoggedInContainer.addView(deviceCardView);
        }

        // Get the latitude and longitude from the location string
        String[] locationParts = location.split(",");
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
        String formattedDate = formatTimestamp(lastActiveTimestamp);
        lastActiveTextView.setText("Last Active: " + formattedDate);
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
        Date date = new Date(timestamp);
        return DateFormat.format("MMM dd, yyyy hh:mm a", date).toString(); // Example: "Nov 19, 2024 05:30 PM"
    }
}

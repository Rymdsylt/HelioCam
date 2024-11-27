package com.summersoft.heliocam.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

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

        return rootView;
    }
}

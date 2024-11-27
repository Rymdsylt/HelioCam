package com.summersoft.heliocam.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.fragment.app.Fragment;

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
            // Start NotificationSettingsActivity when the btnNotification is clicked
            Intent intent = new Intent(getActivity(), NotificationSettingsActivity.class);
            startActivity(intent);
        });

        // Set OnClickListener for btDevicesLogin
        btDevicesLogin.setOnClickListener(v -> {
            // Start DevicesLoginActivity when btDevicesLogin is clicked
            Intent intent = new Intent(getActivity(), DevicesLoginActivity.class);
            startActivity(intent);
        });

        return rootView;
    }
}

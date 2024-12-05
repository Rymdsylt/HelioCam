package com.summersoft.heliocam.ui;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.summersoft.heliocam.R;
import com.summersoft.heliocam.status.LoginStatus;

public class SessionSettingsFragment extends Fragment {

    private Button qualityButton, saveLocationButton, cameraSettingsButton;
    private ListView qualityDropdown, saveLocationDropdown, cameraSettingsDropdown;

    private final String[] qualityOptions = {"1080p", "720p", "480p (Default)", "Auto"};
    private final String[] saveLocationOptions = {"Internal Storage", "External Storage"};
    private final String[] cameraSettingsOptions = {"Sound Detection", "Notification Alert"};

    @SuppressLint("MissingInflatedId")
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View rootView = inflater.inflate(R.layout.fragment_session_settings, container, false);
        LoginStatus.checkLoginStatus(requireContext()); // Check login status

        // Initialize Buttons
        qualityButton = rootView.findViewById(R.id.quality_btn);
        saveLocationButton = rootView.findViewById(R.id.saveLocation_btn);
        cameraSettingsButton = rootView.findViewById(R.id.cameraSettings_btn);

        // Initialize Dropdowns
        qualityDropdown = rootView.findViewById(R.id.qualityOptions);
        saveLocationDropdown = rootView.findViewById(R.id.sLocationOptions);
        cameraSettingsDropdown = rootView.findViewById(R.id.cSettingsOptions);

        // Setup Dropdown Adapters
        setupDropdown(qualityButton, qualityDropdown, qualityOptions);
        setupDropdown(saveLocationButton, saveLocationDropdown, saveLocationOptions);
        setupDropdown(cameraSettingsButton, cameraSettingsDropdown, cameraSettingsOptions);

        return rootView;
    }

    /**
     * Sets up a dropdown for a button and ListView combination.
     *
     * @param button   The button to trigger the dropdown.
     * @param dropdown The ListView representing the dropdown.
     * @param options  The options to display in the dropdown.
     */
    private void setupDropdown(Button button, ListView dropdown, String[] options) {
        // Create and set an ArrayAdapter for the dropdown ListView
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_1, options);
        dropdown.setAdapter(adapter);

        // Handle button click to toggle dropdown visibility
        button.setOnClickListener(v -> {
            if (dropdown.getVisibility() == View.GONE) {
                // Show the dropdown
                dropdown.setVisibility(View.VISIBLE);
            } else {
                // Hide the dropdown
                dropdown.setVisibility(View.GONE);
            }
        });

        // Handle item selection from the dropdown
        dropdown.setOnItemClickListener((parent, view, position, id) -> {
            // Update the button text with the selected option
            String selectedOption = (String) parent.getItemAtPosition(position);
            button.setText(selectedOption);
            dropdown.setVisibility(View.GONE); // Hide the dropdown
        });
    }
}

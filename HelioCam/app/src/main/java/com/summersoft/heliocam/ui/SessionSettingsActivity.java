package com.summersoft.heliocam.ui;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.summersoft.heliocam.R;
import com.summersoft.heliocam.status.LoginStatus;

public class SessionSettingsActivity extends AppCompatActivity {

    private Button qualityButton, saveLocationButton, cameraSettingsButton;
    private ListView qualityDropdown, saveLocationDropdown, cameraSettingsDropdown;

    private final String[] qualityOptions = {"1080p", "720p", "480p (Default)", "Auto"};
    private final String[] saveLocationOptions = {"Internal Storage", "External Storage"};
    private final String[] cameraSettingsOptions = {"Sound Detection", "Notification Alert"};

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_session_settings);
        LoginStatus.checkLoginStatus(this);

        // Initialize Buttons
        qualityButton = findViewById(R.id.quality_btn);
        saveLocationButton = findViewById(R.id.saveLocation_btn);
        cameraSettingsButton = findViewById(R.id.cameraSettings_btn);

        // Initialize Dropdowns
        qualityDropdown = findViewById(R.id.qualityOptions);
        saveLocationDropdown = findViewById(R.id.sLocationOptions);
        cameraSettingsDropdown = findViewById(R.id.cSettingsOptions);

        // Setup Dropdown Adapters
        setupDropdown(qualityButton, qualityDropdown, qualityOptions);
        setupDropdown(saveLocationButton, saveLocationDropdown, saveLocationOptions);
        setupDropdown(cameraSettingsButton, cameraSettingsDropdown, cameraSettingsOptions);
    }

    /**
     * Sets up a dropdown for a button and ListView combination.
     *
     * @param button The button to trigger the dropdown.
     * @param dropdown The ListView representing the dropdown.
     * @param options The options to display in the dropdown.
     */
    private void setupDropdown(Button button, ListView dropdown, String[] options) {
        // Create and set an ArrayAdapter for the dropdown ListView
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, options);
        dropdown.setAdapter(adapter);

        // Handle button click to toggle dropdown visibility
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (dropdown.getVisibility() == View.GONE) {
                    // Show the dropdown
                    dropdown.setVisibility(View.VISIBLE);
                } else {
                    // Hide the dropdown
                    dropdown.setVisibility(View.GONE);
                }
            }
        });

        // Handle item selection from the dropdown
        dropdown.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                // Update the button text with the selected option
                String selectedOption = (String) parent.getItemAtPosition(position);
                button.setText(selectedOption);
                dropdown.setVisibility(View.GONE); // Hide the dropdown
            }
        });
    }
}

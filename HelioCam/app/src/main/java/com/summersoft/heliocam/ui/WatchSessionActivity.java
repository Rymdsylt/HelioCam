package com.summersoft.heliocam.ui;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.summersoft.heliocam.R;


import java.lang.reflect.Type;
import java.util.Map;

public class WatchSessionActivity extends AppCompatActivity {

    private static final String TAG = "WatchSessionActivity";
    private TextView cameraDisabledMessage;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_watch_session);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        SharedPreferences sharedPreferences = getSharedPreferences("SESSION_PREFS", MODE_PRIVATE);
        String sessionDataJson = sharedPreferences.getString("SESSION_DATA", null);

        if (sessionDataJson != null) {
            try {
                // Parse the JSON string
                Gson gson = new Gson();
                Type type = new TypeToken<Map<String, Object>>() {}.getType();
                Map<String, Object> sessionData = gson.fromJson(sessionDataJson, type);

                if (sessionData != null) {
                    // Log the full session data
                    Log.d(TAG, "Full Session Data: " + sessionData);

                    // Safely extract and log "Offer"
                    Object offerObject = sessionData.get("Offer");
                    if (offerObject instanceof String) {
                        String sdpOffer = (String) offerObject;
                        Log.d(TAG, "SDP Offer: " + sdpOffer);
                    } else {
                        Log.e(TAG, "'Offer' is not a String or is missing.");
                    }

                    // Safely extract and log "ice_candidates"
                    Object iceCandidatesObject = sessionData.get("ice_candidates");
                    if (iceCandidatesObject instanceof Map) {
                        @SuppressWarnings("unchecked") // Suppress unchecked cast warning
                        Map<String, Object> iceCandidates = (Map<String, Object>) iceCandidatesObject;
                        Log.d(TAG, "ICE Candidates: " + gson.toJson(iceCandidates));
                    } else {
                        Log.e(TAG, "'ice_candidates' is not a Map or is missing.");
                    }
                } else {
                    Log.e(TAG, "Failed to parse session data into a Map.");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error parsing session data JSON", e);
            }
        } else {
            Log.w(TAG, "No session data found in SharedPreferences.");
        }

        cameraDisabledMessage = findViewById(R.id.camera_disabled_message);
    }



    @Override
    protected void onDestroy() {
        super.onDestroy();
//a
    }
}

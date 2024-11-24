package com.summersoft.heliocam.ui;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.summersoft.heliocam.R;
import com.summersoft.heliocam.webrtc_utils.WebRTCManager;


import java.lang.reflect.Type;
import java.util.Map;

import org.webrtc.EglBase;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceViewRenderer;

public class WatchSessionActivity extends AppCompatActivity {

    private static final String TAG = "WatchSessionActivity";
    private TextView cameraDisabledMessage;
    private WebRTCManager webRTCManager;  // WebRTC manager to handle the connection

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_watch_session);

        // Handle window insets
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Retrieve session data from SharedPreferences
        SharedPreferences sharedPreferences = getSharedPreferences("SESSION_PREFS", MODE_PRIVATE);
        String sessionDataJson = sharedPreferences.getString("SESSION_DATA", null);

        // Initialize camera disabled message and SurfaceViewRenderer
        cameraDisabledMessage = findViewById(R.id.camera_disabled_message);
        SurfaceViewRenderer surfaceViewRenderer = findViewById(R.id.feed_view);
        surfaceViewRenderer.init(EglBase.create().getEglBaseContext(), null); // Initialize the SurfaceViewRenderer

        if (sessionDataJson != null) {
            try {
                Gson gson = new Gson();
                Type type = new TypeToken<Map<String, Object>>() {}.getType();
                Map<String, Object> sessionData = gson.fromJson(sessionDataJson, type);

                if (sessionData != null) {
                    // Extract the SDP Offer
                    Object offerObject = sessionData.get("Offer");
                    if (offerObject instanceof String) {
                        String sdpOffer = (String) offerObject;
                        Log.d(TAG, "SDP Offer: " + sdpOffer);

                        // Initialize WebRTC Manager and setup connection
                        webRTCManager = new WebRTCManager(this, surfaceViewRenderer, sdpOffer);
                        webRTCManager.setupConnection();  // Setup the connection using the SDP offer

                        // Create and send an SDP Answer
                        SessionDescription answerDescription = new SessionDescription(SessionDescription.Type.ANSWER, sdpOffer);
                        webRTCManager.setLocalDescription(answerDescription);  // Set the local SDP answer

                        // Now that the connection is setup, display the camera feed
                        cameraDisabledMessage.setVisibility(View.GONE); // Hide message, camera feed should show
                    } else {
                        Log.e(TAG, "'Offer' is not a String or is missing.");
                    }

                    // Handle ICE candidates if needed
                    Object iceCandidatesObject = sessionData.get("ice_candidates");
                    if (iceCandidatesObject instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Map<String, Object>> iceCandidates = (Map<String, Map<String, Object>>) iceCandidatesObject;
                        Log.d(TAG, "ICE Candidates: " + new Gson().toJson(iceCandidates));
                        // You can process the ICE candidates as needed, sending them to the peer connection
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
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (webRTCManager != null) {
            webRTCManager.closeConnection();  // Clean up WebRTC resources when activity is destroyed
        }
    }
}

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
import com.summersoft.heliocam.webrtc_utils.WebRTCManager;

import java.lang.reflect.Type;
import java.util.Map;

public class WatchSessionActivity extends AppCompatActivity {

    private static final String TAG = "WatchSessionActivity";
    private TextView cameraDisabledMessage;
    private WebRTCManager webRTCManager;

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
            Gson gson = new Gson();
            Type type = new TypeToken<Map<String, Object>>() {}.getType();
            Map<String, Object> sessionData = gson.fromJson(sessionDataJson, type);

            String sdpOffer = (String) sessionData.get("Offer");

            Log.d(TAG, "SDP Offer: " + sdpOffer);

            cameraDisabledMessage = findViewById(R.id.camera_disabled_message);

            // Initialize WebRTCManager
            webRTCManager = new WebRTCManager(this, findViewById(R.id.live_feed_view), sdpOffer);
            webRTCManager.setupConnection();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (webRTCManager != null) {
            webRTCManager.closeConnection();
        }
    }
}

package com.summersoft.heliocam.ui;

import android.content.SharedPreferences;
import android.graphics.SurfaceTexture;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.summersoft.heliocam.R;
import com.summersoft.heliocam.webrtc_utils.RTCJoin;
import com.google.android.material.button.MaterialButton;

import org.webrtc.IceCandidate;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.PeerConnection.IceServer;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.EglBase;  // Import the EglBase class
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.DefaultVideoDecoderFactory;

import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class WatchSessionActivity extends AppCompatActivity {

    private static final String TAG = "WatchSessionActivity";
    private TextView cameraDisabledMessage;
    private RTCJoin rtcJoin;
    private SurfaceViewRenderer feedView;
    private EglBase eglBase;  // Declare EglBase instance

    // Add TURN and STUN server details
    private String stunServer = "stun:stun.relay.metered.ca:80";
    private String turnServer = "turn:asia.relay.metered.ca:80?transport=tcp";
    private String turnUsername = "08a10b202c595304495012c2";
    private String turnPassword = "JnsH2+jc2q3/uGon";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_watch_session);

        // Initialize UI components
        cameraDisabledMessage = findViewById(R.id.camera_disabled_message);
        feedView = findViewById(R.id.feed_view);

        // Initialize the EglBase for OpenGL rendering
        eglBase = new EglBase() {
            @Override
            public void createSurface(Surface surface) {

            }

            @Override
            public void createSurface(SurfaceTexture surfaceTexture) {

            }

            @Override
            public void createDummyPbufferSurface() {

            }

            @Override
            public void createPbufferSurface(int i, int i1) {

            }

            @Override
            public Context getEglBaseContext() {
                return null;
            }

            @Override
            public boolean hasSurface() {
                return false;
            }

            @Override
            public int surfaceWidth() {
                return 0;
            }

            @Override
            public int surfaceHeight() {
                return 0;
            }

            @Override
            public void releaseSurface() {

            }

            @Override
            public void release() {

            }

            @Override
            public void makeCurrent() {

            }

            @Override
            public void detachCurrent() {

            }

            @Override
            public void swapBuffers() {

            }

            @Override
            public void swapBuffers(long l) {

            }
        };

        // Initialize the SurfaceViewRenderer for video feed
        feedView.init(eglBase.getEglBaseContext(), null);

        // Set padding for system bars
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Initialize PeerConnectionFactory with video encoder/decoder
        PeerConnectionFactory.InitializationOptions options =
                PeerConnectionFactory.InitializationOptions.builder(this)
                        .createInitializationOptions();
        PeerConnectionFactory.initialize(options);

        // Set up video encoder and decoder factories
        PeerConnectionFactory peerConnectionFactory = PeerConnectionFactory.builder()
                .setVideoEncoderFactory(new DefaultVideoEncoderFactory(eglBase.getEglBaseContext(), true, true))
                .setVideoDecoderFactory(new DefaultVideoDecoderFactory(eglBase.getEglBaseContext()))
                .createPeerConnectionFactory();

        // Initialize ICE servers with provided STUN and TURN server details
        List<IceServer> iceServers = Arrays.asList(
                new IceServer(stunServer),  // Provided STUN server
                new IceServer(turnServer, turnUsername, turnPassword)  // Provided TURN server with authentication
        );

        // Get session data from SharedPreferences
        SharedPreferences sharedPreferences = getSharedPreferences("SESSION_PREFS", MODE_PRIVATE);
        String sessionDataJson = sharedPreferences.getString("SESSION_DATA", null);

        if (sessionDataJson != null) {
            try {
                Gson gson = new Gson();
                Type type = new TypeToken<Map<String, Object>>() {}.getType();
                Map<String, Object> sessionData = gson.fromJson(sessionDataJson, type);

                if (sessionData != null) {
                    Log.d(TAG, "Full Session Data: " + sessionData);

                    // Extract SDP Offer
                    Object offerObject = sessionData.get("Offer");
                    if (offerObject instanceof String) {
                        String sdpOffer = (String) offerObject;
                        Log.d(TAG, "SDP Offer: " + sdpOffer);

                        // Initialize RTCJoin and join the session
                        rtcJoin = new RTCJoin(this, peerConnectionFactory, iceServers);
                        rtcJoin.joinSession(sdpOffer);  // Automatically join the session when the activity starts
                    } else {
                        Log.e(TAG, "'Offer' is not a String or is missing.");
                    }

                    // Add ICE Candidates
                    Object iceCandidatesObject = sessionData.get("ice_candidates");
                    if (iceCandidatesObject instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Map<String, Object>> iceCandidates = (Map<String, Map<String, Object>>) iceCandidatesObject;
                        for (Map.Entry<String, Map<String, Object>> entry : iceCandidates.entrySet()) {
                            Map<String, Object> candidateDetails = entry.getValue();
                            IceCandidate iceCandidate = new IceCandidate(
                                    (String) candidateDetails.get("candidate"),
                                    ((Double) candidateDetails.get("sdpMLineIndex")).intValue(),
                                    (String) candidateDetails.get("sdpMid")
                            );
                            rtcJoin.addIceCandidate(iceCandidate);
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error parsing session data JSON", e);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (rtcJoin != null) {
            rtcJoin.dispose();
        }

        // Clean up EglBase when the activity is destroyed
        if (eglBase != null) {
            eglBase.release();
        }
    }
}


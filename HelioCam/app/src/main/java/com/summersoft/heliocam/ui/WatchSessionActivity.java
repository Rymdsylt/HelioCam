package com.summersoft.heliocam.ui;

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

import org.webrtc.IceCandidate;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.PeerConnection.IceServer;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.EglBase;
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
    private EglBase eglBase;

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
        eglBase = EglBase.create();

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
                IceServer.builder(stunServer).createIceServer(),
                IceServer.builder(turnServer).setUsername(turnUsername).setPassword(turnPassword).createIceServer()
        );

        // Retrieve intent data
        String sessionKey = getIntent().getStringExtra("SESSION_KEY");
        String sessionName = getIntent().getStringExtra("SESSION_NAME");
        String sdpOffer = getIntent().getStringExtra("OFFER");
        String iceCandidatesJson = getIntent().getStringExtra("ICE_CANDIDATES");

        // Log received data
        Log.d(TAG, "Session Key: " + sessionKey);
        Log.d(TAG, "Session Name: " + sessionName);
        Log.d(TAG, "SDP Offer: " + sdpOffer);
        Log.d(TAG, "ICE Candidates JSON: " + iceCandidatesJson);

        // Parse and process the received session data
        if (sdpOffer != null) {
            rtcJoin = new RTCJoin(this, peerConnectionFactory, iceServers, sessionKey, feedView);  // Pass sessionKey and feedView
            rtcJoin.joinSession();
        }

        // Parse ICE candidates JSON into a Map
        if (iceCandidatesJson != null) {
            try {
                Gson gson = new Gson();
                Type type = new TypeToken<Map<String, Map<String, Object>>>() {}.getType();
                Map<String, Map<String, Object>> iceCandidates = gson.fromJson(iceCandidatesJson, type);

                if (iceCandidates != null) {
                    for (Map.Entry<String, Map<String, Object>> entry : iceCandidates.entrySet()) {
                        Map<String, Object> candidateDetails = entry.getValue();
                        IceCandidate iceCandidate = new IceCandidate(
                                (String) candidateDetails.get("sdpMid"),
                                ((Double) candidateDetails.get("sdpMLineIndex")).intValue(),
                                (String) candidateDetails.get("candidate")
                        );
                        rtcJoin.addIceCandidate(iceCandidate);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error parsing ICE candidates JSON", e);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (rtcJoin != null) {
            rtcJoin.dispose();
        }

        if (eglBase != null) {
            eglBase.release();
        }
    }
}

package com.summersoft.heliocam.ui;

import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.FirebaseDatabase;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.summersoft.heliocam.R;
import com.summersoft.heliocam.webrtc_utils.RTCJoiner;

import org.webrtc.IceCandidate;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.PeerConnection.IceServer;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.EglBase;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.DefaultVideoDecoderFactory;

import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

public class WatchSessionActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private static final String TAG = "WatchSessionActivity";
    private TextView cameraDisabledMessage;
    private RTCJoiner rtcJoin;
    private SurfaceViewRenderer feedView;
    private EglBase eglBase;
    private boolean ignoreRequests = false;

    private String stunServer = "stun:stun.relay.metered.ca:80";
    private String turnServer = "turn:asia.relay.metered.ca:80?transport=tcp";
    private String turnUsername = "08a10b202c595304495012c2";
    private String turnPassword = "JnsH2+jc2q3/uGon";

    private DatabaseReference mDatabase;

    private boolean isMicOn = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_watch_session);

        cameraDisabledMessage = findViewById(R.id.camera_disabled_message);
        feedView = findViewById(R.id.feed_view);

        eglBase = EglBase.create();
        ImageButton micButton = findViewById(R.id.microphone_button);

        feedView.init(eglBase.getEglBaseContext(), null);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        mDatabase = FirebaseDatabase.getInstance().getReference();

        PeerConnectionFactory.InitializationOptions options =
                PeerConnectionFactory.InitializationOptions.builder(this)
                        .createInitializationOptions();
        PeerConnectionFactory.initialize(options);

        PeerConnectionFactory peerConnectionFactory = PeerConnectionFactory.builder()
                .setVideoEncoderFactory(new DefaultVideoEncoderFactory(eglBase.getEglBaseContext(), true, true))
                .setVideoDecoderFactory(new DefaultVideoDecoderFactory(eglBase.getEglBaseContext()))
                .createPeerConnectionFactory();

        List<IceServer> iceServers = Arrays.asList(
                IceServer.builder(stunServer).createIceServer(),
                IceServer.builder(turnServer).setUsername(turnUsername).setPassword(turnPassword).createIceServer()
        );

        String sessionKey = getIntent().getStringExtra("SESSION_KEY");
        String sessionName = getIntent().getStringExtra("SESSION_NAME");
        String sdpOffer = getIntent().getStringExtra("OFFER");
        String iceCandidatesJson = getIntent().getStringExtra("ICE_CANDIDATES");

        Log.d(TAG, "Session Key: " + sessionKey);
        Log.d(TAG, "Session Name: " + sessionName);
        Log.d(TAG, "SDP Offer: " + sdpOffer);
        Log.d(TAG, "ICE Candidates JSON: " + iceCandidatesJson);

        if (sdpOffer != null) {
            rtcJoin = new RTCJoiner(this, peerConnectionFactory, iceServers, sessionKey, feedView);
            rtcJoin.joinSession();
        }

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

        listenForCameraStatus(sessionKey);

        micButton.setOnClickListener(v -> {
            toggleMic();
        });

        listenForMicStatus(sessionKey);

        listenForJoinRequests(sessionKey);

    }

    private void toggleMic() {
        isMicOn = !isMicOn;

        ImageButton micButton = findViewById(R.id.microphone_button);

        if (isMicOn) {
            micButton.setImageResource(R.drawable.ic_baseline_mic_24);
            if (rtcJoin != null) {
                rtcJoin.unmuteMic();
            }
        }
        else {
            micButton.setImageResource(R.drawable.ic_baseline_mic_off_24);
            if (rtcJoin != null) {
                rtcJoin.muteMic();
            }
        }
    }

    private void listenForCameraStatus(String sessionKey) {
        String userEmail = FirebaseAuth.getInstance().getCurrentUser().getEmail().replace(".", "_");

        mDatabase.child("users").child(userEmail).child("sessions").child(sessionKey)
                .child("camera_off")
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        if (dataSnapshot.exists()) {
                            Integer cameraOffFlag = dataSnapshot.getValue(Integer.class);
                            if (cameraOffFlag != null && cameraOffFlag == 1) {

                                cameraDisabledMessage.setText("Camera Off");
                                cameraDisabledMessage.setVisibility(View.VISIBLE);
                            }
                        } else {
                            cameraDisabledMessage.setVisibility(View.GONE);
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {
                        Log.e(TAG, "Failed to read camera_off status", databaseError.toException());
                    }
                });
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

        deleteWantJoinStatus();

    }

    private void deleteWantJoinStatus() {
        String userEmail = FirebaseAuth.getInstance().getCurrentUser().getEmail().replace(".", "_");
        String sessionKey = getIntent().getStringExtra("SESSION_KEY");

        if (sessionKey != null) {
            mDatabase.child("users").child(userEmail).child("sessions").child(sessionKey)
                    .child("want_join").removeValue();
        }
    }

    private void listenForMicStatus(String sessionKey) {
        String userEmail = FirebaseAuth.getInstance().getCurrentUser().getEmail().replace(".", "_");

        mDatabase.child("users").child(userEmail).child("sessions").child(sessionKey)
                .child("mic_on")
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        if (dataSnapshot.exists()) {
                            Integer micOnFlag = dataSnapshot.getValue(Integer.class);
                            if (micOnFlag != null && micOnFlag == 0) {

                                TextView micStatusMessage = findViewById(R.id.mic_status_message);
                                micStatusMessage.setVisibility(View.VISIBLE);
                            } else {

                                TextView micStatusMessage = findViewById(R.id.mic_status_message);
                                micStatusMessage.setVisibility(View.GONE);
                            }
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {
                        Log.e(TAG, "Failed to read mic_on status", databaseError.toException());
                    }
                });
    }

    private void listenForJoinRequests(String sessionKey) {
        String userEmail = FirebaseAuth.getInstance().getCurrentUser().getEmail().replace(".", "_");

        mDatabase.child("users").child(userEmail).child("sessions").child(sessionKey)
                .child("want_join")
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        if (!ignoreRequests && dataSnapshot.exists()) {
                            Integer wantJoinFlag = dataSnapshot.getValue(Integer.class);
                            if (wantJoinFlag != null && wantJoinFlag == 1) {
                                showJoinRequestDialog(sessionKey);
                            }
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {
                        Log.e(TAG, "Failed to read want_join status", databaseError.toException());
                    }
                });
    }

    private AlertDialog dialog;

    private void showJoinRequestDialog(String sessionKey) {
        if (isFinishing()) return;

        runOnUiThread(() -> {

            View dialogView = getLayoutInflater().inflate(R.layout.dialog_join_request, null);
            CheckBox ignoreCheckbox = dialogView.findViewById(R.id.ignore_checkbox);

            dialog = new AlertDialog.Builder(this)
                    .setTitle("Join Request")
                    .setMessage("Someone wants to join your session. Leave?")
                    .setView(dialogView)
                    .setPositiveButton("Yes", (dialog, which) -> {

                        deleteWantJoinStatus();
                        finish();
                    })
                    .setNegativeButton("No", (dialog, which) -> {
                        deleteWantJoinStatus();

                        if (ignoreCheckbox.isChecked()) {
                            ignoreRequests = true;
                            deleteWantJoinStatus();
                            resetIgnoreFlagAfterDelay();
                        }

                        Map<String, Object> declinedStatus = new HashMap<>();
                        declinedStatus.put("declined", 1);

                        String userEmail = FirebaseAuth.getInstance().getCurrentUser().getEmail().replace(".", "_");
                        mDatabase.child("users").child(userEmail).child("sessions").child(sessionKey)
                                .updateChildren(declinedStatus);
                    })
                    .setCancelable(false)
                    .show();
        });
    }


    private void resetIgnoreFlagAfterDelay() {
        new Handler().postDelayed(() -> {
            ignoreRequests = false;
            if (dialog != null && dialog.isShowing()) {
                dialog.dismiss();
            }
        }, 60000);
    }

}
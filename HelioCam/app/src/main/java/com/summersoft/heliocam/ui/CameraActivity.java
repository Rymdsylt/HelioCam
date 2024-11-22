package com.summersoft.heliocam.ui;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraVideoCapturer;
import org.webrtc.EglBase;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;
import org.webrtc.AudioTrack;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnection.IceConnectionState;
import org.webrtc.PeerConnection.IceServer;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.SurfaceTextureHelper;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.summersoft.heliocam.R;
import com.summersoft.heliocam.status.LoginStatus;

import java.util.ArrayList;
import java.util.List;

public class CameraActivity extends AppCompatActivity {

    private DatabaseReference mDatabase;
    private FirebaseAuth mAuth;
    private SurfaceViewRenderer cameraView;
    private PeerConnectionFactory peerConnectionFactory;
    private CameraVideoCapturer videoCapturer;
    private VideoTrack videoTrack;
    private AudioTrack audioTrack;
    private EglBase rootEglBase;
    private boolean isUsingFrontCamera = true;
    private boolean isCameraOn = true;

    // WebRTC PeerConnection
    private PeerConnection peerConnection;
    private List<IceServer> iceServers;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();

        // Fetch and display the session name
        fetchSessionName();

        String sessionName = getIntent().getStringExtra("session_name");
        Log.d("CameraActivity", "Session name: " + sessionName);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        cameraView = findViewById(R.id.camera_view);
        ImageButton switchCameraButton = findViewById(R.id.switch_camera_button);
        ImageButton videoButton = findViewById(R.id.video_button);

        // Initialize WebRTC and check permissions
        initializePeerConnectionFactory();
        checkCameraPermission();

        // Handle switch camera button click
        switchCameraButton.setOnClickListener(v -> switchCamera());

        // Handle video button click
        videoButton.setOnClickListener(v -> toggleCamera(videoButton));

        LoginStatus.checkLoginStatus(this);
    }

    private void initializePeerConnectionFactory() {
        PeerConnectionFactory.InitializationOptions options =
                PeerConnectionFactory.InitializationOptions.builder(this)
                        .createInitializationOptions();
        PeerConnectionFactory.initialize(options);
        peerConnectionFactory = PeerConnectionFactory.builder().createPeerConnectionFactory();

        rootEglBase = EglBase.create();
        cameraView.init(rootEglBase.getEglBaseContext(), null);
        cameraView.setMirror(true); // Enable mirroring for front camera

        iceServers = new ArrayList<>();
        iceServers.add(IceServer.builder("stun:stun.relay.metered.ca:80").createIceServer());
        iceServers.add(IceServer.builder("turn:asia.relay.metered.ca:80?transport=tcp")
                .setUsername("08a10b202c595304495012c2")
                .setPassword("JnsH2+jc2q3/uGon")
                .createIceServer());

        MediaConstraints constraints = new MediaConstraints();
        PeerConnection.RTCConfiguration rtcConfig = new PeerConnection.RTCConfiguration(iceServers);
        peerConnection = peerConnectionFactory.createPeerConnection(rtcConfig, new PeerConnection.Observer() {
            @Override
            public void onIceConnectionChange(IceConnectionState newState) {
                Log.d("PeerConnection", "ICE Connection State Changed: " + newState);
            }

            @Override
            public void onIceCandidate(org.webrtc.IceCandidate candidate) {
                // Handle ICE candidates if needed
            }

            @Override
            public void onAddStream(MediaStream mediaStream) {
                Log.d("PeerConnection", "Media Stream Added");
                if (mediaStream.audioTracks.size() > 0) {
                    audioTrack = mediaStream.audioTracks.get(0);
                }
                if (mediaStream.videoTracks.size() > 0) {
                    videoTrack = mediaStream.videoTracks.get(0);
                }
            }

            @Override
            public void onRemoveStream(MediaStream mediaStream) {
                // Handle stream removal
            }

            @Override
            public void onDataChannel(org.webrtc.DataChannel dataChannel) {
                // Handle DataChannel (if used)
            }

            @Override
            public void onRenegotiationNeeded() {
                // Handle renegotiation (if needed)
            }

            @Override
            public void onSignalingChange(PeerConnection.SignalingState signalingState) {
                Log.d("PeerConnection", "Signaling State Changed: " + signalingState);
            }
        });

        MediaStream mediaStream = peerConnectionFactory.createLocalMediaStream("localStream");

        // Add video track to media stream
        mediaStream.addTrack(videoTrack);
        // Add audio track to media stream
        mediaStream.addTrack(audioTrack);

        // Set local media stream to the peer connection
        peerConnection.addStream(mediaStream);
    }

    private void checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA},
                    100);
        } else {
            initializeCamera();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initializeCamera();
            } else {
                Toast.makeText(this, "Camera permission is required to use this feature.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void initializeCamera() {
        Camera2Enumerator cameraEnumerator = new Camera2Enumerator(this);

        videoCapturer = createCameraCapturer(cameraEnumerator, isUsingFrontCamera);
        if (videoCapturer == null) {
            Toast.makeText(this, "Camera initialization failed.", Toast.LENGTH_LONG).show();
            return;
        }

        VideoSource videoSource = peerConnectionFactory.createVideoSource(videoCapturer.isScreencast());
        videoCapturer.initialize(SurfaceTextureHelper.create("CaptureThread", rootEglBase.getEglBaseContext()),
                getApplicationContext(), videoSource.getCapturerObserver());
        videoTrack = peerConnectionFactory.createVideoTrack("videoTrack", videoSource);

        // Create audio track (default microphone track)
        audioTrack = peerConnectionFactory.createAudioTrack("audioTrack", peerConnectionFactory.createAudioSource(new MediaConstraints()));

        cameraView.post(() -> {
            videoTrack.addSink(cameraView);
            try {
                videoCapturer.startCapture(1280, 720, 30);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private CameraVideoCapturer createCameraCapturer(Camera2Enumerator cameraEnumerator, boolean useFrontCamera) {
        for (String deviceName : cameraEnumerator.getDeviceNames()) {
            if (useFrontCamera && cameraEnumerator.isFrontFacing(deviceName)) {
                return cameraEnumerator.createCapturer(deviceName, null);
            } else if (!useFrontCamera && cameraEnumerator.isBackFacing(deviceName)) {
                return cameraEnumerator.createCapturer(deviceName, null);
            }
        }
        return null;
    }

    private void toggleCamera(ImageButton videoButton) {
        if (videoCapturer == null) {
            Toast.makeText(this, "Camera is not initialized.", Toast.LENGTH_SHORT).show();
            return;
        }

        TextView cameraDisabledText = findViewById(R.id.camera_disabled_text);

        if (isCameraOn) {
            try {
                videoCapturer.stopCapture();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            videoTrack.setEnabled(false);
            videoButton.setImageResource(R.drawable.ic_baseline_videocam_off_24);

            cameraDisabledText.setVisibility(View.VISIBLE);
            cameraView.setVisibility(View.INVISIBLE);
        } else {
            try {
                videoCapturer.startCapture(1280, 720, 30);
            } catch (Exception e) {
                e.printStackTrace();
            }
            videoTrack.setEnabled(true);
            videoButton.setImageResource(R.drawable.ic_baseline_videocam_24);

            cameraDisabledText.setVisibility(View.GONE);
            cameraView.setVisibility(View.VISIBLE);
        }

        isCameraOn = !isCameraOn;
    }

    private void switchCamera() {
        if (videoCapturer != null) {
            try {
                videoCapturer.stopCapture();
                videoCapturer.dispose();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            isUsingFrontCamera = !isUsingFrontCamera;
            initializeCamera();
        }
    }

    private void fetchSessionName() {
        String userEmail = mAuth.getCurrentUser().getEmail().replace(".", "_");

        mDatabase.child("users").child(userEmail).child("sessions").orderByKey().limitToLast(1)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                        if (dataSnapshot.exists()) {
                            for (DataSnapshot sessionSnapshot : dataSnapshot.getChildren()) {
                                String sessionName = sessionSnapshot.child("session_name").getValue(String.class);
                                Log.d("CameraActivity", "Fetched session name: " + sessionName);
                            }
                        } else {
                            Log.w("CameraActivity", "No sessions found for the user.");
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError databaseError) {
                        Log.e("CameraActivity", "Failed to fetch session name: " + databaseError.getMessage());
                    }
                });
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (videoCapturer != null) {
            try {
                videoCapturer.stopCapture();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            videoCapturer.dispose();
        }
        if (peerConnection != null) {
            peerConnection.close();
        }
        peerConnectionFactory.dispose();
        rootEglBase.release();
    }
}

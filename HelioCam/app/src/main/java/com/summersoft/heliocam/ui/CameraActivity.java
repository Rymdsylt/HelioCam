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
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SurfaceTextureHelper;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.summersoft.heliocam.R;
import com.summersoft.heliocam.databinding.ActivityCameraBinding;  // Import the generated binding class
import com.summersoft.heliocam.status.LoginStatus;

import com.summersoft.heliocam.webrtc_utils.WebRTCClient;

public class CameraActivity extends AppCompatActivity {
    private DatabaseReference mDatabase;
    private FirebaseAuth mAuth;

    private SurfaceViewRenderer cameraView;
    private PeerConnectionFactory peerConnectionFactory;
    private CameraVideoCapturer videoCapturer;
    private VideoTrack videoTrack;
    private EglBase rootEglBase;
    private boolean isUsingFrontCamera = true;

    private boolean isCameraOn = true;

    private WebRTCClient webRTCClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();

        String sessionId = getIntent().getStringExtra("session_id");

        cameraView = findViewById(R.id.camera_view);
        ImageButton switchCameraButton = findViewById(R.id.switch_camera_button);
        ImageButton videoButton = findViewById(R.id.video_button);

        webRTCClient = new WebRTCClient(this, cameraView, mDatabase);

        // Get the user's email
        String userEmail = mAuth.getCurrentUser().getEmail().replace(".", "_");

        // Initialize WebRTC with sessionId and userEmail
        initializeWebRTC(sessionId, userEmail);

        switchCameraButton.setOnClickListener(v -> webRTCClient.startCamera(this, !isUsingFrontCamera));
        videoButton.setOnClickListener(v -> toggleCamera(videoButton));

        LoginStatus.checkLoginStatus(this);

        fetchSessionName();
    }

    private void initializeWebRTC(String sessionId, String email) {
        webRTCClient.startCamera(this, isUsingFrontCamera); // Start camera first
        webRTCClient.initializePeerConnection(sessionId, email);  // Pass sessionId and email
        webRTCClient.createOffer(sessionId, email);        // Create and send the offer with sessionId and email
    }



    private void toggleCamera(ImageButton videoButton) {
        if (videoCapturer == null) {
            Toast.makeText(this, "Camera is not initialized.", Toast.LENGTH_SHORT).show();
            return;
        }

        TextView cameraDisabledText = findViewById(R.id.camera_disabled_text);

        if (isCameraOn) {
            // Stop video feed
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


    private void initializePeerConnectionFactory() {
        PeerConnectionFactory.InitializationOptions options =
                PeerConnectionFactory.InitializationOptions.builder(this)
                        .createInitializationOptions();
        PeerConnectionFactory.initialize(options);
        peerConnectionFactory = PeerConnectionFactory.builder().createPeerConnectionFactory();

        rootEglBase = EglBase.create();
        cameraView.init(rootEglBase.getEglBaseContext(), null);
        cameraView.setMirror(true);
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

    private void fetchSessionName() {
        String sessionName = getIntent().getStringExtra("session_name");
        String sessionId = getIntent().getStringExtra("session_id");

        if (sessionId == null || sessionId.isEmpty()) {
            Log.w("CameraActivity", "Session ID is missing.");
            return;
        }

        Log.d("CameraActivity", "Camera is on session: " + sessionId);
        String userEmail = mAuth.getCurrentUser().getEmail().replace(".", "_");

        mDatabase.child("users").child(userEmail).child("sessions").child(sessionId)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult().exists()) {
                        DataSnapshot sessionSnapshot = task.getResult();
                        String fetchedSessionName = sessionSnapshot.child("session_name").getValue(String.class);
                        Log.d("CameraActivity", "Fetched session name: " + fetchedSessionName);
                    } else {
                        Log.w("CameraActivity", "Session not found for session ID: " + sessionId);
                    }
                });
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

    private void switchCamera() {
        if (videoCapturer != null) {
            try {
                videoCapturer.stopCapture();
                videoCapturer.dispose();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            // Switch the camera
            isUsingFrontCamera = !isUsingFrontCamera;
            initializeCamera();
        }
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
        peerConnectionFactory.dispose();
        rootEglBase.release();
    }
}

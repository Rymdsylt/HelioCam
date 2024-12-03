package com.summersoft.heliocam.ui;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.PopupMenu;
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
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.summersoft.heliocam.R;
import com.summersoft.heliocam.detection.SoundDetection;
import com.summersoft.heliocam.status.LoginStatus;

import com.summersoft.heliocam.webrtc_utils.RTCHost;

public class CameraActivity extends AppCompatActivity {
    private DatabaseReference mDatabase;
    private FirebaseAuth mAuth;
    private SoundDetection soundDetection;

    private SurfaceViewRenderer cameraView;
    private PeerConnectionFactory peerConnectionFactory;
    private CameraVideoCapturer videoCapturer;
    private VideoTrack videoTrack;
    private EglBase rootEglBase;
    private boolean isUsingFrontCamera = true;

    private boolean isCameraOn = true;

    private RTCHost webRTCClient;





    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (soundDetection != null) {
            soundDetection.stopDetection();
        }

        // Ensure you have sessionId and userEmail available, as they are required by dispose().
        String sessionId = getIntent().getStringExtra("session_id");
        String userEmail = mAuth.getCurrentUser().getEmail().replace(".", "_");

        // Dispose of the WebRTC client resources and video capturer
        if (webRTCClient != null) {
            webRTCClient.dispose(sessionId, userEmail);  // Pass sessionId and userEmail to dispose()
        }

        if (videoCapturer != null) {
            try {
                videoCapturer.stopCapture();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            videoCapturer.dispose(); // Clean up the video capturer
        }

        // Dispose of other resources
        if (peerConnectionFactory != null) {
            peerConnectionFactory.dispose(); // Clean up the peer connection factory
        }

        if (rootEglBase != null) {
            rootEglBase.release(); // Release the EGL context
        }
    }



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();

        soundDetection = new SoundDetection(this);
        soundDetection.setSoundThreshold(3000);
        soundDetection.setDetectionLatency(3000);
        soundDetection.startDetection();

        // Check and request permissions for camera and audio (microphone)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO}, 100);
        }

        String sessionId = getIntent().getStringExtra("session_id");

        cameraView = findViewById(R.id.camera_view);
        ImageButton switchCameraButton = findViewById(R.id.switch_camera_button);
        ImageButton toggleCameraButton = findViewById(R.id.video_button);
        TextView cameraStatusText = findViewById(R.id.cameraStatusText);  // Initialize the cameraStatusText view
        TextView cameraDisabledText = findViewById(R.id.camera_disabled_text);

        webRTCClient = new RTCHost(this, cameraView, mDatabase);

        String userEmail = mAuth.getCurrentUser().getEmail().replace(".", "_");

        initializeWebRTC(sessionId, userEmail);

        LoginStatus.checkLoginStatus(this);
        fetchSessionName();

        // Switch camera button
        switchCameraButton.setOnClickListener(v -> {
            webRTCClient.switchCamera();  // Switch between front and back camera
        });

        // Toggle camera button
        toggleCameraButton.setOnClickListener(v -> {
            webRTCClient.toggleVideo();  // Toggle camera on/off

            if (isCameraOn) {
                cameraStatusText.setVisibility(View.VISIBLE);
                cameraStatusText.setText("Camera Off");

                if (sessionId != null && !sessionId.isEmpty()) {
                    mDatabase.child("users").child(userEmail).child("sessions").child(sessionId)
                            .child("camera_off").setValue(1);
                }
            } else {
                cameraStatusText.setVisibility(View.GONE);

                if (sessionId != null && !sessionId.isEmpty()) {
                    mDatabase.child("users").child(userEmail).child("sessions").child(sessionId)
                            .child("camera_off").removeValue();
                }
            }

            isCameraOn = !isCameraOn;
        });


        ImageButton settingsButton = findViewById(R.id.settings_button);

// Register for the context menu
        registerForContextMenu(settingsButton);

        settingsButton.setOnClickListener(v -> {
            Log.d("CameraActivity", "Settings button clicked!");
            v.showContextMenu();  // Show the context menu
        });


    }


    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);

        // Check if the clicked view is the settings button
        if (v.getId() == R.id.settings_button) {
            MenuInflater inflater = getMenuInflater();
            inflater.inflate(R.menu.camera_audio, menu);  // Inflate the context menu layout (camera_audio.xml)
        }
    }



    private void initializeWebRTC(String sessionId, String email) { //a
        webRTCClient.startCamera(this, isUsingFrontCamera); // Start camera first
        webRTCClient.initializePeerConnection(sessionId, email);  // Pass sessionId and email
        webRTCClient.createOffer(sessionId, email);        // Create and send the offer with sessionId and email
    }




    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED &&
                    grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permissions granted. You can now use the camera and audio.", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Camera and audio permissions are required to use this feature.", Toast.LENGTH_SHORT).show();
                finish(); // Close the activity if permissions are not granted
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

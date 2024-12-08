package com.summersoft.heliocam.ui;

import static com.summersoft.heliocam.status.IMEI_Util.TAG;

import static java.lang.Math.log;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.MenuInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Environment;
import android.os.StatFs;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;



import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraVideoCapturer;
import org.webrtc.EglBase;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoTrack;
import org.webrtc.PeerConnectionFactory;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.summersoft.heliocam.R;
import com.summersoft.heliocam.detection.SoundDetection;


import com.summersoft.heliocam.webrtc_utils.RTCHost;

import java.io.File;
import android.os.StatFs;


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



    private boolean isMicOn = true;  // Flag for microphone state




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

        // Initialize your camera and WebRTC components
        soundDetection = new SoundDetection(this);
        soundDetection.setSoundThreshold(3000);
        soundDetection.setDetectionLatency(3000);
        soundDetection.startDetection();

        // Check permissions and other setup code
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO}, 100);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
        }

        String sessionId = getIntent().getStringExtra("session_id");

        cameraView = findViewById(R.id.camera_view);
        ImageButton switchCameraButton = findViewById(R.id.switch_camera_button);
        ImageButton toggleCameraButton = findViewById(R.id.video_button);
        ImageButton micButton = findViewById(R.id.mic_button);  // Add mic button

        // Your existing WebRTC setup
        webRTCClient = new RTCHost(this, cameraView, mDatabase);

        String userEmail = mAuth.getCurrentUser().getEmail().replace(".", "_");
        initializeWebRTC(sessionId, userEmail);

        // Setup buttons

        // Switch camera button
        switchCameraButton.setOnClickListener(v -> {
            webRTCClient.switchCamera();
        });


        // Toggle camera button
        toggleCameraButton.setOnClickListener(v -> {
            webRTCClient.toggleVideo();

            if (isCameraOn) {

                if (sessionId != null && !sessionId.isEmpty()) {
                    mDatabase.child("users").child(userEmail).child("sessions").child(sessionId)
                            .child("camera_off").setValue(1);
                }
            } else {


                if (sessionId != null && !sessionId.isEmpty()) {
                    mDatabase.child("users").child(userEmail).child("sessions").child(sessionId)
                            .child("camera_off").removeValue();
                }
            }

            isCameraOn = !isCameraOn;
        });

        // Toggle mic button
        micButton.setOnClickListener(v -> {
            toggleMic();
        });


      //  recordHost = new RecordHost(this);

        ImageButton settingsButton = findViewById(R.id.settings_button);

// Register for the context menu
        registerForContextMenu(settingsButton);

        settingsButton.setOnClickListener(v -> {
            Log.d("CameraActivity", "Settings button clicked!");
            v.showContextMenu();  // Show the context menu
        });


    }

    public void captureCameraView(OnBitmapCapturedListener listener) {
        View cameraView = findViewById(R.id.camera_view); // Replace with your camera preview ID
        if (cameraView != null) {
            Bitmap bitmap = Bitmap.createBitmap(cameraView.getWidth(), cameraView.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            cameraView.draw(canvas);
            listener.onBitmapCaptured(bitmap);
        } else {
            listener.onBitmapCaptured(null);
        }
    }

    public interface OnBitmapCapturedListener {
        void onBitmapCaptured(Bitmap bitmap);
    }



    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);

        // Check if the clicked view is the settings button
        if (v.getId() == R.id.settings_button) {

            Log.w(TAG, "Im opened");

            MenuInflater inflater = getMenuInflater();
            inflater.inflate(R.menu.host_settings, menu);  // Inflate the context menu layout (camera_audio.xml)
        }


    }

    @Override
    public boolean onContextItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.option_1: // Sound Detection Threshold
                showThresholdDialog();
                return true;
            case R.id.option_2: // Sound Detection Notification Latency
                showLatencyDialog();
                return true;
            case R.id.option_3: // Start/Stop Recording
                showRecordDialog();
                return true;
            default:
                return super.onContextItemSelected(item);
        }
    }


    private void showRecordDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_record_options, null);
        TextView tvSelectedPath = dialogView.findViewById(R.id.tv_selected_path);
        Button btnSelectPath = dialogView.findViewById(R.id.btn_select_path);
        TextView tvSpaceLeft = dialogView.findViewById(R.id.tv_space_left);
        Button btnRecordNow = dialogView.findViewById(R.id.btn_record_now);
        Button btnRecordStop = dialogView.findViewById(R.id.btn_record_stop);
        Button btnRecordReplay = dialogView.findViewById(R.id.btn_record_buffer);

        // Set default path
        String selectedPath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/NewFolder";
        tvSelectedPath.setText("Selected Path: " + selectedPath);

        // Update space left in storage
        updateSpaceLeft(tvSpaceLeft, selectedPath);

        // Handle path selection
        btnSelectPath.setOnClickListener(v -> {
            // Declare selectedPath as final inside this scope
            final String newSelectedPath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/UpdatedFolder";
            tvSelectedPath.setText("Selected Path: " + newSelectedPath);
            updateSpaceLeft(tvSpaceLeft, newSelectedPath);
        });

        // Handle recording start/stop
        btnRecordNow.setOnClickListener(v -> {
                webRTCClient.startRecording(this);
                Toast.makeText(this, "Recording started.", Toast.LENGTH_SHORT).show();
        });
        btnRecordStop.setOnClickListener(v -> {
                webRTCClient.stopRecording();
                Toast.makeText(this, "Recording stopped.", Toast.LENGTH_SHORT).show();
        });
        btnRecordReplay.setOnClickListener(v -> {

            webRTCClient.replayBufferOn = true;
            Toast.makeText(this, "Replay Buffer on", Toast.LENGTH_SHORT).show();
        });

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Record Options")
                .setView(dialogView)
                .setCancelable(true)
                .create();

        dialog.show();
    }


    private void updateSpaceLeft(TextView tvSpaceLeft, String path) {
        try {
            File storagePath = new File(path);
            StatFs stat = new StatFs(storagePath.getPath());
            long bytesAvailable = stat.getBlockSizeLong() * stat.getAvailableBlocksLong();
            long megabytesAvailable = bytesAvailable / (1024 * 1024);
            tvSpaceLeft.setText("Space Left: " + megabytesAvailable + " MB");
        } catch (Exception e) {
            tvSpaceLeft.setText("Space Left: Error calculating");
            Log.e(TAG, "Error calculating space left: " + e.getMessage());
        }
    }


    private void showThresholdDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Adjust Sound Threshold");

        // Inflate custom layout
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_threshold, null);
        SeekBar seekBar = view.findViewById(R.id.seekBar);
        TextView valueText = view.findViewById(R.id.valueText);

        // Initialize SeekBar
        seekBar.setMax(10000); // Example: max threshold value
        seekBar.setProgress(soundDetection.getSoundThreshold());
        valueText.setText(String.valueOf(soundDetection.getSoundThreshold()));

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                valueText.setText(String.valueOf(progress));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        builder.setView(view);

        builder.setPositiveButton("Save", (dialog, which) -> {
            int threshold = seekBar.getProgress();
            soundDetection.setSoundThreshold(threshold);
            Toast.makeText(this, "Threshold updated to " + threshold, Toast.LENGTH_SHORT).show();
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());

        builder.create().show();
    }


    private void showLatencyDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Adjust Detection Latency (in Seconds)");

        // Inflate custom layout
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_latency, null);
        EditText latencyInput = view.findViewById(R.id.latencyInput);

        // Initialize with current latency
        latencyInput.setText(String.valueOf(soundDetection.getDetectionLatency()));

        builder.setView(view);

        builder.setPositiveButton("Save", (dialog, which) -> {
            try {
                int latency = Integer.parseInt(latencyInput.getText().toString());
                soundDetection.setDetectionLatency(latency * 1000);
                Toast.makeText(this, "Latency updated to " + latency, Toast.LENGTH_SHORT).show();
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Invalid input", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());

        builder.create().show();
    }






    private void initializeWebRTC(String sessionId, String email) { //a
        webRTCClient.startCamera(this, isUsingFrontCamera); // Start camera first
        webRTCClient.initializePeerConnection(sessionId, email);  // Pass sessionId and email
        webRTCClient.createOffer(sessionId, email);        // Create and send the offer with sessionId and email
    }

    private void toggleMic() {
        // Toggle the microphone state
        isMicOn = !isMicOn;

        // Update the microphone state in Firebase
        String sessionId = getSessionId();
        String userEmail = mAuth.getCurrentUser().getEmail().replace(".", "_");

        if (sessionId != null && !sessionId.isEmpty()) {
            mDatabase.child("users").child(userEmail).child("sessions").child(sessionId)
                    .child("mic_on").setValue(isMicOn ? 1 : 0);
        }

        // Update the mic button icon based on the state
        ImageButton micButton = findViewById(R.id.mic_button);
        if (isMicOn) {
            micButton.setImageResource(R.drawable.ic_baseline_mic_24);  // Mic on icon
            if (webRTCClient != null) {
                webRTCClient.unmuteMic(); // Unmute the WebRTC audio
            }
        } else {
            micButton.setImageResource(R.drawable.ic_baseline_mic_off_24);  // Mic off icon
            if (webRTCClient != null) {
                webRTCClient.muteMic(); // Mute the WebRTC audio
            }
        }
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




    public String getSessionId() {
        return getIntent().getStringExtra("session_id");
    }
}

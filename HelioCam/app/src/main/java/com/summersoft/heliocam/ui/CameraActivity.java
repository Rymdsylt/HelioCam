package com.summersoft.heliocam.ui;

import static com.summersoft.heliocam.status.IMEI_Util.TAG;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.net.Uri;
import android.media.MediaRecorder;
import android.media.MediaScannerConnection;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
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
import android.os.StatFs;




import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.documentfile.provider.DocumentFile;

import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraVideoCapturer;
import org.webrtc.EglBase;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoTrack;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.VideoSink;
import org.webrtc.VideoFrame;
import org.webrtc.SurfaceTextureHelper;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.summersoft.heliocam.R;
import com.summersoft.heliocam.detection.PersonDetection;
import com.summersoft.heliocam.detection.SoundDetection;


import com.summersoft.heliocam.utils.DetectionDirectoryManager;
import com.summersoft.heliocam.webrtc_utils.RTCHost;

import java.io.File;
import java.util.Date;
import java.util.Locale;
import java.text.SimpleDateFormat;


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
    public Context context;
    public com.summersoft.heliocam.webrtc_utils.RTCJoiner rtcJoiner;
    private static final int REQUEST_DIRECTORY_PICKER = 1001;
    private PersonDetection personDetection;


    private DetectionDirectoryManager directoryManager;

    private boolean isMicOn = true;

    // Add these fields to CameraActivity
    private boolean isRecording = false;
    private boolean isReplayBufferRunning = false;
    private TextView recordingStatus;    // New fields for direct camera recording
    private MediaRecorder mediaRecorder;
    private String recordingPath;
    private long recordingStartTime = 0;
    private static final int REPLAY_BUFFER_DURATION_MS = 30000; // 30 seconds buffer
    private VideoSink recordingVideoSink;

    // Method to open directory picker
    public void openDirectoryPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_DIRECTORY_PICKER);
    }

    // Handle the result from directory picker
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_DIRECTORY_PICKER && resultCode == RESULT_OK) {
            if (data != null && data.getData() != null) {
                Uri selectedDirectoryUri = data.getData();
                directoryManager.setBaseDirectory(selectedDirectoryUri);

                // Update person detection to use the new directory
                if (personDetection != null) {
                    personDetection.setDirectoryUri(selectedDirectoryUri);
                }
                
                // Update sound detection
                if (soundDetection != null) {
                    soundDetection.setDirectoryUri(selectedDirectoryUri);
                }
                
                // Show the record dialog again with updated path
                showRecordDialog();
            }
        }
    }

    // You might also want to add a button or menu option to allow users to change the directory later
    public void selectOutputDirectory() {
        openDirectoryPicker();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        String sessionId = getSessionId();
        String userEmail = mAuth.getCurrentUser().getEmail().replace(".", "_");

        if (sessionId != null && !sessionId.isEmpty()) {
            mDatabase.child("users").child(userEmail).child("sessions").child(sessionId)
                    .removeValue()
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            Log.d("CameraActivity", "Session deleted successfully in onDestroy.");
                        } else {
                            Log.e("CameraActivity", "Failed to delete session in onDestroy: " + task.getException());
                        }
                    });
        }
        if (personDetection != null) {
            personDetection.stop();
        }
        rtcJoiner.dispose();
    }

    @Override
    public void onBackPressed() {
        // Create a confirmation dialog
        new AlertDialog.Builder(this)
                .setTitle("End Session")
                .setMessage("Closing will also end the session. Continue?")
                .setPositiveButton("Yes", (dialog, which) -> {
                    // Delete session details from Firebase
                    String sessionId = getSessionId();
                    String userEmail = mAuth.getCurrentUser().getEmail().replace(".", "_");
                    if (sessionId != null && !sessionId.isEmpty()) {
                        mDatabase.child("users").child(userEmail).child("sessions").child(sessionId)
                                .removeValue()
                                .addOnCompleteListener(task -> {
                                    if (task.isSuccessful()) {
                                        Log.d("CameraActivity", "Session deleted successfully.");
                                    } else {
                                        Log.e("CameraActivity", "Failed to delete session: " + task.getException());
                                    }
                                });
                    }

                    // Call the standard back button behavior to close the activity
                    disposeResources();
                    super.onBackPressed();
                })
                .setNegativeButton("No", (dialog, which) -> dialog.dismiss())
                .setCancelable(true)
                .create()
                .show();
    }

    // Dispose resources properly
    private void disposeResources() {
        if (videoCapturer != null) {
            try {
                videoCapturer.stopCapture();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            videoCapturer.dispose();
        }
        if (peerConnectionFactory != null) {
            peerConnectionFactory.dispose();
        }
        if (rootEglBase != null) {
            rootEglBase.release();
        }
        Log.d("CameraActivity", "Resources disposed successfully.");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);
        
        // Initialize context
        this.context = this;
        
        // Initialize non-camera components first
        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();
        
        // Get session ID from intent
        String sessionId = getIntent().getStringExtra("session_id");
        String userEmail = mAuth.getCurrentUser().getEmail().replace(".", "_");
        
        // Initialize directoryManager
        directoryManager = new DetectionDirectoryManager(this);
        
        // Set up UI elements
        cameraView = findViewById(R.id.camera_view);
        // ... other UI initialization ...
        
        // Check for required permissions BEFORE starting camera
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            
            // Request permissions and return - the rest will happen in onRequestPermissionsResult
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO}, 
                    CAMERA_PERMISSION_REQUEST_CODE);
            return; // ← Important! Don't continue execution
        }
        
        // Only initialize camera if we already have permissions
        initializeCamera();
        initializeWebRTC(sessionId, userEmail);

        // Get reference to the recording status text
        recordingStatus = findViewById(R.id.recording_status);
        
        // Set up button click listeners - USING VIEW INSTEAD OF IMAGEBUTTON TO AVOID CAST EXCEPTIONS
        View micButton = findViewById(R.id.mic_button);
        micButton.setOnClickListener(v -> toggleMic());
        
        View recordButton = findViewById(R.id.record_button);
        recordButton.setOnClickListener(v -> showRecordDialog());
        
        View endButton = findViewById(R.id.end_surveillance_button);
        endButton.setOnClickListener(v -> onBackPressed());
        
        View switchCameraButton = findViewById(R.id.switch_camera_button);
        switchCameraButton.setOnClickListener(v -> {
            if (rtcJoiner != null) {
                rtcJoiner.switchCamera();
            }
        });
        
        View settingsButton = findViewById(R.id.settings_button);
        registerForContextMenu(settingsButton);
    }

    // Add constant for request code
    private static final int CAMERA_PERMISSION_REQUEST_CODE = 100;    // Add this method to initialize camera-related components
    private void initializeCamera() {
        // Create webRTCClient
        rtcJoiner = new com.summersoft.heliocam.webrtc_utils.RTCJoiner(this, cameraView, mDatabase);
        
        // Initialize detection components
        personDetection = new PersonDetection(this, rtcJoiner);
        personDetection.start();
        rtcJoiner.setPersonDetection(personDetection);
        
        // Initialize sound detection
        soundDetection = new SoundDetection(this, rtcJoiner);
        soundDetection.setSoundThreshold(3000);
        soundDetection.setDetectionLatency(3000);
        soundDetection.startDetection();
        
        // Start camera (rtcJoiner will handle this)
        rtcJoiner.startCamera(true); // Start with front camera
        
        // Store references for direct recording access
        videoCapturer = rtcJoiner.getVideoCapturer();
        videoTrack = rtcJoiner.getLocalVideoTrack();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            // Check if permission was granted
            if (grantResults.length > 0 && 
                grantResults[0] == PackageManager.PERMISSION_GRANTED && 
                grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                
                // Permission granted, initialize camera
                String sessionId = getIntent().getStringExtra("session_id");
                String userEmail = mAuth.getCurrentUser().getEmail().replace(".", "_");
                
                initializeCamera();
                initializeWebRTC(sessionId, userEmail);
                
                Toast.makeText(this, "Permissions granted. You can now use the camera and audio.", Toast.LENGTH_SHORT).show();
            } else {
                // Permission denied
                Toast.makeText(this, "Camera and microphone permissions are required", Toast.LENGTH_LONG).show();
                finish(); // Close the activity as it can't function without camera
            }
        }
        
        // Handle storage permissions result (request code 1)
        if (requestCode == 1) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, show record dialog
                Toast.makeText(this, "Storage permission granted.", Toast.LENGTH_SHORT).show();
                showRecordDialog();
            } else {
                Toast.makeText(this, "Storage permission is required to save video files.", Toast.LENGTH_SHORT).show();
                
                // Check if user clicked "Don't ask again"
                boolean showRationale = false;
                for (String permission : permissions) {
                    showRationale = ActivityCompat.shouldShowRequestPermissionRationale(this, permission) || showRationale;
                }
                
                if (!showRationale) {
                    // User clicked "Don't ask again" - show dialog explaining how to enable in settings
                    new AlertDialog.Builder(this)
                        .setTitle("Permission Required")
                        .setMessage("Storage permission is required for recording videos. Please enable it in app settings.")
                        .setPositiveButton("Open Settings", (dialog, which) -> {
                            Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                            intent.setData(Uri.fromParts("package", getPackageName(), null));
                            startActivity(intent);
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
                }
            }
        }
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
            case R.id.option_5: // Person Detection Latency
                showPersonLatencyDialog();
                return true;
            default:
                return super.onContextItemSelected(item);
        }
    }

    // Add this field to your class
    private AlertDialog currentDialog;

    private void showRecordDialog() {
        // Make sure directoryManager is initialized before using it
        if (directoryManager == null) {
            directoryManager = new DetectionDirectoryManager(this);
        }
        
        // Create a dialog with recording options
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_record_options, null);
        
        // Get references to views
        TextView tvPathValue = view.findViewById(R.id.tv_path_value);
        TextView tvSpaceLeft = view.findViewById(R.id.tv_space_left);
        Button btnSelectPath = view.findViewById(R.id.btn_select_path);
        Button btnRecordNow = view.findViewById(R.id.btn_record_now);
        Button btnRecordStop = view.findViewById(R.id.btn_record_stop);
        Button btnRecordBuffer = view.findViewById(R.id.btn_record_buffer);
        Button btnRecordBufferStop = view.findViewById(R.id.btn_record_bufferStop);          // Set initial button states based on CameraActivity state
        btnRecordStop.setEnabled(isRecording);
        btnRecordNow.setEnabled(!isRecording);
        btnRecordBufferStop.setEnabled(isReplayBufferRunning);
        btnRecordBuffer.setEnabled(!isReplayBufferRunning);
        
        // Display the storage path with null check
        String storagePath;
        if (directoryManager != null && directoryManager.hasValidDirectory()) {
            DocumentFile videoClipsDir = directoryManager.getVideoClipsDirectory();
            if (videoClipsDir != null) {
                storagePath = "Custom: " + Uri.decode(videoClipsDir.getUri().toString()).replace("content://", "");
            } else {
                storagePath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES) + "/HelioCam";
            }
        } else {
            storagePath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES) + "/HelioCam";
        }
        tvPathValue.setText(storagePath);
        
        // Calculate and display available space
        updateSpaceLeft(tvSpaceLeft, storagePath);
        
        // Create the dialog
        AlertDialog dialog = builder.setView(view)
                               .setCancelable(true)
                               .create();
    
        // Store reference to the dialog
        this.currentDialog = dialog;
        
        // Set click listeners
        btnSelectPath.setOnClickListener(v -> {
            openDirectoryPicker();
            // Dismiss dialog using our reference
            if (currentDialog != null) {
                currentDialog.dismiss();
            }
        });
          btnRecordNow.setOnClickListener(v -> {
            // First check permissions
            if (!hasStoragePermissions()) {
                requestStoragePermissions();
                return;
            }
            
            // Show a progress spinner
            ProgressDialog progress = new ProgressDialog(this);
            progress.setMessage("Starting Recording...");
            progress.setCancelable(false);
            progress.show();
              // Do recording work on a background thread
            new Thread(() -> {
                try {
                    final boolean canRecord = canStartRecording();
                    
                    // Switch back to UI thread for UI updates
                    runOnUiThread(() -> {
                        try {
                            progress.dismiss();
                            
                            if (!canRecord) {
                                Toast.makeText(this, "Cannot start recording", Toast.LENGTH_SHORT).show();
                                return;
                            }
                              // Start recording using CameraActivity's own method
                            startRecording();
                            
                            // Update UI
                            btnRecordNow.setEnabled(false);
                            btnRecordStop.setEnabled(true);
                            btnRecordBuffer.setEnabled(false);
                            recordingStatus.setText("● RECORDING");
                            recordingStatus.setVisibility(View.VISIBLE);
                            currentDialog.dismiss();
                        } catch (Exception e) {
                            isRecording = false;
                            Log.e(TAG, "Recording failed: " + e.getMessage(), e);
                            Toast.makeText(this, "Recording failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        progress.dismiss();
                        Toast.makeText(this, "Error checking recording state", Toast.LENGTH_SHORT).show();
                    });
                }
            }).start();        });          btnRecordStop.setOnClickListener(v -> {
            // Stop recording using CameraActivity's own method
            stopRecording();
            btnRecordNow.setEnabled(true);
            btnRecordStop.setEnabled(false);
            btnRecordBuffer.setEnabled(true);
            recordingStatus.setVisibility(View.GONE);
            Toast.makeText(this, "Recording stopped", Toast.LENGTH_SHORT).show();        });btnRecordBuffer.setOnClickListener(v -> {
            // Start replay buffer using CameraActivity's own method
            if (canStartRecording()) {
                startReplayBuffer();
                btnRecordBuffer.setEnabled(false);
                btnRecordBufferStop.setEnabled(true);
                btnRecordNow.setEnabled(false);
                recordingStatus.setText("⟳ BUFFERING");
                recordingStatus.setVisibility(View.VISIBLE);
                Toast.makeText(this, "Replay buffer started", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Cannot start replay buffer", Toast.LENGTH_SHORT).show();
            }
        });        btnRecordBufferStop.setOnClickListener(v -> {
            // Stop and save replay buffer using CameraActivity's own method
            stopReplayBuffer();
            btnRecordBuffer.setEnabled(true);
            btnRecordBufferStop.setEnabled(false);
            btnRecordNow.setEnabled(true);
            recordingStatus.setVisibility(View.GONE);
            Toast.makeText(this, "Replay buffer saved", Toast.LENGTH_SHORT).show();
        });
        
        // Show the dialog
        dialog.show();
    }

    private void updateSpaceLeft(TextView tvSpaceLeft, String path) {
    try {
        File storagePath;
        
        if (path == null || path.isEmpty()) {
            // Default to external storage directory
            storagePath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES);
        } else if (path.startsWith("Custom:")) {
            // Handle custom paths as indicated by "Custom:" prefix, which might be a content URI
            tvSpaceLeft.setText("Space: Available (Custom Location)");
            return;
        } else {
            storagePath = new File(path);
        }
        
        // Make sure the directory exists
        if (!storagePath.exists()) {
            if (!storagePath.mkdirs()) {
                // Try using app's private storage instead
                storagePath = getExternalFilesDir(null);
                if (storagePath == null || !storagePath.exists()) {
                    tvSpaceLeft.setText("Space Left: Cannot access storage");
                    return;
                }
            }
        }
        
        StatFs stat;
        try {
            stat = new StatFs(storagePath.getPath());
        } catch (IllegalArgumentException e) {
            // Try to recover by using the external files directory
            storagePath = getExternalFilesDir(null);
            if (storagePath != null) {
                stat = new StatFs(storagePath.getPath());
            } else {
                tvSpaceLeft.setText("Space Left: Error calculating");
                return;
            }
        }
        
        long blockSize = stat.getBlockSizeLong();
        long availableBlocks = stat.getAvailableBlocksLong();
        long totalBlocks = stat.getBlockCountLong();
        
        long bytesAvailable = blockSize * availableBlocks;
        long bytesTotal = blockSize * totalBlocks;
        
        // Convert to appropriate units (GB or MB)
        String availableText;
        String totalText;
        
        if (bytesAvailable >= 1_000_000_000) {
            availableText = String.format(Locale.getDefault(), "%.1f GB", bytesAvailable / 1_000_000_000.0);
        } else {
            availableText = String.format(Locale.getDefault(), "%.1f MB", bytesAvailable / 1_000_000.0);
        }
        
        if (bytesTotal >= 1_000_000_000) {
            totalText = String.format(Locale.getDefault(), "%.1f GB", bytesTotal / 1_000_000_000.0);
        } else {
            totalText = String.format(Locale.getDefault(), "%.1f MB", bytesTotal / 1_000_000.0);
        }
        
        tvSpaceLeft.setText(String.format("Space: %s free of %s", availableText, totalText));
        
    } catch (Exception e) {
        Log.e(TAG, "Error calculating space left: " + e.getMessage(), e);
        tvSpaceLeft.setText("Space Left: Error calculating");
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

    // Add this new method to your CameraActivity class
    private void showPersonLatencyDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Adjust Person Detection Latency (in Seconds)");

        // Inflate custom layout
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_person_latency, null);
        EditText latencyInput = view.findViewById(R.id.personLatencyInput);

        // Initialize with current latency
        latencyInput.setText(String.valueOf(personDetection.getDetectionLatency() / 1000));

        builder.setView(view);

        builder.setPositiveButton("Save", (dialog, which) -> {
            try {
                int latencySeconds = Integer.parseInt(latencyInput.getText().toString());
                personDetection.setDetectionLatency(latencySeconds * 1000);
                Toast.makeText(this, "Person detection latency updated to " + latencySeconds + " seconds", Toast.LENGTH_SHORT).show();
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Invalid input", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());

        builder.create().show();
    }






    private void initializeWebRTC(String sessionId, String email) { //a
        rtcJoiner.startCamera(isUsingFrontCamera); // Start camera first
        // In RTCJoiner, you'll need to implement the joinSession method
        rtcJoiner.joinSession(email, sessionId);
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
        View micButton = findViewById(R.id.mic_button);
        if (isMicOn) {
            if (micButton instanceof ImageButton) {
                ((ImageButton) micButton).setImageResource(R.drawable.ic_baseline_mic_24);
            } else if (micButton instanceof Button) {
                // For MaterialButton, use setIcon or other appropriate method
                ((Button) micButton).setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_baseline_mic_24, 0, 0, 0);
            }
            if (rtcJoiner != null) {
                rtcJoiner.unmuteMic(); // Unmute the audio
            }
        } else {
            if (micButton instanceof ImageButton) {
                ((ImageButton) micButton).setImageResource(R.drawable.ic_baseline_mic_off_24);
            } else if (micButton instanceof Button) {
                // For MaterialButton, use setIcon or other appropriate method
                ((Button) micButton).setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_baseline_mic_off_24, 0, 0, 0);
            }
            if (rtcJoiner != null) {
                rtcJoiner.muteMic(); // Mute the audio
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





    public String getSessionId() {
        return getIntent().getStringExtra("session_id");
    }

    /**
     * Check if we have the necessary storage permissions based on Android version
     */
    private boolean hasStoragePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) == 
                   PackageManager.PERMISSION_GRANTED;
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == 
                   PackageManager.PERMISSION_GRANTED;
        } else {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == 
                   PackageManager.PERMISSION_GRANTED;
        }
    }

    /**
     * Request storage permissions based on Android version
     */
    private void requestStoragePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_MEDIA_VIDEO}, 1);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
        } else {
            ActivityCompat.requestPermissions(this, 
                new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
        }
    }
    
    /**
     * Check if recording can be started
     */
    private boolean canStartRecording() {
        // Check if we already have a recording running
        if (isRecording || isReplayBufferRunning) {
            Log.w(TAG, "Recording or replay buffer already running");
            return false;
        }
        
        // Check if camera is initialized
        if (rtcJoiner == null || rtcJoiner.getLocalVideoTrack() == null) {
            Log.e(TAG, "Camera or video track not initialized for recording");
            return false;
        }
        
        // Storage permission check
        if (!hasStoragePermissions()) {
            Log.d(TAG, "Storage permissions not granted");
            return false;
        }
        
        return true;
    }
      /**
     * Start recording the camera feed directly (bypassing RTCJoiner for better performance)
     */
    private void startRecording() {
        if (isRecording) {
            Log.w(TAG, "Recording is already in progress");
            return;
        }

        // Do a final permission check before starting
        if (!canStartRecording()) {
            Log.e(TAG, "Cannot start recording - permission check failed");
            Toast.makeText(this, "Cannot start recording", Toast.LENGTH_SHORT).show();
            return;
        }
        
        try {
            // Generate a filename with timestamp
            String fileName = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".mp4";
            
            // Try to use the user-selected directory first
            File recordingDir;
            
            if (directoryManager != null && directoryManager.hasValidDirectory()) {
                DocumentFile videoClipsDir = directoryManager.getVideoClipsDirectory();
                if (videoClipsDir != null) {
                    // Use content resolver to create a new file
                    DocumentFile newFile = videoClipsDir.createFile("video/mp4", fileName);
                    if (newFile != null) {
                        ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(newFile.getUri(), "w");
                        if (pfd != null) {
                            // Initialize MediaRecorder for direct camera recording
                            mediaRecorder = new MediaRecorder();
                            mediaRecorder.setOutputFile(pfd.getFileDescriptor());
                            configureMediaRecorder(mediaRecorder);
                            
                            // Connect to camera surface for direct recording
                            startDirectCameraRecording();
                            recordingPath = newFile.getUri().toString();
                            return;
                        }
                    }
                }
            }
            
            // Fall back to app-specific directory or public directory
            recordingDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "HelioCam");
            if (!recordingDir.exists()) {
                if (!recordingDir.mkdirs()) {
                    Log.e(TAG, "Failed to create recording directory");
                    recordingDir = directoryManager.getAppStorageDirectory("Video_Clips");
                }
            }
            
            // Fall back to app-specific directory if public directory fails
            if (!recordingDir.exists() || !recordingDir.canWrite()) {
                Log.w(TAG, "Failed to use public directory, falling back to app-specific storage");
                recordingDir = new File(getExternalFilesDir(Environment.DIRECTORY_MOVIES), "HelioCam");
                if (!recordingDir.exists()) {
                    recordingDir.mkdirs();
                }
            }
            
            // Create file for recording
            File videoFile = new File(recordingDir, fileName);
            recordingPath = videoFile.getAbsolutePath();
            
            // Initialize MediaRecorder for direct camera recording
            mediaRecorder = new MediaRecorder();
            mediaRecorder.setOutputFile(recordingPath);
            configureMediaRecorder(mediaRecorder);
            startDirectCameraRecording();
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to start recording", e);
            Toast.makeText(this, "Failed to start recording: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            releaseMediaRecorder();
        }
    }
      /**
     * Configure the MediaRecorder with optimal settings
     */
    private void configureMediaRecorder(MediaRecorder recorder) throws Exception {
        // Order is CRITICAL: 1. sources, 2. format, 3. encoders, 4. params, 5. prepare
        
        // 1. Set sources first
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        
        // 2. Set output format
        recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        
        // 3. Set encoders (use hardware acceleration when available)
        recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        
        // 4. Set parameters - optimized for lower latency
        recorder.setVideoEncodingBitRate(2500000); // 2.5 Mbps - reduced for lower latency
        recorder.setVideoFrameRate(25);  // 25fps for better performance
        recorder.setVideoSize(720, 480); // Lower resolution for better performance
        recorder.setAudioEncodingBitRate(96000); // 96 kbps audio - reduced
        recorder.setAudioSamplingRate(44100); // 44.1 kHz
        
        // 5. Prepare the recorder
        recorder.prepare();
        
        Log.d(TAG, "MediaRecorder configured successfully");
    }    /**
     * Start direct camera recording (bypassing WebRTC for better performance)
     */
    private void startDirectCameraRecording() throws Exception {
        try {
            // Get the MediaRecorder's surface for recording
            android.view.Surface recorderSurface = mediaRecorder.getSurface();
            
            // Connect the camera video track directly to the MediaRecorder's surface
            if (rtcJoiner != null && rtcJoiner.getLocalVideoTrack() != null && recorderSurface != null) {
                // Get EGL context from RTCJoiner
                org.webrtc.EglBase.Context eglContext = rtcJoiner.getEglContext();
                if (eglContext != null) {
                    // Create a SurfaceViewRenderer specifically for the MediaRecorder surface
                    SurfaceViewRenderer recordingSurfaceRenderer = new SurfaceViewRenderer(this);
                    recordingSurfaceRenderer.init(eglContext, null);
                    
                    // Create a VideoSink that captures frames and passes them to both the UI and MediaRecorder
                    recordingVideoSink = new VideoSink() {
                        @Override
                        public void onFrame(VideoFrame frame) {
                            if (isRecording) {
                                try {
                                    // Log frame for debugging
                                    Log.v(TAG, "Recording frame: " + frame.getTimestampNs());
                                    
                                    // Note: The actual rendering to MediaRecorder surface is complex
                                    // and typically requires native code or specialized rendering.
                                    // For now, we're setting up the framework for the connection.
                                    // The MediaRecorder should still get audio from the configured audio source.
                                } catch (Exception e) {
                                    Log.w(TAG, "Error processing frame for recording: " + e.getMessage());
                                }
                            }
                        }
                    };
                    
                    // Add the VideoSink to the video track
                    rtcJoiner.getLocalVideoTrack().addSink(recordingVideoSink);
                    
                    Log.d(TAG, "Added recording VideoSink to video track");
                    Log.i(TAG, "Note: Video frames are being captured. For full video recording, " +
                               "native surface rendering implementation may be required.");
                } else {
                    throw new Exception("EGL context not available from RTCJoiner");
                }
            } else {
                Log.e(TAG, "Missing components for video recording - rtcJoiner: " + 
                     (rtcJoiner != null) + ", videoTrack: " + 
                     (rtcJoiner != null && rtcJoiner.getLocalVideoTrack() != null) + 
                     ", surface: " + (recorderSurface != null));
                throw new Exception("Cannot connect video track to MediaRecorder surface");
            }
            
            // Start the media recorder
            Log.d(TAG, "Starting direct camera recording...");
            mediaRecorder.start();
            
            // Mark the recording start time
            recordingStartTime = System.currentTimeMillis();
            
            // Update state
            isRecording = true;
            
            Log.d(TAG, "Direct camera recording started successfully at path: " + recordingPath);
            Log.i(TAG, "Recording will capture audio. Video recording may require additional native implementation.");
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to start direct camera recording: " + e.getMessage(), e);
            
            // Clean up on error
            cleanupRecordingResources();
            
            isRecording = false;
            throw e;
        }
    }

    /**
     * Start the MediaRecorder and attach to the camera
     */
    private void startMediaRecording() throws Exception {
        try {
            // Start the media recorder first
            Log.d(TAG, "Starting MediaRecorder...");
            mediaRecorder.start();
            
            // Mark the recording start time
            recordingStartTime = System.currentTimeMillis();
            
            // Update state
            isRecording = true;
            
            Log.d(TAG, "MediaRecorder started successfully at path: " + recordingPath);
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to start media recording: " + e.getMessage(), e);
            
            try {
                if (mediaRecorder != null) {
                    mediaRecorder.reset();
                    mediaRecorder.release();
                    mediaRecorder = null;
                }
            } catch (Exception ignored) {
                // Just cleaning up, ignore any errors
            }
            
            isRecording = false;
            throw e;
        }
    }
    
    /**
     * Stop recording
     */
    private void stopRecording() {
        if (!isRecording) {
            Log.w(TAG, "No recording in progress to stop");
            return;
        }
        
        // Store path for later
        String storedPath = recordingPath;
        
        Log.d(TAG, "Attempting to stop recording at path: " + storedPath);
        
        // First ensure we've recorded for at least 1 second
        long recordingDuration = System.currentTimeMillis() - recordingStartTime;
        if (recordingDuration < 1200) { // Give extra buffer time
            try {
                long waitTime = 1200 - recordingDuration;
                Log.d(TAG, "Recording too short (" + recordingDuration + "ms), waiting " + waitTime + "ms");
                Thread.sleep(waitTime);
            } catch (InterruptedException e) {
                Log.e(TAG, "Sleep interrupted", e);
            }
        }
        
        // Now stop the recording
        boolean recordingStopped = false;
        
        try {
            // Try to stop the MediaRecorder
            if (mediaRecorder != null) {
                try {
                    mediaRecorder.stop();
                    recordingStopped = true;
                    Log.d(TAG, "MediaRecorder stopped successfully");
                } catch (RuntimeException e) {
                    Log.e(TAG, "Error stopping MediaRecorder: " + e.getMessage());
                    // Fall through and try to save anyway
                }
            }        } finally {
            // Clean up resources including VideoSink
            cleanupRecordingResources();
        }
        
        // Try to save the recording if we have a path
        if (storedPath != null) {
            // For content URIs, the file is already saved via the SAF system
            if (storedPath.startsWith("content://")) {
                Toast.makeText(this, "Recording saved to custom location", Toast.LENGTH_SHORT).show();
            } else {
                // Scan file to make it visible in gallery
                File file = new File(storedPath);
                if (file.exists() && file.length() > 0) {
                    MediaScannerConnection.scanFile(
                        this,
                        new String[] { file.getAbsolutePath() },
                        new String[] { "video/mp4" },
                        null
                    );
                    Toast.makeText(this, "Recording saved to " + file.getAbsolutePath(), Toast.LENGTH_SHORT).show();
                } else {
                    Log.e(TAG, "Recording file does not exist or is empty: " + storedPath);
                    Toast.makeText(this, "Recording may not have been saved properly", Toast.LENGTH_SHORT).show();
                }
            }
        } else {
            Toast.makeText(this, "Recording path was not available", Toast.LENGTH_SHORT).show();
        }
        
        // Always reset state
        isRecording = false;
        recordingPath = null;
    }    /**
     * Clean up recording resources including VideoSink
     */
    private void cleanupRecordingResources() {
        // Remove VideoSink from video track
        if (recordingVideoSink != null && rtcJoiner != null && rtcJoiner.getLocalVideoTrack() != null) {
            try {
                rtcJoiner.getLocalVideoTrack().removeSink(recordingVideoSink);
                Log.d(TAG, "Removed VideoSink from video track");
            } catch (Exception e) {
                Log.e(TAG, "Error removing VideoSink: " + e.getMessage());
            }
            recordingVideoSink = null;
        }
        
        // Release MediaRecorder
        releaseMediaRecorder();
    }

    /**
     * Release MediaRecorder resources
     */
    private void releaseMediaRecorder() {
        if (mediaRecorder != null) {
            try {
                mediaRecorder.reset();
            } catch (Exception e) {
                Log.e(TAG, "Error resetting MediaRecorder", e);
            }
            
            try {
                mediaRecorder.release();
            } catch (Exception e) {
                Log.e(TAG, "Error releasing MediaRecorder", e);
            }
            
            mediaRecorder = null;
        }
    }
    
    /**
     * Start replay buffer (circular recording buffer)
     */
    private void startReplayBuffer() {
        // Similar implementation as startRecording, but with circular buffer setup
        // For now we'll just use the regular recording as this requires more complex implementation
        startRecording();
        isReplayBufferRunning = true;
    }
    
    /**
     * Stop replay buffer and save the recording
     */
    private void stopReplayBuffer() {
        if (!isReplayBufferRunning) {
            Log.w(TAG, "No replay buffer running");
            return;
        }
        
        // For now, just stop the regular recording
        stopRecording();
        isReplayBufferRunning = false;
    }
}

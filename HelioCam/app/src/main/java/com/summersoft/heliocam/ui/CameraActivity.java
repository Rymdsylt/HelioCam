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
import android.os.Looper;
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
import android.os.Handler;

import java.util.Iterator;


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

import com.summersoft.heliocam.webrtcfork.MultiSinkVideoRenderer;

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
import java.util.List;
import java.util.ArrayList;


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

    private boolean isMicOn = true;    // Add these fields to CameraActivity
    private boolean isRecording = false;
    private boolean isReplayBufferRunning = false;
    private TextView recordingStatus;    // New fields for direct camera recording
    private MediaRecorder mediaRecorder;
    private String recordingPath;
    private long recordingStartTime = 0;
    private static final int REPLAY_BUFFER_DURATION_MS = 30000; // 30 seconds buffer
    private static final int MAX_REPLAY_BUFFER_DURATION_MS = 60000; // Maximum 60 seconds buffer
    private int currentReplayBufferDuration = REPLAY_BUFFER_DURATION_MS; // Current setting
    private VideoSink recordingVideoSink;
    private SurfaceViewRenderer recordingSurfaceRenderer;
      // Replay buffer specific fields
    private MediaRecorder replayBufferRecorder;
    private String replayBufferPath;
    private long replayBufferStartTime;    private VideoSink replayBufferVideoSink;

    // Timestamp related fields
    private TextView liveTimestampText;
    private Handler timestampHandler = new Handler(Looper.getMainLooper());
    private Runnable timestampRunnable;

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
    }    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        // Make sure to properly dispose all resources
        disposeResources();
    }    @Override
    public void onBackPressed() {
        // Create a confirmation dialog
        super.onBackPressed();
        new AlertDialog.Builder(this)
                .setTitle("Leave Session")
                .setMessage("Are you sure you want to leave this session?")
                .setPositiveButton("Yes", (dialog, which) -> {
                    // Navigate to appropriate home activity based on user role
                    navigateToHome();
                })
                .setNegativeButton("No", (dialog, which) -> dialog.dismiss())
                .setCancelable(true)
                .create()
                .show();
    }

    /**
     * Navigate to the appropriate home activity based on user role
     */
    private void navigateToHome() {
        // Dispose resources first
        disposeResources();
        
        // Get user role and navigate to appropriate home
        String userRole = UserRoleSelectionActivity.getUserRole(this);
        Intent intent;
        
        if (UserRoleSelectionActivity.ROLE_JOINER.equals(userRole)) {
            intent = new Intent(this, JoinerHomeActivity.class);
        } else {
            // Default to HOST home (includes null/empty role cases)
            intent = new Intent(this, HomeActivity.class);
        }
        
        // Clear the task stack to prevent going back to camera
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }// Dispose resources properly
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
          // Ensure proper bitmap cleanup
        if (personDetection != null) {
            personDetection.shutdown();
        } else {
            // If personDetection is null but we still need to clean up bitmap resources
            com.summersoft.heliocam.utils.ImageUtils.clearBitmapPool();
            com.summersoft.heliocam.utils.ImageUtils.clearDetectionBitmapPool();
        }
        
        Log.d("CameraActivity", "Resources disposed successfully.");
    }@Override
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

        // Initialize directoryManager early
        directoryManager = new DetectionDirectoryManager(this);

        // Set up UI elements
        cameraView = findViewById(R.id.camera_view);

        // Check for ALL required permissions BEFORE starting anything
        if (!hasAllRequiredPermissions()) {
            // Request all permissions and return - the rest will happen in onRequestPermissionsResult
            requestAllRequiredPermissions();
            return; // ← Important! Don't continue execution
        }

        // Initialize everything after permissions are confirmed
        initializeSession(sessionId, userEmail);
    }

    /**
     * Initialize the complete session including camera, WebRTC, and detection modules
     */
    private void initializeSession(String sessionId, String userEmail) {
        // Setup directories first
        setupDirectoriesAndPermissions();
        
        // Initialize camera components
        initializeCamera();
        
        // Initialize WebRTC connection
        initializeWebRTC(sessionId, userEmail);
        
        // Initialize detection modules with proper directory configuration
        initializeDetectionModules();
        
        // Final setup
        completeSessionSetup();
    }
    
    /**
     * Initialize detection modules after camera and directories are ready
     */
    private void initializeDetectionModules() {
        try {
            // Initialize sound detection
            soundDetection = new SoundDetection(this, rtcJoiner);
            soundDetection.startDetection();
            
            // Initialize person detection
            personDetection = new PersonDetection(this, rtcJoiner);
            
            // Connect person detection to video stream after camera is ready
            if (rtcJoiner != null) {
                rtcJoiner.setPersonDetection(personDetection);
            }
            
            personDetection.start();
            
            Log.d(TAG, "Detection modules initialized successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error initializing detection modules: " + e.getMessage(), e);
            Toast.makeText(this, "Failed to initialize detection: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
    
    /**
     * Complete the session setup with UI elements
     */
    private void completeSessionSetup() {
        // Get reference to the recording status text
        recordingStatus = findViewById(R.id.recording_status);
        liveTimestampText = findViewById(R.id.live_timestamp_text); // Initialize timestamp TextView

        // Set up button click listeners
        setupButtonListeners();
        
        // Fetch and display session information
        fetchSessionName();

        // Make timestamp visible
        if (liveTimestampText != null) {
            liveTimestampText.setVisibility(View.VISIBLE);
        }
        
        Log.d(TAG, "Session setup completed successfully");
    }
    
    /**
     * Setup all button click listeners
     */
    private void setupButtonListeners() {
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
        settingsButton.setOnClickListener(v -> {
            // Show context menu on regular click instead of requiring long press
            settingsButton.showContextMenu();
        });
    }    // Add constant for request code
    private static final int CAMERA_PERMISSION_REQUEST_CODE = 100;
    private static final int ALL_PERMISSIONS_REQUEST_CODE = 101;
    
    /**
     * Check if all required permissions are granted
     */
    private boolean hasAllRequiredPermissions() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
               ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED &&
               directoryManager.hasStoragePermissions();
    }
    
    /**
     * Request all required permissions
     */
    private void requestAllRequiredPermissions() {
        List<String> permissionsToRequest = new ArrayList<>();
        
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.CAMERA);
        }
        
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.RECORD_AUDIO);
        }
        
        // Add storage permissions based on Android version
        if (!directoryManager.hasStoragePermissions()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_VIDEO);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            } else {
                permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
                permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            }
        }
        
        if (!permissionsToRequest.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                    permissionsToRequest.toArray(new String[0]),
                    ALL_PERMISSIONS_REQUEST_CODE);
        }
    }
    
    /**
     * Set up directories and check directory permissions after all permissions are granted
     */
    private void setupDirectoriesAndPermissions() {
        // Ensure we have working directories
        if (!directoryManager.hasValidDirectory()) {
            Log.d(TAG, "No user-selected directory, using app storage as default");
            // Don't prompt immediately, let user initiate if they want custom directory
            Toast.makeText(this, "Using app storage for recordings. Tap Record > Select Path to choose custom folder.", Toast.LENGTH_LONG).show();
        } else {
            Log.d(TAG, "Using previously selected directory");
        }
        
        // Update detection modules with directory manager
        if (personDetection != null) {
            personDetection.setDirectoryUri(directoryManager.hasValidDirectory() ?
                    directoryManager.getBaseDirectoryUri() : null);
        }
        
        if (soundDetection != null) {
            soundDetection.setDirectoryUri(directoryManager.hasValidDirectory() ?
                    directoryManager.getBaseDirectoryUri() : null);
        }
    }// Add this method to initialize camera-related components

    private void initializeCamera() {
        try {
            // Create webRTCClient
            rtcJoiner = new com.summersoft.heliocam.webrtc_utils.RTCJoiner(this, cameraView, mDatabase);

            // Start camera first (rtcJoiner will handle this)
            rtcJoiner.startCamera(true); // Start with front camera

            // Wait a moment for camera to initialize before connecting detection
            new android.os.Handler(Looper.getMainLooper()).postDelayed(() -> {
                // Initialize detection components AFTER camera is started
                personDetection = new PersonDetection(this, rtcJoiner);
                rtcJoiner.setPersonDetection(personDetection);
                personDetection.start();

                // Initialize sound detection
                soundDetection = new SoundDetection(this, rtcJoiner);
                soundDetection.setSoundThreshold(3000);
                soundDetection.setDetectionLatency(3000);
                soundDetection.startDetection();

                Log.d(TAG, "Detection systems initialized successfully");
            }, 500); // 500ms delay to ensure camera is ready

            // Store references for direct recording access
            videoCapturer = rtcJoiner.getVideoCapturer();
            videoTrack = rtcJoiner.getLocalVideoTrack();

            Log.d(TAG, "Camera initialized successfully");

        } catch (Exception e) {
            Log.e(TAG, "Error initializing camera: " + e.getMessage(), e);
            Toast.makeText(this, "Failed to initialize camera: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == ALL_PERMISSIONS_REQUEST_CODE) {
            boolean allGranted = true;
            StringBuilder missingPermissions = new StringBuilder();
            
            for (int i = 0; i < permissions.length; i++) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    if (missingPermissions.length() > 0) {
                        missingPermissions.append(", ");
                    }
                    missingPermissions.append(permissions[i]);
                }
            }
            
            if (allGranted) {
                // All permissions granted, initialize session
                String sessionId = getIntent().getStringExtra("session_id");
                String userEmail = mAuth.getCurrentUser().getEmail().replace(".", "_");
                initializeSession(sessionId, userEmail);
            } else {
                // Some permissions denied
                Toast.makeText(this, "Required permissions not granted: " + missingPermissions.toString(), Toast.LENGTH_LONG).show();
                Log.e(TAG, "Missing permissions: " + missingPermissions.toString());
                finish();
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


    }    @Override
    public boolean onContextItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.option_1: // Detection Settings (Combined)
                showDetectionSettingsDialog();
                return true;

            case R.id.option_2: // Replay Buffer Settings
                showReplayBufferSettingsDialog();
                return true;

            case R.id.option_3: // Start/Stop Recording
                showRecordDialog();
                return true;            case R.id.option_4: // Replay Buffer
                try {
                    if (!isReplayBufferRunning) {
                        startReplayBuffer();
                        Toast.makeText(this, "Replay buffer started", Toast.LENGTH_SHORT).show();
                    } else {
                        stopDirectReplayBufferRecording();
                        Toast.makeText(this, "Replay buffer stopped", Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error toggling replay buffer: " + e.getMessage(), e);
                    Toast.makeText(this, "Error with replay buffer: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
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
            }).start();
        });
        btnRecordStop.setOnClickListener(v -> {
            // Stop recording using CameraActivity's own method
            stopRecording();
            btnRecordNow.setEnabled(true);
            btnRecordStop.setEnabled(false);
            btnRecordBuffer.setEnabled(true);
            recordingStatus.setVisibility(View.GONE);
            Toast.makeText(this, "Recording stopped", Toast.LENGTH_SHORT).show();
        });
        btnRecordBuffer.setOnClickListener(v -> {
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
        });
        btnRecordBufferStop.setOnClickListener(v -> {
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
        builder.setTitle("Sound Detection Settings");

        // Inflate custom layout
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_latency, null);
        com.google.android.material.textfield.TextInputEditText latencyInput = view.findViewById(R.id.latencyInput);

        // Initialize with current latency (convert from milliseconds to seconds)
        int currentLatencySeconds = (int) (soundDetection.getDetectionLatency() / 1000);
        latencyInput.setText(String.valueOf(currentLatencySeconds));

        builder.setView(view);

        builder.setPositiveButton("Save", (dialog, which) -> {
            try {
                String input = latencyInput.getText().toString().trim();
                if (input.isEmpty()) {
                    Toast.makeText(this, "Please enter a value", Toast.LENGTH_SHORT).show();
                    return;
                }
                
                int latencySeconds = Integer.parseInt(input);
                if (latencySeconds < 1 || latencySeconds > 300) {
                    Toast.makeText(this, "Please enter a value between 1 and 300 seconds", Toast.LENGTH_SHORT).show();
                    return;
                }
                
                soundDetection.setDetectionLatency(latencySeconds * 1000);
                Toast.makeText(this, "Sound detection cooldown updated to " + latencySeconds + " seconds", Toast.LENGTH_SHORT).show();
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Please enter a valid number", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());

        AlertDialog dialog = builder.create();
        dialog.show();
        
        // Focus on the input field and show keyboard
        if (latencyInput != null) {
            latencyInput.requestFocus();
            latencyInput.setSelection(latencyInput.getText().length());
        }
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
     * Start recording from person detection - public method for PersonDetection class
     * @return true if recording started successfully, false otherwise
     */
    public boolean startRecordingFromDetection() {
        return startRecordingFromDetection("Person_Detected");
    }

    /**
     * Start recording from person detection with custom filename prefix
     * @param filenamePrefix Custom prefix for the filename (e.g., "Person_Detected")
     * @return true if recording started successfully, false otherwise
     */
    public boolean startRecordingFromDetection(String filenamePrefix) {
        try {
            if (canStartRecording()) {
                startRecording(filenamePrefix);
                return isRecording;
            }
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Error starting recording from detection", e);
            return false;
        }
    }

    /**
     * Stop recording from person detection - public method for PersonDetection class
     */
    public void stopRecordingFromDetection() {
        try {
            if (isRecording) {
                stopRecording();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error stopping recording from detection", e);
        }
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
    }    /**
     * Start recording the camera feed directly (bypassing RTCJoiner for better performance)
     */
    private void startRecording() {
        startRecording(null);
    }

    /**
     * Start recording the camera feed with custom filename prefix
     * @param filenamePrefix Optional prefix for the filename (null for default naming)
     */
    private void startRecording(String filenamePrefix) {
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
            // Generate a filename with timestamp and optional prefix
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            String fileName;
            if (filenamePrefix != null && !filenamePrefix.trim().isEmpty()) {
                fileName = filenamePrefix + "_" + timestamp + ".mp4";
            } else {
                fileName = timestamp + ".mp4";
            }

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
    }

    /**
     * Start direct camera recording by connecting VideoTrack to MediaRecorder surface
     */
    private void startDirectCameraRecording() throws Exception {
        try {
            // Get the MediaRecorder's surface for recording
            android.view.Surface recorderSurface = mediaRecorder.getSurface();

            if (rtcJoiner != null && rtcJoiner.getLocalVideoTrack() != null && recorderSurface != null) {
                // Get EGL context from RTCJoiner
                org.webrtc.EglBase.Context eglContext = rtcJoiner.getEglContext();
                if (eglContext != null) {

                    // Create a VideoSink that renders frames to the MediaRecorder surface
                    recordingVideoSink = new org.webrtc.VideoSink() {
                        private org.webrtc.VideoFrame lastFrame;
                        private android.graphics.Canvas canvas;

                        @Override
                        public void onFrame(org.webrtc.VideoFrame frame) {
                            try {
                                // Convert VideoFrame to bitmap and draw to surface
                                this.lastFrame = frame;

                                // Lock the canvas for drawing
                                if (recorderSurface != null && recorderSurface.isValid()) {
                                    canvas = recorderSurface.lockCanvas(null);
                                    if (canvas != null) {
                                        // Convert VideoFrame to Bitmap using proper I420 to RGB conversion
                                        org.webrtc.VideoFrame.I420Buffer i420Buffer = frame.getBuffer().toI420();

                                        // Get dimensions
                                        int width = i420Buffer.getWidth();
                                        int height = i420Buffer.getHeight();

                                        // Use the direct conversion method for better color accuracy
                                        android.graphics.Bitmap bitmap = convertI420ToBitmapDirect(i420Buffer, width, height);

                                        if (bitmap != null) {
                                            // Create a mutable copy to draw on
                                            android.graphics.Bitmap mutableBitmap = bitmap.copy(android.graphics.Bitmap.Config.ARGB_8888, true);
                                            android.graphics.Canvas tempCanvas = new android.graphics.Canvas(mutableBitmap);
                                            android.graphics.Paint paint = new android.graphics.Paint();
                                            paint.setColor(android.graphics.Color.WHITE);
                                            paint.setTextSize(20); // Adjust size as needed
                                            paint.setAntiAlias(true);
                                            String currentTime = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(new java.util.Date());
                                            tempCanvas.drawText(currentTime, 10, 30, paint); // Adjust position as needed

                                            // Scale bitmap to fit canvas
                                            android.graphics.Bitmap scaledBitmap = android.graphics.Bitmap.createScaledBitmap(
                                                    mutableBitmap, canvas.getWidth(), canvas.getHeight(), false);

                                            // Draw bitmap to canvas
                                            canvas.drawBitmap(scaledBitmap, 0, 0, null);

                                            bitmap.recycle();
                                            mutableBitmap.recycle();
                                            scaledBitmap.recycle();
                                        }

                                        // Unlock and post the canvas
                                        recorderSurface.unlockCanvasAndPost(canvas);

                                        i420Buffer.release();
                                    }
                                }
                            } catch (Exception e) {
                                Log.w(TAG, "Error rendering frame to recording surface: " + e.getMessage());
                                if (canvas != null) {
                                    try {
                                        recorderSurface.unlockCanvasAndPost(canvas);
                                    } catch (Exception ignored) {
                                    }
                                }
                            }
                        }
                    };

                    // Add the video sink to capture frames
                    rtcJoiner.getLocalVideoTrack().addSink(recordingVideoSink);

                    Log.d(TAG, "Connected video track to MediaRecorder surface via custom VideoSink");
                } else {
                    throw new Exception("EGL context not available from RTCJoiner");
                }
            } else {
                Log.e(TAG, "Missing components - rtcJoiner: " + (rtcJoiner != null) +
                        ", videoTrack: " + (rtcJoiner != null && rtcJoiner.getLocalVideoTrack() != null) +
                        ", surface: " + (recorderSurface != null));
                throw new Exception("Cannot connect video track to MediaRecorder surface - missing components");
            }

            // Start the media recorder
            Log.d(TAG, "Starting MediaRecorder...");
            mediaRecorder.start();

            // Mark the recording start time
            recordingStartTime = System.currentTimeMillis();

            // Update state
            isRecording = true;

            Log.d(TAG, "Direct camera recording started successfully at path: " + recordingPath);

        } catch (Exception e) {
            Log.e(TAG, "Failed to start direct camera recording: " + e.getMessage(), e);
            cleanupRecordingResources();
            isRecording = false;
            throw e;
        }
    }

    /**
     * Convert I420 buffer to RGB bitmap with direct pixel conversion
     */
    private android.graphics.Bitmap convertI420ToBitmapDirect(org.webrtc.VideoFrame.I420Buffer i420Buffer, int width, int height) {
        try {
            // Get Y, U, V planes
            java.nio.ByteBuffer yPlane = i420Buffer.getDataY();
            java.nio.ByteBuffer uPlane = i420Buffer.getDataU();
            java.nio.ByteBuffer vPlane = i420Buffer.getDataV();

            int yStride = i420Buffer.getStrideY();
            int uStride = i420Buffer.getStrideU();
            int vStride = i420Buffer.getStrideV();

            // Create RGB array
            int[] rgbArray = new int[width * height];

            // Convert YUV to RGB pixel by pixel
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int yIndex = y * yStride + x;
                    int uvIndex = (y / 2) * uStride + (x / 2);

                    // Get YUV values
                    int yValue = yPlane.get(yIndex) & 0xFF;
                    int uValue = (uPlane.get(uvIndex) & 0xFF) - 128;
                    int vValue = (vPlane.get(uvIndex) & 0xFF) - 128;

                    // Convert to RGB using standard formula
                    int r = (int) (yValue + 1.402 * vValue);
                    int g = (int) (yValue - 0.344 * uValue - 0.714 * vValue);
                    int b = (int) (yValue + 1.772 * uValue);

                    // Clamp values to 0-255
                    r = Math.max(0, Math.min(255, r));
                    g = Math.max(0, Math.min(255, g));
                    b = Math.max(0, Math.min(255, b));

                    // Set RGB pixel
                    rgbArray[y * width + x] = (0xFF << 24) | (r << 16) | (g << 8) | b;
                }
            }

            // Create bitmap from RGB array
            android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(
                    rgbArray, width, height, android.graphics.Bitmap.Config.ARGB_8888);

            return bitmap;

        } catch (Exception e) {
            Log.e(TAG, "Error converting I420 to bitmap directly: " + e.getMessage());
            return null;
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
            }
        } finally {
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
                            new String[]{file.getAbsolutePath()},
                            new String[]{"video/mp4"},
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
    }

    /**
     * Clean up recording resources including VideoSink and SurfaceRenderer
     */
    private void cleanupRecordingResources() {
        // Remove VideoSink from video track if it exists
        if (recordingVideoSink != null && rtcJoiner != null && rtcJoiner.getLocalVideoTrack() != null) {
            try {
                rtcJoiner.getLocalVideoTrack().removeSink(recordingVideoSink);
                Log.d(TAG, "Removed recording VideoSink from video track");
            } catch (Exception e) {
                Log.e(TAG, "Error removing VideoSink: " + e.getMessage());
            }
            recordingVideoSink = null;
        }

        // Clean up recording surface renderer
        if (recordingSurfaceRenderer != null && rtcJoiner != null && rtcJoiner.getLocalVideoTrack() != null) {
            try {
                rtcJoiner.getLocalVideoTrack().removeSink(recordingSurfaceRenderer);
                Log.d(TAG, "Removed recording SurfaceViewRenderer from video track");
            } catch (Exception e) {
                Log.e(TAG, "Error removing SurfaceViewRenderer from video track: " + e.getMessage());
            }
        }

        if (recordingSurfaceRenderer != null) {
            try {
                recordingSurfaceRenderer.release();
                Log.d(TAG, "Released recording SurfaceViewRenderer");
            } catch (Exception e) {
                Log.e(TAG, "Error releasing SurfaceViewRenderer: " + e.getMessage());
            }
            recordingSurfaceRenderer = null;
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
    }    /**
     * Start replay buffer (continuous recording buffer)
     */
    private void startReplayBuffer() {
        if (isReplayBufferRunning) {
            Log.w(TAG, "Replay buffer is already running");
            return;
        }

        // Do a final permission check before starting
        if (!canStartRecording()) {
            Log.e(TAG, "Cannot start replay buffer - permission check failed");
            Toast.makeText(this, "Cannot start replay buffer", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            // Generate a filename with timestamp
            String fileName = "replay_buffer_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".mp4";

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
                            // Initialize MediaRecorder for replay buffer
                            replayBufferRecorder = new MediaRecorder();
                            replayBufferRecorder.setOutputFile(pfd.getFileDescriptor());
                            configureMediaRecorder(replayBufferRecorder);

                            // Connect to camera surface for direct recording
                            startDirectReplayBufferRecording();
                            replayBufferPath = newFile.getUri().toString();
                            
                            isReplayBufferRunning = true;
                            Log.d(TAG, "Replay buffer started successfully");
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

            // Create file for replay buffer
            File videoFile = new File(recordingDir, fileName);
            replayBufferPath = videoFile.getAbsolutePath();

            // Initialize MediaRecorder for replay buffer
            replayBufferRecorder = new MediaRecorder();
            replayBufferRecorder.setOutputFile(replayBufferPath);
            configureMediaRecorder(replayBufferRecorder);
            startDirectReplayBufferRecording();
            
            isReplayBufferRunning = true;
            Log.d(TAG, "Replay buffer started successfully");

        } catch (Exception e) {
            Log.e(TAG, "Failed to start replay buffer", e);
            Toast.makeText(this, "Failed to start replay buffer: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            releaseReplayBufferRecorder();
            isReplayBufferRunning = false;
        }
    }

    /**
     * Start direct replay buffer recording by connecting VideoTrack to MediaRecorder surface
     */
    private void startDirectReplayBufferRecording() throws Exception {
        try {
            // Get the MediaRecorder's surface for recording
            android.view.Surface recorderSurface = replayBufferRecorder.getSurface();

            if (rtcJoiner != null && rtcJoiner.getLocalVideoTrack() != null && recorderSurface != null) {
                // Get EGL context from RTCJoiner
                org.webrtc.EglBase.Context eglContext = rtcJoiner.getEglContext();
                if (eglContext != null) {

                    // Create a VideoSink that renders frames to the MediaRecorder surface
                    replayBufferVideoSink = new org.webrtc.VideoSink() {
                        private org.webrtc.VideoFrame lastFrame;
                        private android.graphics.Canvas canvas;

                        @Override
                        public void onFrame(org.webrtc.VideoFrame frame) {
                            try {
                                // Convert VideoFrame to bitmap and draw to surface
                                this.lastFrame = frame;

                                // Lock the canvas for drawing
                                if (recorderSurface != null && recorderSurface.isValid()) {
                                    canvas = recorderSurface.lockCanvas(null);
                                    if (canvas != null) {
                                        // Convert VideoFrame to Bitmap using proper I420 to RGB conversion
                                        org.webrtc.VideoFrame.I420Buffer i420Buffer = frame.getBuffer().toI420();

                                        // Get dimensions
                                        int width = i420Buffer.getWidth();
                                        int height = i420Buffer.getHeight();

                                        // Use the direct conversion method for better color accuracy
                                        android.graphics.Bitmap bitmap = convertI420ToBitmapDirect(i420Buffer, width, height);

                                        if (bitmap != null) {
                                            // Create a mutable copy to draw on
                                            android.graphics.Bitmap mutableBitmap = bitmap.copy(android.graphics.Bitmap.Config.ARGB_8888, true);
                                            android.graphics.Canvas tempCanvas = new android.graphics.Canvas(mutableBitmap);
                                            android.graphics.Paint paint = new android.graphics.Paint();
                                            paint.setColor(android.graphics.Color.WHITE);
                                            paint.setTextSize(20); // Adjust size as needed
                                            paint.setAntiAlias(true);
                                            String currentTime = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(new java.util.Date());
                                            tempCanvas.drawText(currentTime, 10, 30, paint); // Adjust position as needed

                                            // Scale bitmap to fit canvas
                                            android.graphics.Bitmap scaledBitmap = android.graphics.Bitmap.createScaledBitmap(
                                                    mutableBitmap, canvas.getWidth(), canvas.getHeight(), false);

                                            // Draw bitmap to canvas
                                            canvas.drawBitmap(scaledBitmap, 0, 0, null);

                                            bitmap.recycle();
                                            mutableBitmap.recycle();
                                            scaledBitmap.recycle();
                                        }

                                        // Unlock and post the canvas
                                        recorderSurface.unlockCanvasAndPost(canvas);

                                        i420Buffer.release();
                                    }
                                }
                            } catch (Exception e) {
                                Log.w(TAG, "Error rendering frame to replay buffer surface: " + e.getMessage());
                                if (canvas != null) {
                                    try {
                                        recorderSurface.unlockCanvasAndPost(canvas);
                                    } catch (Exception ignored) {
                                    }
                                }
                            }
                        }
                    };

                    // Add the video sink to capture frames
                    rtcJoiner.getLocalVideoTrack().addSink(replayBufferVideoSink);

                    Log.d(TAG, "Connected video track to replay buffer MediaRecorder surface via custom VideoSink");
                } else {
                    throw new Exception("EGL context not available from RTCJoiner");
                }
            } else {
                Log.e(TAG, "Missing components - rtcJoiner: " + (rtcJoiner != null) +
                        ", videoTrack: " + (rtcJoiner != null && rtcJoiner.getLocalVideoTrack() != null) +
                        ", surface: " + (recorderSurface != null));
                throw new Exception("Cannot connect video track to replay buffer MediaRecorder surface - missing components");
            }            // Start the media recorder
            Log.d(TAG, "Starting replay buffer MediaRecorder...");
            replayBufferRecorder.start();

            // Mark the recording start time
            replayBufferStartTime = System.currentTimeMillis();

            // Schedule automatic stop based on current replay buffer duration
            Handler durationHandler = new Handler(Looper.getMainLooper());
            durationHandler.postDelayed(() -> {
                if (isReplayBufferRunning) {
                    checkReplayBufferDuration();
                }
            }, currentReplayBufferDuration);

            Log.d(TAG, "Direct replay buffer recording started successfully at path: " + replayBufferPath + 
                      " with duration limit: " + currentReplayBufferDuration + "ms");

        } catch (Exception e) {
            Log.e(TAG, "Failed to start direct replay buffer recording: " + e.getMessage(), e);
            cleanupReplayBufferResources();
            throw e;
        }
    }

    /**
     * Stop direct replay buffer recording - wrapper for stopReplayBuffer
     */
    private void stopDirectReplayBufferRecording() {
        stopReplayBuffer();
    }

    /**
     * Stop replay buffer and save the recording
     */
    private void stopReplayBuffer() {
        if (!isReplayBufferRunning) {
            Log.w(TAG, "No replay buffer running");
            return;
        }

        // Store path for later
        String storedPath = replayBufferPath;

        Log.d(TAG, "Attempting to stop replay buffer at path: " + storedPath);

        // First ensure we've recorded for at least 1 second
        long recordingDuration = System.currentTimeMillis() - replayBufferStartTime;
        if (recordingDuration < 1200) { // Give extra buffer time
            try {
                long waitTime = 1200 - recordingDuration;
                Log.d(TAG, "Replay buffer too short (" + recordingDuration + "ms), waiting " + waitTime + "ms");
                Thread.sleep(waitTime);
            } catch (InterruptedException e) {
                Log.e(TAG, "Sleep interrupted", e);
            }
        }

        // Now stop the recording
        boolean recordingStopped = false;

        try {
            // Try to stop the MediaRecorder
            if (replayBufferRecorder != null) {
                try {
                    replayBufferRecorder.stop();
                    recordingStopped = true;
                    Log.d(TAG, "Replay buffer MediaRecorder stopped successfully");
                } catch (RuntimeException e) {
                    Log.e(TAG, "Error stopping replay buffer MediaRecorder: " + e.getMessage());
                    // Fall through and try to save anyway
                }
            }
        } finally {
            // Clean up resources including VideoSink
            cleanupReplayBufferResources();
        }

        // Try to save the recording if we have a path
        if (storedPath != null) {
            // For content URIs, the file is already saved via the SAF system
            if (storedPath.startsWith("content://")) {
                Toast.makeText(this, "Replay buffer saved to custom location", Toast.LENGTH_SHORT).show();
            } else {
                // Scan file to make it visible in gallery
                File file = new File(storedPath);
                if (file.exists() && file.length() > 0) {
                    MediaScannerConnection.scanFile(
                            this,
                            new String[]{file.getAbsolutePath()},
                            new String[]{"video/mp4"},
                            null
                    );
                    Toast.makeText(this, "Replay buffer saved to " + file.getAbsolutePath(), Toast.LENGTH_SHORT).show();
                } else {
                    Log.e(TAG, "Replay buffer file does not exist or is empty: " + storedPath);
                    Toast.makeText(this, "Replay buffer may not have been saved properly", Toast.LENGTH_SHORT).show();
                }
            }
        } else {
            Toast.makeText(this, "Replay buffer path was not available", Toast.LENGTH_SHORT).show();
        }

        // Always reset state
        isReplayBufferRunning = false;
        replayBufferPath = null;
    }

    /**
     * Clean up replay buffer resources including VideoSink
     */
    private void cleanupReplayBufferResources() {
        // Remove VideoSink from video track if it exists
        if (replayBufferVideoSink != null && rtcJoiner != null && rtcJoiner.getLocalVideoTrack() != null) {
            try {
                rtcJoiner.getLocalVideoTrack().removeSink(replayBufferVideoSink);
                Log.d(TAG, "Removed replay buffer VideoSink from video track");
            } catch (Exception e) {
                Log.e(TAG, "Error removing replay buffer VideoSink: " + e.getMessage());
            }
            replayBufferVideoSink = null;
        }

        // Release MediaRecorder
        releaseReplayBufferRecorder();
    }

    /**
     * Release replay buffer MediaRecorder resources
     */
    private void releaseReplayBufferRecorder() {
        if (replayBufferRecorder != null) {
            try {
                replayBufferRecorder.reset();
            } catch (Exception e) {
                Log.e(TAG, "Error resetting replay buffer MediaRecorder", e);
            }

            try {
                replayBufferRecorder.release();
            } catch (Exception e) {
                Log.e(TAG, "Error releasing replay buffer MediaRecorder", e);
            }

            replayBufferRecorder = null;
        }
    }

    // Add this new method to your CameraActivity class
    private void showDetectionSettingsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Detection Settings");        // Inflate custom layout
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_detection_settings, null);
          // Get references to views - you'll need to create this layout file
        SeekBar soundThresholdSeekBar = view.findViewById(R.id.soundThresholdSeekBar);
        TextView soundThresholdValue = view.findViewById(R.id.soundThresholdValue);
        com.google.android.material.textfield.TextInputEditText soundLatencyInput = view.findViewById(R.id.soundLatencyInput);
        com.google.android.material.textfield.TextInputEditText personLatencyInput = view.findViewById(R.id.personLatencyInput);
        com.google.android.material.textfield.TextInputEditText videoRecordingDurationInput = view.findViewById(R.id.videoRecordingDurationInput);
        com.google.android.material.switchmaterial.SwitchMaterial autoRecordingSwitch = view.findViewById(R.id.autoRecordingSwitch);

        // Initialize sound threshold
        if (soundThresholdSeekBar != null && soundThresholdValue != null) {
            soundThresholdSeekBar.setMax(10000);
            soundThresholdSeekBar.setProgress(soundDetection.getSoundThreshold());
            soundThresholdValue.setText(String.valueOf(soundDetection.getSoundThreshold()));

            soundThresholdSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    soundThresholdValue.setText(String.valueOf(progress));
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {}

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {}
            });
        }

        // Initialize latency inputs
        if (soundLatencyInput != null) {
            int currentSoundLatencySeconds = (int) (soundDetection.getDetectionLatency() / 1000);
            soundLatencyInput.setText(String.valueOf(currentSoundLatencySeconds));
        }        if (personLatencyInput != null) {
            int currentPersonLatencySeconds = personDetection.getDetectionLatency() / 1000;
            personLatencyInput.setText(String.valueOf(currentPersonLatencySeconds));
        }        // Initialize video recording duration input
        if (videoRecordingDurationInput != null) {
            int currentVideoRecordingSeconds = personDetection.getVideoRecordingDuration() / 1000;
            videoRecordingDurationInput.setText(String.valueOf(currentVideoRecordingSeconds));
        }

        // Initialize automatic recording toggle
        if (autoRecordingSwitch != null) {
            autoRecordingSwitch.setChecked(personDetection.isAutoRecordingEnabled());
        }

        builder.setView(view);

        builder.setPositiveButton("Save", (dialog, which) -> {
            try {
                // Save sound threshold
                if (soundThresholdSeekBar != null) {
                    int threshold = soundThresholdSeekBar.getProgress();
                    soundDetection.setSoundThreshold(threshold);
                }

                // Save sound latency
                if (soundLatencyInput != null) {
                    String input = soundLatencyInput.getText().toString().trim();
                    if (!input.isEmpty()) {
                        int latencySeconds = Integer.parseInt(input);
                        if (latencySeconds >= 1 && latencySeconds <= 300) {
                            soundDetection.setDetectionLatency(latencySeconds * 1000);
                        }
                    }
                }                // Save person latency
                if (personLatencyInput != null) {
                    String input = personLatencyInput.getText().toString().trim();
                    if (!input.isEmpty()) {
                        int latencySeconds = Integer.parseInt(input);
                        if (latencySeconds >= 1 && latencySeconds <= 300) { // 1-300 seconds constraint
                            personDetection.setDetectionLatency(latencySeconds * 1000);
                        } else {
                            Toast.makeText(this, "Person detection latency must be between 1-300 seconds", Toast.LENGTH_SHORT).show();
                            return; // Don't close dialog if invalid
                        }
                    }
                }// Save video recording duration
                if (videoRecordingDurationInput != null) {
                    String input = videoRecordingDurationInput.getText().toString().trim();
                    if (!input.isEmpty()) {
                        int durationSeconds = Integer.parseInt(input);
                        if (durationSeconds >= 3 && durationSeconds <= 30) { // Match PersonDetection constraints
                            personDetection.setVideoRecordingDuration(durationSeconds * 1000);
                        } else {
                            Toast.makeText(this, "Video recording duration must be between 3-30 seconds", Toast.LENGTH_SHORT).show();
                            return; // Don't close dialog if invalid
                        }
                    }
                }

                // Save automatic recording toggle state
                if (autoRecordingSwitch != null) {
                    personDetection.setAutoRecordingEnabled(autoRecordingSwitch.isChecked());
                }

                Toast.makeText(this, "Detection settings updated", Toast.LENGTH_SHORT).show();
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Please enter valid numbers", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    /**
     * Set the replay buffer duration in milliseconds.
     * This defines how long the replay buffer should record continuously.
     * Duration is constrained between 10-60 seconds for optimal performance and storage.
     *
     * @param milliseconds The replay buffer duration in milliseconds (10000-60000ms)
     */
    public void setReplayBufferDuration(int milliseconds) {
        // Enforce 10-60 seconds constraint
        int constrainedDuration = Math.max(10000, Math.min(MAX_REPLAY_BUFFER_DURATION_MS, milliseconds));
        this.currentReplayBufferDuration = constrainedDuration;
        
        if (milliseconds != constrainedDuration) {
            Log.w(TAG, "Replay buffer duration constrained from " + milliseconds + "ms to " + constrainedDuration + "ms (10-60 seconds)");
        }
        
        Log.d(TAG, "Replay buffer duration set to " + constrainedDuration + " ms");
    }

    /**
     * Get the current replay buffer duration in milliseconds.
     *
     * @return The current replay buffer duration
     */
    public int getReplayBufferDuration() {
        return currentReplayBufferDuration;
    }

    /**
     * Check if replay buffer has reached its maximum duration and should be automatically saved
     */
    private void checkReplayBufferDuration() {
        if (isReplayBufferRunning && replayBufferStartTime > 0) {
            long currentDuration = System.currentTimeMillis() - replayBufferStartTime;
            if (currentDuration >= currentReplayBufferDuration) {
                Log.d(TAG, "Replay buffer reached maximum duration (" + currentReplayBufferDuration + "ms), auto-saving...");
                stopReplayBuffer();
                
                // Automatically restart replay buffer for continuous buffering
                Handler handler = new Handler(Looper.getMainLooper());
                handler.postDelayed(() -> {
                    if (!isRecording) { // Only restart if not doing regular recording
                        startReplayBuffer();
                        Toast.makeText(this, "Replay buffer auto-restarted", Toast.LENGTH_SHORT).show();
                    }
                }, 1000); // 1 second delay before restart
            }
        }
    }

    // Add this new method to your CameraActivity class
    private void showReplayBufferSettingsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Replay Buffer Settings");

        // Inflate custom layout
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_replay_buffer_settings, null);
        EditText durationInput = view.findViewById(R.id.replayBufferDurationInput);        // Initialize with current duration (convert from milliseconds to seconds)
        int currentDurationSeconds = getReplayBufferDuration() / 1000;
        durationInput.setText(String.valueOf(currentDurationSeconds));

        builder.setView(view);

        builder.setPositiveButton("Save", (dialog, which) -> {
            try {
                String input = durationInput.getText().toString().trim();
                if (input.isEmpty()) {
                    Toast.makeText(this, "Please enter a value", Toast.LENGTH_SHORT).show();
                    return;
                }
                
                int durationSeconds = Integer.parseInt(input);
                if (durationSeconds < 10 || durationSeconds > 60) {
                    Toast.makeText(this, "Please enter a value between 10 and 60 seconds", Toast.LENGTH_SHORT).show();
                    return;
                }
                
                setReplayBufferDuration(durationSeconds * 1000);
                Toast.makeText(this, "Replay buffer duration updated to " + durationSeconds + " seconds", Toast.LENGTH_SHORT).show();
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Please enter a valid number", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());

        AlertDialog dialog = builder.create();
        dialog.show();
        
        // Focus on the input field and show keyboard
        if (durationInput != null) {
            durationInput.requestFocus();
            durationInput.setSelection(durationInput.getText().length());
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        
        // Stop timestamp updates
        if (timestampRunnable != null) {
            timestampHandler.removeCallbacks(timestampRunnable);
        }

        // If we're pausing the activity, we should clean up some resources to avoid memory leaks
        if (isFinishing()) {
            // Only do full cleanup if we're actually finishing
            disposeResources();
        } else {
            // For temporary pause, we might need lighter cleanup
            if (personDetection != null) {
                personDetection.stop(); // Stop detection but don't fully shut down
            }
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        
        // Similar to onPause, but we might want different behavior
        if (isFinishing()) {
            // Only do full cleanup if we're actually finishing
            disposeResources();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        startTimestampUpdates();
    }

    private void startTimestampUpdates() {
        timestampRunnable = new Runnable() {
            @Override
            public void run() {
                if (liveTimestampText != null) {
                    String currentTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
                    liveTimestampText.setText(currentTime);
                }
                timestampHandler.postDelayed(this, 1000); // Update every second
            }
        };
        timestampHandler.post(timestampRunnable);
    }
}

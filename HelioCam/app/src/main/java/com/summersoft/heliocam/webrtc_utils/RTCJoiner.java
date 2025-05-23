package com.summersoft.heliocam.webrtc_utils;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaRecorder;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.opengl.GLES20;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.view.Surface;
import android.widget.Toast;
import android.app.AlertDialog;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.documentfile.provider.DocumentFile;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.summersoft.heliocam.utils.DetectionDirectoryManager;

import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraVideoCapturer;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.GlRectDrawer;
import org.webrtc.GlUtil;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RendererCommon;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoFrame;

import org.webrtc.VideoSink;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.Set;



public class RTCJoiner {
    private static final String TAG = "RTCJoiner";

    // WebRTC components
    private EglBase eglBase;
    private PeerConnectionFactory peerConnectionFactory;
    private PeerConnection peerConnection;
    private VideoTrack localVideoTrack;
    private AudioTrack localAudioTrack;
    private VideoSource videoSource;
    private AudioSource audioSource;
    private CameraVideoCapturer videoCapturer;
    private SurfaceTextureHelper surfaceTextureHelper;
    
    // Connection components
    private Context context;
    private SurfaceViewRenderer localView;
    private String sessionId;
    private String hostEmail;
    private String formattedHostEmail;
    private String joinerId;
    
    // Firebase reference
    private DatabaseReference firebaseDatabase;
    
    // User info
    private String userEmail;
    private String formattedUserEmail;
    
    // Camera state
    private boolean isUsingFrontCamera = true;
    
    // Audio/video state
    private boolean isAudioEnabled = true;
    private boolean isVideoEnabled = true;
    
    // Recording state
    private boolean isRecording = false;
    private boolean isReplayBufferRunning = false;
    private String recordingPath = null;
    private MediaRecorder mediaRecorder = null;
    
    // WebRTC TURN/STUN server configuration
    private final String stunServer = "stun:stun.relay.metered.ca:80";
   
    
    private final String meteredApiKey = "4611ab82d5ec3882669178686393394a7ca4";
    
    // Add these constants
    private static final int REPLAY_BUFFER_DURATION_MS = 30000; // 30 seconds

    // Add these fields to the top of the class with other field declarations
    private com.summersoft.heliocam.detection.PersonDetection personDetection;
    private org.webrtc.VideoSink detectionVideoSink;
    
    // Add to RTCJoiner class fields
    private int assignedCameraNumber = 1; // Default to 1, but will be set by host
    
    // Recording sinks
    private final Set<VideoSink> recordingSinks = new HashSet<>();

    // Add this private field
    private DetectionDirectoryManager directoryManager;

    // Add this field to the class
    private long recordingStartTime = 0;

    /**
     * Constructor for RTCJoiner
     * @param context Android context
     * @param localView Surface view for rendering the camera feed
     * @param firebaseDatabase Firebase database reference
     */
    public RTCJoiner(Context context, SurfaceViewRenderer localView, DatabaseReference firebaseDatabase) {
        this.context = context;
        this.localView = localView;
        this.firebaseDatabase = firebaseDatabase;
        
        // Initialize directoryManager
        this.directoryManager = new DetectionDirectoryManager(context);
        
        // Get user email
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() != null) {
            this.userEmail = auth.getCurrentUser().getEmail();
            this.formattedUserEmail = this.userEmail.replace(".", "_");
        } else {
            Log.e(TAG, "User is not authenticated");
            throw new IllegalStateException("User must be authenticated");
        }
        
        // Initialize WebRTC components
        initializeWebRTC();
    }
    
    /**
     * Check if recording can be started
     */
    public boolean canStartRecording() {
        // Check if we already have a recording running
        if (isRecording || isReplayBufferRunning) {
            return false;
        }
        
        // Check if camera and audio components are ready
        if (videoCapturer == null || localVideoTrack == null) {
            Log.e(TAG, "Camera or video track not initialized for recording");
            return false;
        }
        
        // Storage permission check based on Android version:
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // Android 13+
            // Check for READ_MEDIA_VIDEO permission
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) 
                    != PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "READ_MEDIA_VIDEO permission not granted for Android 13+");
                return false;
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { // Android 10-12
            // For Android 10-12, we need either legacy storage or WRITE_EXTERNAL_STORAGE
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) 
                    != PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "WRITE_EXTERNAL_STORAGE permission not granted for Android 10-12");
                return false;
            }
        } else { // Android 9 and below
            // Check both READ and WRITE permissions
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) 
                    != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) 
                    != PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Storage permissions not granted for Android 9 and below");
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Start recording the camera feed
     */
    public void startRecording() {
        if (isRecording) {
            Log.w(TAG, "Recording is already in progress");
            return;
        }

        // Do a final permission check before starting
        if (!canStartRecording()) {
            // Less aggressive permission requests - just return false and log why
            Log.e(TAG, "Cannot start recording - permission check failed");
            return;
        }
        
        try {
            File recordingDir;
            String fileName = directoryManager.generateTimestampedFilename("HelioCam", ".mp4");
            
            // Try to use the user-selected directory first
            DocumentFile videoClipsDir = directoryManager.getVideoClipsDirectory();
            if (videoClipsDir != null) {
                // Using SAF directory
                DocumentFile newFile = videoClipsDir.createFile("video/mp4", fileName);
                if (newFile != null) {
                    recordingPath = newFile.getUri().toString();
                    
                    // For API 26+ we need to use ParcelFileDescriptor
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        mediaRecorder = new MediaRecorder();
                        
                        ParcelFileDescriptor pfd = context.getContentResolver().openFileDescriptor(newFile.getUri(), "w");
                        if (pfd != null) {
                            mediaRecorder.setOutputFile(pfd.getFileDescriptor());
                            configureMediaRecorder(mediaRecorder);
                            startMediaRecording(mediaRecorder);
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
                recordingDir = new File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "HelioCam");
                if (!recordingDir.exists()) {
                    recordingDir.mkdirs();
                }
            }
            
            // Create file for recording
            File videoFile = new File(recordingDir, fileName);
            recordingPath = videoFile.getAbsolutePath();
            
            // Initialize MediaRecorder
            mediaRecorder = new MediaRecorder();
            mediaRecorder.setOutputFile(recordingPath);
            configureMediaRecorder(mediaRecorder);
            startMediaRecording(mediaRecorder);
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to start recording", e);
            Toast.makeText(context, "Failed to start recording: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            releaseMediaRecorder();
        }
    }
      // Helper method to configure MediaRecorder
    private void configureMediaRecorder(MediaRecorder recorder) {
        try {
            // Order is CRITICAL: 1. sources, 2. format, 3. encoders, 4. params, 5. prepare
            
            // 1. Set sources first
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
            
            // 2. Set output format
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            
            // 3. Set encoders
            recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            
            // 4. Set parameters - improved quality settings but ensure compatibility
            recorder.setVideoEncodingBitRate(2500000); // 2.5 Mbps - good balance of quality vs compatibility
            recorder.setVideoFrameRate(30);  // 30fps for smoother video

            // Ensure we use the same resolution as we're capturing
            // This ensures proper aspect ratio without black bars
            recorder.setVideoSize(1280, 720); // HD resolution with 16:9 aspect ratio
            
            // Better audio quality but ensure compatibility
            recorder.setAudioEncodingBitRate(128000); // 128 kbps audio
            recorder.setAudioChannels(2); // Stereo
            recorder.setAudioSamplingRate(44100); // 44.1 kHz
            
            // 5. Prepare the recorder
            recorder.prepare();
            
            Log.d(TAG, "MediaRecorder configured successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error configuring MediaRecorder: " + e.getMessage(), e);
            throw new RuntimeException("MediaRecorder configuration failed", e);
        }
    }    // Helper method to start media recording
    private void startMediaRecording(MediaRecorder recorder) throws Exception {
        try {
            // Step 1: Get the surface from the recorder BEFORE starting
            Surface recorderSurface = recorder.getSurface();
            if (recorderSurface == null) {
                throw new RuntimeException("Failed to get recording surface");
            }
            
            // Step 2: Clean up any existing recording sinks
            for (VideoSink sink : new HashSet<>(recordingSinks)) {
                if (localVideoTrack != null) {
                    localVideoTrack.removeSink(sink);
                }
                if (sink instanceof SurfaceVideoSink) {
                    ((SurfaceVideoSink) sink).release();
                }
            }
            recordingSinks.clear();
            
            // Step 3: Create and add a new video sink
            SurfaceVideoSink videoSink = new SurfaceVideoSink(recorderSurface);
            recordingSinks.add(videoSink);
            
            // Step 4: Add the sink to the video track BEFORE starting recording
            if (localVideoTrack != null) {
                localVideoTrack.addSink(videoSink);
                
                // Wait briefly to ensure sink is initialized
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ignored) {}
                
                // Step 5: Now start the media recorder
                Log.d(TAG, "Starting MediaRecorder...");
                recorder.start();
                
                isRecording = true;
                recordingStartTime = System.currentTimeMillis();
                Log.d(TAG, "Recording started at: " + recordingPath);
            } else {
                throw new RuntimeException("Video track is not available");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to start recording: " + e.getMessage(), e);
            
            try {
                // Try to reset the recorder on failure
                recorder.reset();
                recordingSinks.clear();
            } catch (Exception ignored) {
                // Ignore cleanup errors
            }
            throw e;
        }
    }
    
    /**
     * Stop recording
     */
    public void stopRecording() {
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
            // First remove any sinks before stopping
            if (localVideoTrack != null) {
                for (VideoSink sink : new HashSet<>(recordingSinks)) { // Use copy to avoid concurrent modification
                    localVideoTrack.removeSink(sink);
                }
            }
            
            // Then try to stop the MediaRecorder
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
            // Clean up resources
            for (VideoSink sink : recordingSinks) {
                if (sink instanceof SurfaceVideoSink) {
                    ((SurfaceVideoSink) sink).release();
                }
            }
            recordingSinks.clear();
            releaseMediaRecorder();
        }
        
        // Try to save the recording if we have a path
        if (storedPath != null) {
            // For content URIs, the file is already saved via the SAF system
            if (storedPath.startsWith("content://")) {
                Toast.makeText(context, recordingStopped ? 
                        "Recording saved successfully" : 
                        "Recording may not have saved properly", Toast.LENGTH_SHORT).show();
            } else {
                // For file paths, check if the file exists and has data
                File recordingFile = new File(storedPath);
                if (recordingFile.exists() && recordingFile.length() > 0) {
                    // Make the file visible in Gallery
                    MediaScannerConnection.scanFile(context,
                            new String[]{storedPath}, null,
                            (path, uri) -> {
                                Log.d(TAG, "Recording added to gallery: " + uri);
                            });
                
                    Toast.makeText(context, "Recording saved", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(context, "Recording failed to save properly", Toast.LENGTH_SHORT).show();
                }
            }
        } else {
            Toast.makeText(context, "Recording path was not available", Toast.LENGTH_SHORT).show();
        }
        
        // Always reset state
        isRecording = false;
        recordingPath = null;
    }
    
    /**
     * Release MediaRecorder resources
     */
    private void releaseMediaRecorder() {
        // First remove any video sinks associated with recording
        if (localVideoTrack != null) {
            for (VideoSink sink : recordingSinks) {
                localVideoTrack.removeSink(sink);
                if (sink instanceof SurfaceVideoSink) {
                    ((SurfaceVideoSink) sink).release();
                }
            }
            recordingSinks.clear();
        }
        
        // Release the MediaRecorder
        if (mediaRecorder != null) {
            try {
                mediaRecorder.reset();
                mediaRecorder.release();
            } catch (Exception e) {
                Log.e(TAG, "Error releasing MediaRecorder", e);
            } finally {
                mediaRecorder = null;
            }
        }
    }
    
    /**
     * Check if replay buffer can be started
     */
    public boolean canStartReplayBuffer() {
        // Similar checks as for regular recording
        if (isRecording || isReplayBufferRunning) {
            return false;
        }
        
        // For Android 10+ (Q and above), we need to use scoped storage
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) 
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions((Activity) context,
                        new String[]{
                            Manifest.permission.READ_EXTERNAL_STORAGE, 
                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                        }, 1);
                return false;
            }
        } else {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) 
                    != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) 
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions((Activity) context,
                        new String[]{
                            Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                        }, 1);
                return false;
            }
        }
        
        return videoCapturer != null && localVideoTrack != null;
    }
    
    /**
     * Start replay buffer (circular recording buffer)
     */
    public void startReplayBuffer() {
        if (isReplayBufferRunning) {
            Log.w(TAG, "Replay buffer is already running");
            return;
        }
        
        try {
            // Create temporary file for buffer
            File outputDir = context.getCacheDir();
            File tempFile = File.createTempFile("buffer", ".mp4", outputDir);
            recordingPath = tempFile.getAbsolutePath();
            
            // Initialize MediaRecorder
            mediaRecorder = new MediaRecorder();
            mediaRecorder.setOutputFile(recordingPath);
            
            // Configure MediaRecorder for buffer recording
            mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            
            // Set consistent resolution with camera capture (1280x720) for proper aspect ratio
            mediaRecorder.setVideoEncodingBitRate(1500000); // Lower bitrate for buffer
            mediaRecorder.setVideoFrameRate(30); // Match frame rate with camera capture
            mediaRecorder.setVideoSize(1280, 720); // HD resolution with 16:9 aspect ratio
            
            // Don't set max duration - causes issues
            // if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            //    mediaRecorder.setMaxDuration(REPLAY_BUFFER_DURATION_MS);
            // }
            
            mediaRecorder.prepare();
            
            // Get the surface for recording
            Surface recorderSurface = mediaRecorder.getSurface();
            SurfaceVideoSink videoSink = new SurfaceVideoSink(recorderSurface);
            
            // Add the sink to our collection and to the video track
            recordingSinks.add(videoSink);
            if (localVideoTrack != null) {
                localVideoTrack.addSink(videoSink);
            }
            
            // Start recording
            mediaRecorder.start();
            isReplayBufferRunning = true;
            recordingStartTime = System.currentTimeMillis(); // Track when we started recording
            
            Log.d(TAG, "Replay buffer started");
            Toast.makeText(context, "Replay buffer started", Toast.LENGTH_SHORT).show();
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to start replay buffer", e);
            Toast.makeText(context, "Failed to start replay buffer: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            releaseMediaRecorder();
        }
    }
    
    /**
     * Stop replay buffer and save the recording
     */
    public void stopReplayBuffer() {
        if (!isReplayBufferRunning) {
            Log.w(TAG, "No replay buffer running to stop");
            return;
        }
        
        // First remove any video sinks associated with recording
        if (localVideoTrack != null) {
            for (VideoSink sink : recordingSinks) {
                localVideoTrack.removeSink(sink);
                if (sink instanceof SurfaceVideoSink) {
                    ((SurfaceVideoSink) sink).release();
                }
            }
            recordingSinks.clear();
        }
        
        // Store path for later
        String storedPath = recordingPath;
        
        boolean recordingStopped = false;
        if (mediaRecorder != null) {
            try {
                mediaRecorder.stop();
                recordingStopped = true;
            } catch (RuntimeException e) {
                Log.e(TAG, "Error stopping replay buffer", e);
                // We'll still try to save if something exists
            } finally {
                releaseMediaRecorder();
            }
        }
        
        // Only try to save if we have a path and successfully stopped
        if (recordingStopped && storedPath != null) {
            try {
                // Generate a filename with timestamp
                String fileName = directoryManager.generateTimestampedFilename("HelioCam_Buffer", ".mp4");
                
                // Try to use the user-selected directory first
                DocumentFile videoClipsDir = directoryManager.getVideoClipsDirectory();
                if (videoClipsDir != null) {
                    // Create a new file in DocumentFile directory
                    DocumentFile newFile = videoClipsDir.createFile("video/mp4", fileName);
                    if (newFile != null) {
                        // Copy from temp file to DocumentFile
                        InputStream input = new FileInputStream(storedPath);
                        OutputStream output = context.getContentResolver().openOutputStream(newFile.getUri());
                        
                        if (output != null) {
                            byte[] buffer = new byte[4096];
                            int length;
                            while ((length = input.read(buffer)) > 0) {
                                output.write(buffer, 0, length);
                            }
                            output.close();
                            input.close();
                            
                            Toast.makeText(context, "Replay buffer saved", Toast.LENGTH_SHORT).show();
                            // Delete temp file
                            new File(storedPath).delete();
                            isReplayBufferRunning = false;
                            recordingPath = null;
                            return;
                        }
                    }
                }
                
                // (The rest of the save functionality stays the same)
                // Fallback to app-specific or public directory
                File moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES);
                File recordingDir = new File(moviesDir, "HelioCam");
                if (!recordingDir.exists()) {
                    recordingDir.mkdirs();
                }
                
                // If that fails, use app storage
                if (!recordingDir.exists()) {
                    recordingDir = directoryManager.getAppStorageDirectory("Video_Clips");
                }
                
                File destFile = new File(recordingDir, fileName);
                
                // Copy buffer to permanent file
                FileInputStream fis = new FileInputStream(recordingPath);
                FileOutputStream fos = new FileOutputStream(destFile);
                byte[] buffer = new byte[4096];
                int length;
                while ((length = fis.read(buffer)) > 0) {
                    fos.write(buffer, 0, length);
                }
                fis.close();
                fos.close();
                
                // Make the video visible in gallery
                MediaScannerConnection.scanFile(context,
                        new String[]{destFile.getAbsolutePath()}, null,
                        (path, uri) -> Log.d(TAG, "Buffer saved: " + uri));
                
                Toast.makeText(context, "Replay buffer saved", Toast.LENGTH_SHORT).show();
                
                // Delete temp file
                new File(recordingPath).delete();
            } catch (Exception e) {
                Log.e(TAG, "Error saving buffer: " + e.getMessage());
                Toast.makeText(context, "Error saving buffer: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(context, "Buffer not saved - no valid data recorded", Toast.LENGTH_SHORT).show();
        }
        
        // Always reset state
        isReplayBufferRunning = false;
        recordingPath = null;
    }
    
    /**
     * Initialize WebRTC components
     */
    private void initializeWebRTC() {
        // Create EGL context
        eglBase = EglBase.create();
        
        // Initialize the local view
        localView.init(eglBase.getEglBaseContext(), null);
        localView.setMirror(true);
        
        // Initialize the PeerConnectionFactory
        PeerConnectionFactory.InitializationOptions options = 
                PeerConnectionFactory.InitializationOptions.builder(context)
                        .createInitializationOptions();
        PeerConnectionFactory.initialize(options);
        
        DefaultVideoEncoderFactory encoderFactory = 
                new DefaultVideoEncoderFactory(eglBase.getEglBaseContext(), true, true);
        DefaultVideoDecoderFactory decoderFactory = 
                new DefaultVideoDecoderFactory(eglBase.getEglBaseContext());
        
        PeerConnectionFactory.Options peerOptions = new PeerConnectionFactory.Options();
        
        peerConnectionFactory = PeerConnectionFactory.builder()
                .setOptions(peerOptions)
                .setVideoEncoderFactory(encoderFactory)
                .setVideoDecoderFactory(decoderFactory)
                .createPeerConnectionFactory();
    }
    
    /**
     * Start camera capture
     * @param useFrontCamera Whether to use front camera
     */
    public void startCamera(boolean useFrontCamera) {
        this.isUsingFrontCamera = useFrontCamera;
        
        // Check if camera is already running
        if (videoCapturer != null) {
            Log.d(TAG, "Camera is already running");
            return;
        }
        
        // Create camera capturer
        Camera2Enumerator enumerator = new Camera2Enumerator(context);
        videoCapturer = createCameraCapturer(enumerator, useFrontCamera);
        
        if (videoCapturer == null) {
            Log.e(TAG, "Failed to create camera capturer");
            Toast.makeText(context, "Failed to access camera", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Create surface texture helper
        surfaceTextureHelper = 
                SurfaceTextureHelper.create("CaptureThread", eglBase.getEglBaseContext());
        
        // Create video source and track
        videoSource = peerConnectionFactory.createVideoSource(videoCapturer.isScreencast());
        videoCapturer.initialize(surfaceTextureHelper, context, videoSource.getCapturerObserver());
        localVideoTrack = peerConnectionFactory.createVideoTrack("video", videoSource);
        localVideoTrack.setEnabled(true);
        localVideoTrack.addSink(localView);
        // Connect to person detection if available
        if (personDetection != null) {
            Log.d(TAG, "Connecting video track to person detection");
            localVideoTrack.addSink(personDetection);
        }
        
        // Create audio source and track
        MediaConstraints audioConstraints = new MediaConstraints();
        audioConstraints.mandatory.add(
                new MediaConstraints.KeyValuePair("googEchoCancellation", "true"));
        audioConstraints.mandatory.add(
                new MediaConstraints.KeyValuePair("googAutoGainControl", "true"));
        audioConstraints.mandatory.add(
                new MediaConstraints.KeyValuePair("googHighpassFilter", "true"));
        audioConstraints.mandatory.add(
                new MediaConstraints.KeyValuePair("googNoiseSuppression", "true"));
                
        audioSource = peerConnectionFactory.createAudioSource(audioConstraints);
        localAudioTrack = peerConnectionFactory.createAudioTrack("audio", audioSource);
        localAudioTrack.setEnabled(true);
        
        // Start capture
        try {
            videoCapturer.startCapture(1280, 720, 30); // HD resolution with 16:9 aspect ratio
            Log.d(TAG, "Camera started with " + (useFrontCamera ? "front" : "back") + " camera");
        } catch (Exception e) {
            Log.e(TAG, "Failed to start camera capture", e);
        }
    }
    
    /**
     * Join a session as a camera
     * @param hostEmail Email of the host
     * @param sessionId Session ID to join
     */
    public void joinSession(String hostEmail, String sessionId) {
        this.hostEmail = hostEmail;
        this.formattedHostEmail = hostEmail.replace(".", "_");
        this.sessionId = sessionId;
        this.joinerId = UUID.randomUUID().toString();
        
        Log.d(TAG, "Joining session: " + sessionId + " hosted by: " + hostEmail);
        
        // Get unique device identifiers
        String deviceId = android.provider.Settings.Secure.getString(
                context.getContentResolver(), 
                android.provider.Settings.Secure.ANDROID_ID);
        
        String deviceName = Build.MANUFACTURER + " " + Build.MODEL;
        
        // Send join request with device identifiers
        Map<String, Object> joinRequest = new HashMap<>();
        joinRequest.put("email", userEmail);
        joinRequest.put("timestamp", System.currentTimeMillis());
        joinRequest.put("deviceId", deviceId);  // Add unique device ID
        joinRequest.put("deviceName", deviceName);  // Add device name
        
        DatabaseReference joinRequestRef = firebaseDatabase.child("users")
                .child(formattedHostEmail)
                .child("sessions")
                .child(sessionId)
                .child("join_requests")
                .child(joinerId);
                
        joinRequestRef.setValue(joinRequest)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Join request sent successfully");
                    listenForOffer();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to send join request", e);
                    Toast.makeText(context, "Failed to join session", Toast.LENGTH_SHORT).show();
                });
    }
    
    /**
     * Listen for SDP offer from host
     */
    private void listenForOffer() {
        DatabaseReference offerRef = firebaseDatabase.child("users")
                .child(formattedUserEmail)
                .child("sessions")
                .child(sessionId)
                .child("Offer");
                
        offerRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    String offerSdp = dataSnapshot.getValue(String.class);
                    if (offerSdp != null) {
                        Log.d(TAG, "Received offer from host");
                        
                        // Initialize peer connection now that we have an offer
                        initializePeerConnection(offerSdp);
                        
                        // Remove this listener after getting the offer
                        offerRef.removeEventListener(this);
                    }
                }
            }
            
            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.e(TAG, "Failed to listen for offer: " + databaseError.getMessage());
            }
        });

        DatabaseReference assignedCameraRef = firebaseDatabase.child("users")
                .child(formattedHostEmail)
                .child("sessions")
                .child(sessionId)
                .child("join_requests")
                .child(joinerId)
                .child("assigned_camera");
                
        assignedCameraRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    Integer cameraNumber = dataSnapshot.getValue(Integer.class);
                    if (cameraNumber != null) {
                        setAssignedCameraNumber(cameraNumber);
                    }
                }
            }
            
            @Override
            public void onCancelled(DatabaseError databaseError) {
                // Handle error
            }
        });
    }
    
    /**
     * Initialize peer connection with received offer
     */
    private void initializePeerConnection(String offerSdp) {
        // Create RTCConfiguration with ICE servers
        PeerConnection.RTCConfiguration rtcConfig = 
                new PeerConnection.RTCConfiguration(getIceServers());
        rtcConfig.iceTransportsType = PeerConnection.IceTransportsType.ALL;
        
        // Create peer connection
        peerConnection = peerConnectionFactory.createPeerConnection(
                rtcConfig,
                new PeerConnectionAdapter() {
                    @Override
                    public void onIceCandidate(IceCandidate candidate) {
                        super.onIceCandidate(candidate);
                        sendIceCandidateToHost(candidate);
                    }
                    
                    @Override
                    public void onAddStream(MediaStream stream) {
                        super.onAddStream(stream);
                        // Handle incoming audio from host if needed
                        if (stream.audioTracks.size() > 0) {
                            stream.audioTracks.get(0).setEnabled(true);
                        }
                    }
                });
                
        if (peerConnection == null) {
            Log.e(TAG, "Failed to create peer connection");
            return;
        }
        
        // Add local media stream with video and audio
        MediaStream localStream = peerConnectionFactory.createLocalMediaStream("cameraStream");
        if (localVideoTrack != null) {
            localStream.addTrack(localVideoTrack);
        }
        if (localAudioTrack != null) {
            localStream.addTrack(localAudioTrack);
        }
        peerConnection.addStream(localStream);
        
        // Set remote description (the host's offer)
        SessionDescription offer = new SessionDescription(
                SessionDescription.Type.OFFER, offerSdp);
                
        peerConnection.setRemoteDescription(new SdpAdapter("SetRemoteDescription") {
            @Override
            public void onSetSuccess() {
                super.onSetSuccess();
                
                // Create answer
                createAnswer();
            }
            
            @Override
            public void onSetFailure(String error) {
                Log.e(TAG, "Failed to set remote description: " + error);
            }
        }, offer);
        
        // Listen for ICE candidates from host
        listenForHostIceCandidates();
    }
    
    /**
     * Create SDP answer
     */
    private void createAnswer() {
        MediaConstraints constraints = new MediaConstraints();
        constraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        constraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"));
        
        peerConnection.createAnswer(new SdpAdapter("CreateAnswer") {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                super.onCreateSuccess(sessionDescription);
                
                peerConnection.setLocalDescription(
                        new SdpAdapter("SetLocalDescription"), sessionDescription);
                
                // Send answer to host
                sendAnswerToHost(sessionDescription);
            }
            
            @Override
            public void onCreateFailure(String error) {
                Log.e(TAG, "Failed to create answer: " + error);
            }
        }, constraints);
    }
    
    /**
     * Send SDP answer to host
     */
    private void sendAnswerToHost(SessionDescription answer) {
        DatabaseReference answerRef = firebaseDatabase.child("users")
                .child(formattedUserEmail)
                .child("sessions")
                .child(sessionId)
                .child("Answer");
                
        answerRef.setValue(answer.description)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Answer sent to host successfully");
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to send answer to host", e);
                });
    }
    
    /**
     * Send ICE candidate to host
     */
    private void sendIceCandidateToHost(IceCandidate candidate) {
        DatabaseReference iceCandidatesRef = firebaseDatabase.child("users")
                .child(formattedUserEmail)
                .child("sessions")
                .child(sessionId)
                .child("ice_candidates");
                
        String candidateId = "camera_candidate_" + UUID.randomUUID().toString().substring(0, 8);
        
        Map<String, Object> candidateData = new HashMap<>();
        candidateData.put("candidate", candidate.sdp);
        candidateData.put("sdpMid", candidate.sdpMid);
        candidateData.put("sdpMLineIndex", candidate.sdpMLineIndex);
        
        iceCandidatesRef.child(candidateId).setValue(candidateData)
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to send ICE candidate to host", e);
                });
    }
    
    /**
     * Listen for ICE candidates from host
     */
    private void listenForHostIceCandidates() {
        DatabaseReference hostCandidatesRef = firebaseDatabase.child("users")
                .child(formattedUserEmail)
                .child("sessions")
                .child(sessionId)
                .child("host_candidates");
                
        hostCandidatesRef.addChildEventListener(new ChildEventListener() {
            @Override
            public void onChildAdded(DataSnapshot dataSnapshot, String previousChildName) {
                String sdp = dataSnapshot.child("sdp").getValue(String.class);
                String sdpMid = dataSnapshot.child("sdpMid").getValue(String.class);
                Integer sdpMLineIndex = dataSnapshot.child("sdpMLineIndex").getValue(Integer.class);
                
                if (sdp != null && sdpMid != null && sdpMLineIndex != null) {
                    IceCandidate iceCandidate = new IceCandidate(sdpMid, sdpMLineIndex, sdp);
                    
                    if (peerConnection != null) {
                        peerConnection.addIceCandidate(iceCandidate);
                        Log.d(TAG, "Added ICE candidate from host");
                    }
                }
            }

            @Override
            public void onChildChanged(DataSnapshot dataSnapshot, String previousChildName) {}

            @Override
            public void onChildRemoved(DataSnapshot dataSnapshot) {}

            @Override
            public void onChildMoved(DataSnapshot dataSnapshot, String previousChildName) {}

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.e(TAG, "Failed to listen for ICE candidates: " + databaseError.getMessage());
            }
        });
    }
    
    /**
     * Create camera capturer
     */
    private CameraVideoCapturer createCameraCapturer(Camera2Enumerator enumerator, 
                                                    boolean useFrontCamera) {
        for (String deviceName : enumerator.getDeviceNames()) {
            if (useFrontCamera && enumerator.isFrontFacing(deviceName)) {
                return enumerator.createCapturer(deviceName, null);
            } else if (!useFrontCamera && enumerator.isBackFacing(deviceName)) {
                return enumerator.createCapturer(deviceName, null);
            }
        }
        
        // If specific camera not found, try any camera
        for (String deviceName : enumerator.getDeviceNames()) {
            return enumerator.createCapturer(deviceName, null);
        }
        
        return null;
    }
    
    /**
     * Switch between front and back camera
     */
    public void switchCamera() {
        if (videoCapturer != null) {
            ((CameraVideoCapturer) videoCapturer).switchCamera(null);
            isUsingFrontCamera = !isUsingFrontCamera;
            localView.setMirror(isUsingFrontCamera);
        }
    }
    
    /**
     * Toggle video on/off
     */
    public void toggleVideo() {
        if (localVideoTrack != null) {
            isVideoEnabled = !isVideoEnabled;
            localVideoTrack.setEnabled(isVideoEnabled);
            String message = isVideoEnabled ? "Video enabled" : "Video disabled";
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * Mute microphone
     */
    public void muteMic() {
        if (localAudioTrack != null) {
            isAudioEnabled = false;
            localAudioTrack.setEnabled(false);
        }
    }
    
    /**
     * Unmute microphone
     */
    public void unmuteMic() {
        if (localAudioTrack != null) {
            isAudioEnabled = true;
            localAudioTrack.setEnabled(true);
        }
    }
    
    /**
     * Get ICE servers list
     */
    private List<PeerConnection.IceServer> getIceServers() {
        return Arrays.asList(
            PeerConnection.IceServer.builder("stun:stun.relay.metered.ca:80").createIceServer(),
            PeerConnection.IceServer.builder("turn:asia.relay.metered.ca:80")
                .setUsername("08a10b202c595304495012c2")
                .setPassword("JnsH2+jc2q3/uGon")
                .createIceServer(),
            PeerConnection.IceServer.builder("turn:asia.relay.metered.ca:80?transport=tcp")
                .setUsername("08a10b202c595304495012c2")
                .setPassword("JnsH2+jc2q3/uGon")
                .createIceServer(),
            PeerConnection.IceServer.builder("turn:asia.relay.metered.ca:443")
                .setUsername("08a10b202c595304495012c2")
                .setPassword("JnsH2+jc2q3/uGon")
                .createIceServer(),
            PeerConnection.IceServer.builder("turns:asia.relay.metered.ca:443?transport=tcp")
                .setUsername("08a10b202c595304495012c2")
                .setPassword("JnsH2+jc2q3/uGon")
                .createIceServer()
        );
    }
    
    /**
     * Dispose of all resources
     */
    public void dispose() {
        // Stop recording if active
        if (isRecording) {
            stopRecording();
        }
        
        if (isReplayBufferRunning) {
            stopReplayBuffer();
        }
        
        // Release MediaRecorder resources
        releaseMediaRecorder();
        
        // Remove join request from Firebase
        if (joinerId != null && formattedHostEmail != null && sessionId != null) {
            firebaseDatabase.child("users")
                    .child(formattedHostEmail)
                    .child("sessions")
                    .child(sessionId)
                    .child("join_requests")
                    .child(joinerId)
                    .removeValue();
        }
        
        // Stop camera capture
        if (videoCapturer != null) {
            try {
                videoCapturer.stopCapture();
            } catch (InterruptedException e) {
                Log.e(TAG, "Failed to stop camera", e);
            }
            videoCapturer.dispose();
            videoCapturer = null;
        }
        
        // Close peer connection
        if (peerConnection != null) {
            peerConnection.close();
            peerConnection = null;
        }
        
        // Dispose of video source
        if (videoSource != null) {
            videoSource.dispose();
            videoSource = null;
        }
        
        // Dispose of audio source
        if (audioSource != null) {
            audioSource.dispose();
            audioSource = null;
        }
        
        // Release surface texture helper
        if (surfaceTextureHelper != null) {
            surfaceTextureHelper.dispose();
            surfaceTextureHelper = null;
        }
        
        // Release local view
        if (localView != null) {
            localView.release();
        }
        
        // Release EGL base
        if (eglBase != null) {
            eglBase.release();
            eglBase = null;
        }
        
        // Dispose of factory
        if (peerConnectionFactory != null) {
            peerConnectionFactory.dispose();
            peerConnectionFactory = null;
        }
    }
    
    /**
     * Set person detection module to receive video frames
     * @param personDetection The PersonDetection instance
     */
    public void setPersonDetection(com.summersoft.heliocam.detection.PersonDetection personDetection) {
        this.personDetection = personDetection;
        
        // If we have a video track, connect it to the person detection
        if (localVideoTrack != null && personDetection != null) {
            Log.d(TAG, "Connecting video track to person detection");
            localVideoTrack.addSink(personDetection);
        }
    }
    
    /**
     * Find a session by session code (passkey)
     * @param sessionCode The 6-digit session code entered by user
     */
    public static void findSessionByCode(String sessionCode, SessionFoundCallback callback) {
        DatabaseReference sessionCodeRef = FirebaseDatabase.getInstance().getReference().child("session_codes").child(sessionCode);
    
        sessionCodeRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    String sessionId = dataSnapshot.child("session_id").getValue(String.class);
                    String hostEmail = dataSnapshot.child("host_email").getValue(String.class);
                    
                    if (sessionId != null && hostEmail != null) {
                        callback.onSessionFound(sessionId, hostEmail);
                    } else {
                        callback.onError("Session data incomplete", null);
                    }
                } else {
                    callback.onSessionNotFound();
                }
            }
            
            @Override
            public void onCancelled(DatabaseError databaseError) {
                callback.onError("Database error", databaseError.toException());
            }
        });
    }
    
    /**
     * Callback interface for session lookup
     */
    public interface SessionFoundCallback {
        void onSessionFound(String sessionId, String hostEmail);
        void onSessionNotFound();
        void onError(String message, Exception e);
    }

    /**
     * Report a detection event to the host session
     * @param detectionType Type of detection (sound, person)
     * @param detectionData Additional data about the detection
     */
    public void reportDetectionEvent(String detectionType, Map<String, Object> detectionData) {
        if (sessionId == null || hostEmail == null) {
            Log.e(TAG, "Cannot report detection - missing session or host info");
            return;
        }

        // Get the current time for the event ID
        long timestamp = System.currentTimeMillis();
        
        // Create detection event data matching the web format
        Map<String, Object> detectionEvent = new HashMap<>();
        detectionEvent.put("type", detectionType);
        detectionEvent.put("timestamp", timestamp);
        detectionEvent.put("cameraNumber", assignedCameraNumber);
        detectionEvent.put("deviceName", Build.MANUFACTURER + " " + Build.MODEL);
        detectionEvent.put("email", userEmail);
        
        // Add any additional data
        if (detectionData != null) {
            detectionEvent.put("data", detectionData);
        }
        
        // Format email for Firebase path
        String formattedHostEmail = hostEmail.replace(".", "_");
        
        // Use the detection_events path that matches the web app
        String detectionPath = "users/" + formattedHostEmail + "/sessions/" + sessionId + 
                "/detection_events/" + timestamp;
        
        // Send to Firebase
        firebaseDatabase.child(detectionPath).setValue(detectionEvent)
            .addOnSuccessListener(aVoid -> {
                Log.d(TAG, "Reported " + detectionType + " detection to host");
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error reporting detection", e);
            });
    }

    // Helper methods for specific detection types (no motion detection)
    public void reportSoundDetection(double amplitude) {
        Map<String, Object> data = new HashMap<>();
        data.put("amplitude", amplitude);
        reportDetectionEvent("sound", data);
    }

    public void reportPersonDetection(int personCount) {
        Map<String, Object> data = new HashMap<>();
        data.put("count", personCount);
        reportDetectionEvent("person", data);
    }

    /**
     * Set the camera number assigned by the host
     * @param cameraNumber The assigned camera number (1-4)
     */
    public void setAssignedCameraNumber(int cameraNumber) {
        this.assignedCameraNumber = cameraNumber;
        Log.d(TAG, "Camera assigned number: " + cameraNumber);
    }

    /**
     * Check if recording capabilities are available on this device
     * @return true if recording is supported
     */
    public boolean isRecordingSupported() {
        // Check if the necessary components exist
        return (eglBase != null && videoSource != null && localVideoTrack != null);
    }
      private class SurfaceVideoSink implements VideoSink {
        private final Surface surface;
        private EglBase eglBase;
        private boolean initialized = false;
        private final Object syncObject = new Object();
        private Handler handler;
        private HandlerThread thread;
        private GlRectDrawer drawer;
        private VideoFrame frame; // Store frame reference for rotation information

        public SurfaceVideoSink(Surface surface) {
            this.surface = surface;
            // Create a dedicated thread for EGL operations
            thread = new HandlerThread("SurfaceVideoSinkThread");
            thread.start();
            handler = new Handler(thread.getLooper());
            // Initialize on the EGL thread
            handler.post(this::initialize);
        }
        
        private void initialize() {
            synchronized (syncObject) {
                // Clean up any existing EGL resources
                releaseEglContext();
                
                try {
                    // Create a shared EGL context with RECORDABLE flag
                    eglBase = EglBase.create(RTCJoiner.this.eglBase.getEglBaseContext(), EglBase.CONFIG_RECORDABLE);
                    if (eglBase == null) {
                        Log.e(TAG, "Failed to create EGL base for recording");
                        return;
                    }
                    
                    // Create surface and make context current
                    try {
                        eglBase.createSurface(surface);
                        eglBase.makeCurrent();
                        
                        // Create the drawer for rendering frames
                        drawer = new GlRectDrawer();
                        
                        // Clear surface with black color
                        GLES20.glClearColor(0, 0, 0, 1);
                        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
                        
                        // Swap buffers to apply changes
                        eglBase.swapBuffers();
                        
                        initialized = true;
                        Log.d(TAG, "SurfaceVideoSink initialized successfully");
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to initialize EGL surface: " + e.getMessage(), e);
                        releaseEglContext();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Failed to create EGL context: " + e.getMessage(), e);
                    releaseEglContext();
                }
            }
        }

        private void releaseEglContext() {
            synchronized (syncObject) {
                if (drawer != null) {
                    drawer.release();
                    drawer = null;
                }
                
                if (eglBase != null) {
                    try {
                        eglBase.release();
                    } catch (Exception e) {
                        Log.e(TAG, "Error releasing EGL resources", e);
                    } finally {
                        eglBase = null;
                    }
                }            }
        }
        
        @Override
        public void onFrame(VideoFrame frame) {
            // Skip if not initialized
            if (!initialized || eglBase == null) {
                return;
            }
            
            // Retain the frame for processing on our thread
            frame.retain();
            
            // Process frame on dedicated EGL thread
            handler.post(() -> {
                try {
                    synchronized (syncObject) {
                        if (!initialized || eglBase == null || drawer == null) {
                            frame.release();
                            return;
                        }
                        
                        try {
                            eglBase.makeCurrent();
                            
                            // Get the frame buffer and convert to I420 if needed
                            VideoFrame.Buffer frameBuffer = frame.getBuffer();
                            VideoFrame.I420Buffer i420Buffer;
                            
                            if (frameBuffer instanceof VideoFrame.I420Buffer) {
                                i420Buffer = (VideoFrame.I420Buffer) frameBuffer;
                            } else {
                                i420Buffer = frameBuffer.toI420();
                            }
                            
                            try {
                                // Render the frame using our I420 rendering method
                                // The frame reference is needed for rotation info
                                this.frame = frame;  // Store frame reference for rotation info
                                renderI420Frame(drawer, i420Buffer, frame.getRotatedWidth(), frame.getRotatedHeight());
                                
                                // Swap buffers to present the frame
                                eglBase.swapBuffers();
                            } finally {
                                // Release I420Buffer if we created a new one
                                if (frameBuffer != i420Buffer) {
                                    i420Buffer.release();
                                }
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error rendering frame: " + e.getMessage(), e);
                            initialize(); // Try to reinitialize on error
                        }
                    }
                } finally {
                    // Always release the frame when done
                    frame.release();
                }
            });
        }          private void renderI420Frame(GlRectDrawer drawer, VideoFrame.I420Buffer buffer, int width, int height) {
            try {
                // Create transformation matrix for proper orientation and scaling
                float[] samplingMatrix = new float[16];
                // Initialize identity matrix
                for (int i = 0; i < 16; i++) {
                    samplingMatrix[i] = 0.0f;
                }
                samplingMatrix[0] = 1.0f;
                samplingMatrix[5] = 1.0f;
                samplingMatrix[10] = 1.0f;
                samplingMatrix[15] = 1.0f;
                
                // Handle rotation based on frame rotation
                int rotation = (frame == null) ? 0 : frame.getRotation();
                if (rotation != 0) {
                    // Apply proper rotation transformation
                    if (rotation == 90) {
                        samplingMatrix[0] = 0.0f;
                        samplingMatrix[1] = 1.0f;
                        samplingMatrix[4] = -1.0f;
                        samplingMatrix[5] = 0.0f;
                        samplingMatrix[12] = 1.0f;
                    } else if (rotation == 180) {
                        samplingMatrix[0] = -1.0f;
                        samplingMatrix[5] = -1.0f;
                        samplingMatrix[12] = 1.0f;
                        samplingMatrix[13] = 1.0f;
                    } else if (rotation == 270) {
                        samplingMatrix[0] = 0.0f;
                        samplingMatrix[1] = -1.0f;
                        samplingMatrix[4] = 1.0f;
                        samplingMatrix[5] = 0.0f;
                        samplingMatrix[13] = 1.0f;
                    }
                }
                
                // Adjust matrix to maintain proper aspect ratio without black bars
                float aspectRatio = (float) buffer.getWidth() / buffer.getHeight();
                float targetAspectRatio = (float) width / height;
                
                if (aspectRatio > targetAspectRatio) {
                    // Buffer is wider than target: scale height to fit
                    float scale = targetAspectRatio / aspectRatio;
                    samplingMatrix[5] *= scale;
                    samplingMatrix[13] = (1 - scale) / 2;
                } else if (aspectRatio < targetAspectRatio) {
                    // Buffer is taller than target: scale width to fit
                    float scale = aspectRatio / targetAspectRatio;
                    samplingMatrix[0] *= scale;
                    samplingMatrix[12] = (1 - scale) / 2;
                }
                
                // Fill the surface completely - clear to black first
                GLES20.glViewport(0, 0, width, height);
                GLES20.glClearColor(0, 0, 0, 1);
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
                
                // Generate textures for YUV planes
                int[] yuvTextures = new int[3];
                GLES20.glGenTextures(3, yuvTextures, 0);
                
                // Upload YUV textures with proper parameters
                for (int i = 0; i < 3; i++) {
                    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, yuvTextures[i]);
                    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
                    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
                    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
                    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
                }
                
                // Upload Y-plane
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, yuvTextures[0]);
                GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_LUMINANCE, 
                    buffer.getWidth(), buffer.getHeight(), 0, 
                    GLES20.GL_LUMINANCE, GLES20.GL_UNSIGNED_BYTE, buffer.getDataY());
                    
                // Upload U-plane
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, yuvTextures[1]);
                GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_LUMINANCE, 
                    buffer.getWidth()/2, buffer.getHeight()/2, 0, 
                    GLES20.GL_LUMINANCE, GLES20.GL_UNSIGNED_BYTE, buffer.getDataU());
                
                // Upload V-plane
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, yuvTextures[2]);
                GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_LUMINANCE, 
                    buffer.getWidth()/2, buffer.getHeight()/2, 0, 
                    GLES20.GL_LUMINANCE, GLES20.GL_UNSIGNED_BYTE, buffer.getDataV());
        
        // Draw the frame with adjusted matrix to maintain aspect ratio and fill view
        drawer.drawYuv(yuvTextures, samplingMatrix, buffer.getWidth(), buffer.getHeight(), 0, 0, width, height);
        
        // Delete textures after drawing
        GLES20.glDeleteTextures(3, yuvTextures, 0);
        
    } catch (Exception e) {
        Log.e(TAG, "Error in renderI420Frame: " + e.getMessage(), e);
        GLES20.glClearColor(0, 0, 0, 1);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
    }
}
        
        public void release() {
            // Post release operation to the handler thread
            if (handler != null) {
                handler.post(() -> {
                    synchronized (syncObject) {
                        initialized = false;
                        releaseEglContext();
                    }
                    
                    // Quit the handler thread
                    if (thread != null) {
                        thread.quitSafely();
                        thread = null;
                    }
                    handler = null;
                });
            }
        }
    }
}


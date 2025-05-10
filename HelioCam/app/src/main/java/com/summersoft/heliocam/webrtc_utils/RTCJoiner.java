package com.summersoft.heliocam.webrtc_utils;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaRecorder;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraVideoCapturer;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

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
        
        // Check if we have necessary permissions
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) 
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions((Activity) context,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
            return false;
        }
        
        // Check if camera is running
        return videoCapturer != null && localVideoTrack != null;
    }
    
    /**
     * Start recording the camera feed
     */
    public void startRecording() {
        if (isRecording) {
            Log.w(TAG, "Recording is already in progress");
            return;
        }
        
        try {
            // Create directory for recordings if it doesn't exist
            File moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES);
            File recordingDir = new File(moviesDir, "HelioCam");
            if (!recordingDir.exists()) {
                if (!recordingDir.mkdirs()) {
                    Log.e(TAG, "Failed to create recording directory");
                    return;
                }
            }
            
            // Create file for recording with timestamp
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault());
            String fileName = "HelioCam_" + sdf.format(new Date()) + ".mp4";
            recordingPath = new File(recordingDir, fileName).getAbsolutePath();
            
            // Initialize MediaRecorder
            mediaRecorder = new MediaRecorder();
            mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            mediaRecorder.setVideoEncodingBitRate(2000000);
            mediaRecorder.setVideoFrameRate(30);
            mediaRecorder.setOutputFile(recordingPath);
            mediaRecorder.prepare();
            
            // Start recording
            mediaRecorder.start();
            isRecording = true;
            
            Log.d(TAG, "Recording started: " + recordingPath);
            Toast.makeText(context, "Recording started", Toast.LENGTH_SHORT).show();
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to start recording", e);
            Toast.makeText(context, "Failed to start recording: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            releaseMediaRecorder();
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
        
        try {
            if (mediaRecorder != null) {
                mediaRecorder.stop();
                releaseMediaRecorder();
                
                // Make the video visible in gallery
                if (recordingPath != null) {
                    MediaScannerConnection.scanFile(context,
                            new String[]{recordingPath}, null,
                            (path, uri) -> Log.d(TAG, "Recording saved: " + uri));
                    
                    Toast.makeText(context, "Recording saved to " + recordingPath, Toast.LENGTH_LONG).show();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error stopping recording", e);
        } finally {
            isRecording = false;
            recordingPath = null;
        }
    }
    
    /**
     * Release MediaRecorder resources
     */
    private void releaseMediaRecorder() {
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
        
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) 
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions((Activity) context,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
            return false;
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
            
            // Initialize MediaRecorder similar to regular recording
            mediaRecorder = new MediaRecorder();
            mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            mediaRecorder.setVideoEncodingBitRate(2000000);
            mediaRecorder.setVideoFrameRate(30);
            mediaRecorder.setMaxDuration(REPLAY_BUFFER_DURATION_MS); // 30 seconds max
            mediaRecorder.setOutputFile(recordingPath);
            mediaRecorder.prepare();
            
            // Start recording
            mediaRecorder.start();
            isReplayBufferRunning = true;
            
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
        
        try {
            if (mediaRecorder != null) {
                mediaRecorder.stop();
                releaseMediaRecorder();
                
                // Copy from temp file to permanent location
                File moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES);
                File recordingDir = new File(moviesDir, "HelioCam");
                if (!recordingDir.exists()) {
                    recordingDir.mkdirs();
                }
                
                // Create file with timestamp
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault());
                String fileName = "HelioCam_Buffer_" + sdf.format(new Date()) + ".mp4";
                File destFile = new File(recordingDir, fileName);
                
                // Copy buffer to permanent file
                FileInputStream fis = new FileInputStream(recordingPath);
                FileOutputStream fos = new FileOutputStream(destFile);
                byte[] buffer = new byte[1024];
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
                
                Toast.makeText(context, "Replay buffer saved to " + destFile.getAbsolutePath(), Toast.LENGTH_LONG).show();
                
                // Delete temp file
                new File(recordingPath).delete();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error stopping replay buffer", e);
        } finally {
            isReplayBufferRunning = false;
            recordingPath = null;
        }
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
            videoCapturer.startCapture(640, 480, 30);
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
        if (isRecording || isReplayBufferRunning) {
            releaseMediaRecorder();
            isRecording = false;
            isReplayBufferRunning = false;
        }
        
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
}


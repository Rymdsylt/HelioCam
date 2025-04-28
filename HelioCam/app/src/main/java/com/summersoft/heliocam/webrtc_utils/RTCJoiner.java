package com.summersoft.heliocam.webrtc_utils;

import android.content.Context;
import android.media.MediaRecorder;
import android.os.Handler;
import android.util.Log;
import android.view.View;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraVideoCapturer;
import org.webrtc.DataChannel;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpReceiver;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import com.summersoft.heliocam.detection.PersonDetection;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class RTCJoiner {
    private static final String TAG = "RTCJoiner";

    private final Context context;
    private final String sessionId;
    private final SurfaceViewRenderer renderer;
    private final DatabaseReference firebaseDatabase;
    private SfuManager sfuManager;
    private CameraVideoCapturer videoCapturer;
    private ValueEventListener hostSignalingListener;
    private boolean isAudioEnabled = true;
    private boolean isVideoEnabled = true;
    private PersonDetection personDetection;
    private boolean isUsingFrontCamera = true;

    // For recording functionality
    private MediaRecorder mediaRecorder;
    private boolean isRecording = false;
    public boolean replayBufferOn = false;
    private File currentVideoFile;

    // WebRTC components
    private EglBase rootEglBase;
    private PeerConnectionFactory peerConnectionFactory;
    private VideoSource videoSource;
    private VideoTrack localVideoTrack;
    private AudioSource audioSource;
    private AudioTrack localAudioTrack;
    private MediaStream localMediaStream;
    private PeerConnection peerConnection;
    private List<PeerConnection.IceServer> iceServers;

    public RTCJoiner(Context context, String sessionId, SurfaceViewRenderer renderer, DatabaseReference firebaseDatabase) {
        this.context = context;
        this.sessionId = sessionId;
        this.renderer = renderer;
        this.firebaseDatabase = firebaseDatabase;
        this.sfuManager = initializeSfuManager();

        setupRenderer();
        initializeWebRTCComponents();
    }

    private void initializeWebRTCComponents() {
        rootEglBase = EglBase.create();

        PeerConnectionFactory.InitializationOptions initializationOptions =
                PeerConnectionFactory.InitializationOptions.builder(context)
                        .createInitializationOptions();
        PeerConnectionFactory.initialize(initializationOptions);

        PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();
        peerConnectionFactory = PeerConnectionFactory.builder()
                .setOptions(options)
                .createPeerConnectionFactory();

        // Initialize ice servers
        iceServers = new ArrayList<>();
        iceServers.add(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer());
    }

    private SfuManager initializeSfuManager() {
        return new SfuManager(context, new SfuManager.SignalingInterface() {
            @Override
            public void sendOffer(SessionDescription offer, String sessionId, String role) {
                String hostEmail = getHostEmailFromSessionId(sessionId);
                firebaseDatabase.child("users")
                        .child(hostEmail)
                        .child("sessions")
                        .child(sessionId)
                        .child("JoinerOffer")
                        .setValue(offer.description);
            }

            @Override
            public void sendAnswer(SessionDescription answer, String sessionId, String role) {
                String hostEmail = getHostEmailFromSessionId(sessionId);
                firebaseDatabase.child("users")
                        .child(hostEmail)
                        .child("sessions")
                        .child(sessionId)
                        .child("JoinerAnswer")
                        .setValue(answer.description);
            }

            @Override
            public void sendIceCandidate(IceCandidate candidate, String sessionId, String role) {
                String hostEmail = getHostEmailFromSessionId(sessionId);

                IceCandidateData candidateData = new IceCandidateData(
                        candidate.sdp,
                        candidate.sdpMid,
                        candidate.sdpMLineIndex
                );

                firebaseDatabase.child("users")
                        .child(hostEmail)
                        .child("sessions")
                        .child(sessionId)
                        .child("JoinerCandidate")
                        .setValue(candidateData);
            }
        });
    }

    private void setupRenderer() {
        if (renderer != null) {
            renderer.setVisibility(View.VISIBLE);

            // Initialize the renderer with EglBase context
            if (rootEglBase != null) {
                renderer.init(rootEglBase.getEglBaseContext(), null);
                renderer.setZOrderMediaOverlay(true);
                renderer.setEnableHardwareScaler(true);
            }

            sfuManager.attachRemoteView(renderer);
        }
    }

    public void startCamera(Context context, boolean useFrontCamera) {
        this.isUsingFrontCamera = useFrontCamera;
        try {
            Camera2Enumerator enumerator = new Camera2Enumerator(context);
            String[] deviceNames = enumerator.getDeviceNames();

            String selectedCamera = null;
            for (String deviceName : deviceNames) {
                if (useFrontCamera && enumerator.isFrontFacing(deviceName)) {
                    selectedCamera = deviceName;
                    break;
                } else if (!useFrontCamera && enumerator.isBackFacing(deviceName)) {
                    selectedCamera = deviceName;
                    break;
                }
            }

            if (selectedCamera == null && deviceNames.length > 0) {
                selectedCamera = deviceNames[0];
            }

            if (selectedCamera != null) {
                videoCapturer = enumerator.createCapturer(selectedCamera, null);
                if (videoCapturer != null) {
                    sfuManager.setupLocalMediaStream(videoCapturer);

                    // Also setup local media stream for our class
                    setupLocalMediaStream();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error starting camera: " + e.getMessage());
        }
    }

    public void setupLocalMediaStream() {
        if (videoCapturer == null) return;

        SurfaceTextureHelper surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", rootEglBase.getEglBaseContext());
        videoSource = peerConnectionFactory.createVideoSource(false);
        videoCapturer.initialize(surfaceTextureHelper, context, videoSource.getCapturerObserver());

        MediaConstraints constraints = new MediaConstraints();
        videoCapturer.startCapture(1280, 720, 30);

        localVideoTrack = peerConnectionFactory.createVideoTrack("video", videoSource);
        localVideoTrack.setEnabled(isVideoEnabled);

        // Create audio source and track
        audioSource = peerConnectionFactory.createAudioSource(constraints);
        localAudioTrack = peerConnectionFactory.createAudioTrack("audio", audioSource);
        localAudioTrack.setEnabled(isAudioEnabled);

        // Create media stream
        localMediaStream = peerConnectionFactory.createLocalMediaStream("stream");
        localMediaStream.addTrack(localVideoTrack);
        localMediaStream.addTrack(localAudioTrack);

        // Attach the video to the renderer
        if (renderer != null) {
            localVideoTrack.addSink(renderer);
        }
    }

    public void setupLocalMediaStream(CameraVideoCapturer videoCapturer) {
        this.videoCapturer = videoCapturer;
        setupLocalMediaStream();
    }

    public void initializePeerConnection(String sessionId, String email) {
        // Create PeerConnection
        PeerConnection.RTCConfiguration rtcConfig = new PeerConnection.RTCConfiguration(iceServers);
        rtcConfig.enableDtlsSrtp = true;

        peerConnection = peerConnectionFactory.createPeerConnection(
                rtcConfig,
                new PeerConnection.Observer() {
                    @Override
                    public void onSignalingChange(PeerConnection.SignalingState signalingState) {
                        Log.d(TAG, "onSignalingChange: " + signalingState);
                    }

                    @Override
                    public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {
                        Log.d(TAG, "onIceConnectionChange: " + iceConnectionState);
                        if (iceConnectionState == PeerConnection.IceConnectionState.CONNECTED) {
                            // Update Firebase with connection status
                            String hostEmail = getHostEmailFromSessionId(sessionId);
                            firebaseDatabase.child("users")
                                    .child(hostEmail)
                                    .child("sessions")
                                    .child(sessionId)
                                    .child("connection_state")
                                    .setValue("connected");
                        }
                    }

                    @Override
                    public void onIceConnectionReceivingChange(boolean b) {
                        Log.d(TAG, "onIceConnectionReceivingChange: " + b);
                    }

                    @Override
                    public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {
                        Log.d(TAG, "onIceGatheringChange: " + iceGatheringState);
                    }

                    @Override
                    public void onIceCandidate(IceCandidate iceCandidate) {
                        Log.d(TAG, "onIceCandidate: " + iceCandidate);
                        sfuManager.handleLocalIceCandidate(iceCandidate);
                    }

                    @Override
                    public void onIceCandidatesRemoved(IceCandidate[] iceCandidates) {
                        Log.d(TAG, "onIceCandidatesRemoved: " + iceCandidates.length);
                    }

                    @Override
                    public void onAddStream(MediaStream mediaStream) {
                        Log.d(TAG, "onAddStream: " + mediaStream.getId());
                        if (mediaStream.videoTracks.size() > 0) {
                            VideoTrack remoteVideoTrack = mediaStream.videoTracks.get(0);
                            remoteVideoTrack.addSink(renderer);
                        }
                    }

                    @Override
                    public void onRemoveStream(MediaStream mediaStream) {
                        Log.d(TAG, "onRemoveStream: " + mediaStream.getId());
                    }

                    @Override
                    public void onDataChannel(DataChannel dataChannel) {
                        Log.d(TAG, "onDataChannel: " + dataChannel.label());
                    }

                    @Override
                    public void onRenegotiationNeeded() {
                        Log.d(TAG, "onRenegotiationNeeded");
                    }

                    @Override
                    public void onAddTrack(RtpReceiver rtpReceiver, MediaStream[] mediaStreams) {
                        Log.d(TAG, "onAddTrack");
                    }
                });

        // Add streams
        if (localMediaStream != null) {
            peerConnection.addStream(localMediaStream);
        }
    }

    public void createOffer(String sessionId, String email) {
        MediaConstraints constraints = new MediaConstraints();
        constraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        constraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"));

        peerConnection.createOffer(new SdpObserverImpl("createOffer") {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                Log.d(TAG, "Offer created successfully");
                peerConnection.setLocalDescription(new SdpObserverImpl("setLocalDescription") {
                    @Override
                    public void onSetSuccess() {
                        Log.d(TAG, "Local description set successfully");
                        // Send offer to Firebase
                        String hostEmail = email.replace(".", "_");
                        firebaseDatabase.child("users")
                                .child(hostEmail)
                                .child("sessions")
                                .child(sessionId)
                                .child("Offer")
                                .setValue(sessionDescription.description);
                    }

                    @Override
                    public void onSetFailure(String s) {
                        Log.e(TAG, "Failed to set local description: " + s);
                    }
                }, sessionDescription);
            }

            @Override
            public void onCreateFailure(String s) {
                Log.e(TAG, "Failed to create offer: " + s);
            }
        }, constraints);
    }

    public void initiateSessionJoin(String sessionId, String hostEmail) {
        if (sessionId == null || hostEmail == null) {
            Log.e(TAG, "Invalid session parameters");
            return;
        }

        String formattedHostEmail = hostEmail.replace(".", "_");
        
        try {
            // First set up listeners to prevent missing any signals
            listenForHostSignaling(sessionId, formattedHostEmail);
            
            // Ensure EGL context is initialized
            if (rootEglBase == null) {
                rootEglBase = EglBase.create();
            }
            
            // Initialize renderer with EGL context if needed
            if (renderer != null) {
                try {
                    renderer.init(rootEglBase.getEglBaseContext(), null);
                    renderer.setZOrderMediaOverlay(true);
                    renderer.setEnableHardwareScaler(true);
                } catch (IllegalStateException e) {
                    // Already initialized
                    Log.d(TAG, "Renderer already initialized: " + e.getMessage());
                }
            }
            
            // Make sure camera is started
            startCamera(context, isUsingFrontCamera);
            
            // Setup local media stream before joining
            if (videoCapturer != null) {
                sfuManager.setupLocalMediaStream(videoCapturer);
            } else {
                Log.w(TAG, "Video capturer not initialized, trying to join without camera");
                sfuManager.setupLocalMediaStream();
            }
            
            // Join the session
            sfuManager.joinSession(sessionId);

            // Request to join after setup is complete
            firebaseDatabase.child("users")
                    .child(formattedHostEmail)
                    .child("sessions")
                    .child(sessionId)
                    .child("want_join")
                    .setValue(1)
                    .addOnSuccessListener(aVoid -> Log.d(TAG, "Join request sent successfully"))
                    .addOnFailureListener(e -> Log.e(TAG, "Failed to send join request", e));
        } catch (Exception e) {
            Log.e(TAG, "Error initiating session join", e);
        }
    }

    private void listenForHostSignaling(String sessionId, String hostEmail) {
        if (hostSignalingListener != null) {
            return;
        }

        DatabaseReference hostRef = firebaseDatabase.child("users")
                .child(hostEmail)
                .child("sessions")
                .child(sessionId);

        hostSignalingListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                handleHostSignalingData(snapshot);
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Log.e(TAG, "Host signaling listener cancelled", error.toException());
            }
        };

        hostRef.addValueEventListener(hostSignalingListener);
    }

    private void handleHostSignalingData(DataSnapshot snapshot) {
        if (!snapshot.exists()) return;

        try {
            // Check if join was approved
            if (snapshot.hasChild("join_approved")) {
                int joinApproved = snapshot.child("join_approved").getValue(Integer.class);
                if (joinApproved == 1) {
                    Log.d(TAG, "Join request approved");
                }
            }

            // Handle ICE candidates
            DataSnapshot candidateSnapshot = snapshot.child("HostCandidate");
            if (candidateSnapshot.exists()) {
                String candidateStr = candidateSnapshot.child("candidate").getValue(String.class);
                String sdpMid = candidateSnapshot.child("sdpMid").getValue(String.class);
                Integer sdpMLineIndex = candidateSnapshot.child("sdpMLineIndex").getValue(Integer.class);
                
                if (candidateStr != null && sdpMid != null && sdpMLineIndex != null) {
                    IceCandidate candidate = new IceCandidate(
                            sdpMid,
                            sdpMLineIndex,
                            candidateStr
                    );
                    
                    if (sfuManager != null) {
                        sfuManager.handleRemoteIceCandidate(candidate);
                    } else {
                        Log.e(TAG, "SFU Manager is null when processing ICE candidate");
                    }
                }
            }

            // Handle SDP offer
            DataSnapshot offerSnapshot = snapshot.child("Offer");
            if (offerSnapshot.exists() && offerSnapshot.getValue() != null) {
                String sdp = offerSnapshot.getValue(String.class);
                if (sdp != null && sfuManager != null) {
                    SessionDescription offer = new SessionDescription(
                            SessionDescription.Type.OFFER,
                            sdp
                    );
                    sfuManager.handleRemoteOffer(offer);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error processing signaling data", e);
        }
    }

    public void switchCamera() {
        if (videoCapturer != null) {
            try {
                videoCapturer.switchCamera(null);
                isUsingFrontCamera = !isUsingFrontCamera;
            } catch (Exception e) {
                Log.e(TAG, "Error switching camera: " + e.getMessage());
            }
        }
    }

    public void toggleVideo() {
        isVideoEnabled = !isVideoEnabled;
        if (localVideoTrack != null) {
            localVideoTrack.setEnabled(isVideoEnabled);
        }
    }

    public void toggleAudio() {
        isAudioEnabled = !isAudioEnabled;
        if (localAudioTrack != null) {
            localAudioTrack.setEnabled(isAudioEnabled);
        }
    }

    public void muteMic() {
        if (localAudioTrack != null) {
            localAudioTrack.setEnabled(false);
            isAudioEnabled = false;
        }
    }

    public void unmuteMic() {
        if (localAudioTrack != null) {
            localAudioTrack.setEnabled(true);
            isAudioEnabled = true;
        }
    }

    public void setPersonDetection(PersonDetection personDetection) {
        this.personDetection = personDetection;
    }

    // Recording functionality
    public void startRecording(Context context) {
        if (isRecording) {
            Log.w(TAG, "Recording already in progress");
            return;
        }

        try {
            File videoDir = new File(context.getExternalFilesDir(null), "HelioCam");
            if (!videoDir.exists()) {
                videoDir.mkdirs();
            }

            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            currentVideoFile = new File(videoDir, "VID_" + timestamp + ".mp4");

            mediaRecorder = new MediaRecorder();
            mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            mediaRecorder.setVideoSize(1280, 720);
            mediaRecorder.setVideoFrameRate(30);
            mediaRecorder.setOutputFile(currentVideoFile.getAbsolutePath());
            mediaRecorder.prepare();
            mediaRecorder.start();

            isRecording = true;
            Log.d(TAG, "Recording started: " + currentVideoFile.getAbsolutePath());
        } catch (IOException e) {
            Log.e(TAG, "Error starting recording: " + e.getMessage());
            if (mediaRecorder != null) {
                mediaRecorder.release();
                mediaRecorder = null;
            }
        }
    }

    public void stopRecording() {
        if (!isRecording || mediaRecorder == null) {
            Log.w(TAG, "No recording in progress");
            return;
        }

        try {
            mediaRecorder.stop();
            mediaRecorder.release();
            mediaRecorder = null;
            isRecording = false;
            Log.d(TAG, "Recording stopped and saved: " + currentVideoFile.getAbsolutePath());
        } catch (Exception e) {
            Log.e(TAG, "Error stopping recording: " + e.getMessage());
        }
    }

    public void joinSession(String sessionId) {
        sfuManager.joinSession(sessionId);
    }

    public void handleRemoteOffer(SessionDescription offer) {
        sfuManager.handleRemoteOffer(offer);
    }

    public void handleRemoteIceCandidate(IceCandidate candidate) {
        sfuManager.handleRemoteIceCandidate(candidate);
    }

    public void dispose() {
        if (hostSignalingListener != null) {
            String hostEmail = getHostEmailFromSessionId(sessionId);
            firebaseDatabase.child("users")
                    .child(hostEmail)
                    .child("sessions")
                    .child(sessionId)
                    .removeEventListener(hostSignalingListener);
            hostSignalingListener = null;
        }

        if (videoCapturer != null) {
            try {
                videoCapturer.stopCapture();
            } catch (InterruptedException e) {
                Log.e(TAG, "Error stopping video capture", e);
            }
            videoCapturer.dispose();
            videoCapturer = null;
        }

        if (sfuManager != null) {
            sfuManager.release();
        }

        // Clean up WebRTC resources
        if (localVideoTrack != null) {
            localVideoTrack.dispose();
        }

        if (videoSource != null) {
            videoSource.dispose();
        }

        if (audioSource != null) {
            audioSource.dispose();
        }

        if (peerConnection != null) {
            peerConnection.close();
            peerConnection.dispose();
        }

        if (peerConnectionFactory != null) {
            peerConnectionFactory.dispose();
        }

        if (renderer != null) {
            renderer.release();
        }

        if (rootEglBase != null) {
            rootEglBase.release();
        }

        // Stop recording if in progress
        if (isRecording) {
            stopRecording();
        }
    }

    private String getHostEmailFromSessionId(String sessionId) {
        return sessionId.split("_")[0].replace(".", "_");
    }

    private static class IceCandidateData {
        public String candidate;
        public String sdpMid;
        public int sdpMLineIndex;

        public IceCandidateData() {} // Required for Firebase

        public IceCandidateData(String candidate, String sdpMid, int sdpMLineIndex) {
            this.candidate = candidate;
            this.sdpMid = sdpMid;
            this.sdpMLineIndex = sdpMLineIndex;
        }
    }

    // SdpObserver implementation for WebRTC
    private abstract class SdpObserverImpl implements org.webrtc.SdpObserver {
        private String tag;

        SdpObserverImpl(String tag) {
            this.tag = tag;
        }

        @Override
        public void onCreateSuccess(SessionDescription sessionDescription) {
            Log.d(TAG, tag + ": onCreateSuccess");
        }

        @Override
        public void onSetSuccess() {
            Log.d(TAG, tag + ": onSetSuccess");
        }

        @Override
        public void onCreateFailure(String s) {
            Log.e(TAG, tag + ": onCreateFailure: " + s);
        }

        @Override
        public void onSetFailure(String s) {
            Log.e(TAG, tag + ": onSetFailure: " + s);
        }
    }

    // Add to RTCJoiner.java to monitor connection state
    private void monitorConnectionState() {
        if (peerConnection != null) {
            new Handler().postDelayed(() -> {
                if (peerConnection != null) {
                    PeerConnection.IceConnectionState state = peerConnection.iceConnectionState();
                    Log.d(TAG, "Current ICE connection state: " + state);
                    
                    if (state == PeerConnection.IceConnectionState.FAILED || 
                        state == PeerConnection.IceConnectionState.DISCONNECTED) {
                        Log.w(TAG, "Connection appears to be failing, attempting recovery");
                        // Try to recreate the connection
                        initiateSessionJoin(sessionId, getHostEmailFromSessionId(sessionId));
                    } else if (state != PeerConnection.IceConnectionState.CONNECTED && 
                             state != PeerConnection.IceConnectionState.COMPLETED) {
                        // If not yet connected, keep monitoring
                        monitorConnectionState();
                    }
                }
            }, 5000); // Check every 5 seconds
        }
    }
}
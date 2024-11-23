package com.summersoft.heliocam.webrtc_utils;

import android.content.Context;
import android.util.Log;

import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraVideoCapturer;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SessionDescription;
import org.webrtc.SdpObserver;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.VideoCodecInfo;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoCodecInfo;

import android.content.Context;
import android.widget.Toast;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import java.util.Arrays;
import java.util.List;

public class WebRTCClient {
    private static final String TAG = "WebRTCClient";

    private PeerConnectionFactory peerConnectionFactory;
    private PeerConnection peerConnection;
    private VideoTrack videoTrack;
    private VideoSource videoSource;
    private EglBase rootEglBase;

    private CameraVideoCapturer videoCapturer;
    private SurfaceViewRenderer localView;

    private DatabaseReference firebaseDatabase;
    private String stunServer = "stun:stun.relay.metered.ca:80";
    private String turnServer = "turn:asia.relay.metered.ca:80";
    private String turnUsername = "08a10b202c595304495012c2";
    private String turnPassword = "JnsH2+jc2q3/uGon";


    public WebRTCClient(Context context, SurfaceViewRenderer localView, DatabaseReference firebaseDatabase) {
        this.localView = localView;
        this.firebaseDatabase = firebaseDatabase;

        // Initialize WebRTC
        PeerConnectionFactory.InitializationOptions options =
                PeerConnectionFactory.InitializationOptions.builder(context).createInitializationOptions();
        PeerConnectionFactory.initialize(options);

        rootEglBase = EglBase.create();
        localView.init(rootEglBase.getEglBaseContext(), null);
        localView.setMirror(true);

        // Enable H264 codec and VP8 codec for maximum device compatibility
        PeerConnectionFactory.Options peerOptions = new PeerConnectionFactory.Options();
        peerOptions.disableNetworkMonitor = true;  // Optional for better performance

        // Default video encoder factory that supports multiple codecs
        DefaultVideoEncoderFactory encoderFactory = new DefaultVideoEncoderFactory(rootEglBase.getEglBaseContext(), true, true);
        DefaultVideoDecoderFactory decoderFactory = new DefaultVideoDecoderFactory(rootEglBase.getEglBaseContext());

        // Create the PeerConnectionFactory with custom encoder and decoder
        peerConnectionFactory = PeerConnectionFactory.builder()
                .setVideoEncoderFactory(encoderFactory)
                .setVideoDecoderFactory(decoderFactory)
                .setOptions(peerOptions)
                .createPeerConnectionFactory();

        // Check which encoder is being used
        String codecUsed = "Unknown codec";
        for (VideoCodecInfo codecInfo : encoderFactory.getSupportedCodecs()) {
            if (codecInfo.name.equalsIgnoreCase("H264")) {
                codecUsed = "H.264";
                break;
            } else if (codecInfo.name.equalsIgnoreCase("VP8")) {
                codecUsed = "VP8";
            }
        }

        // Show a toast with the codec being used
        Toast.makeText(context, "Using codec: " + codecUsed, Toast.LENGTH_LONG).show();
    }


    // Initialize SurfaceTextureHelper once
    private SurfaceTextureHelper surfaceTextureHelper;

    public void startCamera(Context context, boolean useFrontCamera) {
        if (peerConnectionFactory == null) {
            Log.e(TAG, "PeerConnectionFactory is not initialized.");
            return;
        }

        Camera2Enumerator cameraEnumerator = new Camera2Enumerator(context);
        videoCapturer = createCameraCapturer(cameraEnumerator, useFrontCamera);

        if (videoCapturer == null) {
            Log.e(TAG, "Failed to initialize video capturer.");
            return;
        }

        // Create SurfaceTextureHelper once and reuse
        if (surfaceTextureHelper == null) {
            surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", rootEglBase.getEglBaseContext());
        }

        videoSource = peerConnectionFactory.createVideoSource(videoCapturer.isScreencast());
        videoCapturer.initialize(surfaceTextureHelper, context, videoSource.getCapturerObserver());
        videoTrack = peerConnectionFactory.createVideoTrack("videoTrack", videoSource);

        if (videoTrack == null) {
            Log.e(TAG, "Failed to create video track.");
            return;
        }

        videoTrack.addSink(localView);

        try {
            videoCapturer.startCapture(1280, 720, 30);  // 720p resolution, 30 fps for compatibility
        } catch (Exception e) {
            Log.e(TAG, "Failed to start video capturer.", e);
        }
    }


    public void initializePeerConnection(String sessionId, String email) {
        PeerConnection.RTCConfiguration rtcConfig = new PeerConnection.RTCConfiguration(getIceServers());
        rtcConfig.iceTransportsType = PeerConnection.IceTransportsType.ALL;

        peerConnection = peerConnectionFactory.createPeerConnection(rtcConfig, new PeerConnectionAdapter() {
            @Override
            public void onIceCandidate(IceCandidate candidate) {
                // Send ICE candidate to Firebase under the specific session
                String emailKey = email.replace(".", "_"); // Firebase does not support '@' or '.' in keys
                DatabaseReference iceCandidatesRef = firebaseDatabase.child("users")
                        .child(emailKey)
                        .child("sessions")
                        .child(sessionId)
                        .child("ice_candidates");

                // Check if the candidate already exists in Firebase, otherwise add it
                iceCandidatesRef.child("candidate_" + System.currentTimeMillis()).setValue(
                        new IceCandidateData(candidate.sdp, candidate.sdpMid, candidate.sdpMLineIndex)
                );
            }

        });

        MediaStream localStream = peerConnectionFactory.createLocalMediaStream("localStream");
        localStream.addTrack(videoTrack);
        peerConnection.addStream(localStream);
    }

    public void createOffer(String sessionId, String email) {
        peerConnection.createOffer(new SdpAdapter("CreateOffer") {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                // Set local description
                peerConnection.setLocalDescription(new SdpAdapter("SetLocalDescription"), sessionDescription);

                // Format the offer object
                String offer = sessionDescription.description;

                // Create a reference to the user's session in Firebase
                String emailKey = email.replace(".", "_"); // Firebase does not support '@' or '.' in keys

                // Update Firebase with the session offer
                firebaseDatabase.child("users").child(emailKey).child("sessions").child(sessionId)
                        .child("Offer").setValue(offer)
                        .addOnCompleteListener(task -> {
                            if (task.isSuccessful()) {
                                // Log if the offer was successfully sent to Firebase
                                Log.d(TAG, "Offer created for session " + sessionId + ": " + offer);
                            } else {
                                // Log failure if the operation was unsuccessful
                                Log.e(TAG, "Failed to send offer to Firebase for session: " + sessionId, task.getException());
                            }
                        });
            }

            @Override
            public void onCreateFailure(String error) {
                Log.e(TAG, "Failed to create offer: " + error);
            }
        }, new MediaConstraints());
    }


    public void onReceiveAnswer(SessionDescription answer) {
        if (peerConnection != null) {
            if (answer != null) {
                peerConnection.setRemoteDescription(new SdpAdapter("SetRemoteDescription"), answer);
                // Show toast when the answer is received
                Toast.makeText(localView.getContext(), "Answer received", Toast.LENGTH_SHORT).show();
            } else {
                Log.e(TAG, "Received invalid answer: null");
            }
        } else {
            Log.e(TAG, "PeerConnection is null, cannot set remote description.");
        }
    }


    public void startListeningForAnswer(String sessionId, String email) {
        listenForAnswer(sessionId, email);
    }


    public void listenForAnswer(String sessionId, String email) {
        String emailKey = email.replace(".", "_"); // Firebase does not support '@' or '.' in keys

        DatabaseReference answerRef = firebaseDatabase.child("users")
                .child(emailKey)
                .child("sessions")
                .child(sessionId)
                .child("Answer");

        // Listen for changes to the answer
        answerRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                String answer = dataSnapshot.getValue(String.class);

                if (answer != null) {
                    // Once the answer is received, create the SessionDescription and call onReceiveAnswer
                    SessionDescription sessionDescription = new SessionDescription(SessionDescription.Type.ANSWER, answer);
                    onReceiveAnswer(sessionDescription);
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                // Handle error in case the database operation is canceled or fails
                Log.e(TAG, "Failed to listen for answer: " + databaseError.getMessage());
            }
        });
    }





    private CameraVideoCapturer createCameraCapturer(Camera2Enumerator enumerator, boolean useFrontCamera) {
        for (String deviceName : enumerator.getDeviceNames()) {
            if (useFrontCamera && enumerator.isFrontFacing(deviceName)) {
                return enumerator.createCapturer(deviceName, null);
            } else if (!useFrontCamera && enumerator.isBackFacing(deviceName)) {
                return enumerator.createCapturer(deviceName, null);
            }
        }
        return null;
    }

    private List<PeerConnection.IceServer> getIceServers() {
        return Arrays.asList(
                PeerConnection.IceServer.builder(stunServer).createIceServer(),
                PeerConnection.IceServer.builder(turnServer)
                        .setUsername(turnUsername)
                        .setPassword(turnPassword)
                        .createIceServer()
        );
    }


    public void release() {
        if (videoCapturer != null) {
            try {
                videoCapturer.stopCapture();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            videoCapturer.dispose();
        }
        if (peerConnection != null) {
            peerConnection.dispose();
        }
        if (peerConnectionFactory != null) {
            peerConnectionFactory.dispose();
        }
        if (rootEglBase != null) {
            rootEglBase.release();
        }
    }

    // IceCandidate data class
    public static class IceCandidateData {
        public String candidate;
        public String sdpMid;
        public int sdpMLineIndex;

        // Default constructor for Firebase
        public IceCandidateData() {}

        public IceCandidateData(String candidate, String sdpMid, int sdpMLineIndex) {
            this.candidate = candidate;
            this.sdpMid = sdpMid;
            this.sdpMLineIndex = sdpMLineIndex;
        }
    }
}

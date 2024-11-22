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


import com.google.firebase.database.DatabaseReference;

import java.util.Arrays;
import java.util.List;
import com.google.firebase.database.DatabaseReference;

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
    private String turnServer = "turn:asia.relay.metered.ca:443";
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

        peerConnectionFactory = PeerConnectionFactory.builder()
                .setVideoEncoderFactory(new DefaultVideoEncoderFactory(rootEglBase.getEglBaseContext(), true, true))
                .setVideoDecoderFactory(new DefaultVideoDecoderFactory(rootEglBase.getEglBaseContext()))
                .createPeerConnectionFactory();
    }

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

        videoSource = peerConnectionFactory.createVideoSource(videoCapturer.isScreencast());
        videoCapturer.initialize(SurfaceTextureHelper.create("CaptureThread", rootEglBase.getEglBaseContext()),
                context, videoSource.getCapturerObserver());
        videoTrack = peerConnectionFactory.createVideoTrack("videoTrack", videoSource);

        if (videoTrack == null) {
            Log.e(TAG, "Failed to create video track.");
            return;
        }

        videoTrack.addSink(localView);

        try {
            videoCapturer.startCapture(1280, 720, 30);
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

                // Generate a candidate key, e.g., candidate_1, candidate_2, etc.
                iceCandidatesRef.child("candidate_" + System.currentTimeMillis()).setValue(
                        new IceCandidateData(candidate.sdp, candidate.sdpMid, candidate.sdpMLineIndex)
                );
            }
        });

        MediaStream localStream = peerConnectionFactory.createLocalMediaStream("localStream");
        localStream.addTrack(videoTrack);
        peerConnection.addStream(localStream);
    }

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
        }, new MediaConstraints());
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
}

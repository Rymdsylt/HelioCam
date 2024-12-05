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
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.VideoCodecInfo;

import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import java.util.Arrays;
import java.util.List;

import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;


public class RTCHost {
    private static final String TAG = "WebRTCClient";

    private PeerConnectionFactory peerConnectionFactory;
    private PeerConnection peerConnection;
    private VideoTrack videoTrack;
    private VideoSource videoSource;
    private EglBase rootEglBase;

    private AudioSource audioSource;
    private AudioTrack audioTrack;





    private CameraVideoCapturer videoCapturer;
    private SurfaceViewRenderer localView;

    private DatabaseReference firebaseDatabase;
    private String stunServer = "stun:stun.relay.metered.ca:80";
    private String turnServer = "turn:asia.relay.metered.ca:80?transport=tcp";
    private String turnUsername = "08a10b202c595304495012c2";
    private String turnPassword = "JnsH2+jc2q3/uGon";


    public RTCHost(Context context, SurfaceViewRenderer localView, DatabaseReference firebaseDatabase) {
        this.localView = localView;
        this.firebaseDatabase = firebaseDatabase;


        PeerConnectionFactory.InitializationOptions options =
                PeerConnectionFactory.InitializationOptions.builder(context).createInitializationOptions();
        PeerConnectionFactory.initialize(options);

        rootEglBase = EglBase.create();
        localView.init(rootEglBase.getEglBaseContext(), null);
        localView.setMirror(false);

        PeerConnectionFactory.Options peerOptions = new PeerConnectionFactory.Options();
        peerOptions.disableNetworkMonitor = true;

        DefaultVideoEncoderFactory encoderFactory = new DefaultVideoEncoderFactory(rootEglBase.getEglBaseContext(), true, true);
        DefaultVideoDecoderFactory decoderFactory = new DefaultVideoDecoderFactory(rootEglBase.getEglBaseContext());

        peerConnectionFactory = PeerConnectionFactory.builder()
                .setVideoEncoderFactory(encoderFactory)
                .setVideoDecoderFactory(decoderFactory)
                .setOptions(peerOptions)
                .createPeerConnectionFactory();

        String codecUsed = "Unknown codec";
        for (VideoCodecInfo codecInfo : encoderFactory.getSupportedCodecs()) {
            if (codecInfo.name.equalsIgnoreCase("H264")) {
                codecUsed = "H.264";
                break;
            } else if (codecInfo.name.equalsIgnoreCase("VP8")) {
                codecUsed = "VP8";
            }
        }

        Toast.makeText(context, "Using codec: " + codecUsed, Toast.LENGTH_LONG).show();
    }

    private SurfaceTextureHelper surfaceTextureHelper;

    public void startCamera(Context context, boolean useFrontCamera) {
        if (peerConnectionFactory == null) {
            Log.e(TAG, "PeerConnectionFactory is not initialized.");
            return;
        }

        if (videoTrack != null) {
            Toast.makeText(context, "Streaming has already started. Just adding observers.", Toast.LENGTH_SHORT).show();
            return;
        }

        Camera2Enumerator cameraEnumerator = new Camera2Enumerator(context);
        videoCapturer = createCameraCapturer(cameraEnumerator, useFrontCamera);

        if (videoCapturer == null) {
            Log.e(TAG, "Failed to initialize video capturer.");
            return;
        }

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
            videoCapturer.startCapture(1280, 720, 30);
        } catch (Exception e) {
            Log.e(TAG, "Failed to start video capturer.", e);
        }
    }

    public void initializePeerConnection(String sessionId, String email) {
        PeerConnection.RTCConfiguration rtcConfig = new PeerConnection.RTCConfiguration(getIceServers());
        rtcConfig.iceTransportsType = PeerConnection.IceTransportsType.ALL;
        listenForDisconnect(sessionId, email);
        peerConnection = peerConnectionFactory.createPeerConnection(rtcConfig, new PeerConnectionAdapter() {
            @Override
            public void onAddStream(MediaStream mediaStream) {
                super.onAddStream(mediaStream);

                AudioTrack remoteAudioTrack = mediaStream.audioTracks.get(0);

                if (remoteAudioTrack != null) {

                    remoteAudioTrack.setEnabled(true);

                }
            }
        });




        listenForIceCandidates(sessionId, email);

        MediaConstraints audioConstraints = new MediaConstraints();
        audioSource = peerConnectionFactory.createAudioSource(audioConstraints);
        audioTrack = peerConnectionFactory.createAudioTrack("audioTrack", audioSource);


        MediaStream localStream = peerConnectionFactory.createLocalMediaStream("localStream");
        localStream.addTrack(videoTrack);
        localStream.addTrack(audioTrack);
        peerConnection.addStream(localStream);
    }





    public boolean isAudioEnabled = true;

    public void toggleAudio() {
        if (audioTrack != null) {
            isAudioEnabled = !isAudioEnabled;
            audioTrack.setEnabled(isAudioEnabled);
            String message = isAudioEnabled ? "Audio enabled" : "Audio disabled";
            Log.d(TAG, message);
            Toast.makeText(localView.getContext(), message, Toast.LENGTH_SHORT).show();
        } else {
            Log.e(TAG, "AudioTrack is not initialized.");
        }
    }



    public void createOffer(String sessionId, String email) {
        peerConnection.createOffer(new SdpAdapter("CreateOffer") {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                peerConnection.setLocalDescription(new SdpAdapter("SetLocalDescription"), sessionDescription);


                String offer = sessionDescription.description;


                String emailKey = email.replace(".", "_");


                firebaseDatabase.child("users").child(emailKey).child("sessions").child(sessionId)
                        .child("Offer").setValue(offer)
                        .addOnCompleteListener(task -> {
                            if (task.isSuccessful()) {
                                Log.d(TAG, "Offer created for session " + sessionId + ": " + offer);
                            } else {
                                Log.e(TAG, "Failed to send offer to Firebase for session: " + sessionId, task.getException());
                            }
                        });


                startListeningForAnswer(sessionId, email);
            }

            @Override
            public void onCreateFailure(String error) {
                Log.e(TAG, "Failed to create offer: " + error);
            }
        }, new MediaConstraints());
    }

    public void listenForDisconnect(String sessionId, String email) {
        String emailKey = email.replace(".", "_");

        DatabaseReference disconnectRef = firebaseDatabase.child("users")
                .child(emailKey)
                .child("sessions")
                .child(sessionId)
                .child("disconnect");


        disconnectRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {

                if (dataSnapshot.exists() && dataSnapshot.getValue(Integer.class) == 1) {
                    Log.d(TAG, "Received disconnect signal. Generating new SDP offer...");

                    generateNewSdpOffer(sessionId, email);

                    disconnectRef.removeValue();
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.e(TAG, "Failed to listen for disconnect signal: " + databaseError.getMessage());
            }
        });
    }
    public void generateNewSdpOffer(String sessionId, String email) {

        createOffer(sessionId, email);
    }


    public void onReceiveAnswer(SessionDescription answer) {
        if (peerConnection != null) {
            if (answer != null) {

                peerConnection.setRemoteDescription(new SdpAdapter("SetRemoteDescription"), answer);


                Toast.makeText(localView.getContext(), "Answer received, streaming starts now!", Toast.LENGTH_SHORT).show();


                startStreaming();
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

    public void listenForIceCandidates(String sessionId, String email) {
        String emailKey = email.replace(".", "_");

        DatabaseReference iceCandidatesRef = firebaseDatabase.child("users")
                .child(emailKey)
                .child("sessions")
                .child(sessionId)
                .child("ice_candidates");


        iceCandidatesRef.addChildEventListener(new ChildEventListener() {
            @Override
            public void onChildAdded(DataSnapshot dataSnapshot, String previousChildName) {

                String candidate = dataSnapshot.child("candidate").getValue(String.class);
                String sdpMid = dataSnapshot.child("sdpMid").getValue(String.class);
                int sdpMLineIndex = dataSnapshot.child("sdpMLineIndex").getValue(Integer.class);

                if (candidate != null) {

                    IceCandidate iceCandidate = new IceCandidate(sdpMid, sdpMLineIndex, candidate);


                    peerConnection.addIceCandidate(iceCandidate);
                    Log.d(TAG, "Received and added ICE candidate from Firebase.");
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



    public void listenForAnswer(String sessionId, String email) {
        String emailKey = email.replace(".", "_");

        DatabaseReference answerRef = firebaseDatabase.child("users")
                .child(emailKey)
                .child("sessions")
                .child(sessionId)
                .child("Answer");


        answerRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                String answer = dataSnapshot.getValue(String.class);

                if (answer != null) {

                    SessionDescription sessionDescription = new SessionDescription(SessionDescription.Type.ANSWER, answer);
                    onReceiveAnswer(sessionDescription);


                    answerRef.removeEventListener(this);
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.e(TAG, "Failed to listen for answer: " + databaseError.getMessage());
            }
        });
    }

    private void startStreaming() {
        if (peerConnection != null && videoTrack != null) {
            MediaStream localStream = peerConnectionFactory.createLocalMediaStream("localStream");
            localStream.addTrack(videoTrack);

            peerConnection.addStream(localStream);
        }
    }

    private List<PeerConnection.IceServer> getIceServers() {
        String StunServer = "stun:stun.relay.metered.ca:80";
        String TurnServer = "turn:asia.relay.metered.ca:80?transport=tcp";

        PeerConnection.IceServer stunServer = PeerConnection.IceServer.builder(StunServer).createIceServer();
        PeerConnection.IceServer turnServer = PeerConnection.IceServer.builder(TurnServer)
                .setUsername(turnUsername)
                .setPassword(turnPassword)
                .createIceServer();
        return Arrays.asList(stunServer, turnServer);
    }

    private CameraVideoCapturer createCameraCapturer(Camera2Enumerator enumerator, boolean useFrontCamera) {
        CameraVideoCapturer capturer = null;
        for (String deviceName : enumerator.getDeviceNames()) {
            if (useFrontCamera && enumerator.isFrontFacing(deviceName)) {
                capturer = enumerator.createCapturer(deviceName, null);
                break;
            } else if (!useFrontCamera && enumerator.isBackFacing(deviceName)) {
                capturer = enumerator.createCapturer(deviceName, null);
                break;
            }
        }
        return capturer;
    }

    public void dispose(String sessionId, String email) {

        if (peerConnection != null) {
            peerConnection.close();
            peerConnection = null;
        }

        if (videoCapturer != null) {
            videoCapturer = null;
        }

        if (videoSource != null) {
            videoSource.dispose();
            videoSource = null;
        }

        if (videoTrack != null) {
            videoTrack.setEnabled(false);
            videoTrack = null;
        }

        if (localView != null) {
            localView.release();
            localView = null;
        }

        if (audioSource != null) {
            audioSource.dispose();
            audioSource = null;
        }

        if (audioTrack != null) {
            audioTrack.setEnabled(false);
            audioTrack = null;
        }


        String emailKey = email.replace(".", "_");
        DatabaseReference sessionRef = firebaseDatabase.child("users")
                .child(emailKey)
                .child("sessions")
                .child(sessionId);

        sessionRef.removeValue().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                Log.d(TAG, "Session " + sessionId + " deleted successfully.");
            } else {
                Log.e(TAG, "Failed to delete session " + sessionId);
            }
        });

        if (rootEglBase != null) {
            rootEglBase.release();
            rootEglBase = null;
        }

        if (peerConnectionFactory != null) {
            peerConnectionFactory.dispose();
            peerConnectionFactory = null;
        }

        Toast.makeText(localView.getContext(), "Session disposed of and resources released.", Toast.LENGTH_SHORT).show();
    }


    public void switchCamera() {
        if (videoCapturer != null && videoCapturer instanceof CameraVideoCapturer) {
            CameraVideoCapturer cameraCapturer = (CameraVideoCapturer) videoCapturer;
            try {
                cameraCapturer.switchCamera(new CameraVideoCapturer.CameraSwitchHandler() {
                    @Override
                    public void onCameraSwitchDone(boolean isFrontCamera) {
                        Log.d(TAG, "Camera switched successfully. Front Camera: " + isFrontCamera);
                    }

                    @Override
                    public void onCameraSwitchError(String error) {
                        Log.e(TAG, "Error switching camera: " + error);
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Failed to switch camera.", e);
            }
        } else {
            Log.e(TAG, "CameraVideoCapturer is not initialized or not a valid instance.");
        }
    }


    private boolean isVideoEnabled = true;

    public void toggleVideo() {
        if (videoTrack != null) {
            isVideoEnabled = !isVideoEnabled;
            videoTrack.setEnabled(isVideoEnabled);
            String message = isVideoEnabled ? "Video enabled" : "Video disabled";
            Log.d(TAG, message);
            Toast.makeText(localView.getContext(), message, Toast.LENGTH_SHORT).show();
        } else {
            Log.e(TAG, "VideoTrack is not initialized.");
        }
    }

    public void muteMic() {
        if (audioTrack != null) {
            audioTrack.setEnabled(false);
        }
    }

    public void unmuteMic() {
        if (audioTrack != null) {
            audioTrack.setEnabled(true);
        }
    }

}


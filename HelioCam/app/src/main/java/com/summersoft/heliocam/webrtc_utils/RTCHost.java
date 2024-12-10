package com.summersoft.heliocam.webrtc_utils;

import static com.summersoft.heliocam.status.IMEI_Util.TAG;

import android.Manifest;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
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
import org.webrtc.VideoFrame;
import org.webrtc.VideoSink;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.VideoCodecInfo;
import org.webrtc.VideoFileRenderer;
import org.webrtc.YuvConverter;
import org.webrtc.YuvHelper;

import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;
import com.summersoft.heliocam.webrtcfork.MultiSinkVideoRenderer;


import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;


public class RTCHost {
    private static final String TAG = "WebRTCClient";

    private PeerConnectionFactory peerConnectionFactory;
    private PeerConnection peerConnection;
    private VideoTrack videoTrack;
    private VideoTrack videoTrackRecord;
    private VideoSource videoSource;
    private EglBase rootEglBase;

    private AudioSource audioSource;
    private AudioTrack audioTrack;
    private VideoFileRenderer videoFileRenderer;
    private MultiSinkVideoRenderer multiSink;
    private static final int WRITE_EXTERNAL_STORAGE_REQUEST_CODE = 1;




    private CameraVideoCapturer videoCapturer;
    private SurfaceViewRenderer localView;

    private DatabaseReference firebaseDatabase;
    private String stunServer = "stun:stun.relay.metered.ca:80";
    private String turnServer = "turn:asia.relay.metered.ca:80?transport=tcp";
    private String turnUsername = "08a10b202c595304495012c2";
    private String turnPassword = "JnsH2+jc2q3/uGon";
    private Context context;

public RTCHost(){

}
    public RTCHost(Context context, SurfaceViewRenderer localView, DatabaseReference firebaseDatabase) {
        this.localView = localView;
        this.firebaseDatabase = firebaseDatabase;
        this.context = context;


        // Initialize WebRTC
        PeerConnectionFactory.InitializationOptions options =
                PeerConnectionFactory.InitializationOptions.builder(context).createInitializationOptions();
        PeerConnectionFactory.initialize(options);

        rootEglBase = EglBase.create();
        localView.init(rootEglBase.getEglBaseContext(), null);
        localView.setMirror(false);

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

    private SurfaceTextureHelper surfaceTextureHelper;

    public void startCamera(Context context, boolean useFrontCamera) {
        if (peerConnectionFactory == null) {
            Log.e(TAG, "PeerConnectionFactory is not initialized.");
            return;
        }

        if (videoTrack != null) {
            // Toast if the streaming has already started
            Toast.makeText(context, "Streaming has already started. Just adding observers.", Toast.LENGTH_SHORT).show();
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
            //width
            videoCapturer.startCapture(1280, 720, 15);  // 720p resolution, 30 fps for compatibility
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
                // Retrieve the remote audio track
                AudioTrack remoteAudioTrack = mediaStream.audioTracks.get(0); // Assuming only one audio track

                if (remoteAudioTrack != null) {
                    // Enable the remote audio track
                    remoteAudioTrack.setEnabled(true);

                    // You can further add the audio track to an audio renderer if necessary
                    // The audio playback should be handled by WebRTC internally
                }
            }
        });



        // Listen for incoming ICE candidates from Firebase in real-time
        listenForIceCandidates(sessionId, email);

        // Create audio source and track
        MediaConstraints audioConstraints = new MediaConstraints();
        audioSource = peerConnectionFactory.createAudioSource(audioConstraints);
        audioTrack = peerConnectionFactory.createAudioTrack("audioTrack", audioSource);

        // Create the local media stream and add both audio and video tracks
        MediaStream localStream = peerConnectionFactory.createLocalMediaStream("localStream");
        localStream.addTrack(videoTrack);  // Add video track
        localStream.addTrack(audioTrack);  // Add audio track
        peerConnection.addStream(localStream);
    }





    public boolean isAudioEnabled = true; // Track audio state

    public void toggleAudio() {
        if (audioTrack != null) {
            isAudioEnabled = !isAudioEnabled;
            audioTrack.setEnabled(isAudioEnabled); // Enable or disable audio track
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
                                Log.d(TAG, "Offer created for session " + sessionId + ": " + offer);
                            } else {
                                Log.e(TAG, "Failed to send offer to Firebase for session: " + sessionId, task.getException());
                            }
                        });

                // Start listening for the answer from the remote peer
                startListeningForAnswer(sessionId, email);
            }

            @Override
            public void onCreateFailure(String error) {
                Log.e(TAG, "Failed to create offer: " + error);
            }
        }, new MediaConstraints());
    }

    public void listenForDisconnect(String sessionId, String email) {
        String emailKey = email.replace(".", "_"); // Firebase key formatting

        DatabaseReference disconnectRef = firebaseDatabase.child("users")
                .child(emailKey)
                .child("sessions")
                .child(sessionId)
                .child("disconnect");

        // Listen for changes to the "disconnect" signal in real-time
        disconnectRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                // Check if "disconnect" value is 1
                if (dataSnapshot.exists() && dataSnapshot.getValue(Integer.class) == 1) {
                    Log.d(TAG, "Received disconnect signal. Generating new SDP offer...");

                    // Generate a fresh SDP offer
                    generateNewSdpOffer(sessionId, email);

                    // Remove the "disconnect" flag from Firebase to acknowledge the event
                    disconnectRef.removeValue();
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                // Handle error in case the database operation is canceled or fails
                Log.e(TAG, "Failed to listen for disconnect signal: " + databaseError.getMessage());
            }
        });
    }
    public void generateNewSdpOffer(String sessionId, String email) {
        // Call the existing createOffer method to generate the new SDP offer
        createOffer(sessionId, email);
    }


    public void onReceiveAnswer(SessionDescription answer) {//a
        if (peerConnection != null) {
            if (answer != null) {
                // Set the remote description once the answer is received
                peerConnection.setRemoteDescription(new SdpAdapter("SetRemoteDescription"), answer);

                // Show a toast when the answer is received
                Toast.makeText(localView.getContext(), "Answer received, streaming starts now!", Toast.LENGTH_SHORT).show();

                // Start streaming your local media to the remote peer
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
        String emailKey = email.replace(".", "_"); // Firebase key formatting

        DatabaseReference iceCandidatesRef = firebaseDatabase.child("users")
                .child(emailKey)
                .child("sessions")
                .child(sessionId)
                .child("ice_candidates");

        // Listen for changes to the ice_candidates in real-time
        iceCandidatesRef.addChildEventListener(new ChildEventListener() {
            @Override
            public void onChildAdded(DataSnapshot dataSnapshot, String previousChildName) {
                // Retrieve the ICE candidate data
                String candidate = dataSnapshot.child("candidate").getValue(String.class);
                String sdpMid = dataSnapshot.child("sdpMid").getValue(String.class);
                int sdpMLineIndex = dataSnapshot.child("sdpMLineIndex").getValue(Integer.class);

                if (candidate != null) {
                    // Create an IceCandidate object from the received data
                    IceCandidate iceCandidate = new IceCandidate(sdpMid, sdpMLineIndex, candidate);

                    // Add the candidate to the peer connection
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
        String emailKey = email.replace(".", "_"); // Firebase does not support '@' or '.' in keys

        DatabaseReference answerRef = firebaseDatabase.child("users")
                .child(emailKey)
                .child("sessions")
                .child(sessionId)
                .child("Answer");

        // Listen for changes to the answer in real-time
        answerRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                // Get the answer value
                String answer = dataSnapshot.getValue(String.class);

                if (answer != null) {
                    // Once the answer is received, create the SessionDescription and call onReceiveAnswer
                    SessionDescription sessionDescription = new SessionDescription(SessionDescription.Type.ANSWER, answer);
                    onReceiveAnswer(sessionDescription);

                    // Optionally, remove the listener after receiving the answer to prevent further updates
                    answerRef.removeEventListener(this);
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                // Handle error in case the database operation is canceled or fails
                Log.e(TAG, "Failed to listen for answer: " + databaseError.getMessage());
            }
        });
    }

    private void startStreaming() {
        // Assuming you already have the local video track and media stream set up
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
        // This method returns the camera capturer based on user preference for front or rear camera
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
        // Clean up the peer connection if it's not already null
        if (peerConnection != null) {
            peerConnection.close();
            peerConnection = null;
        }

        // Release the video capturer
        if (videoCapturer != null) {
            videoCapturer = null;
        }

        // Clean up video source and track
        if (videoSource != null) {
            videoSource.dispose();
            videoSource = null;
        }

        if (videoTrack != null) {
            videoTrack.setEnabled(false);
            videoTrack = null;
        }

        // Remove the surface view renderer
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

        // Finally, cleanup WebRTC resources
        if (rootEglBase != null) {
            rootEglBase.release();
            rootEglBase = null;
        }

        if (peerConnectionFactory != null) {
            peerConnectionFactory.dispose();
            peerConnectionFactory = null;
        }

        // Optionally, you can show a toast confirming that the session is disposed of
        Toast.makeText(localView.getContext(), "Session disposed of and resources released.", Toast.LENGTH_SHORT).show();
    }



    // Switch between front and rear cameras
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

    // Toggle video capture on or off
    private boolean isVideoEnabled = true; // Track video state

    public void toggleVideo() {
        if (videoTrack != null) {
            isVideoEnabled = !isVideoEnabled;
            videoTrack.setEnabled(isVideoEnabled); // Enable or disable video track
            String message = isVideoEnabled ? "Video enabled" : "Video disabled";
            Log.d(TAG, message);
            Toast.makeText(localView.getContext(), message, Toast.LENGTH_SHORT).show();
        } else {
            Log.e(TAG, "VideoTrack is not initialized.");
        }
    }

    public void muteMic() {
        if (audioTrack != null) {
            audioTrack.setEnabled(false);  // Disables the microphone
        }
    }

    public void unmuteMic() {
        if (audioTrack != null) {
            audioTrack.setEnabled(true);  // Enables the microphone
        }
    }


    public boolean isRecording = false;

    public void startRecording(Context context) {
        if (isRecording) {
            Log.d(TAG, "Recording is already in progress.");
            return;
        }

        try {
            Log.d(TAG, "Preparing file path for recording.");
            // Generate a unique file name using UUID
            String randomFileName = "Recording_" + UUID.randomUUID().toString() + ".yuv";
            File outputFile = new File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), randomFileName);
            String filePath = outputFile.getAbsolutePath();  // Get the absolute file path
            Log.d(TAG, "Recording file path: " + filePath);

            int width = 90;  // Adjust width as needed
            int height = 160;  // Adjust height as needed



            if (videoTrack != null) {
                videoFileRenderer = new VideoFileRenderer(filePath, width, height, rootEglBase.getEglBaseContext());

                Log.d(TAG, "VideoFileRenderer initialized.");
                Log.d(TAG, "Removing localView sink from videoTrack.");
                isRecording = true;
                Log.d(TAG, "Recording started. Saving raw YUV frames to file.");

            } else {
                Log.e(TAG, "Video track is not initialized, cannot start recording.");
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to start recording.", e);
        }
       videoTrack.addSink(localView);
        videoTrack.addSink(new VideoSink() {
            @Override
            public void onFrame(VideoFrame frame) {
                if (isRecording) {
                    // Forward the frame to VideoFileRenderer for recording
                    videoFileRenderer.onFrame(frame);
                }
            }
        });
    }

    public void stopRecording() {
        if (!isRecording) {
            Log.w(TAG, "Recording is not started.");
            return;
        }

        // Stop recording
        isRecording = false;
        Log.d(TAG, "Recording stopped.");

        // Release the VideoFileRenderer
        if (videoFileRenderer != null) {
            videoFileRenderer.release();
            videoFileRenderer = null;
        }
        videoTrack.removeSink(new VideoSink() {
            @Override
            public void onFrame(VideoFrame frame) {
                if (isRecording) {
                    // Forward the frame to VideoFileRenderer for recording
                    videoFileRenderer.onFrame(frame);
                }
            }
        });
      videoTrack.addSink(localView);
    }
    public boolean replayBufferOn =false;
    public void replayBuffer(Context context) {
        Log.d(TAG, "Replay Buffer Triggered.");
        if (isRecording) {
            Log.w(TAG, "Recording is already in progress.");
            return;
        }

        try {
            Log.d(TAG, "Preparing file path for recording.");
            // Generate a unique file name using UUID
            String randomFileName = "ReplayBuffer_" + UUID.randomUUID().toString() + ".yuv";
            File outputFile = new File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), randomFileName);
            String filePath = outputFile.getAbsolutePath();  // Get the absolute file path
            Log.d(TAG, "Recording file path: " + filePath);

            int width = 90;  // Adjust width as needed
            int height = 160;  // Adjust height as needed

            if (videoTrack != null) {
                videoFileRenderer = new VideoFileRenderer(filePath, width, height, rootEglBase.getEglBaseContext());

                Log.d(TAG, "VideoFileRenderer initialized.");
                Log.d(TAG, "Removing localView sink from videoTrack.");
                isRecording = true;
                Log.d(TAG, "Recording started. Saving raw YUV frames to file.");

            } else {
                Log.e(TAG, "Video track is not initialized, cannot start recording.");
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to start recording.", e);
        }

        videoTrack.addSink(localView);
        videoTrack.addSink(new VideoSink() {
            @Override
            public void onFrame(VideoFrame frame) {
                if (isRecording) {
                    // Forward the frame to VideoFileRenderer for recording
                    videoFileRenderer.onFrame(frame);
                }
            }
        });

        // Schedule stopRecord() to be called after 30 seconds
        new Handler(Looper.getMainLooper()).postDelayed(() -> stopRecording(), 30000);

    }





//Permission





}




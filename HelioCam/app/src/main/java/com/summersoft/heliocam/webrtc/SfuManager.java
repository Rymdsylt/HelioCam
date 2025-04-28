package com.summersoft.heliocam.webrtc;

import android.content.Context;
import android.util.Log;

import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraVideoCapturer;
import org.webrtc.DataChannel;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SfuManager implements PeerConnectionCallback {
    private static final String TAG = "SfuManager";
    private static final String LOCAL_TRACK_ID = "local_track";
    private static final String LOCAL_STREAM_ID = "local_stream";

    private final Context context;
    private final SignalingInterface signalingInterface;
    private EglBase eglBase;
    private PeerConnectionFactory peerConnectionFactory;
    private MediaStream localStream;
    private VideoTrack localVideoTrack;
    private AudioTrack localAudioTrack;
    private CameraVideoCapturer videoCapturer;
    private VideoSource videoSource;
    private AudioSource audioSource;
    public PeerConnection peerConnection;
    private String currentSessionId;
    private String currentRole;
    private SurfaceViewRenderer remoteRenderer;
    public Map<String, VideoTrack> remoteVideoTracks = new HashMap<>();
    private boolean isAudioEnabled = true;
    private boolean isVideoEnabled = true;
    private boolean isUsingFrontCamera = true;

    public interface SignalingInterface {
        void sendOffer(SessionDescription offer, String sessionId, String role);
        void sendAnswer(SessionDescription answer, String sessionId, String role);
        void sendIceCandidate(IceCandidate candidate, String sessionId, String role);
    }

    public interface RemoteTrackListener {
        void onRemoteVideoTrackReceived(VideoTrack videoTrack);
    }

    private List<RemoteTrackListener> remoteTrackListeners = new ArrayList<>();

    public SfuManager(Context context, SignalingInterface signalingInterface) {
        this.context = context;
        this.signalingInterface = signalingInterface;
        initWebRTC();
    }

    private void initWebRTC() {
        eglBase = EglBase.create();
        PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(context)
                        .createInitializationOptions());

        PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();
        DefaultVideoEncoderFactory encoderFactory = new DefaultVideoEncoderFactory(
                eglBase.getEglBaseContext(), true, true);
        DefaultVideoDecoderFactory decoderFactory = new DefaultVideoDecoderFactory(
                eglBase.getEglBaseContext());

        peerConnectionFactory = PeerConnectionFactory.builder()
                .setOptions(options)
                .setVideoEncoderFactory(encoderFactory)
                .setVideoDecoderFactory(decoderFactory)
                .createPeerConnectionFactory();
    }

    public void setupLocalMediaStream() {
        localStream = peerConnectionFactory.createLocalMediaStream(LOCAL_STREAM_ID);
        createAudioTrack();
        createVideoTrack();
    }

    public void setupLocalMediaStream(CameraVideoCapturer capturer) {
        this.videoCapturer = capturer;
        localStream = peerConnectionFactory.createLocalMediaStream(LOCAL_STREAM_ID);
        createAudioTrack();

        // Create video source with provided capturer
        if (videoCapturer != null) {
            SurfaceTextureHelper surfaceTextureHelper = SurfaceTextureHelper.create(
                    "CaptureThread", eglBase.getEglBaseContext());
            videoSource = peerConnectionFactory.createVideoSource(videoCapturer.isScreencast());
            videoCapturer.initialize(surfaceTextureHelper, context, videoSource.getCapturerObserver());
            try {
                videoCapturer.startCapture(1280, 720, 30);
            } catch (Exception e) {
                Log.e(TAG, "Failed to start capture", e);
            }

            localVideoTrack = peerConnectionFactory.createVideoTrack(LOCAL_TRACK_ID, videoSource);
            localVideoTrack.setEnabled(isVideoEnabled);
            localStream.addTrack(localVideoTrack);
        }
    }

    private void createAudioTrack() {
        MediaConstraints audioConstraints = new MediaConstraints();
        audioSource = peerConnectionFactory.createAudioSource(audioConstraints);
        localAudioTrack = peerConnectionFactory.createAudioTrack("audio_track", audioSource);
        localAudioTrack.setEnabled(isAudioEnabled);
        localStream.addTrack(localAudioTrack);
    }

    private void createVideoTrack() {
        videoSource = peerConnectionFactory.createVideoSource(false);
        localVideoTrack = peerConnectionFactory.createVideoTrack(LOCAL_TRACK_ID, videoSource);
        localVideoTrack.setEnabled(isVideoEnabled);
        localStream.addTrack(localVideoTrack);
    }

    public void startCamera() {
        if (videoCapturer == null) {
            videoCapturer = createCameraCapturer(new Camera2Enumerator(context));
        }

        if (videoCapturer != null && videoSource == null) {
            SurfaceTextureHelper surfaceTextureHelper = SurfaceTextureHelper.create(
                    "CaptureThread", eglBase.getEglBaseContext());
            videoSource = peerConnectionFactory.createVideoSource(videoCapturer.isScreencast());
            videoCapturer.initialize(surfaceTextureHelper, context, videoSource.getCapturerObserver());
            try {
                videoCapturer.startCapture(1280, 720, 30);
            } catch (Exception e) {
                Log.e(TAG, "Failed to start capture", e);
            }

            if (localVideoTrack == null) {
                localVideoTrack = peerConnectionFactory.createVideoTrack(LOCAL_TRACK_ID, videoSource);
                localVideoTrack.setEnabled(isVideoEnabled);
                if (localStream != null) {
                    localStream.addTrack(localVideoTrack);
                }
            }
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

    private CameraVideoCapturer createCameraCapturer(Camera2Enumerator enumerator) {
        for (String deviceName : enumerator.getDeviceNames()) {
            if (isUsingFrontCamera && enumerator.isFrontFacing(deviceName)) {
                return enumerator.createCapturer(deviceName, null);
            } else if (!isUsingFrontCamera && enumerator.isBackFacing(deviceName)) {
                return enumerator.createCapturer(deviceName, null);
            }
        }

        // If preferred camera not found, try any camera
        for (String deviceName : enumerator.getDeviceNames()) {
            if (enumerator.isFrontFacing(deviceName) || enumerator.isBackFacing(deviceName)) {
                return enumerator.createCapturer(deviceName, null);
            }
        }

        return null;
    }

    public void attachLocalVideoTrack(SurfaceViewRenderer renderer) {
        if (localVideoTrack != null && renderer != null) {
            renderer.init(eglBase.getEglBaseContext(), null);
            localVideoTrack.addSink(renderer);
        }
    }

    public void attachRemoteView(SurfaceViewRenderer renderer) {
        this.remoteRenderer = renderer;
        if (renderer != null) {
            renderer.init(eglBase.getEglBaseContext(), null);
            renderer.setZOrderMediaOverlay(true);
            renderer.setEnableHardwareScaler(true);

            // Attach any existing remote tracks
            for (VideoTrack track : remoteVideoTracks.values()) {
                track.addSink(renderer);
            }
        }
    }

    public void createHostSession(String sessionId) {
        this.currentSessionId = sessionId;
        this.currentRole = "host";
        createPeerConnection();
        createOffer();
    }

    public void joinSession(String sessionId) {
        this.currentSessionId = sessionId;
        this.currentRole = "joiner";
        createPeerConnection();
    }

    private void createPeerConnection() {
        List<PeerConnection.IceServer> iceServers = new ArrayList<>();
        iceServers.add(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer());

        PeerConnection.RTCConfiguration config = new PeerConnection.RTCConfiguration(iceServers);
        config.enableDtlsSrtp = true;

        MyPeerConnectionObserver observer = new MyPeerConnectionObserver(this, currentRole, currentSessionId);
        peerConnection = peerConnectionFactory.createPeerConnection(config, observer);

        if (localStream != null) {
            peerConnection.addStream(localStream);
        }
    }

    private void createOffer() {
        MediaConstraints constraints = new MediaConstraints();
        constraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"));
        constraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));

        peerConnection.createOffer(new MySdpObserver("createOffer") {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                Log.d(TAG, "Offer created successfully");
                peerConnection.setLocalDescription(new MySdpObserver("setLocalOffer") {
                    @Override
                    public void onSetSuccess() {
                        Log.d(TAG, "Local description set successfully");
                        signalingInterface.sendOffer(sessionDescription, currentSessionId, currentRole);
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

    public void handleRemoteOffer(SessionDescription offer) {
        peerConnection.setRemoteDescription(new MySdpObserver("setRemoteOffer") {
            @Override
            public void onSetSuccess() {
                Log.d(TAG, "Remote offer set successfully");
                createAnswer();
            }

            @Override
            public void onSetFailure(String s) {
                Log.e(TAG, "Failed to set remote offer: " + s);
            }
        }, offer);
    }

    private void createAnswer() {
        MediaConstraints constraints = new MediaConstraints();
        constraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"));
        constraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));

        peerConnection.createAnswer(new MySdpObserver("createAnswer") {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                Log.d(TAG, "Answer created successfully");
                peerConnection.setLocalDescription(new MySdpObserver("setLocalAnswer") {
                    @Override
                    public void onSetSuccess() {
                        Log.d(TAG, "Local answer set successfully");
                        signalingInterface.sendAnswer(sessionDescription, currentSessionId, currentRole);
                    }

                    @Override
                    public void onSetFailure(String s) {
                        Log.e(TAG, "Failed to set local answer: " + s);
                    }
                }, sessionDescription);
            }

            @Override
            public void onCreateFailure(String s) {
                Log.e(TAG, "Failed to create answer: " + s);
            }
        }, constraints);
    }

    public void handleRemoteAnswer(SessionDescription answer) {
        peerConnection.setRemoteDescription(new MySdpObserver("setRemoteAnswer") {
            @Override
            public void onSetSuccess() {
                Log.d(TAG, "Remote answer set successfully");
            }

            @Override
            public void onSetFailure(String s) {
                Log.e(TAG, "Failed to set remote answer: " + s);
            }
        }, answer);
    }

    public void handleLocalIceCandidate(IceCandidate candidate) {
        signalingInterface.sendIceCandidate(candidate, currentSessionId, currentRole);
    }

    public void handleRemoteIceCandidate(IceCandidate candidate) {
        if (peerConnection != null) {
            peerConnection.addIceCandidate(candidate);
        }
    }

    public void toggleVideo() {
        if (localVideoTrack != null) {
            isVideoEnabled = !isVideoEnabled;
            localVideoTrack.setEnabled(isVideoEnabled);
        }
    }

    public void toggleAudio() {
        if (localAudioTrack != null) {
            isAudioEnabled = !isAudioEnabled;
            localAudioTrack.setEnabled(isAudioEnabled);
        }
    }

    public void setVideoEnabled(boolean enabled) {
        isVideoEnabled = enabled;
        if (localVideoTrack != null) {
            localVideoTrack.setEnabled(enabled);
        }
    }

    public void setAudioEnabled(boolean enabled) {
        isAudioEnabled = enabled;
        if (localAudioTrack != null) {
            localAudioTrack.setEnabled(enabled);
        }
    }

    public void addRemoteTrackListener(RemoteTrackListener listener) {
        remoteTrackListeners.add(listener);
    }

    public void attachRemoteVideoTrackToRenderer(String trackId, SurfaceViewRenderer renderer) {
        VideoTrack track = remoteVideoTracks.get(trackId);
        if (track != null && renderer != null) {
            track.addSink(renderer);
        }
    }

    public EglBase getEglBase() {
        return eglBase;
    }

    public void release() {
        if (videoCapturer != null) {
            try {
                videoCapturer.stopCapture();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            videoCapturer.dispose();
            videoCapturer = null;
        }

        if (videoSource != null) {
            videoSource.dispose();
            videoSource = null;
        }

        if (audioSource != null) {
            audioSource.dispose();
            audioSource = null;
        }

        if (localVideoTrack != null) {
            localVideoTrack.dispose();
            localVideoTrack = null;
        }

        if (localAudioTrack != null) {
            localAudioTrack.dispose();
            localAudioTrack = null;
        }

        if (localStream != null) {
            localStream = null;
        }

        if (peerConnection != null) {
            peerConnection.close();
            peerConnection.dispose();
            peerConnection = null;
        }

        if (peerConnectionFactory != null) {
            peerConnectionFactory.dispose();
            peerConnectionFactory = null;
        }

        remoteVideoTracks.clear();

        if (eglBase != null) {
            eglBase.release();
            eglBase = null;
        }
    }

    // PeerConnectionCallback implementation
    @Override
    public void onIceCandidate(IceCandidate candidate, String sessionId, String role) {
        signalingInterface.sendIceCandidate(candidate, sessionId, role);
    }

    @Override
    public void onAddStream(MediaStream stream) {
        for (VideoTrack track : stream.videoTracks) {
            remoteVideoTracks.put(track.id(), track);
            if (remoteRenderer != null) {
                track.addSink(remoteRenderer);
            }
            for (RemoteTrackListener listener : remoteTrackListeners) {
                listener.onRemoteVideoTrackReceived(track);
            }
        }
    }

    @Override
    public void onRemoveStream(MediaStream stream) {
        for (VideoTrack track : stream.videoTracks) {
            remoteVideoTracks.remove(track.id());
        }
    }

    @Override
    public void onIceConnectionChange(PeerConnection.IceConnectionState state) {
        Log.d(TAG, "Ice connection state changed to: " + state);
    }

    @Override
    public void onSignalingChange(PeerConnection.SignalingState state) {
        Log.d(TAG, "Signaling state changed to: " + state);
    }

    @Override
    public void onIceGatheringChange(PeerConnection.IceGatheringState state) {
        Log.d(TAG, "Ice gathering state changed to: " + state);
    }

    @Override
    public void onDataChannel(DataChannel dataChannel) {
        Log.d(TAG, "Data channel received: " + dataChannel.label());
    }

    @Override
    public void onRenegotiationNeeded(String sessionId, String role) {
        Log.d(TAG, "Renegotiation needed for session: " + sessionId + ", role: " + role);
    }

    @Override
    public void onAddTrack(RtpReceiver rtpReceiver, MediaStream[] mediaStreams) {
        Log.d(TAG, "Track added from RtpReceiver");
    }
}

// These supporting classes need to be added
class MySdpObserver implements org.webrtc.SdpObserver {
    private String tag;

    MySdpObserver(String tag) {
        this.tag = tag;
    }

    @Override
    public void onCreateSuccess(SessionDescription sessionDescription) {
        Log.d("SfuManager", tag + ": onCreateSuccess");
    }

    @Override
    public void onSetSuccess() {
        Log.d("SfuManager", tag + ": onSetSuccess");
    }

    @Override
    public void onCreateFailure(String s) {
        Log.e("SfuManager", tag + ": onCreateFailure: " + s);
    }

    @Override
    public void onSetFailure(String s) {
        Log.e("SfuManager", tag + ": onSetFailure: " + s);
    }
}

interface PeerConnectionCallback {
    void onIceCandidate(IceCandidate candidate, String sessionId, String role);
    void onAddStream(MediaStream stream);
    void onRemoveStream(MediaStream stream);
    void onIceConnectionChange(PeerConnection.IceConnectionState state);
    void onSignalingChange(PeerConnection.SignalingState state);
    void onIceGatheringChange(PeerConnection.IceGatheringState state);
    void onDataChannel(DataChannel dataChannel);
    void onRenegotiationNeeded(String sessionId, String role);
    void onAddTrack(RtpReceiver rtpReceiver, MediaStream[] mediaStreams);
}

class MyPeerConnectionObserver implements PeerConnection.Observer {
    private final PeerConnectionCallback callback;
    private final String role;
    private final String sessionId;

    MyPeerConnectionObserver(PeerConnectionCallback callback, String role, String sessionId) {
        this.callback = callback;
        this.role = role;
        this.sessionId = sessionId;
    }

    @Override
    public void onSignalingChange(PeerConnection.SignalingState signalingState) {
        callback.onSignalingChange(signalingState);
    }

    @Override
    public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {
        callback.onIceConnectionChange(iceConnectionState);
    }

    @Override
    public void onIceConnectionReceivingChange(boolean b) {
        // Not used in the callback
    }

    @Override
    public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {
        callback.onIceGatheringChange(iceGatheringState);
    }

    @Override
    public void onIceCandidate(IceCandidate iceCandidate) {
        callback.onIceCandidate(iceCandidate, sessionId, role);
    }

    @Override
    public void onIceCandidatesRemoved(IceCandidate[] iceCandidates) {
        // Not used in the callback
    }

    @Override
    public void onAddStream(MediaStream mediaStream) {
        callback.onAddStream(mediaStream);
    }

    @Override
    public void onRemoveStream(MediaStream mediaStream) {
        callback.onRemoveStream(mediaStream);
    }

    @Override
    public void onDataChannel(DataChannel dataChannel) {
        callback.onDataChannel(dataChannel);
    }

    @Override
    public void onRenegotiationNeeded() {
        callback.onRenegotiationNeeded(sessionId, role);
    }

    @Override
    public void onAddTrack(RtpReceiver rtpReceiver, MediaStream[] mediaStreams) {
        callback.onAddTrack(rtpReceiver, mediaStreams);
    }
}
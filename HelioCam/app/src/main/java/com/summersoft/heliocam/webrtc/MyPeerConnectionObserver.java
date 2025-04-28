package com.summersoft.heliocam.webrtc;

import android.util.Log;

import com.summersoft.heliocam.webrtc.PeerConnectionCallback;

import org.webrtc.DataChannel;
import org.webrtc.IceCandidate;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.RtpReceiver;
import org.webrtc.VideoTrack;

public class MyPeerConnectionObserver implements PeerConnection.Observer {
    private final PeerConnectionCallback callback;
    private final String role;
    private final String sessionId;

    public MyPeerConnectionObserver(PeerConnectionCallback callback, String role, String sessionId) {
        this.callback = callback;
        this.role = role;
        this.sessionId = sessionId;
    }

    @Override
    public void onSignalingChange(PeerConnection.SignalingState signalingState) {
        // Forward to callback
        callback.onSignalingChange(signalingState);
    }

    @Override
    public void onIceConnectionChange(PeerConnection.IceConnectionState state) {
        // Enhanced ICE connection logging
        Log.d("MyPeerConnectionObserver", "onIceConnectionChange: " + state +
                " for session " + sessionId + ", role: " + role);

        callback.onIceConnectionChange(state);
    }

    @Override
    public void onIceConnectionReceivingChange(boolean receiving) {
        // Not used in callback interface, can be left empty
    }

    @Override
    public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {
        // Forward to callback
        callback.onIceGatheringChange(iceGatheringState);
    }

    @Override
    public void onIceCandidate(IceCandidate iceCandidate) {
        // Forward to callback with additional parameters
        callback.onIceCandidate(iceCandidate, sessionId, role);
    }

    @Override
    public void onIceCandidatesRemoved(IceCandidate[] iceCandidates) {
        // Not used in callback interface, can be left empty
    }

    @Override
    public void onAddStream(MediaStream mediaStream) {
        // Enhanced logging for stream reception
        int videoTracks = mediaStream.videoTracks.size();
        int audioTracks = mediaStream.audioTracks.size();

        Log.d("MyPeerConnectionObserver", "onAddStream: Stream ID " + mediaStream.getId() +
                " with " + videoTracks + " video tracks and " +
                audioTracks + " audio tracks, role: " + role);

        callback.onAddStream(mediaStream);
    }

    @Override
    public void onRemoveStream(MediaStream mediaStream) {
        // Forward to callback
        callback.onRemoveStream(mediaStream);
    }

    @Override
    public void onDataChannel(DataChannel dataChannel) {
        // Forward to callback
        callback.onDataChannel(dataChannel);
    }

    @Override
    public void onRenegotiationNeeded() {
        // Forward to callback with additional parameters
        callback.onRenegotiationNeeded(sessionId, role);
    }

    @Override
    public void onAddTrack(RtpReceiver rtpReceiver, MediaStream[] mediaStreams) {
        // Add more detailed logging
        if (rtpReceiver == null) {
            Log.e("MyPeerConnectionObserver", "onAddTrack: Received null rtpReceiver");
            return;
        }

        if (rtpReceiver.track() == null) {
            Log.e("MyPeerConnectionObserver", "onAddTrack: Received null track");
            return;
        }

        String trackType = rtpReceiver.track().kind();
        String trackId = rtpReceiver.track().id();
        int streamCount = mediaStreams != null ? mediaStreams.length : 0;

        Log.d("MyPeerConnectionObserver", "onAddTrack: " + trackType +
                " track received with ID " + trackId +
                ", attached to " + streamCount + " streams, role: " + role);

        callback.onAddTrack(rtpReceiver, mediaStreams);
    }
}
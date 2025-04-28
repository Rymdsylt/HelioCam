package com.summersoft.heliocam.webrtc;

import org.webrtc.DataChannel;
import org.webrtc.IceCandidate;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.RtpReceiver;

public interface PeerConnectionCallback {
    void onSignalingChange(PeerConnection.SignalingState signalingState);
    void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState);
    void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState);
    void onIceCandidate(IceCandidate iceCandidate, String sessionId, String role);
    void onAddStream(MediaStream mediaStream);
    void onRemoveStream(MediaStream mediaStream);
    void onDataChannel(DataChannel dataChannel);
    void onRenegotiationNeeded(String sessionId, String role);
    void onAddTrack(RtpReceiver rtpReceiver, MediaStream[] mediaStreams);
}
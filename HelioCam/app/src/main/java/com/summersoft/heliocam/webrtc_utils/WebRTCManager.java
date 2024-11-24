package com.summersoft.heliocam.webrtc_utils;

import android.content.Context;
import android.util.Log;

import org.webrtc.DataChannel;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpReceiver;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoTrack;
import org.webrtc.PeerConnection.IceServer;
import org.webrtc.PeerConnection.RTCConfiguration;

import java.util.ArrayList;
import java.util.List;

public class WebRTCManager {

    private static final String TAG = "WebRTCManager";
    private PeerConnectionFactory peerConnectionFactory;
    private PeerConnection peerConnection;
    private SurfaceViewRenderer surfaceViewRenderer;
    private String sdpOffer;

    // STUN and TURN server details
    private String stunServer = "stun:stun.relay.metered.ca:80";
    private String turnServer = "turn:asia.relay.metered.ca:80?transport=tcp";
    private String turnUsername = "08a10b202c595304495012c2";
    private String turnPassword = "JnsH2+jc2q3/uGon";

    public WebRTCManager(Context context, SurfaceViewRenderer surfaceViewRenderer, String sdpOffer) {
        this.surfaceViewRenderer = surfaceViewRenderer;
        this.sdpOffer = sdpOffer;
        initializeWebRTC(context);
    }

    public void setLocalDescription(SessionDescription description) {
        if (peerConnection != null) {
            peerConnection.setLocalDescription(new SdpAdapter(TAG), description);
            Log.d(TAG, "Local SDP Description set successfully.");
        } else {
            Log.e(TAG, "Peer connection is null. Cannot set local description.");
        }
    }

    private void initializeWebRTC(Context context) {
        // Initialize WebRTC
        PeerConnectionFactory.InitializationOptions options = PeerConnectionFactory.InitializationOptions.builder(context)
                .createInitializationOptions();
        PeerConnectionFactory.initialize(options);

        peerConnectionFactory = PeerConnectionFactory.builder().createPeerConnectionFactory();
    }

    public void setupConnection() {
        // Set up ICE servers
        List<IceServer> iceServers = new ArrayList<>();
        iceServers.add(new IceServer(stunServer)); // STUN server
        iceServers.add(new IceServer(turnServer, turnUsername, turnPassword)); // TURN server with credentials

        RTCConfiguration rtcConfig = new RTCConfiguration(iceServers);
        peerConnection = peerConnectionFactory.createPeerConnection(rtcConfig, new PeerConnectionAdapter() {
            @Override
            public void onAddTrack(RtpReceiver rtpReceiver, MediaStream[] mediaStreams) {
                super.onAddTrack(rtpReceiver, mediaStreams);

                if (mediaStreams.length > 0) {
                    MediaStream mediaStream = mediaStreams[0];
                    if (mediaStream.videoTracks.size() > 0) {
                        VideoTrack videoTrack = mediaStream.videoTracks.get(0);
                        videoTrack.addSink(surfaceViewRenderer); // Display the video track
                    }
                }
            }

            @Override
            public void onIceCandidate(IceCandidate iceCandidate) {
                super.onIceCandidate(iceCandidate);
                Log.d(TAG, "New ICE candidate: " + iceCandidate);
            }

            @Override
            public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {
                super.onIceConnectionChange(iceConnectionState);
                Log.d(TAG, "ICE Connection State changed: " + iceConnectionState);
            }

            @Override
            public void onAddStream(MediaStream mediaStream) {
                super.onAddStream(mediaStream);
                Log.d(TAG, "Stream added: " + mediaStream);
            }

            @Override
            public void onRemoveStream(MediaStream mediaStream) {
                super.onRemoveStream(mediaStream);
                Log.d(TAG, "Stream removed: " + mediaStream);
            }
        });

        try {
            // Validate the SDP Offer
            if (sdpOffer == null || sdpOffer.isEmpty()) {
                throw new IllegalArgumentException("SDP offer is null or empty");
            }

            // Set remote SDP offer using custom SdpAdapter for handling callbacks
            SessionDescription offerDescription = new SessionDescription(SessionDescription.Type.OFFER, sdpOffer);
            peerConnection.setRemoteDescription(new SdpAdapter(TAG), offerDescription);

            // Create an answer with constraints for receiving only video
            MediaConstraints mediaConstraints = new MediaConstraints();
            mediaConstraints.optional.add(new MediaConstraints.KeyValuePair("offerToReceiveVideo", "true"));
            mediaConstraints.optional.add(new MediaConstraints.KeyValuePair("offerToReceiveAudio", "true"));  // Make sure to offer audio if needed
            mediaConstraints.optional.add(new MediaConstraints.KeyValuePair("DtlsSrtpKeyAgreement", "true"));

            // Create an answer and set the local description
            peerConnection.createAnswer(new SdpAdapter(TAG), mediaConstraints);

        } catch (Exception e) {
            e.printStackTrace();
            Log.d(TAG, "Error parsing offer: " + e.getMessage());
        }
    }

    public void closeConnection() {
        if (peerConnection != null) {
            peerConnection.close();
        }
        if (peerConnectionFactory != null) {
            peerConnectionFactory.dispose();
        }
    }
}

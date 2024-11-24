package com.summersoft.heliocam.webrtc_utils;

import android.content.Context;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SessionDescription;

import java.util.List;

public class RTCJoin {

    private static final String TAG = "RTCJoin";

    private final PeerConnectionFactory peerConnectionFactory;
    private final PeerConnection peerConnection;
    private final Context context;

    public RTCJoin(Context context, PeerConnectionFactory peerConnectionFactory, List<PeerConnection.IceServer> iceServers) {
        this.context = context;
        this.peerConnectionFactory = peerConnectionFactory;

        // Initialize the PeerConnection
        PeerConnection.RTCConfiguration rtcConfig = new PeerConnection.RTCConfiguration(iceServers);
        rtcConfig.iceTransportsType = PeerConnection.IceTransportsType.ALL;

        this.peerConnection = peerConnectionFactory.createPeerConnection(
                rtcConfig,
                new PeerConnectionAdapter() {
                    @Override
                    public void onIceCandidate(IceCandidate candidate) {
                        super.onIceCandidate(candidate);
                        Log.d(TAG, "New ICE Candidate: " + candidate.sdp);
                        // Send the ICE candidate to the remote peer if required
                    }

                    @Override
                    public void onAddStream(org.webrtc.MediaStream stream) {
                        super.onAddStream(stream);
                        Log.d(TAG, "Media Stream added");
                        // Handle remote media stream
                    }
                }
        );

        if (this.peerConnection == null) {
            throw new IllegalStateException("Failed to create PeerConnection");
        }
    }

    /**
     * Joins a session with the given SDP Offer.
     *
     * @param sdpOffer SDP offer received from the remote peer.
     */
    public void joinSession(String sdpOffer) {
        // Set remote description with the received SDP Offer
        SessionDescription remoteSdp = new SessionDescription(SessionDescription.Type.OFFER, sdpOffer);
        peerConnection.setRemoteDescription(new SdpAdapter(TAG) {
            @Override
            public void onSetSuccess() {
                super.onSetSuccess();
                Log.d(TAG, "Remote SDP set successfully");

                // Create an SDP Answer
                createAnswer();
            }
        }, remoteSdp);
    }

    /**
     * Creates an SDP Answer to the remote peer.
     */
    public void createAnswer() {
        MediaConstraints mediaConstraints = new MediaConstraints();
        mediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        mediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"));

        peerConnection.createAnswer(new SdpAdapter(TAG) {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                super.onCreateSuccess(sessionDescription);
                Log.d(TAG, "SDP Answer created: " + sessionDescription.description);

                // Set the local description with the created SDP Answer
                peerConnection.setLocalDescription(new SdpAdapter(TAG), sessionDescription);

                // Send the SDP Answer to Firebase
                sendSdpAnswerToFirebase(sessionDescription);
            }
        }, mediaConstraints);
    }

    /**
     * Sends the SDP Answer to Firebase Firestore.
     *
     * @param sessionDescription The SDP answer to send.
     */
    public void sendSdpAnswerToFirebase(SessionDescription sessionDescription) {
        // Get the current user's email
        FirebaseAuth firebaseAuth = FirebaseAuth.getInstance();
        String userEmail = firebaseAuth.getCurrentUser().getEmail();
        if (userEmail == null) {
            Log.e(TAG, "User not logged in!");
            return;
        }

        // Sanitize the email (Firebase doesn't allow '.' or '@' in document IDs)
        String sanitizedEmail = userEmail.replace(".", "_");

        // Create Firestore instance
        FirebaseFirestore firestore = FirebaseFirestore.getInstance();

        // Send the SDP answer to Firebase
        firestore.collection("users")
                .document(sanitizedEmail) // Use sanitized email as the document ID
                .update("sdpAnswer", sessionDescription.description) // Field name 'sdpAnswer' can be changed
                .addOnSuccessListener(aVoid -> Log.d(TAG, "SDP Answer sent successfully"))
                .addOnFailureListener(e -> Log.e(TAG, "Error sending SDP answer to Firebase", e));
    }

    /**
     * Adds ICE candidates received from the signaling server.
     *
     * @param iceCandidate ICE candidate to add.
     */
    public void addIceCandidate(IceCandidate iceCandidate) {
        peerConnection.addIceCandidate(iceCandidate);
    }

    /**
     * Clean up resources when the session ends.
     */
    public void dispose() {
        if (peerConnection != null) {
            peerConnection.dispose();
        }
    }
}

package com.summersoft.heliocam.webrtc_utils;

import static android.content.Intent.getIntent;

import android.content.Context;
import android.util.Log;

import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SessionDescription;

import java.util.List;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;

public class RTCJoin {

    private static final String TAG = "RTCJoin";

    private final PeerConnectionFactory peerConnectionFactory;
    private final PeerConnection peerConnection;
    private final Context context;
    private final String sessionKey;  // Add sessionKey

    public RTCJoin(Context context, PeerConnectionFactory peerConnectionFactory, List<PeerConnection.IceServer> iceServers, String sessionKey) {
        this.context = context;
        this.peerConnectionFactory = peerConnectionFactory;
        this.sessionKey = sessionKey;  // Initialize sessionKey

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
    public void joinSession(final String sdpOffer) {
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

    private void sendSdpAnswerToFirebase(final SessionDescription answerSdp) {
        if (sessionKey != null) {  // Use the sessionKey passed into RTCJoin
            // Get the currently logged-in user's email
            String email = FirebaseAuth.getInstance().getCurrentUser().getEmail();
            if (email != null) {
                String formattedEmail = email.replace(".", "_"); // Format email for Firebase paths

                // Get a reference to the session in Firebase
                DatabaseReference sessionRef = FirebaseDatabase.getInstance().getReference("users")
                        .child(formattedEmail)
                        .child("sessions")
                        .child(sessionKey);

                // Prepare the SDP Answer to send to Firebase
                sessionRef.child("Answer").setValue(answerSdp.description)
                        .addOnCompleteListener(task -> {
                            if (task.isSuccessful()) {
                                Log.d(TAG, "SDP Answer sent to Firebase successfully");
                            } else {
                                Log.e(TAG, "Failed to send SDP Answer to Firebase", task.getException());
                            }
                        });
            }
        }
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

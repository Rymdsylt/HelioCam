package com.summersoft.heliocam.webrtc_utils;

import android.content.Context;
import android.util.Log;

import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceViewRenderer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import android.widget.Toast;


public class RTCJoin {

    private static final String TAG = "RTCJoin";
    private static boolean candidateSent = false; // Track if the candidate is already sent

    private final PeerConnectionFactory peerConnectionFactory;
    private final PeerConnection peerConnection;
    private final Context context;
    private final String sessionKey;  // Add sessionKey
    private final SurfaceViewRenderer feedView; // View to show the remote feed

    public RTCJoin(Context context, PeerConnectionFactory peerConnectionFactory, List<PeerConnection.IceServer> iceServers, String sessionKey, SurfaceViewRenderer feedView) {
        this.context = context;
        this.peerConnectionFactory = peerConnectionFactory;
        this.sessionKey = sessionKey;  // Initialize sessionKey
        this.feedView = feedView; // Initialize the feedView

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

                        // Ensure only one ICE candidate is sent
                        if (!candidateSent) {
                            candidateSent = true;

                            // Get the currently logged-in user's email
                            String email = FirebaseAuth.getInstance().getCurrentUser().getEmail();
                            if (email != null && sessionKey != null) {
                                // Format email for Firebase path (Firebase doesn't allow '.' or '@' in keys)
                                String formattedEmail = email.replace(".", "_");

                                // Get a reference to the session in Firebase
                                DatabaseReference sessionRef = FirebaseDatabase.getInstance().getReference("users")
                                        .child(formattedEmail)
                                        .child("sessions")
                                        .child(sessionKey)
                                        .child("ViewerCandidate");  // Store ICE candidates under "ViewerCandidate"

                                // Create a map to store the candidate data with simplified structure
                                Map<String, Object> candidateData = new HashMap<>();
                                candidateData.put("ViewerSdp", candidate.sdp);
                                candidateData.put("ViewerSdpMid", candidate.sdpMid);
                                candidateData.put("ViewerSdpMLineIndex", candidate.sdpMLineIndex);

                                // Set the candidate data directly under "ViewerCandidate"
                                sessionRef.setValue(candidateData)
                                        .addOnCompleteListener(task -> {
                                            if (task.isSuccessful()) {
                                                Log.d(TAG, "ICE candidate sent to Firebase successfully.");
                                            } else {
                                                Log.e(TAG, "Failed to send ICE candidate to Firebase", task.getException());
                                            }
                                        });
                            }
                        }
                    }

                    @Override
                    public void onAddStream(org.webrtc.MediaStream stream) {
                        super.onAddStream(stream);
                        Log.d(TAG, "Remote Media Stream added");

                        // Display a toast when the remote stream is received
                        if (stream.videoTracks.size() > 0) {
                            // Add the video track to the feed view
                            stream.videoTracks.get(0).addSink(feedView);  // feedView is the SurfaceViewRenderer in your UI

                            // Show a toast indicating that the stream has been received
                            Log.d(TAG, "Remote Media Stream Started");
                        }
                    }
                }
        );

        if (this.peerConnection == null) {
            throw new IllegalStateException("Failed to create PeerConnection");
        }

        // Load HostCandidate and HostSdp from Firebase when joining the session
        loadHostCandidateAndSdp(sessionKey);
    }

    private void loadHostCandidateAndSdp(String sessionKey) {
        // Assuming the current user is the viewer, and the host's SDP is stored under "HostCandidate"
        String email = FirebaseAuth.getInstance().getCurrentUser().getEmail();
        if (email != null) {
            String formattedEmail = email.replace(".", "_"); // Format email for Firebase paths

            DatabaseReference sessionRef = FirebaseDatabase.getInstance().getReference("users")
                    .child(formattedEmail)
                    .child("sessions")
                    .child(sessionKey);

            // Fetch the Offer and HostCandidate
            sessionRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot dataSnapshot) {
                    if (dataSnapshot.exists()) {
                        // Log and parse the Offer
                        String offer = dataSnapshot.child("Offer").getValue(String.class);
                        if (offer != null) {
                            Log.d(TAG, "Fetched Offer SDP: " + offer);

                            // Set the remote SDP using the fetched offer
                            SessionDescription remoteSdp = new SessionDescription(SessionDescription.Type.OFFER, offer);
                            peerConnection.setRemoteDescription(new SdpAdapter(TAG) {
                                @Override
                                public void onSetSuccess() {
                                    super.onSetSuccess();
                                    Log.d(TAG, "Remote SDP (Offer) set successfully");

                                    // Create an answer to the fetched offer
                                    createAnswer();
                                }
                            }, remoteSdp);
                        } else {
                            Log.e(TAG, "Offer SDP not found in Firebase");
                        }

                        // Fetch and log the HostCandidate
                        String sdp = dataSnapshot.child("HostCandidate").child("HostSdp").getValue(String.class);
                        String sdpMid = dataSnapshot.child("HostCandidate").child("HostSdpMid").getValue(String.class);
                        Integer sdpMLineIndex = dataSnapshot.child("HostCandidate").child("HostSdpMLineIndex").getValue(Integer.class);

                        if (sdp != null && sdpMid != null && sdpMLineIndex != null) {
                            Log.d(TAG, "Fetched Host Candidate SDP: " + sdp);
                            Log.d(TAG, "Fetched Host Candidate SDP Mid: " + sdpMid);
                            Log.d(TAG, "Fetched Host Candidate SDP MLine Index: " + sdpMLineIndex);

                            // Add the fetched HostCandidate to the PeerConnection
                            IceCandidate hostCandidate = new IceCandidate(sdpMid, sdpMLineIndex, sdp);
                            peerConnection.addIceCandidate(hostCandidate);
                            Log.d(TAG, "Host Candidate added successfully");
                        } else {
                            Log.e(TAG, "Incomplete HostCandidate information found in Firebase");
                        }
                    } else {
                        Log.e(TAG, "Session data not found in Firebase");
                    }
                }

                @Override
                public void onCancelled(DatabaseError databaseError) {
                    Log.e(TAG, "Failed to load data from Firebase: " + databaseError.getMessage());
                }
            });
        }
    }


    public void joinSession() {
        // Fetch the offer SDP from Firebase
        String email = FirebaseAuth.getInstance().getCurrentUser().getEmail();
        if (email != null) {
            String formattedEmail = email.replace(".", "_"); // Format email for Firebase paths

            DatabaseReference sessionRef = FirebaseDatabase.getInstance().getReference("users")
                    .child(formattedEmail)
                    .child("sessions")
                    .child(sessionKey)
                    .child("Offer"); // Retrieve the Offer SDP

            sessionRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot dataSnapshot) {
                    if (dataSnapshot.exists()) {
                        String sdpOffer = dataSnapshot.getValue(String.class); // Get the SDP offer
                        if (sdpOffer != null) {
                            // Set remote description with the SDP Offer from Firebase
                            SessionDescription remoteSdp = new SessionDescription(SessionDescription.Type.OFFER, sdpOffer);
                            peerConnection.setRemoteDescription(new SdpAdapter(TAG) {
                                @Override
                                public void onSetSuccess() {
                                    super.onSetSuccess();
                                    Log.d(TAG, "Remote SDP from Firebase set successfully");

                                    // Create an SDP Answer
                                    createAnswer();
                                }
                            }, remoteSdp);
                        } else {
                            Log.e(TAG, "SDP Offer in Firebase is null");
                        }
                    } else {
                        Log.e(TAG, "No SDP Offer found in Firebase");
                    }
                }

                @Override
                public void onCancelled(DatabaseError databaseError) {
                    Log.e(TAG, "Failed to load SDP Offer from Firebase: " + databaseError.getMessage());
                }
            });
        }
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





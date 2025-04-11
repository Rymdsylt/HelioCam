package com.summersoft.heliocam.webrtc_utils;

import android.content.Context;
import android.util.Log;

import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
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


public class RTCJoiner {

    private static final String TAG = "RTCJoin";
    private static boolean candidateSent = false; // Track if the candidate is already sent

    private final PeerConnectionFactory peerConnectionFactory;
    private final PeerConnection peerConnection;
    private final Context context;
    private final String sessionKey;
    private final SurfaceViewRenderer feedView; // View to show the remote feed

    private AudioTrack localAudioTrack;
    private AudioSource localAudioSource;

    public RTCJoiner(Context context, PeerConnectionFactory peerConnectionFactory, List<PeerConnection.IceServer> iceServers, String sessionKey, SurfaceViewRenderer feedView) {
        this.context = context;
        this.peerConnectionFactory = peerConnectionFactory;
        this.sessionKey = sessionKey;
        this.feedView = feedView;

        // Initialize the PeerConnection
        PeerConnection.RTCConfiguration rtcConfig = new PeerConnection.RTCConfiguration(iceServers);
        rtcConfig.iceTransportsType = PeerConnection.IceTransportsType.ALL;

        localAudioSource = peerConnectionFactory.createAudioSource(new MediaConstraints());

        // Create local audio track
        localAudioTrack = peerConnectionFactory.createAudioTrack("audioTrack", localAudioSource);

        // Create the PeerConnection with media constraints
        this.peerConnection = peerConnectionFactory.createPeerConnection(
                rtcConfig,
                new PeerConnectionAdapter() {
                    @Override
                    public void onIceCandidate(IceCandidate candidate) {
                        super.onIceCandidate(candidate);
                        Log.d(TAG, "New ICE Candidate: " + candidate.sdp);

                        // Send the ICE candidate to Firebase as soon as it is available
                        if (candidate != null) {
                            sendIceCandidateToFirebase(candidate);
                        }
                    }

                    @Override
                    public void onAddStream(org.webrtc.MediaStream stream) {
                        super.onAddStream(stream);
                        Log.d(TAG, "Remote Media Stream added");

                        // Handling Video Stream
                        if (stream.videoTracks.size() > 0) {
                            stream.videoTracks.get(0).addSink(feedView);  // Add video to SurfaceViewRenderer
                            Log.d(TAG, "Remote Video Stream Started");
                        }

                        // Handling Audio Stream
                        if (stream.audioTracks.size() > 0) {
                            // Add audio track to local playback if needed
                            Log.d(TAG, "Remote Audio Stream Started");
                        }
                    }
                }
        );

        if (this.peerConnection == null) {
            throw new IllegalStateException("Failed to create PeerConnection");
        }

        // Add local audio track to media stream and add stream to peer connection
        org.webrtc.MediaStream mediaStream = peerConnectionFactory.createLocalMediaStream("mediaStream");
        mediaStream.addTrack(localAudioTrack);
        peerConnection.addStream(mediaStream);

        // Load HostCandidate and HostSdp from Firebase when joining the session
        loadHostCandidateAndSdp(sessionKey);
    }

    private void sendIceCandidateToFirebase(IceCandidate candidate) {
        String email = FirebaseAuth.getInstance().getCurrentUser().getEmail();
        if (email != null && sessionKey != null) {
            String formattedEmail = email.replace(".", "_");  // Format email for Firebase paths

            DatabaseReference iceCandidatesRef = FirebaseDatabase.getInstance().getReference("users")
                    .child(formattedEmail)
                    .child("sessions")
                    .child(sessionKey)
                    .child("ice_candidates");

            // Get the next available candidate index
            iceCandidatesRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot dataSnapshot) {
                    int candidateCount = (int) dataSnapshot.getChildrenCount();
                    String candidateKey = "candidate_" + (candidateCount + 1);  // Generate unique key for new candidate

                    // Create a map to store the candidate data
                    Map<String, Object> candidateData = new HashMap<>();
                    candidateData.put("candidate", candidate.sdp);
                    candidateData.put("sdpMid", candidate.sdpMid);
                    candidateData.put("sdpMLineIndex", candidate.sdpMLineIndex);

                    // Set the candidate data in Firebase under the unique key
                    iceCandidatesRef.child(candidateKey).setValue(candidateData)
                            .addOnCompleteListener(task -> {
                                if (task.isSuccessful()) {
                                    Log.d(TAG, "ICE candidate sent to Firebase.");
                                } else {
                                    Log.e(TAG, "Failed to send ICE candidate to Firebase.", task.getException());
                                }
                            });
                }

                @Override
                public void onCancelled(DatabaseError databaseError) {
                    Log.e(TAG, "Failed to fetch ice candidates: " + databaseError.getMessage());
                }
            });
        }
    }

    private void loadHostCandidateAndSdp(String sessionKey) {
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

    public void addIceCandidate(IceCandidate iceCandidate) {
        if (peerConnection != null) {
            peerConnection.addIceCandidate(iceCandidate);
            Log.d(TAG, "Added ICE candidate: " + iceCandidate.sdp);
        } else {
            Log.e(TAG, "PeerConnection is null, can't add ICE candidate.");
        }
    }

    public void joinSession() {
        String email = FirebaseAuth.getInstance().getCurrentUser().getEmail();
        if (email != null) {
            String formattedEmail = email.replace(".", "_");

            DatabaseReference sessionRef = FirebaseDatabase.getInstance().getReference("users")
                    .child(formattedEmail)
                    .child("sessions")
                    .child(sessionKey);

            // Set the "someone_watching" flag to 1
            sessionRef.child("someone_watching").setValue(1)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            Log.d(TAG, "someone_watching flag set to 1.");
                        } else {
                            Log.e(TAG, "Failed to set someone_watching flag.", task.getException());
                        }
                    });

            sessionRef.child("Offer").addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot dataSnapshot) {
                    if (dataSnapshot.exists()) {
                        String sdpOffer = dataSnapshot.getValue(String.class);
                        if (sdpOffer != null) {
                            SessionDescription remoteSdp = new SessionDescription(SessionDescription.Type.OFFER, sdpOffer);
                            peerConnection.setRemoteDescription(new SdpAdapter(TAG) {
                                @Override
                                public void onSetSuccess() {
                                    super.onSetSuccess();
                                    Log.d(TAG, "Remote SDP from Firebase set successfully");

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

    private void sendSdpAnswerToFirebase(final SessionDescription answerSdp) {
        if (sessionKey != null) {
            String email = FirebaseAuth.getInstance().getCurrentUser().getEmail();
            if (email != null) {
                String formattedEmail = email.replace(".", "_");

                DatabaseReference sessionRef = FirebaseDatabase.getInstance().getReference("users")
                        .child(formattedEmail)
                        .child("sessions")
                        .child(sessionKey);

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

    public void dispose() {
        if (peerConnection != null) {
            String email = FirebaseAuth.getInstance().getCurrentUser().getEmail();
            if (email != null && sessionKey != null) {
                String formattedEmail = email.replace(".", "_");

                DatabaseReference sessionRef = FirebaseDatabase.getInstance().getReference("users")
                        .child(formattedEmail)
                        .child("sessions")
                        .child(sessionKey);

                // Remove "someone_watching" flag
                sessionRef.child("someone_watching").removeValue()
                        .addOnCompleteListener(task -> {
                            if (task.isSuccessful()) {
                                Log.d(TAG, "someone_watching flag removed successfully.");
                            } else {
                                Log.e(TAG, "Failed to remove someone_watching flag.", task.getException());
                            }
                        });

                // Send the "disconnect" status to Firebase before disposing the peer connection
                sessionRef.child("disconnect").setValue(1)
                        .addOnCompleteListener(task -> {
                            if (task.isSuccessful()) {
                                sessionRef.child("Answer").removeValue();
                                sessionRef.child("ice_candidates").removeValue();
                                Log.d(TAG, "Disconnect status, Answer, and ice_candidates removed from Firebase successfully.");
                            } else {
                                Log.e(TAG, "Failed to send disconnect status to Firebase", task.getException());
                            }
                        });
            }

            peerConnection.dispose();
            Log.d(TAG, "PeerConnection disposed.");
        }
    }



    public void muteMic() {
        if (localAudioTrack != null) {
            localAudioTrack.setEnabled(false);
        }
    }

    public void unmuteMic() {
        if (localAudioTrack != null) {
            localAudioTrack.setEnabled(true);
        }
    }



}


package com.summersoft.heliocam.webrtc;

import android.content.Context;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoSink;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SfuManager {
    private static final String TAG = "SfuManager";
    private static final int MAX_JOINERS = 4;
    
    // Firebase references
    private DatabaseReference mDatabase;
    private String userEmail;
    private String formattedEmail;
    
    // WebRTC components
    private Context context;
    private PeerConnectionFactory peerConnectionFactory;
    private List<PeerConnection.IceServer> iceServers =   Arrays.asList(
        PeerConnection.IceServer.builder("stun:stun.relay.metered.ca:80").createIceServer(),
        PeerConnection.IceServer.builder("turn:asia.relay.metered.ca:80")
            .setUsername("08a10b202c595304495012c2")
            .setPassword("JnsH2+jc2q3/uGon")
            .createIceServer(),
        PeerConnection.IceServer.builder("turn:asia.relay.metered.ca:80?transport=tcp")
            .setUsername("08a10b202c595304495012c2")
            .setPassword("JnsH2+jc2q3/uGon")
            .createIceServer(),
        PeerConnection.IceServer.builder("turn:asia.relay.metered.ca:443")
            .setUsername("08a10b202c595304495012c2")
            .setPassword("JnsH2+jc2q3/uGon")
            .createIceServer(),
        PeerConnection.IceServer.builder("turns:asia.relay.metered.ca:443?transport=tcp")
            .setUsername("08a10b202c595304495012c2")
            .setPassword("JnsH2+jc2q3/uGon")
            .createIceServer()
    );
    private EglBase eglBase;
    
    // Session management
    private String sessionId;
    private String sessionName;
    private boolean isHost;
    
    // Store all peer connections and renderers
    private Map<String, JoinerConnection> joinerConnections = new ConcurrentHashMap<>();
    
    // Main renderer for host view
    private SurfaceViewRenderer mainRenderer;
    
    // Audio components
    private AudioSource audioSource;
    private AudioTrack audioTrack;
    
    /**
     * Create SFU Manager
     * @param context Android context
     * @param peerConnectionFactory WebRTC peer connection factory
     * @param iceServers List of ICE servers
     * @param mainRenderer Main surface view renderer
     */
    public SfuManager(Context context, PeerConnectionFactory peerConnectionFactory, 
                     List<PeerConnection.IceServer> iceServers, SurfaceViewRenderer mainRenderer) {
        this.context = context;
        this.peerConnectionFactory = peerConnectionFactory;
        this.iceServers = iceServers;
        this.mainRenderer = mainRenderer;
        this.eglBase = EglBase.create();
        
        // Get Firebase reference
        mDatabase = FirebaseDatabase.getInstance().getReference();
        
        // Get and format user email
        String email = FirebaseAuth.getInstance().getCurrentUser().getEmail();
        if (email != null) {
            this.userEmail = email;
            this.formattedEmail = email.replace(".", "_");
        } else {
            Log.e(TAG, "User is not authenticated");
        }
        
        // Create audio source and track
        MediaConstraints audioConstraints = new MediaConstraints();
        audioConstraints.mandatory.add(new MediaConstraints.KeyValuePair("googEchoCancellation", "true"));
        audioConstraints.mandatory.add(new MediaConstraints.KeyValuePair("googAutoGainControl", "true"));
        audioConstraints.mandatory.add(new MediaConstraints.KeyValuePair("googHighpassFilter", "true"));
        audioConstraints.mandatory.add(new MediaConstraints.KeyValuePair("googNoiseSuppression", "true"));
        
        audioSource = peerConnectionFactory.createAudioSource(audioConstraints);
        audioTrack = peerConnectionFactory.createAudioTrack("audioTrack", audioSource);
    }
    
    /**
     * Create a new session as host (viewer)
     * @param sessionName Human-readable name for the session
     * @return Generated session ID
     */
    public String createHostSession(String sessionName) {
        this.sessionId = UUID.randomUUID().toString();
        this.sessionName = sessionName;
        this.isHost = true;
        
        Log.d(TAG, "Creating host session: " + sessionName + " with ID: " + sessionId);
        
        // Create session entry in Firebase
        Map<String, Object> sessionData = new HashMap<>();
        sessionData.put("session_name", sessionName);
        sessionData.put("host_email", userEmail);
        sessionData.put("created_at", System.currentTimeMillis());
        sessionData.put("active", true);
        sessionData.put("max_joiners", MAX_JOINERS);
        
        DatabaseReference sessionRef = mDatabase.child("users")
                .child(formattedEmail)
                .child("sessions")
                .child(sessionId);
                
        sessionRef.setValue(sessionData)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Session created successfully in Firebase");
                    // Start listening for join requests
                    listenForJoinRequests();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to create session in Firebase", e);
                });
        
        return sessionId;
    }
    
    /**
     * Listen for join requests from cameras
     */
    private void listenForJoinRequests() {
        if (!isHost) {
            Log.e(TAG, "Only host can listen for join requests");
            return;
        }
        
        DatabaseReference joinRequestsRef = mDatabase.child("users")
                .child(formattedEmail)
                .child("sessions")
                .child(sessionId)
                .child("join_requests");
                
        joinRequestsRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                for (DataSnapshot requestSnapshot : dataSnapshot.getChildren()) {
                    String joinerId = requestSnapshot.getKey();
                    String joinerEmail = requestSnapshot.child("email").getValue(String.class);
                    Long timestamp = requestSnapshot.child("timestamp").getValue(Long.class);
                    
                    // Check if not already connected and under max limit
                    if (!joinerConnections.containsKey(joinerId) && joinerConnections.size() < MAX_JOINERS) {
                        Log.d(TAG, "New join request from: " + joinerEmail + " with ID: " + joinerId);
                        
                        // Create a new connection for this joiner
                        JoinerConnection connection = createJoinerConnection(joinerId, joinerEmail);
                        joinerConnections.put(joinerId, connection);
                        
                        // Create and send offer to this joiner
                        createOfferForJoiner(connection);
                        
                        // Update the joiner count in Firebase
                        updateJoinerCount();
                    }
                }
            }
            
            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.e(TAG, "Failed to listen for join requests: " + databaseError.getMessage());
            }
        });
    }
    
    /**
     * Create a joiner connection object
     */
    private JoinerConnection createJoinerConnection(String joinerId, String joinerEmail) {
        PeerConnection.RTCConfiguration rtcConfig = new PeerConnection.RTCConfiguration(iceServers);
        rtcConfig.iceTransportsType = PeerConnection.IceTransportsType.ALL;
        
        // Create the peer connection
        PeerConnection peerConnection = peerConnectionFactory.createPeerConnection(
                rtcConfig,
                new PeerConnectionAdapter() {
                    @Override
                    public void onIceCandidate(IceCandidate candidate) {
                        super.onIceCandidate(candidate);
                        Log.d(TAG, "Host ICE candidate for joiner " + joinerId);
                        
                        // Send ICE candidate to joiner
                        sendIceCandidateToJoiner(joinerId, joinerEmail, candidate);
                    }
                    
                    @Override
                    public void onAddStream(MediaStream stream) {
                        super.onAddStream(stream);
                        Log.d(TAG, "Received stream from joiner: " + joinerId);
                        
                        // Handle the stream from joiner (this is the camera feed)
                        JoinerConnection connection = joinerConnections.get(joinerId);
                        if (connection != null) {
                            connection.setMediaStream(stream);
                            
                            // If video tracks exist, add them to the renderer
                            if (stream.videoTracks.size() > 0 && connection.getRenderer() != null) {
                                stream.videoTracks.get(0).addSink(connection.getRenderer());
                                Log.d(TAG, "Added video sink for joiner: " + joinerId);
                            } else {
                                // If this is the first joiner and we have a main renderer, use it
                                if (mainRenderer != null && joinerConnections.size() == 1) {
                                    assignRendererToJoiner(joinerId, mainRenderer);
                                }
                            }
                        }
                    }
                }
        );
        
        // Return the new connection
        return new JoinerConnection(joinerId, joinerEmail, peerConnection, null);
    }
    
    /**
     * Create and send an SDP offer to a joiner
     */
    private void createOfferForJoiner(JoinerConnection connection) {
        MediaConstraints constraints = new MediaConstraints();
        constraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        constraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"));
        
        connection.getPeerConnection().createOffer(new SdpAdapter(TAG) {
            @Override
            public void onCreateSuccess(SessionDescription sdp) {
                Log.d(TAG, "Created offer SDP for joiner: " + connection.getId());
                
                // Set local description
                connection.getPeerConnection().setLocalDescription(new SdpAdapter(TAG), sdp);
                
                // Send offer to Firebase for joiner
                String joinerFormattedEmail = connection.getEmail().replace(".", "_");
                
                DatabaseReference joinerSessionRef = mDatabase.child("users")
                        .child(joinerFormattedEmail)
                        .child("sessions")
                        .child(sessionId);
                        
                joinerSessionRef.child("Offer").setValue(sdp.description)
                        .addOnCompleteListener(task -> {
                            if (task.isSuccessful()) {
                                Log.d(TAG, "Offer sent to joiner: " + connection.getId());
                                
                                // Listen for answer from this joiner
                                listenForAnswerFromJoiner(connection);
                            } else {
                                Log.e(TAG, "Failed to send offer to joiner", task.getException());
                            }
                        });
            }
            
            @Override
            public void onCreateFailure(String error) {
                Log.e(TAG, "Failed to create offer: " + error);
            }
        }, constraints);
    }
    
    /**
     * Listen for SDP answer from a joiner
     */
    private void listenForAnswerFromJoiner(JoinerConnection connection) {
        String joinerFormattedEmail = connection.getEmail().replace(".", "_");
        
        DatabaseReference answerRef = mDatabase.child("users")
                .child(joinerFormattedEmail)
                .child("sessions")
                .child(sessionId)
                .child("Answer");
                
        answerRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    String sdpAnswer = dataSnapshot.getValue(String.class);
                    if (sdpAnswer != null) {
                        Log.d(TAG, "Received answer from joiner: " + connection.getId());
                        
                        // Set remote description with the answer
                        SessionDescription answer = new SessionDescription(SessionDescription.Type.ANSWER, sdpAnswer);
                        connection.getPeerConnection().setRemoteDescription(new SdpAdapter(TAG), answer);
                        
                        // Remove this listener after receiving the answer
                        answerRef.removeEventListener(this);
                        
                        // Listen for ICE candidates from this joiner
                        listenForIceCandidatesFromJoiner(connection);
                    }
                }
            }
            
            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.e(TAG, "Failed to listen for answer: " + databaseError.getMessage());
            }
        });
    }
    
    /**
     * Send ICE candidate to a joiner
     */
    private void sendIceCandidateToJoiner(String joinerId, String joinerEmail, IceCandidate candidate) {
        String joinerFormattedEmail = joinerEmail.replace(".", "_");
        
        DatabaseReference hostCandidatesRef = mDatabase.child("users")
                .child(joinerFormattedEmail)
                .child("sessions")
                .child(sessionId)
                .child("host_candidates");
                
        String candidateId = "candidate_" + UUID.randomUUID().toString().substring(0, 8);
        
        Map<String, Object> candidateData = new HashMap<>();
        candidateData.put("sdp", candidate.sdp);
        candidateData.put("sdpMid", candidate.sdpMid);
        candidateData.put("sdpMLineIndex", candidate.sdpMLineIndex);
        
        hostCandidatesRef.child(candidateId).setValue(candidateData)
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        Log.e(TAG, "Failed to send ICE candidate to joiner", task.getException());
                    }
                });
    }
    
    /**
     * Listen for ICE candidates from a joiner
     */
    private void listenForIceCandidatesFromJoiner(JoinerConnection connection) {
        String joinerFormattedEmail = connection.getEmail().replace(".", "_");
        
        DatabaseReference iceCandidatesRef = mDatabase.child("users")
                .child(joinerFormattedEmail)
                .child("sessions")
                .child(sessionId)
                .child("ice_candidates");
                
        iceCandidatesRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                for (DataSnapshot candidateSnapshot : dataSnapshot.getChildren()) {
                    if (candidateSnapshot.exists()) {
                        String sdp = candidateSnapshot.child("candidate").getValue(String.class);
                        String sdpMid = candidateSnapshot.child("sdpMid").getValue(String.class);
                        Integer sdpMLineIndex = candidateSnapshot.child("sdpMLineIndex").getValue(Integer.class);
                        
                        if (sdp != null && sdpMid != null && sdpMLineIndex != null) {
                            Log.d(TAG, "Received ICE candidate from joiner: " + connection.getId());
                            
                            IceCandidate candidate = new IceCandidate(sdpMid, sdpMLineIndex, sdp);
                            connection.getPeerConnection().addIceCandidate(candidate);
                        }
                    }
                }
            }
            
            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.e(TAG, "Failed to listen for ICE candidates: " + databaseError.getMessage());
            }
        });
    }
    
    /**
     * Update the current joiner count in Firebase
     */
    private void updateJoinerCount() {
        if (isHost) {
            mDatabase.child("users")
                    .child(formattedEmail)
                    .child("sessions")
                    .child(sessionId)
                    .child("current_joiners")
                    .setValue(joinerConnections.size())
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to update joiner count", e);
                    });
        }
    }
    
    /**
     * Assign a renderer to a specific joiner
     * @param joinerId The joiner ID
     * @param renderer The renderer to use for this joiner's video
     * @return true if successful, false otherwise
     */
    public boolean assignRendererToJoiner(String joinerId, SurfaceViewRenderer renderer) {
        JoinerConnection connection = joinerConnections.get(joinerId);
        if (connection != null) {
            connection.setRenderer(renderer);
            
            MediaStream stream = connection.getMediaStream();
            if (stream != null && stream.videoTracks.size() > 0) {
                stream.videoTracks.get(0).addSink(renderer);
                Log.d(TAG, "Assigned renderer to joiner: " + joinerId);
                return true;
            } else {
                Log.d(TAG, "No video stream available yet for joiner: " + joinerId);
            }
        } else {
            Log.e(TAG, "Joiner not found: " + joinerId);
        }
        return false;
    }
    
    /**
     * Join a session as a camera
     * @param hostEmail The email of the host
     * @param sessionId The session ID to join
     * @param localRenderer The renderer for local preview
     */
    public void joinAsCamera(String hostEmail, String sessionId, SurfaceViewRenderer localRenderer) {
        this.sessionId = sessionId;
        this.isHost = false;
        
        String hostFormattedEmail = hostEmail.replace(".", "_");
        
        // Generate a unique joiner ID
        String joinerId = UUID.randomUUID().toString();
        
        // Send join request to Firebase
        Map<String, Object> joinRequest = new HashMap<>();
        joinRequest.put("email", userEmail);
        joinRequest.put("timestamp", System.currentTimeMillis());
        
        DatabaseReference joinRequestRef = mDatabase.child("users")
                .child(hostFormattedEmail)
                .child("sessions")
                .child(sessionId)
                .child("join_requests")
                .child(joinerId);
                
        joinRequestRef.setValue(joinRequest)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Join request sent successfully");
                    
                    // Listen for offer from host
                    listenForOfferFromHost(hostEmail, sessionId, joinerId, localRenderer);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to send join request", e);
                });
    }
    
    /**
     * Listen for SDP offer from host
     */
    private void listenForOfferFromHost(String hostEmail, String sessionId, 
                                         String joinerId, SurfaceViewRenderer localRenderer) {
        DatabaseReference offerRef = mDatabase.child("users")
                .child(formattedEmail)
                .child("sessions")
                .child(sessionId)
                .child("Offer");
                
        offerRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    String offerSdp = dataSnapshot.getValue(String.class);
                    if (offerSdp != null) {
                        Log.d(TAG, "Received offer from host");
                        
                        // Process offer and create peer connection as joiner (camera)
                        createJoinerPeerConnection(hostEmail, sessionId, joinerId, localRenderer, offerSdp);
                        
                        // Remove listener after receiving offer
                        offerRef.removeEventListener(this);
                    }
                }
            }
            
            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.e(TAG, "Failed to listen for offer");
            }
        });
    }
    
    /**
     * Create peer connection as joiner (camera) and process the host's offer
     */
    private void createJoinerPeerConnection(String hostEmail, String sessionId, 
                                            String joinerId, SurfaceViewRenderer localRenderer, 
                                            String offerSdp) {
        // Create RTCConfiguration with ICE servers
        PeerConnection.RTCConfiguration rtcConfig = new PeerConnection.RTCConfiguration(iceServers);
        rtcConfig.iceTransportsType = PeerConnection.IceTransportsType.ALL;
        
        // Create joiner peer connection
        PeerConnection peerConnection = peerConnectionFactory.createPeerConnection(
                rtcConfig,
                new PeerConnectionAdapter() {
                    @Override
                    public void onIceCandidate(IceCandidate candidate) {
                        super.onIceCandidate(candidate);
                        
                        // Send ICE candidate to host
                        sendIceCandidateToHost(candidate);
                    }
                });
                
        if (peerConnection == null) {
            Log.e(TAG, "Failed to create peer connection");
            return;
        }
        
        // Create a media stream for the camera feed
        MediaStream mediaStream = peerConnectionFactory.createLocalMediaStream("camera_stream");
        
        // Add audio track to the stream if available
        if (audioTrack != null) {
            mediaStream.addTrack(audioTrack);
        }
        
        // Add the stream to the peer connection
        peerConnection.addStream(mediaStream);
        
        // Store connection information
        JoinerConnection connection = new JoinerConnection(
                joinerId, hostEmail, peerConnection, localRenderer);
        joinerConnections.put(joinerId, connection);
        
        // Set the remote description (offer from host)
        SessionDescription remoteSdp = new SessionDescription(SessionDescription.Type.OFFER, offerSdp);
        peerConnection.setRemoteDescription(new SdpAdapter(TAG) {
            @Override
            public void onSetSuccess() {
                super.onSetSuccess();
                
                // Create answer
                createAnswer(connection);
            }
        }, remoteSdp);
        
        // Listen for ICE candidates from host
        listenForIceCandidatesFromHost(hostEmail);
    }
    
    /**
     * Create and send answer as joiner
     */
    private void createAnswer(JoinerConnection connection) {
        MediaConstraints constraints = new MediaConstraints();
        constraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        constraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false")); // Camera doesn't need to receive video
        
        connection.getPeerConnection().createAnswer(new SdpAdapter(TAG) {
            @Override
            public void onCreateSuccess(SessionDescription sdp) {
                Log.d(TAG, "Created answer SDP");
                
                // Set local description
                connection.getPeerConnection().setLocalDescription(new SdpAdapter(TAG), sdp);
                
                // Send answer to Firebase for host
                sendAnswerToHost(sdp);
            }
            
            @Override
            public void onCreateFailure(String error) {
                Log.e(TAG, "Failed to create answer: " + error);
            }
        }, constraints);
    }
    
    /**
     * Send answer to host
     */
    private void sendAnswerToHost(SessionDescription sdp) {
        DatabaseReference answerRef = mDatabase.child("users")
                .child(formattedEmail)
                .child("sessions")
                .child(sessionId)
                .child("Answer");
                
        answerRef.setValue(sdp.description)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "Answer sent to host successfully");
                    } else {
                        Log.e(TAG, "Failed to send answer to host", task.getException());
                    }
                });
    }
    
    /**
     * Send ICE candidate to host
     */
    private void sendIceCandidateToHost(IceCandidate candidate) {
        DatabaseReference iceCandidatesRef = mDatabase.child("users")
                .child(formattedEmail)
                .child("sessions")
                .child(sessionId)
                .child("ice_candidates");
                
        String candidateId = "candidate_" + UUID.randomUUID().toString().substring(0, 8);
        
        Map<String, Object> candidateData = new HashMap<>();
        candidateData.put("candidate", candidate.sdp);
        candidateData.put("sdpMid", candidate.sdpMid);
        candidateData.put("sdpMLineIndex", candidate.sdpMLineIndex);
        
        iceCandidatesRef.child(candidateId).setValue(candidateData)
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to send ICE candidate to host", e);
                });
    }
    
    /**
     * Listen for ICE candidates from host
     */
    private void listenForIceCandidatesFromHost(String hostEmail) {
        String hostFormattedEmail = hostEmail.replace(".", "_");
        
        DatabaseReference hostCandidatesRef = mDatabase.child("users")
                .child(formattedEmail)
                .child("sessions")
                .child(sessionId)
                .child("host_candidates");
                
        hostCandidatesRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                for (DataSnapshot candidateSnapshot : dataSnapshot.getChildren()) {
                    if (candidateSnapshot.exists()) {
                        String sdp = candidateSnapshot.child("sdp").getValue(String.class);
                        String sdpMid = candidateSnapshot.child("sdpMid").getValue(String.class);
                        Integer sdpMLineIndex = candidateSnapshot.child("sdpMLineIndex").getValue(Integer.class);
                        
                        if (sdp != null && sdpMid != null && sdpMLineIndex != null) {
                            IceCandidate candidate = new IceCandidate(sdpMid, sdpMLineIndex, sdp);
                            
                            // Add to all connections if we're a joiner
                            if (!isHost && !joinerConnections.isEmpty()) {
                                for (JoinerConnection connection : joinerConnections.values()) {
                                    connection.getPeerConnection().addIceCandidate(candidate);
                                }
                            }
                        }
                    }
                }
            }
            
            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.e(TAG, "Failed to listen for host ICE candidates");
            }
        });
    }
    
    /**
     * Add a video track to the joiner's media stream
     * @param localVideoTrack Video track to add
     */
    public void addLocalVideoTrack(org.webrtc.VideoTrack localVideoTrack) {
        if (!isHost && !joinerConnections.isEmpty()) {
            for (JoinerConnection connection : joinerConnections.values()) {
                MediaStream mediaStream = peerConnectionFactory.createLocalMediaStream("camera_stream");
                mediaStream.addTrack(localVideoTrack);
                if (audioTrack != null) {
                    mediaStream.addTrack(audioTrack);
                }
                connection.getPeerConnection().addStream(mediaStream);
            }
        }
    }
    
    /**
     * Get list of active joiners
     * @return List of joiner IDs
     */
    public List<String> getActiveJoiners() {
        return new ArrayList<>(joinerConnections.keySet());
    }
    
    /**
     * Close the SFU manager and clean up resources
     */
    public void close() {
        // Clean up all peer connections
        for (JoinerConnection connection : joinerConnections.values()) {
            if (connection.getPeerConnection() != null) {
                connection.getPeerConnection().close();
            }
        }
        joinerConnections.clear();
        
        // Update session status in Firebase if we're a host
        if (isHost && sessionId != null) {
            mDatabase.child("users")
                    .child(formattedEmail)
                    .child("sessions")
                    .child(sessionId)
                    .child("active")
                    .setValue(false);
        }
        
        // If we're a joiner, remove our join request
        if (!isHost && sessionId != null) {
            // Remove from other Firebase references as needed
        }
    }
    
    /**
     * Inner class to represent a joiner connection
     */
    private static class JoinerConnection {
        private String id;
        private String email;
        private PeerConnection peerConnection;
        private SurfaceViewRenderer renderer;
        private MediaStream mediaStream;
        
        public JoinerConnection(String id, String email, PeerConnection peerConnection, SurfaceViewRenderer renderer) {
            this.id = id;
            this.email = email;
            this.peerConnection = peerConnection;
            this.renderer = renderer;
        }
        
        public String getId() { return id; }
        public String getEmail() { return email; }
        public PeerConnection getPeerConnection() { return peerConnection; }
        public SurfaceViewRenderer getRenderer() { return renderer; }
        public MediaStream getMediaStream() { return mediaStream; }
        
        public void setRenderer(SurfaceViewRenderer renderer) { this.renderer = renderer; }
        public void setMediaStream(MediaStream mediaStream) { this.mediaStream = mediaStream; }
    }
}
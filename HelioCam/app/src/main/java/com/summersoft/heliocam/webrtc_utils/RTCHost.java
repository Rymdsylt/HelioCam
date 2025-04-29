package com.summersoft.heliocam.webrtc_utils;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceViewRenderer;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class RTCHost {
    private static final String TAG = "RTCHost";
    private static final int MAX_JOINERS = 4;
    
    // WebRTC components
    private PeerConnectionFactory peerConnectionFactory;
    private EglBase rootEglBase;
    private AudioSource audioSource;
    private AudioTrack audioTrack;
    
    // Connection components
    private Map<String, PeerConnection> peerConnections = new ConcurrentHashMap<>();
    private SurfaceViewRenderer mainView; // Main view to show remote feed
    private Context context;
    
    // Firebase components
    private DatabaseReference firebaseDatabase;
    private String sessionId;
    private String userEmail;
    private String formattedEmail;
    
    // STUN/TURN servers
    private List<PeerConnection.IceServer> iceServers = Arrays.asList(
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

    // Multiple camera support
    private Map<String, SurfaceViewRenderer> joinerRenderers = new HashMap<>();

    public RTCHost(Context context, SurfaceViewRenderer mainView, DatabaseReference firebaseDatabase) {
        this.mainView = mainView;
        this.firebaseDatabase = firebaseDatabase;
        this.context = context;
        
        // Get user email for Firebase
        String email = FirebaseAuth.getInstance().getCurrentUser().getEmail();
        if (email != null) {
            this.userEmail = email;
            this.formattedEmail = email.replace(".", "_");
        } else {
            Log.e(TAG, "User not authenticated");
        }

        // Initialize WebRTC
        initializeWebRTC();
    }
    
    private void initializeWebRTC() {
        // Initialize PeerConnectionFactory
        PeerConnectionFactory.InitializationOptions options =
                PeerConnectionFactory.InitializationOptions.builder(context).createInitializationOptions();
        PeerConnectionFactory.initialize(options);

        rootEglBase = EglBase.create();
        mainView.init(rootEglBase.getEglBaseContext(), null);
        mainView.setMirror(false);

        PeerConnectionFactory.Options peerOptions = new PeerConnectionFactory.Options();
        peerOptions.disableNetworkMonitor = true;

        DefaultVideoEncoderFactory encoderFactory = new DefaultVideoEncoderFactory(
                rootEglBase.getEglBaseContext(), true, true);
        DefaultVideoDecoderFactory decoderFactory = new DefaultVideoDecoderFactory(
                rootEglBase.getEglBaseContext());

        peerConnectionFactory = PeerConnectionFactory.builder()
                .setVideoEncoderFactory(encoderFactory)
                .setVideoDecoderFactory(decoderFactory)
                .setOptions(peerOptions)
                .createPeerConnectionFactory();
                
        // Create audio source and track (for communication)
        MediaConstraints audioConstraints = new MediaConstraints();
        audioConstraints.mandatory.add(new MediaConstraints.KeyValuePair("googEchoCancellation", "true"));
        audioConstraints.mandatory.add(new MediaConstraints.KeyValuePair("googAutoGainControl", "true"));
        audioConstraints.mandatory.add(new MediaConstraints.KeyValuePair("googHighpassFilter", "true"));
        audioConstraints.mandatory.add(new MediaConstraints.KeyValuePair("googNoiseSuppression", "true"));
        
        audioSource = peerConnectionFactory.createAudioSource(audioConstraints);
        audioTrack = peerConnectionFactory.createAudioTrack("audioTrack", audioSource);
    }

    /**
     * Create a new session for hosting multiple cameras
     * @param sessionName Name of the session
     * @return Session ID created
     */
    public String createSession(String sessionName) {
        sessionId = UUID.randomUUID().toString();
        
        // Create session data
        Map<String, Object> sessionData = new HashMap<>();
        sessionData.put("session_name", sessionName);
        sessionData.put("host_email", userEmail);
        sessionData.put("created_at", System.currentTimeMillis());
        sessionData.put("active", true);
        sessionData.put("max_joiners", MAX_JOINERS);
        sessionData.put("current_joiners", 0);
        
        // Save session to Firebase
        DatabaseReference sessionRef = firebaseDatabase.child("users")
                .child(formattedEmail)
                .child("sessions")
                .child(sessionId);
                
        sessionRef.setValue(sessionData)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Session created successfully: " + sessionId);
                    listenForJoinRequests();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to create session", e);
                    Toast.makeText(context, "Failed to create session", Toast.LENGTH_SHORT).show();
                });
                
        return sessionId;
    }
    
    /**
     * Listen for join requests from cameras
     */
    private void listenForJoinRequests() {
        DatabaseReference joinRequestsRef = firebaseDatabase.child("users")
                .child(formattedEmail)
                .child("sessions")
                .child(sessionId)
                .child("join_requests");
                
        joinRequestsRef.addChildEventListener(new ChildEventListener() {
            @Override
            public void onChildAdded(DataSnapshot dataSnapshot, String previousChildName) {
                if (peerConnections.size() >= MAX_JOINERS) {
                    Log.d(TAG, "Maximum joiners reached, ignoring request");
                    return;
                }
                
                String joinerId = dataSnapshot.getKey();
                String joinerEmail = dataSnapshot.child("email").getValue(String.class);
                
                if (joinerId != null && joinerEmail != null && !peerConnections.containsKey(joinerId)) {
                    Log.d(TAG, "New join request: " + joinerEmail + " (ID: " + joinerId + ")");
                    createPeerConnectionForJoiner(joinerId, joinerEmail);
                }
            }

            @Override
            public void onChildChanged(DataSnapshot dataSnapshot, String previousChildName) {}

            @Override
            public void onChildRemoved(DataSnapshot dataSnapshot) {
                String joinerId = dataSnapshot.getKey();
                if (joinerId != null && peerConnections.containsKey(joinerId)) {
                    Log.d(TAG, "Joiner removed: " + joinerId);
                    removePeerConnection(joinerId);
                }
            }

            @Override
            public void onChildMoved(DataSnapshot dataSnapshot, String previousChildName) {}

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.e(TAG, "Failed to listen for join requests: " + databaseError.getMessage());
            }
        });
    }
    
    /**
     * Create peer connection for a new camera joiner
     */
    private void createPeerConnectionForJoiner(String joinerId, String joinerEmail) {
        // Create RTCConfiguration with ICE servers
        PeerConnection.RTCConfiguration rtcConfig = 
                new PeerConnection.RTCConfiguration(iceServers);
        rtcConfig.iceTransportsType = PeerConnection.IceTransportsType.ALL;
        
        // Create peer connection
        PeerConnection peerConnection = peerConnectionFactory.createPeerConnection(rtcConfig, 
                new PeerConnectionAdapter() {
                    @Override
                    public void onIceCandidate(IceCandidate candidate) {
                        super.onIceCandidate(candidate);
                        sendIceCandidateToJoiner(joinerId, joinerEmail, candidate);
                    }
                    
                    @Override
                    public void onAddStream(MediaStream stream) {
                        super.onAddStream(stream);
                        Log.d(TAG, "Remote stream added from joiner: " + joinerId);
                        
                        // Handle incoming video/audio from camera
                        new Handler(Looper.getMainLooper()).post(() -> {
                            if (stream.videoTracks.size() > 0) {
                                // If this is the first joiner, use the main view
                                if (peerConnections.size() == 1 || 
                                        joinerRenderers.get(joinerId) == mainView) {
                                    stream.videoTracks.get(0).addSink(mainView);
                                } else if (joinerRenderers.containsKey(joinerId)) {
                                    // Use assigned renderer for this joiner
                                    stream.videoTracks.get(0).addSink(joinerRenderers.get(joinerId));
                                }
                            }
                        });
                    }
                });
                
        if (peerConnection == null) {
            Log.e(TAG, "Failed to create peer connection for joiner: " + joinerId);
            return;
        }
        
        // Store the peer connection
        peerConnections.put(joinerId, peerConnection);
        
        // Add the audio track to the connection
        MediaStream localStream = peerConnectionFactory.createLocalMediaStream("hostAudio");
        localStream.addTrack(audioTrack);
        peerConnection.addStream(localStream);
        
        // Create offer for this joiner
        createOfferForJoiner(joinerId, joinerEmail, peerConnection);
        
        // Update joiner count
        updateJoinerCount();
    }
    
    /**
     * Create and send SDP offer to a joiner
     */
    private void createOfferForJoiner(String joinerId, String joinerEmail, PeerConnection peerConnection) {
        MediaConstraints constraints = new MediaConstraints();
        constraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        constraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"));
        
        peerConnection.createOffer(new SdpAdapter("CreateOffer") {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                peerConnection.setLocalDescription(new SdpAdapter("SetLocalDescription"), 
                        sessionDescription);
                
                // Send offer to joiner via Firebase
                String formattedJoinerEmail = joinerEmail.replace(".", "_");
                
                DatabaseReference joinerSessionRef = firebaseDatabase.child("users")
                        .child(formattedJoinerEmail)
                        .child("sessions")
                        .child(sessionId);
                        
                joinerSessionRef.child("Offer").setValue(sessionDescription.description)
                        .addOnCompleteListener(task -> {
                            if (task.isSuccessful()) {
                                Log.d(TAG, "Offer sent to joiner: " + joinerId);
                                listenForAnswer(joinerId, joinerEmail, peerConnection);
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
     * Listen for SDP answer from joiner
     */
    private void listenForAnswer(String joinerId, String joinerEmail, PeerConnection peerConnection) {
        String formattedJoinerEmail = joinerEmail.replace(".", "_");
        
        DatabaseReference answerRef = firebaseDatabase.child("users")
                .child(formattedJoinerEmail)
                .child("sessions")
                .child(sessionId)
                .child("Answer");
                
        answerRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    String sdpAnswer = dataSnapshot.getValue(String.class);
                    if (sdpAnswer != null) {
                        SessionDescription answer = new SessionDescription(
                                SessionDescription.Type.ANSWER, sdpAnswer);
                        
                        peerConnection.setRemoteDescription(new SdpAdapter("SetRemoteDescription"), 
                                answer);
                        
                        Log.d(TAG, "Answer received from joiner: " + joinerId);
                        
                        // Listen for ICE candidates from this joiner
                        listenForIceCandidates(joinerId, joinerEmail, peerConnection);
                        
                        // Remove listener after receiving answer
                        answerRef.removeEventListener(this);
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
     * Listen for ICE candidates from a joiner
     */
    private void listenForIceCandidates(String joinerId, String joinerEmail, 
                                        PeerConnection peerConnection) {
        String formattedJoinerEmail = joinerEmail.replace(".", "_");
        
        DatabaseReference iceCandidatesRef = firebaseDatabase.child("users")
                .child(formattedJoinerEmail)
                .child("sessions")
                .child(sessionId)
                .child("ice_candidates");
                
        iceCandidatesRef.addChildEventListener(new ChildEventListener() {
            @Override
            public void onChildAdded(DataSnapshot dataSnapshot, String previousChildName) {
                String sdp = dataSnapshot.child("candidate").getValue(String.class);
                String sdpMid = dataSnapshot.child("sdpMid").getValue(String.class);
                Integer sdpMLineIndex = dataSnapshot.child("sdpMLineIndex").getValue(Integer.class);
                
                if (sdp != null && sdpMid != null && sdpMLineIndex != null) {
                    IceCandidate iceCandidate = new IceCandidate(sdpMid, sdpMLineIndex, sdp);
                    peerConnection.addIceCandidate(iceCandidate);
                    Log.d(TAG, "Added ICE candidate from joiner: " + joinerId);
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
    
    /**
     * Send ICE candidate to joiner
     */
    private void sendIceCandidateToJoiner(String joinerId, String joinerEmail, 
                                         IceCandidate candidate) {
        String formattedJoinerEmail = joinerEmail.replace(".", "_");
        
        DatabaseReference hostCandidatesRef = firebaseDatabase.child("users")
                .child(formattedJoinerEmail)
                .child("sessions")
                .child(sessionId)
                .child("host_candidates");
                
        String candidateId = "host_candidate_" + UUID.randomUUID().toString().substring(0, 8);
        
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
     * Update joiner count in Firebase
     */
    private void updateJoinerCount() {
        firebaseDatabase.child("users")
                .child(formattedEmail)
                .child("sessions")
                .child(sessionId)
                .child("current_joiners")
                .setValue(peerConnections.size());
    }
    
    /**
     * Remove a peer connection (when a joiner disconnects)
     */
    private void removePeerConnection(String joinerId) {
        PeerConnection peerConnection = peerConnections.remove(joinerId);
        if (peerConnection != null) {
            peerConnection.close();
        }
        
        joinerRenderers.remove(joinerId);
        updateJoinerCount();
    }
    
    /**
     * Assign a renderer to a specific joiner
     * @param joinerId Joiner ID
     * @param renderer Surface view renderer to use
     */
    public void assignRendererToJoiner(String joinerId, SurfaceViewRenderer renderer) {
        if (renderer != null) {
            renderer.init(rootEglBase.getEglBaseContext(), null);
            renderer.setMirror(false);
            joinerRenderers.put(joinerId, renderer);
        }
    }

    /**
     * Toggle audio on/off
     */
    public void toggleAudio() {
        if (audioTrack != null) {
            boolean enabled = !audioTrack.enabled();
            audioTrack.setEnabled(enabled);
            String message = enabled ? "Audio enabled" : "Audio disabled";
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * Get list of active joiners
     * @return Array of joiner IDs
     */
    public String[] getActiveJoiners() {
        return peerConnections.keySet().toArray(new String[0]);
    }
    
    /**
     * Dispose of all resources
     */
    public void dispose() {
        // Close all peer connections
        for (PeerConnection peerConnection : peerConnections.values()) {
            peerConnection.close();
        }
        peerConnections.clear();
        
        // Release all renderers
        for (SurfaceViewRenderer renderer : joinerRenderers.values()) {
            renderer.release();
        }
        joinerRenderers.clear();
        
        // Release main view
        if (mainView != null) {
            mainView.release();
        }
        
        // Dispose of audio resources
        if (audioSource != null) {
            audioSource.dispose();
        }
        
        // Dispose of factory
        if (peerConnectionFactory != null) {
            peerConnectionFactory.dispose();
        }
        
        // Release EGL base
        if (rootEglBase != null) {
            rootEglBase.release();
        }
        
        // Delete session in Firebase
        if (sessionId != null) {
            firebaseDatabase.child("users")
                    .child(formattedEmail)
                    .child("sessions")
                    .child(sessionId)
                    .child("active")
                    .setValue(false);
        }
    }
}




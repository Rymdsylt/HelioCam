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
import org.webrtc.RtpTransceiver;
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
    private DatabaseReference mDatabase;
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
    private HashMap<Object, Object> assignedRenderers;

    // Add these fields to your RTCHost class
    private Map<String, String> joinerDeviceIds = new HashMap<>();
    private Map<String, String> joinerDeviceNames = new HashMap<>();

    // Undefined variables
    private Map<String, String> joinerEmails = new HashMap<>(); // Missing declaration
    private SurfaceViewRenderer[] videoRenderers; // Missing declaration
    private JoinRequestCallback joinRequestCallback; // Missing declaration
    private Map<String, Map<String, String>> joinerInfoMap = new HashMap<>(); // Missing declaration

    public RTCHost(Context context, SurfaceViewRenderer mainView, DatabaseReference mDatabase) {
        this.context = context;
        this.mainView = mainView;
        this.mDatabase = mDatabase;
        
        // Initialize user info
        FirebaseAuth mAuth = FirebaseAuth.getInstance();
        if (mAuth.getCurrentUser() != null) {
            userEmail = mAuth.getCurrentUser().getEmail();
            formattedEmail = userEmail.replace(".", "_");
        }
        
        // Initialize the videoRenderers array for position management
        this.videoRenderers = new SurfaceViewRenderer[4]; // Assuming max 4 renderers
        
        try {
            initializeWebRTC();
        } catch (IllegalStateException e) {
            // Already initialized - try to recover
            Log.e(TAG, "Surface already initialized, trying to recover", e);
            
            try {
                // Try to release and reinitialize - No need to check getEglBaseContext()
                safeReleaseRenderer(mainView);
                initializeWebRTC();
            } catch (Exception ex) {
                Log.e(TAG, "Failed to recover from initialization error", ex);
                throw ex; // Rethrow if recovery fails
            }
        }
    }
    
    // Helper method to safely release a renderer
    private void safeReleaseRenderer(SurfaceViewRenderer renderer) {
        if (renderer != null) {
            try {
                renderer.release();
            } catch (Exception e) {
                Log.e(TAG, "Error releasing renderer: " + e.getMessage());
            }
        }
    }

    private void initializeWebRTC() {
        // Initialize the PeerConnectionFactory first (MISSING STEP)
        PeerConnectionFactory.InitializationOptions initOptions = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(true)
            .createInitializationOptions();
        PeerConnectionFactory.initialize(initOptions);
        
        // Create an EglBase instance for rendering
        rootEglBase = EglBase.create();
        
        // Initialize the renderer
        try {
            mainView.init(rootEglBase.getEglBaseContext(), null);
            mainView.setEnableHardwareScaler(true);
            mainView.setMirror(false);
        } catch (IllegalStateException e) {
            Log.e(TAG, "Error initializing main view: " + e.getMessage());
            throw e; // Let the caller handle it
        }

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
        
        // Create a simple session code (last 6 chars of UUID)
        String simpleSessionCode = sessionId.substring(sessionId.length() - 6);
        
        // Create session data
        Map<String, Object> sessionData = new HashMap<>();
        sessionData.put("session_name", sessionName);
        sessionData.put("host_email", userEmail);
        sessionData.put("created_at", System.currentTimeMillis());
        sessionData.put("active", true);
        sessionData.put("max_joiners", MAX_JOINERS);
        sessionData.put("current_joiners", 0);
        sessionData.put("session_code", simpleSessionCode);  // Add this line
        
        // Save session to Firebase
        DatabaseReference sessionRef = mDatabase.child("users")
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
        DatabaseReference joinRequestsRef = mDatabase.child("users")
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
     * Create peer connection for a joiner
     * @param joinerId Joiner ID
     * @param joinerEmail Joiner email
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
                        Log.d(TAG, "Remote stream added from joiner: " + joinerId + 
                              " with video tracks: " + stream.videoTracks.size());
                        
                        // Handle incoming video/audio from camera
                        new Handler(Looper.getMainLooper()).post(() -> {
                            if (stream.videoTracks.size() > 0) {
                                // Get the assigned renderer for this joiner
                                SurfaceViewRenderer assignedRenderer = joinerRenderers.get(joinerId);
                                
                                // If no renderer assigned yet or it's the main view for first joiner
                                if (assignedRenderer == null) {
                                    // If this is the first joiner and no specific assignment
                                    if (peerConnections.size() == 1) {
                                        try {
                                            // Use VideoTrack.addSink directly from the stream's videoTracks
                                            stream.videoTracks.get(0).addSink(mainView);
                                            joinerRenderers.put(joinerId, mainView);
                                            Log.d(TAG, "Added video sink to main view for first joiner: " + joinerId);
                                        } catch (Exception e) {
                                            Log.e(TAG, "Error adding sink to main view: " + e.getMessage(), e);
                                        }
                                    }
                                } else {
                                    // Use the assigned renderer
                                    try {
                                        // Use VideoTrack.addSink directly from the stream's videoTracks
                                        stream.videoTracks.get(0).addSink(assignedRenderer);
                                        Log.d(TAG, "Added video sink to assigned renderer for joiner: " + joinerId);
                                    } catch (Exception e) {
                                        Log.e(TAG, "Error adding video sink in onAddStream: " + e.getMessage(), e);
                                    }
                                }
                                Log.d(TAG, "TRACK DEBUG - Adding video track to renderer: " + 
                                      (assignedRenderer == null ? "null" : assignedRenderer.toString()) + 
                                      " for joiner " + joinerId + 
                                      " with joiners assigned to renderers: " + joinerRenderers.toString());
                            } else {
                                Log.w(TAG, "Stream has no video tracks from joiner: " + joinerId);
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
                
                DatabaseReference joinerSessionRef = mDatabase.child("users")
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
        
        DatabaseReference answerRef = mDatabase.child("users")
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
        
        DatabaseReference iceCandidatesRef = mDatabase.child("users")
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
        
        DatabaseReference hostCandidatesRef = mDatabase.child("users")
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
        mDatabase.child("users")
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
        Log.d(TAG, "assignRendererToJoiner called for " + joinerId + " with renderer: " + renderer);
        
        if (renderer == null) {
            Log.e(TAG, "Cannot assign null renderer to joiner");
            return;
        }
        
        // Clear any existing assignments for this renderer
        clearAssignmentsForRenderer(renderer);
        
        // Initialize renderer if needed - THIS IS CRITICAL
        try {
            if (rootEglBase != null) {
                // Always re-initialize to ensure clean state
                renderer.release();
                renderer.init(rootEglBase.getEglBaseContext(), null);
                renderer.setEnableHardwareScaler(true);
                renderer.setMirror(false);
                Log.d(TAG, "Renderer initialized with EGL context: " + rootEglBase.getEglBaseContext());
            } else {
                Log.e(TAG, "Root EglBase is null, cannot initialize renderer");
                return;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error initializing renderer: " + e.getMessage(), e);
            return;
        }
        
        // Store renderer assignment - this is what matters for onAddStream
        joinerRenderers.put(joinerId, renderer);
        Log.d(TAG, "Stored renderer assignment for joiner: " + joinerId);
        
        // Handle any existing streams for this joiner
        PeerConnection pc = peerConnections.get(joinerId);
        if (pc != null) {
            // We'll rely on the onAddStream callback to actually connect 
            // the video track to the renderer
            Log.d(TAG, "Peer connection exists for joiner: " + joinerId + 
                  ", connection will be handled by onAddStream");
        } else {
            Log.w(TAG, "No peer connection found for joiner: " + joinerId);
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
        // Add this debug statement
        Log.d(TAG, "getActiveJoiners called, peers: " + peerConnections.size());
        
        // Add more detail on what's being returned
        String[] joiners = peerConnections.keySet().toArray(new String[0]);
        Log.d(TAG, "Returning joiners array of length: " + joiners.length);
        return joiners;
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
            mDatabase.child("users")
                    .child(formattedEmail)
                    .child("sessions")
                    .child(sessionId)
                    .child("active")
                    .setValue(false);
        }
    }

    /**
     * Initialize a session with the given ID
     */
    public void initializeSession(String sessionId) {
        this.sessionId = sessionId;
        Log.d(TAG, "Initializing session: " + sessionId);
        
        // Set up listeners for incoming connections
        setupJoinListeners();
    }

    private void setupJoinListeners() {
        String userEmail = this.formattedEmail;
        
        if (sessionId == null || userEmail == null) {
            Log.e(TAG, "Cannot setup join listeners: missing sessionId or userEmail");
            return;
        }
        
        // Listen for accepted join requests
        mDatabase.child("users").child(userEmail).child("sessions").child(sessionId)
                .child("join_requests").orderByChild("status").equalTo("accepted")
                .addChildEventListener(new ChildEventListener() {
                    @Override
                    public void onChildAdded(DataSnapshot dataSnapshot, String previousChildName) {
                        String requestId = dataSnapshot.getKey();
                        Log.d(TAG, "Join request accepted: " + requestId);
                        
                        // Start connection process with this joiner
                        createPeerConnection(requestId);
                    }
                    
                    @Override
                    public void onChildChanged(DataSnapshot dataSnapshot, String previousChildName) {}
                    
                    @Override
                    public void onChildRemoved(DataSnapshot dataSnapshot) {}
                    
                    @Override
                    public void onChildMoved(DataSnapshot dataSnapshot, String previousChildName) {}
                    
                    @Override
                    public void onCancelled(DatabaseError databaseError) {}
                });
    }

    // Add methods for mute/unmute if missing
    public void muteMic() {
        // Implement audio muting
        Log.d(TAG, "Muting microphone");
    }

    public void unmuteMic() {
        // Implement audio unmuting
        Log.d(TAG, "Unmuting microphone");
    }

    /**
     * Create peer connection for a joiner from a request ID
     * @param requestId The ID of the join request
     */
    public void createPeerConnection(String requestId) {
        // First, get the joiner's email from the request data
        String userEmail = this.formattedEmail;
        if (sessionId == null || userEmail == null || requestId == null) {
            Log.e(TAG, "Cannot create peer connection: missing required information");
            return;
        }
        
        DatabaseReference requestRef = mDatabase.child("users").child(userEmail)
                .child("sessions").child(sessionId)
                .child("join_requests").child(requestId);
                
        requestRef.get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult().exists()) {
                String joinerEmail = task.getResult().child("email").getValue(String.class);
                if (joinerEmail != null) {
                    // Now call the existing method with both required parameters
                    createPeerConnectionForJoiner(requestId, joinerEmail);
                } else {
                    Log.e(TAG, "Failed to get joiner email for request: " + requestId);
                }
            } else {
                Log.e(TAG, "Failed to fetch join request data", task.getException());
            }
        });
    }

    /**
     * Find a session by session code (passkey)
     * @param sessionCode The 6-digit session code entered by user
     */
    public static void findSessionByCode(String sessionCode, SessionFoundCallback callback) {
        // Add debug logging
        Log.d(TAG, "Looking for session with code: " + sessionCode);
        
        // Normalize session code (trim whitespace, convert to lowercase)
        String normalizedCode = sessionCode.trim().toLowerCase();
        
        // First check in the users path where sessions are actually created
        DatabaseReference usersRef = FirebaseDatabase.getInstance().getReference("users");
        
        // Use a single query instead of fetching all users
        usersRef.get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                boolean found = false;
                Log.d(TAG, "Searching through " + task.getResult().getChildrenCount() + " users");
                
                // Iterate through all users
                for (DataSnapshot userSnapshot : task.getResult().getChildren()) {
                    DataSnapshot sessionsSnapshot = userSnapshot.child("sessions");
                    
                    // Search sessions for this user
                    for (DataSnapshot sessionSnapshot : sessionsSnapshot.getChildren()) {
                        String code = sessionSnapshot.child("session_code").getValue(String.class);
                        Boolean active = sessionSnapshot.child("active").getValue(Boolean.class);
                        
                        Log.d(TAG, "Found session with code: " + code + ", active: " + active + 
                              " (comparing with: " + normalizedCode + ")");
                        
                        // Check if session is active and code matches (case-insensitive)
                        if (code != null && active != null && active && 
                                code.equalsIgnoreCase(normalizedCode)) {
                            String sessionId = sessionSnapshot.getKey();
                            String hostEmail = sessionSnapshot.child("host_email").getValue(String.class);
                            
                            if (hostEmail != null) {
                                Log.d(TAG, "Session found! ID: " + sessionId + ", Host: " + hostEmail);
                                callback.onSessionFound(sessionId, hostEmail);
                                found = true;
                                break;
                            }
                        }
                    }
                    
                    if (found) break;
                }
                
                if (!found) {
                    Log.d(TAG, "No session found with code: " + normalizedCode);
                    callback.onSessionNotFound();
                }
            } else {
                Log.e(TAG, "Failed to search for sessions", task.getException());
                callback.onError("Failed to search for sessions", task.getException());
            }
        });
    }

    /**
     * Callback interface for session lookup
     */
    public interface SessionFoundCallback {
        void onSessionFound(String sessionId, String hostEmail);
        void onSessionNotFound();
        void onError(String message, Exception e);
    }

    /**
     * Check if a joiner needs a renderer assignment
     * @param joinerId The joiner ID to check
     * @return true if needs assignment, false if already assigned
     */
    public boolean needsRendererAssignment(String joinerId) {
        return !joinerRenderers.containsKey(joinerId);
    }

    /**
     * Get the EglBase context for initializing renderers
     * @return EglBase.Context used for rendering
     */
    public EglBase.Context getEglBaseContext() {
        return rootEglBase != null ? rootEglBase.getEglBaseContext() : null;
    }

    /**
     * Check if a renderer is assigned to a specific joiner
     * @param joinerId The joiner ID to check
     * @param renderer The renderer to check
     * @return true if this renderer is assigned to this joiner
     */
    public boolean isRendererAssignedToJoiner(String joinerId, SurfaceViewRenderer renderer) {
        SurfaceViewRenderer assignedRenderer = joinerRenderers.get(joinerId);
        return assignedRenderer != null && assignedRenderer == renderer;
    }

    /**
     * Clear any existing renderer assignments for this renderer
     * @param renderer The renderer to clear assignments for
     */
    private void clearAssignmentsForRenderer(SurfaceViewRenderer renderer) {
        // Remove any existing assignments of this renderer to other joiners
        String joinerToRemove = null;
        
        for (Map.Entry<String, SurfaceViewRenderer> entry : joinerRenderers.entrySet()) {
            if (entry.getValue() == renderer) {
                joinerToRemove = entry.getKey();
                break;
            }
        }
        
        if (joinerToRemove != null && joinerToRemove.length() > 0) {
            Log.d(TAG, "Clearing previous assignment of renderer to joiner: " + joinerToRemove);
            joinerRenderers.remove(joinerToRemove);
        }
    }

    /**
     * Debug method to print current renderer assignments
     */
    public void logRendererAssignments() {
        StringBuilder sb = new StringBuilder("Current renderer assignments:\n");
        for (Map.Entry<String, SurfaceViewRenderer> entry : joinerRenderers.entrySet()) {
            sb.append("Joiner: ").append(entry.getKey())
              .append(" -> Renderer: ").append(entry.getValue())
              .append("\n");
        }
        Log.d(TAG, sb.toString());
    }

    // Modify handleJoinRequest method to include device identifiers
    private void handleJoinRequest(String joinerId, String joinerEmail, DataSnapshot requestData) {
        // Extract device information from join request
        String deviceId = requestData.child("deviceId").getValue(String.class);
        String deviceName = requestData.child("deviceName").getValue(String.class);
        
        // Create composite unique identifier
        String uniqueJoinerId = deviceId != null ? joinerEmail + "_" + deviceId : joinerId;
        
        // Store device info
        if (deviceId != null) joinerDeviceIds.put(joinerId, deviceId);
        if (deviceName != null) joinerDeviceNames.put(joinerId, deviceName);
        
        Log.d(TAG, "Handling join request from: " + joinerEmail + 
              " (Device: " + (deviceName != null ? deviceName : "Unknown") + ")");
        
        // Rest of your existing join request handling code...
        
        // When notifying UI about new join request, include device info
        if (joinRequestCallback != null) {
            joinRequestCallback.onJoinRequest(joinerId, joinerEmail, deviceName);
        }
    }

    // Update your join request callback interface to include device name
    public interface JoinRequestCallback {
        void onJoinRequest(String joinerId, String joinerEmail, String deviceName);
    }

    // Update the acceptJoinRequest method to include device identifiers
    public void acceptJoinRequest(String joinerId, String joinerEmail, String deviceName, String deviceId) {
        // Store device info for this joiner
        if (deviceId != null) joinerDeviceIds.put(joinerId, deviceId);
        if (deviceName != null) joinerDeviceNames.put(joinerId, deviceName);
        
        Log.d(TAG, "Accepting join request from: " + joinerEmail + 
              " (Device: " + (deviceName != null ? deviceName : "Unknown") + 
              ", DeviceID: " + (deviceId != null ? deviceId : "unknown") + ")");
        
        // Update firebase status for this joiner
        String formattedJoinerEmail = joinerEmail.replace(".", "_");
        DatabaseReference joinRequestRef = mDatabase.child("users")
                .child(formattedEmail)
                .child("sessions")
                .child(sessionId)
                .child("join_requests")
                .child(joinerId);
                
        // Set status to accepted and include device information
        joinRequestRef.child("status").setValue("accepted");
        if (deviceId != null) joinRequestRef.child("deviceId").setValue(deviceId);
        if (deviceName != null) joinRequestRef.child("deviceName").setValue(deviceName);
        
        // Create a connection for this joiner with device info
        int position = findNextAvailablePosition();
        createPeerConnectionForJoiner(joinerId, joinerEmail, deviceId, deviceName, position);
    }

    private void createPeerConnectionForJoiner(String joinerId, String joinerEmail, 
                                          String deviceId, String deviceName, int position) {
        // Store which renderer is used by this joiner
        joinerRenderers.put(joinerId, videoRenderers[position]);
        
        // Store composite info in joinerEmails map 
        joinerEmails.put(joinerId, joinerEmail + " (" + deviceName + ")");
        
        // Add device info to joinerInfo for UI display
        Map<String, String> joinerInfo = new HashMap<>();
        joinerInfo.put("email", joinerEmail);
        joinerInfo.put("deviceName", deviceName);
        joinerInfo.put("deviceId", deviceId);
        joinerInfo.put("position", String.valueOf(position));
        joinerInfoMap.put(joinerId, joinerInfo);
        
        // Rest of your existing code...
    }

    // Add these methods to RTCHost for accessing device info
    public String getJoinerDeviceName(String joinerId) {
        return joinerDeviceNames.getOrDefault(joinerId, "");
    }

    public String getJoinerDeviceId(String joinerId) {
        return joinerDeviceIds.getOrDefault(joinerId, "");
    }

    public String getJoinerDisplayName(String joinerId) {
        String email = joinerEmails.getOrDefault(joinerId, "Unknown");
        String deviceName = getJoinerDeviceName(joinerId);
        
        if (deviceName != null && !deviceName.isEmpty()) {
            return email + " (" + deviceName + ")";
        }
        return email;
    }

    // In your notification handler
    private void handleDetectionEvent(String joinerId, String eventType, Map<String, Object> eventData) {
        String joinerEmail = joinerEmails.get(joinerId);
        String deviceName = joinerDeviceNames.get(joinerId);
        
        String notificationText = eventType + " detected from " + joinerEmail;
        if (deviceName != null && !deviceName.isEmpty()) {
            notificationText += " (" + deviceName + ")";
        }
        
        // Show notification with device-specific information
        // ...
    }

    /**
     * Find the next available position for a new joiner/camera
     * @return Position index (0-3) for the next available position, or -1 if all positions are taken
     */
    private int findNextAvailablePosition() {
        // Check if we have initialized video renderers array
        if (videoRenderers == null) {
            Log.e(TAG, "Video renderers array is null");
            return 0; // Default to first position
        }
        
        // Track which positions are already in use
        boolean[] positionTaken = new boolean[4]; // Assuming max 4 positions
        
        // Mark positions that are already assigned to joiners
        for (Map.Entry<String, SurfaceViewRenderer> entry : joinerRenderers.entrySet()) {
            for (int i = 0; i < videoRenderers.length; i++) {
                if (entry.getValue() == videoRenderers[i]) {
                    positionTaken[i] = true;
                    break;
                }
            }
        }
        
        // Find first available position
        for (int i = 0; i < positionTaken.length; i++) {
            if (!positionTaken[i]) {
                Log.d(TAG, "Found available position: " + i);
                return i;
            }
        }
        
        // If we get here, all positions are taken
        Log.w(TAG, "No available positions found, all are taken");
        return -1;
    }

    // Add this method to properly register renderers in the array:
    public void registerRendererAtPosition(int position, SurfaceViewRenderer renderer) {
        if (videoRenderers != null && position >= 0 && position < videoRenderers.length) {
            videoRenderers[position] = renderer;
            Log.d(TAG, "Registered renderer at position " + position);
        } else {
            Log.e(TAG, "Invalid position or videoRenderers array not initialized");
        }
    }
}




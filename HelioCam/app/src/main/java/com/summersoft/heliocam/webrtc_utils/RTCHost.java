package com.summersoft.heliocam.webrtc_utils;

import android.content.Context;
import android.util.Log;
import android.view.View;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;
import com.summersoft.heliocam.webrtc.SfuManager;

import org.webrtc.IceCandidate;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceViewRenderer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class RTCHost {
    private static final String TAG = "RTCHost";
    private final Context context;
    private final String sessionId;
    private final SurfaceViewRenderer renderer;
    private final DatabaseReference firebaseDatabase;
    public SfuManager sfuManager;
    private final Set<SfuManager.RemoteTrackListener> remoteTrackListeners = new HashSet<>();

    public RTCHost(Context context, String sessionId, SurfaceViewRenderer renderer, DatabaseReference firebaseDatabase) {
        this.context = context;
        this.sessionId = sessionId;
        this.renderer = renderer;
        this.firebaseDatabase = firebaseDatabase;
        initSfuManager();
    }

    private void initSfuManager() {
        sfuManager = new SfuManager(context, new SfuManager.SignalingInterface() {
            @Override
            public void sendOffer(SessionDescription offer, String sessionId, String role) {
                String userEmail = sessionId.split("_")[0].replace(".", "_");
                firebaseDatabase.child("users").child(userEmail).child("sessions").child(sessionId)
                        .child("Offer").setValue(offer.description)
                        .addOnCompleteListener(task -> {
                            if (task.isSuccessful()) {
                                Log.d(TAG, "SDP offer sent to Firebase");
                            } else {
                                Log.e(TAG, "Failed to send SDP offer", task.getException());
                            }
                        });
            }

            @Override
            public void sendAnswer(SessionDescription answer, String sessionId, String role) {
                String userEmail = sessionId.split("_")[0].replace(".", "_");
                firebaseDatabase.child("users").child(userEmail).child("sessions").child(sessionId)
                        .child("Answer").setValue(answer.description)
                        .addOnCompleteListener(task -> {
                            if (task.isSuccessful()) {
                                Log.d(TAG, "SDP answer sent to Firebase");
                            } else {
                                Log.e(TAG, "Failed to send SDP answer", task.getException());
                            }
                        });
            }

            @Override
            public void sendIceCandidate(IceCandidate candidate, String sessionId, String role) {
                String userEmail = sessionId.split("_")[0].replace(".", "_");
                Map<String, Object> candidateData = new HashMap<>();
                candidateData.put("candidate", candidate.sdp);
                candidateData.put("sdpMid", candidate.sdpMid);
                candidateData.put("sdpMLineIndex", candidate.sdpMLineIndex);

                firebaseDatabase.child("users").child(userEmail).child("sessions").child(sessionId)
                        .child("HostCandidate").setValue(candidateData);
            }
        });

        // Attach remote track listeners
        sfuManager.addRemoteTrackListener(videoTrack -> {
            for (SfuManager.RemoteTrackListener listener : remoteTrackListeners) {
                listener.onRemoteVideoTrackReceived(videoTrack);
            }
        });
    }

    public void createSession() {
        try {
            Log.d(TAG, "Creating WebRTC session with ID: " + sessionId);

            // Initialize the renderer
            try {
                Log.d(TAG, "Initializing renderer");
                if (renderer != null) {
                    renderer.setVisibility(View.VISIBLE);
                    sfuManager.attachRemoteView(renderer);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error initializing renderer: " + e.getMessage());
            }

            // Create the host session
            sfuManager.createHostSession(sessionId);

            // Set up listeners for signaling
            listenForRemoteOfferAndAnswer();

            Log.d(TAG, "Session creation complete");
        } catch (Exception e) {
            Log.e(TAG, "Error creating session: " + e.getMessage(), e);
        }
    }

    private void listenForRemoteOfferAndAnswer() {
        String userEmail = sessionId.split("_")[0].replace(".", "_");

        // Listen for remote answer
        firebaseDatabase.child("users").child(userEmail).child("sessions").child(sessionId)
                .child("Answer").addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        if (dataSnapshot.exists()) {
                            String answerSdp = dataSnapshot.getValue(String.class);
                            if (answerSdp != null) {
                                SessionDescription answer = new SessionDescription(
                                        SessionDescription.Type.ANSWER, answerSdp);
                                sfuManager.handleRemoteAnswer(answer);
                            }
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {
                        Log.e(TAG, "Failed to listen for remote answer: " + databaseError.getMessage());
                    }
                });

        // Listen for joiner ICE candidates
        firebaseDatabase.child("users").child(userEmail).child("sessions").child(sessionId)
                .child("JoinerCandidate").addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        if (dataSnapshot.exists()) {
                            String sdp = dataSnapshot.child("candidate").getValue(String.class);
                            String sdpMid = dataSnapshot.child("sdpMid").getValue(String.class);
                            Long sdpMLineIndex = dataSnapshot.child("sdpMLineIndex").getValue(Long.class);

                            if (sdp != null && sdpMid != null && sdpMLineIndex != null) {
                                IceCandidate candidate = new IceCandidate(sdpMid, sdpMLineIndex.intValue(), sdp);
                                sfuManager.handleRemoteIceCandidate(candidate);
                            }
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {
                        Log.e(TAG, "Failed to listen for joiner ICE candidates: " + databaseError.getMessage());
                    }
                });
    }

    public void addRemoteTrackListener(SfuManager.RemoteTrackListener listener) {
        remoteTrackListeners.add(listener);
    }

    public void ensureRemoteVideoTrackIsAttached(SurfaceViewRenderer renderer) {
        if (sfuManager != null && sfuManager.remoteVideoTracks != null && !sfuManager.remoteVideoTracks.isEmpty()) {
            for (String trackId : sfuManager.remoteVideoTracks.keySet()) {
                sfuManager.attachRemoteVideoTrackToRenderer(trackId, renderer);
                break;
            }
        }
    }

    public void muteMic() {
        if (sfuManager != null) {
            sfuManager.toggleAudio();
        }
    }

    public void unmuteMic() {
        if (sfuManager != null) {
            sfuManager.toggleAudio();
        }
    }

    public void updateRendererCount(int count, java.util.List<SurfaceViewRenderer> renderers) {
        // Optionally update renderer assignments for multiple participants
    }

    public void dispose() {
        if (sfuManager != null) {
            sfuManager.release();
        }
    }
}
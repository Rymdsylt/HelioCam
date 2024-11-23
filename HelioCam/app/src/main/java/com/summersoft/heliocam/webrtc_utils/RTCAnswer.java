package com.summersoft.heliocam.webrtc_utils;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import org.webrtc.PeerConnection;
import org.webrtc.SessionDescription;
import com.google.firebase.database.DatabaseReference;

public class RTCAnswer {
    private static final String TAG = "RTCAnswer";

    private PeerConnection peerConnection;
    private DatabaseReference firebaseDatabase;
    private Context context;  // Declare the context variable

    public RTCAnswer(PeerConnection peerConnection, DatabaseReference firebaseDatabase, Context context) {
        this.peerConnection = peerConnection;
        this.firebaseDatabase = firebaseDatabase;
        this.context = context;  // Initialize context
    }

    public void createAnswer(String sessionId, String email) {
        if (peerConnection == null) {
            Log.e(TAG, "PeerConnection is not initialized.");
            return;
        }

        // Create the answer
        peerConnection.createAnswer(new SdpAdapter("CreateAnswer") {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                // Set local description
                peerConnection.setLocalDescription(new SdpAdapter("SetLocalDescription"), sessionDescription);

                // Send the answer to Firebase
                String answer = sessionDescription.description;

                String emailKey = email.replace(".", "_"); // Firebase doesn't support '@' or '.' in keys

                // Update Firebase with the session answer
                firebaseDatabase.child("users").child(emailKey).child("sessions").child(sessionId)
                        .child("Answer").setValue(answer)
                        .addOnCompleteListener(task -> {
                            if (task.isSuccessful()) {
                                // Log if the answer was successfully sent to Firebase
                                Log.d(TAG, "Answer created for session " + sessionId + ": " + answer);
                            } else {
                                // Log failure if the operation was unsuccessful
                                Log.e(TAG, "Failed to send answer to Firebase for session: " + sessionId, task.getException());
                            }
                        });
            }

            @Override
            public void onCreateFailure(String error) {
                Log.e(TAG, "Failed to create answer: " + error);
                Toast.makeText(context, "Failed to create answer: " + error, Toast.LENGTH_SHORT).show();
            }

        }, null); // MediaConstraints can be null if not required
    }

    // IceCandidate data class for answer
    public static class IceCandidateData {
        public String candidate;
        public String sdpMid;
        public int sdpMLineIndex;

        // Default constructor for Firebase
        public IceCandidateData() {}

        public IceCandidateData(String candidate, String sdpMid, int sdpMLineIndex) {
            this.candidate = candidate;
            this.sdpMid = sdpMid;
            this.sdpMLineIndex = sdpMLineIndex;
        }
    }
}
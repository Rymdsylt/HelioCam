package com.summersoft.heliocam.status;

import android.app.AlertDialog;
import android.os.Build;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.summersoft.heliocam.R;
import com.summersoft.heliocam.ui.HomeActivity;

public class SessionLoader {

    private FirebaseAuth mAuth;
    private LinearLayout sessionCardContainer;
    private HomeActivity homeActivity;

    public SessionLoader(HomeActivity homeActivity, LinearLayout sessionCardContainer) {
        this.mAuth = FirebaseAuth.getInstance();
        this.homeActivity = homeActivity;
        this.sessionCardContainer = sessionCardContainer;
    }

    public void loadUserSessions() {
        String userEmail = mAuth.getCurrentUser().getEmail().replace(".", "_");
        String deviceIdentifier = Build.MANUFACTURER + " " + Build.DEVICE;

        DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("users").child(userEmail);

        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                sessionCardContainer.removeAllViews();

                if (dataSnapshot.exists()) {
                    for (DataSnapshot loginInfoSnapshot : dataSnapshot.getChildren()) {
                        if (loginInfoSnapshot.getKey().startsWith("logininfo_")) {
                            String deviceName = loginInfoSnapshot.child("deviceName").getValue(String.class);
                            if (deviceName != null && deviceName.equals(deviceIdentifier)) {
                                DataSnapshot sessionsAddedSnapshot = loginInfoSnapshot.child("sessions_added");

                                if (sessionsAddedSnapshot.exists()) {
                                    int sessionNumber = 1;

                                    for (DataSnapshot sessionSnapshot : sessionsAddedSnapshot.getChildren()) {
                                        String sessionKey = sessionSnapshot.getKey(); // Firebase key for this session
                                        String sessionName = sessionSnapshot.child("session_name").getValue(String.class);

                                        if (sessionName != null && !sessionName.isEmpty()) {
                                            View sessionCard = LayoutInflater.from(sessionCardContainer.getContext())
                                                    .inflate(R.layout.session_card, null);

                                            TextView sessionNumberTextView = sessionCard.findViewById(R.id.session_number);
                                            sessionNumberTextView.setText("Session " + sessionNumber);

                                            TextView sessionNameTextView = sessionCard.findViewById(R.id.session_name);
                                            sessionNameTextView.setText(sessionName);

                                            View deleteButton = sessionCard.findViewById(R.id.delete_button);
                                            deleteButton.setOnClickListener(v -> deleteSession(userRef, loginInfoSnapshot.getKey(), sessionKey, sessionCard));

                                            sessionCardContainer.addView(sessionCard);
                                            sessionNumber++;
                                        }
                                    }
                                }
                                break;
                            }
                        }
                    }
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.e("SessionLoader", "Failed to load sessions: " + databaseError.getMessage());
            }
        });
    }

    private void deleteSession(DatabaseReference userRef, String loginInfoKey, String sessionKey, View sessionCard) {
        // Create a confirmation dialog
        new AlertDialog.Builder(sessionCardContainer.getContext())
                .setTitle("Confirm Delete")
                .setMessage("Are you sure you want to delete this session?")
                .setPositiveButton("Yes", (dialog, which) -> {
                    // Proceed with deletion if the user confirms
                    DatabaseReference sessionRef = userRef.child(loginInfoKey).child("sessions_added").child(sessionKey);

                    sessionRef.removeValue().addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            sessionCardContainer.removeView(sessionCard); // Remove the card from the UI
                            Toast.makeText(sessionCardContainer.getContext(), "Session deleted successfully" , Toast.LENGTH_SHORT).show();
                        } else {
                            Log.e("SessionLoader", "Failed to delete session: " + task.getException().getMessage());
                        }
                    });

                })
                .setNegativeButton("No", (dialog, which) -> {
                    // Dismiss the dialog if the user cancels
                    dialog.dismiss();
                })
                .show();
    }

}
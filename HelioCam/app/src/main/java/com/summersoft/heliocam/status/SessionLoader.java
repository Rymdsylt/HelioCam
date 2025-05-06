package com.summersoft.heliocam.status;

import static android.content.Context.MODE_PRIVATE;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Build;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.Gson;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.summersoft.heliocam.R;
import com.summersoft.heliocam.ui.HomeActivity;
import com.summersoft.heliocam.ui.SessionPreviewActivity;
import com.summersoft.heliocam.ui.WatchSessionActivity;

import java.util.Map;

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
                Boolean sessionsFound = false;

                for (DataSnapshot loginInfoSnapshot : dataSnapshot.getChildren()) {
                    if (loginInfoSnapshot.hasChild("deviceName") && loginInfoSnapshot.child("deviceName").getValue().equals(deviceIdentifier)) {
                        DataSnapshot sessionsAddedSnapshot = loginInfoSnapshot.child("sessions_added");
                        int sessionCount = 1;

                        for (DataSnapshot sessionSnapshot : sessionsAddedSnapshot.getChildren()) {
                            String sessionKey = sessionSnapshot.getKey();
                            String sessionName = sessionSnapshot.child("session_name").getValue(String.class);

                            if (sessionName != null) {
                                createSessionCard(sessionKey, sessionName, sessionCount);
                                sessionCount++;
                                sessionsFound = true;
                            }
                        }

                        String loginInfoKey = loginInfoSnapshot.getKey();

                        if (!sessionsFound) {
                            View noSessionsView = LayoutInflater.from(homeActivity).inflate(R.layout.no_sessions_placeholder, sessionCardContainer, false);
                            sessionCardContainer.addView(noSessionsView);
                        }
                    }
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                // Handle the error
                Log.e("SessionLoader", "Database error: " + databaseError.getMessage());
            }
        });
    }

    private void createSessionCard(String sessionKey, String sessionName, int sessionCount) {
        View sessionCardView = LayoutInflater.from(homeActivity).inflate(R.layout.session_card, sessionCardContainer, false);
        TextView sessionNameView = sessionCardView.findViewById(R.id.session_name);
        TextView sessionNumberView = sessionCardView.findViewById(R.id.session_number);

        sessionNameView.setText(sessionName);
        sessionNumberView.setText("Session " + sessionCount);

        // THIS IS THE KEY CHANGE: Make the session card open SessionPreviewActivity instead of WatchSessionActivity
        View clickableArea = (View) sessionCardView.findViewById(R.id.session_name).getParent();
        clickableArea.setOnClickListener(v -> {
            // Open SessionPreviewActivity instead of WatchSessionActivity
            Intent intent = new Intent(homeActivity, SessionPreviewActivity.class);
            intent.putExtra("SESSION_KEY", sessionKey);
            intent.putExtra("SESSION_NAME", sessionName);
            homeActivity.startActivity(intent);
        });

        // Setup delete button
        View deleteButton = sessionCardView.findViewById(R.id.delete_button);
        if (deleteButton != null) {
            deleteButton.setOnClickListener(v -> {
                String userEmail = mAuth.getCurrentUser().getEmail().replace(".", "_");
                String deviceIdentifier = Build.MANUFACTURER + " " + Build.DEVICE;
                DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("users").child(userEmail);

                userRef.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        for (DataSnapshot loginInfoSnapshot : dataSnapshot.getChildren()) {
                            if (loginInfoSnapshot.hasChild("deviceName") &&
                                    loginInfoSnapshot.child("deviceName").getValue().equals(deviceIdentifier)) {
                                String loginInfoKey = loginInfoSnapshot.getKey();
                                deleteSession(userRef, loginInfoKey, sessionKey, sessionCardView);
                                break;
                            }
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {
                        Log.e("SessionLoader", "Failed to delete session: " + databaseError.getMessage());
                    }
                });
            });
        }

        // Setup settings button
        View settingsButton = sessionCardView.findViewById(R.id.iconSettings);
        if (settingsButton != null) {
            settingsButton.setOnClickListener(v -> {
                Toast.makeText(homeActivity, "Settings not implemented yet", Toast.LENGTH_SHORT).show();
            });
        }

        sessionCardContainer.addView(sessionCardView);
    }

    private void deleteSession(DatabaseReference userRef, String loginInfoKey, String sessionKey, View sessionCard) {
        new AlertDialog.Builder(sessionCardContainer.getContext())
                .setTitle("Confirm Delete")
                .setMessage("Are you sure you want to delete this session?")
                .setPositiveButton("Yes", (dialog, which) -> {
                    DatabaseReference sessionRef = userRef.child(loginInfoKey).child("sessions_added").child(sessionKey);

                    sessionRef.removeValue().addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            Toast.makeText(homeActivity, "Session deleted successfully", Toast.LENGTH_SHORT).show();
                            sessionCardContainer.removeView(sessionCard);

                            // Check if there are no more sessions
                            if (sessionCardContainer.getChildCount() == 0) {
                                View noSessionsView = LayoutInflater.from(homeActivity).inflate(R.layout.no_sessions_placeholder, sessionCardContainer, false);
                                sessionCardContainer.addView(noSessionsView);
                            }
                        } else {
                            Toast.makeText(homeActivity, "Failed to delete session", Toast.LENGTH_SHORT).show();
                        }
                    });
                })
                .setNegativeButton("No", (dialog, which) -> {
                    dialog.dismiss();
                })
                .show();
    }
}
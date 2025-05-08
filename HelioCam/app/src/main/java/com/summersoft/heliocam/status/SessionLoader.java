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
import com.summersoft.heliocam.ui.HostSession;
import com.summersoft.heliocam.ui.SessionPreviewActivity;
import com.summersoft.heliocam.ui.WatchSessionActivity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
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

    // Add an interface at the top of the class
    public interface SessionChangeListener {
        void onSessionsChanged();
    }

    private SessionChangeListener changeListener;

    // Add a method to set the listener
    public void setSessionChangeListener(SessionChangeListener listener) {
        this.changeListener = listener;
    }

    public void loadUserSessions() {
        String userEmail = mAuth.getCurrentUser().getEmail().replace(".", "_");
        String deviceIdentifier = Build.MANUFACTURER + " " + Build.DEVICE;

        DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("users").child(userEmail);

        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (sessionCardContainer == null) {
                    Log.e("SessionLoader", "Session container is null");
                    return;
                }
                
                // Clear existing views
                sessionCardContainer.removeAllViews();
                
                // Flag to track if any sessions were found
                boolean sessionsFound = false;
                int sessionCount = 1;

                // Check for sessions in various locations
                // First, check for sessions in login info (original approach)
                for (DataSnapshot loginInfoSnapshot : dataSnapshot.getChildren()) {
                    if (loginInfoSnapshot.hasChild("deviceName") && 
                            loginInfoSnapshot.child("deviceName").getValue().equals(deviceIdentifier)) {
                        DataSnapshot sessionsAddedSnapshot = loginInfoSnapshot.child("sessions_added");
                        
                        for (DataSnapshot sessionSnapshot : sessionsAddedSnapshot.getChildren()) {
                            String sessionKey = sessionSnapshot.getKey();
                            String sessionName = sessionSnapshot.child("session_name").getValue(String.class);

                            if (sessionName != null) {
                                createSessionCard(sessionKey, sessionName, sessionCount);
                                sessionCount++;
                                sessionsFound = true;
                            }
                        }
                    }
                }
                
                // ADDED: Also check for sessions directly under the user's "sessions" node
                DataSnapshot directSessionsSnapshot = dataSnapshot.child("sessions");
                for (DataSnapshot sessionSnapshot : directSessionsSnapshot.getChildren()) {
                    String sessionKey = sessionSnapshot.getKey();
                    String sessionName = sessionSnapshot.child("session_name").getValue(String.class);
                    
                    if (sessionName != null) {
                        createSessionCard(sessionKey, sessionName, sessionCount);
                        sessionCount++;
                        sessionsFound = true;
                    }
                }

                // REMOVE this part - let the fragment handle showing the placeholder
                // if (!sessionsFound) {
                //     View noSessionsView = LayoutInflater.from(homeActivity)
                //         .inflate(R.layout.no_sessions_placeholder, sessionCardContainer, false);
                //     sessionCardContainer.addView(noSessionsView);
                // }
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
        TextView sessionPasskeyView = sessionCardView.findViewById(R.id.session_passkey);
        TextView sessionCreationDateView = sessionCardView.findViewById(R.id.session_creation_date);

        sessionNameView.setText(sessionName);
        sessionNumberView.setText("Session " + sessionCount);

        // Get additional session details
        String userEmail = mAuth.getCurrentUser().getEmail().replace(".", "_");
        DatabaseReference sessionRef = FirebaseDatabase.getInstance().getReference("users")
                .child(userEmail).child("sessions").child(sessionKey);
        
        sessionRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    // Get passkey and creation date
                    String passkey = dataSnapshot.child("passkey").getValue(String.class);
                    Long createdAt = dataSnapshot.child("created_at").getValue(Long.class);
                    
                    if (passkey != null) {
                        sessionPasskeyView.setText(passkey);
                    } else {
                        sessionPasskeyView.setText("N/A");
                    }
                    
                    if (createdAt != null) {
                        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
                        sessionCreationDateView.setText("Created: " + sdf.format(new Date(createdAt)));
                    } else {
                        sessionCreationDateView.setText("Created: Unknown");
                    }
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.e("SessionLoader", "Error loading session details: " + databaseError.getMessage());
            }
        });

        // Make the card open HostSession with prefilled data
        sessionCardView.setOnClickListener(v -> {
            // Add an intent to open the HostSession activity with pre-filled data
            Intent intent = new Intent(homeActivity, HostSession.class);
            intent.putExtra("SESSION_KEY", sessionKey);
            intent.putExtra("SESSION_NAME", sessionName);
            intent.putExtra("RECREATE", true);
            
            // You can add more session data to pass as needed
            homeActivity.startActivity(intent);
        });

        // Setup delete button
        View deleteButton = sessionCardView.findViewById(R.id.delete_button);
        if (deleteButton != null) {
            deleteButton.setOnClickListener(v -> {
                // Renamed variable to avoid conflict with outer scope
                String currentUserEmail = mAuth.getCurrentUser().getEmail().replace(".", "_");
                String deviceIdentifier = Build.MANUFACTURER + " " + Build.DEVICE;
                DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("users").child(currentUserEmail);

                userRef.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        if (dataSnapshot.child("sessions").hasChild(sessionKey)) {
                            // Delete from sessions node
                            deleteSession(userRef, "sessions", sessionKey, sessionCardView);
                        } else {
                            // Try to find in login info (original approach)
                            for (DataSnapshot loginInfoSnapshot : dataSnapshot.getChildren()) {
                                if (loginInfoSnapshot.hasChild("deviceName") &&
                                        loginInfoSnapshot.child("deviceName").getValue().equals(deviceIdentifier)) {
                                    String loginInfoKey = loginInfoSnapshot.getKey();
                                    deleteSession(userRef, loginInfoKey + "/sessions_added", sessionKey, sessionCardView);
                                    break;
                                }
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

        sessionCardContainer.addView(sessionCardView);
    }

    private void deleteSession(DatabaseReference userRef, String path, String sessionKey, View sessionCard) {
        new AlertDialog.Builder(sessionCardContainer.getContext())
                .setTitle("Confirm Delete")
                .setMessage("Are you sure you want to delete this session?")
                .setPositiveButton("Yes", (dialog, which) -> {
                    DatabaseReference sessionRef = userRef.child(path).child(sessionKey);

                    sessionRef.removeValue().addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            Toast.makeText(homeActivity, "Session deleted successfully", Toast.LENGTH_SHORT).show();
                            sessionCardContainer.removeView(sessionCard);
                            if (changeListener != null) {
                                changeListener.onSessionsChanged();
                            }

                            // REMOVE THIS CODE - let the fragment handle showing the placeholder
                            // if (sessionCardContainer.getChildCount() == 0) {
                            //     View noSessionsView = LayoutInflater.from(homeActivity).inflate(R.layout.no_sessions_placeholder, sessionCardContainer, false);
                            //     sessionCardContainer.addView(noSessionsView);
                            // }
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
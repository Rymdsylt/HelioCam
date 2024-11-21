package com.summersoft.heliocam.status;

import android.os.Build;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

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
        // Get the logged-in user's email, replace dots with underscores for Firebase compatibility
        String userEmail = mAuth.getCurrentUser().getEmail().replace(".", "_");

        Log.d("SessionLoader", "User email formatted for Firebase path: " + userEmail);

        // Get the current device information (Build.MANUFACTURER + " " + Build.DEVICE)
        String deviceIdentifier = Build.MANUFACTURER + " " + Build.DEVICE;
        Log.d("SessionLoader", "Device identifier: " + deviceIdentifier);

        // Reference to the user's login info in the Firebase database
        DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("users").child(userEmail);

        Log.d("SessionLoader", "Firebase path: " + userRef.toString());

        // Fetch login information for the user
        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                sessionCardContainer.removeAllViews();

                Log.d("SessionLoader", "DataSnapshot: " + dataSnapshot.toString());

                if (dataSnapshot.exists()) {
                    // Look for the logininfo corresponding to the device identifier
                    for (DataSnapshot loginInfoSnapshot : dataSnapshot.getChildren()) {
                        if (loginInfoSnapshot.getKey().startsWith("logininfo_")) {
                            String deviceName = loginInfoSnapshot.child("deviceName").getValue(String.class);
                            if (deviceName != null && deviceName.equals(deviceIdentifier)) {
                                // Found the correct device, now fetch the sessions_added
                                DataSnapshot sessionsAddedSnapshot = loginInfoSnapshot.child("sessions_added");

                                if (sessionsAddedSnapshot.exists()) {
                                    int sessionNumber = 1;

                                    // Iterate over the sessions added for this device
                                    for (DataSnapshot sessionSnapshot : sessionsAddedSnapshot.getChildren()) {
                                        String sessionName = sessionSnapshot.child("session_name").getValue(String.class);
                                        Log.d("SessionLoader", "Session name: " + sessionName);

                                        if (sessionName != null && !sessionName.isEmpty()) {
                                            // Inflate the session card and populate it
                                            View sessionCard = LayoutInflater.from(sessionCardContainer.getContext()).inflate(R.layout.session_card, null);

                                            TextView sessionNumberTextView = sessionCard.findViewById(R.id.session_number);
                                            sessionNumberTextView.setText("Session " + sessionNumber);

                                            TextView sessionNameTextView = sessionCard.findViewById(R.id.session_name);
                                            sessionNameTextView.setText(sessionName);

                                            sessionCardContainer.addView(sessionCard);

                                            sessionNumber++;
                                        }
                                    }
                                } else {
                                    Log.d("SessionLoader", "No sessions added for this device.");
                                }
                                break;
                            }
                        }
                    }
                } else {
                    Log.d("SessionLoader", "No user data found.");
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.e("SessionLoader", "Failed to load sessions: " + databaseError.getMessage());
            }
        });
    }
}

package com.summersoft.heliocam.status;

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

        String userEmail = mAuth.getCurrentUser().getEmail().replace(".", "_");


        Log.d("SessionLoader", "User email formatted for Firebase path: " + userEmail);


        DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("users").child(userEmail).child("sessions");


        Log.d("SessionLoader", "Firebase path: " + userRef.toString());


        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {

                sessionCardContainer.removeAllViews();


                Log.d("SessionLoader", "DataSnapshot: " + dataSnapshot.toString());

                if (dataSnapshot.exists()) {
                    int sessionNumber = 1;


                    for (DataSnapshot sessionSnapshot : dataSnapshot.getChildren()) {

                        String sessionName = sessionSnapshot.child("session_name").getValue(String.class);


                        Log.d("SessionLoader", "Session name: " + sessionName);

                        if (sessionName != null && !sessionName.isEmpty()) {

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

                    Log.d("SessionLoader", "No sessions found.");
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.e("SessionLoader", "Failed to load sessions: " + databaseError.getMessage());
            }
        });
    }

}

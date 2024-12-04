package com.summersoft.heliocam.notifs;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ImageView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.summersoft.heliocam.R;

import java.util.ArrayList;
import java.util.List;

public class PopulateNotifs {

    private static final String TAG = "PopulateNotifs";
    private List<String> sessionKeys = new ArrayList<>();

    public void startPopulatingNotifs(Context context, ViewGroup notificationContainer) {
        fetchSessionKeys(new Callback() {
            @Override
            public void onSessionKeysFetched() {
                fetchNotificationsForSessions(context, notificationContainer);
            }
        });
    }

    private void fetchSessionKeys(Callback callback) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            String email = currentUser.getEmail();
            if (email != null) {
                String formattedEmail = email.replace(".", "_");

                DatabaseReference userRef = FirebaseDatabase.getInstance()
                        .getReference("users")
                        .child(formattedEmail);

                userRef.get().addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        DataSnapshot userSnapshot = task.getResult();
                        if (userSnapshot.exists()) {
                            String currentDeviceInfo = android.os.Build.MANUFACTURER + " " + android.os.Build.DEVICE;

                            for (DataSnapshot loginInfo : userSnapshot.getChildren()) {
                                String key = loginInfo.getKey();
                                if (key != null && key.startsWith("logininfo_")) {
                                    String deviceName = loginInfo.child("deviceName").getValue(String.class);
                                    if (currentDeviceInfo.equals(deviceName)) {
                                        DataSnapshot sessions = loginInfo.child("sessions_added");
                                        if (sessions.exists()) {
                                            for (DataSnapshot session : sessions.getChildren()) {
                                                String sessionKey = session.getKey();
                                                sessionKeys.add(sessionKey); // Add session key to the list
                                            }
                                        }
                                    }
                                }
                            }
                            // Once session keys are fetched, invoke the callback
                            callback.onSessionKeysFetched();
                        } else {
                            Log.w(TAG, "No data found for user.");
                        }
                    } else {
                        Log.e(TAG, "Failed to fetch data: ", task.getException());
                    }
                });
            }
        }
    }

    private void fetchNotificationsForSessions(Context context, ViewGroup notificationContainer) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            String email = currentUser.getEmail();
            if (email != null) {
                String formattedEmail = email.replace(".", "_");

                for (String sessionKey : sessionKeys) {
                    DatabaseReference sessionRef = FirebaseDatabase.getInstance()
                            .getReference("users")
                            .child(formattedEmail)
                            .child("sessions")
                            .child(sessionKey);

                    // Fetch session details (including session_name) and notifications
                    sessionRef.addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(DataSnapshot sessionSnapshot) {
                            if (sessionSnapshot.exists()) {
                                String sessionName = sessionSnapshot.child("session_name").getValue(String.class);
                                if (sessionName != null) {
                                    DatabaseReference notificationsRef = sessionRef.child("notifications");

                                    // Fetch notifications for the given session
                                    notificationsRef.addListenerForSingleValueEvent(new ValueEventListener() {
                                        @Override
                                        public void onDataChange(DataSnapshot dataSnapshot) {
                                            if (dataSnapshot.exists()) {
                                                for (DataSnapshot notificationSnapshot : dataSnapshot.getChildren()) {
                                                    String where = notificationSnapshot.child("session_name").getValue(String.class);
                                                    String reason = notificationSnapshot.child("reason").getValue(String.class);
                                                    String time = notificationSnapshot.child("time").getValue(String.class);
                                                    String date = notificationSnapshot.child("date").getValue(String.class);

                                                    if (reason != null && time != null && date != null) {
                                                        // Inflate notification card and populate it
                                                        View notificationCard = LayoutInflater.from(context)
                                                                .inflate(R.layout.notifications_card, notificationContainer, false);

                                                        TextView titleView = notificationCard.findViewById(R.id.notification_title);
                                                        TextView dateView = notificationCard.findViewById(R.id.notification_date);
                                                        TextView timeView = notificationCard.findViewById(R.id.notification_time);

                                                        // Set the data
                                                        titleView.setText(reason + "  at " + sessionName);
                                                        dateView.setText("Date: " + date);
                                                        timeView.setText("Time: " + time);

                                                        // Add the card to the container
                                                        notificationContainer.addView(notificationCard);
                                                    }
                                                }
                                            }
                                        }

                                        @Override
                                        public void onCancelled(DatabaseError databaseError) {
                                            Log.e(TAG, "Error fetching notifications: " + databaseError.getMessage());
                                        }
                                    });
                                }
                            }
                        }

                        @Override
                        public void onCancelled(DatabaseError databaseError) {
                            Log.e(TAG, "Error fetching session details: " + databaseError.getMessage());
                        }
                    });
                }
            }
        }
    }

    // Callback interface for when session keys are fetched
    interface Callback {
        void onSessionKeysFetched();
    }
}

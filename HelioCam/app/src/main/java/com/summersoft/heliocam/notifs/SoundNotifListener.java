package com.summersoft.heliocam.notifs;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class SoundNotifListener {

    private static final String TAG = "SoundNotifListener";
    private static final String CHANNEL_ID = "SoundNotifChannel";
    private static final String CHANNEL_NAME = "Sound Notifications";
    private List<String> sessionKeys = new ArrayList<>();

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
                            String currentDeviceInfo = Build.MANUFACTURER + " " + Build.DEVICE;

                            for (DataSnapshot loginInfo : userSnapshot.getChildren()) {
                                String key = loginInfo.getKey();
                                if (key != null && key.startsWith("logininfo_")) {
                                    String deviceName = loginInfo.child("deviceName").getValue(String.class);
                                    if (currentDeviceInfo.equals(deviceName)) {
                                        DataSnapshot sessions = loginInfo.child("sessions_added");
                                        if (sessions.exists()) {
                                            for (DataSnapshot session : sessions.getChildren()) {
                                                String sessionKey = session.getKey();
                                                Log.d(TAG, "Session Key: " + sessionKey);
                                                sessionKeys.add(sessionKey); // Add session key to the list
                                            }
                                        }
                                    }
                                }
                            }

                            // Once session keys are fetched, invoke the callback
                            callback.onSessionKeysFetched();
                        } else {
                            Log.w(TAG, "No data found for user: " + formattedEmail);
                        }
                    } else {
                        Log.e(TAG, "Failed to fetch data: ", task.getException());
                    }
                });
            } else {
                Log.e(TAG, "User email is null.");
            }
        } else {
            Log.e(TAG, "No user is logged in.");
        }
    }

    public void startListeningForNotifications(Context context) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            String email = currentUser.getEmail();
            if (email != null) {
                String formattedEmail = email.replace(".", "_");

                // Fetch session keys asynchronously and then start listening for notifications
                fetchSessionKeys(() -> {
                    // Now that sessionKeys are fetched, we can start listening for notifications
                    listenToSessions(context, formattedEmail);
                });
            } else {
                Log.e(TAG, "User email is null.");
            }
        } else {
            Log.e(TAG, "No user is logged in.");
        }
    }

    private void listenToSessions(Context context, String formattedEmail) {
        // Now that sessionKeys are populated, we can use them
        for (String sessionKey : sessionKeys) {
            listenToSessionNotifications(context, formattedEmail, sessionKey);
        }
    }

    private void listenToSessionNotifications(Context context, String formattedEmail, String sessionKey) {
        DatabaseReference sessionRef = FirebaseDatabase.getInstance()
                .getReference("users")
                .child(formattedEmail)
                .child("sessions")
                .child(sessionKey);

        // Fetch session details (including session_name)
        sessionRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot sessionSnapshot) {
                String sessionName = sessionSnapshot.child("session_name").getValue(String.class);

                if (sessionName != null) {
                    DatabaseReference notificationsRef = sessionRef.child("notifications");

                    // Attach a persistent listener for real-time notifications
                    notificationsRef.addChildEventListener(new com.google.firebase.database.ChildEventListener() {
                        @Override
                        public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
                            String reason = snapshot.child("reason").getValue(String.class);
                            String time = snapshot.child("time").getValue(String.class);
                            String date = snapshot.child("date").getValue(String.class);

                            if (reason != null && time != null && date != null) {
                                // Create the notification text in the desired format
                                String notificationText = reason + ", " + time + " at " + sessionName + " on " + date;
                                showNotification(context, sessionName, notificationText);

                                Log.d(TAG, "Notification added: " + notificationText);
                            }
                        }

                        @Override
                        public void onChildChanged(DataSnapshot snapshot, String previousChildName) {
                            // Handle changes, if needed
                        }

                        @Override
                        public void onChildRemoved(DataSnapshot snapshot) {
                            // Handle removal, if needed
                        }

                        @Override
                        public void onChildMoved(DataSnapshot snapshot, String previousChildName) {
                            // Handle move, if needed
                        }

                        @Override
                        public void onCancelled(DatabaseError error) {
                            Log.e(TAG, "Notification listener cancelled: " + error.getMessage());
                        }
                    });
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Log.e(TAG, "Error fetching session details: " + error.getMessage());
            }
        });
    }


    private void showNotification(Context context, String title, String message) {
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        // Create a notification channel for Android O and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH);
            notificationManager.createNotificationChannel(channel);
        }

        Notification notification = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(message)
                .setSmallIcon(android.R.drawable.ic_notification_overlay)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build();

        notificationManager.notify((int) System.currentTimeMillis(), notification);
    }

    // Callback interface for when session keys are fetched
    interface Callback {
        void onSessionKeysFetched();
    }
}

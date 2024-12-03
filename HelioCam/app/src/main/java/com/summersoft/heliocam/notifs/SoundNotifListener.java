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

import java.util.ArrayList;
import java.util.List;

public class SoundNotifListener {

    private static final String TAG = "SoundNotifListener";
    private static final String CHANNEL_ID = "SoundNotifChannel";
    private static final String CHANNEL_NAME = "Sound Notifications";
    private List<String> sessionKeys = new ArrayList<>();

    private void fetchSessionKeys() {
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
        fetchSessionKeys(); // Fetch session keys before proceeding

        if (currentUser != null) {
            String email = currentUser.getEmail();
            if (email != null) {
                String formattedEmail = email.replace(".", "_");

                // Use the session keys fetched earlier to listen for notifications
                DatabaseReference userRef = FirebaseDatabase.getInstance()
                        .getReference("users")
                        .child(formattedEmail)
                        .child("sessions");

                userRef.get().addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        // Iterate through all session keys fetched and listen to notifications for each session
                        for (String sessionKey : sessionKeys) {
                            listenToSessionNotifications(context, formattedEmail, sessionKey);
                        }
                    } else {
                        Log.e(TAG, "Failed to fetch sessions: ", task.getException());
                    }
                });
            } else {
                Log.e(TAG, "User email is null.");
            }
        } else {
            Log.e(TAG, "No user is logged in.");
        }
    }

    private void listenToSessionNotifications(Context context, String formattedEmail, String sessionKey) {
        DatabaseReference notificationsRef = FirebaseDatabase.getInstance()
                .getReference("users")
                .child(formattedEmail)
                .child("sessions")
                .child(sessionKey)
                .child("notifications");

        // Attach a persistent listener for real-time notifications
        notificationsRef.addChildEventListener(new com.google.firebase.database.ChildEventListener() {
            @Override
            public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
                String reason = snapshot.child("reason").getValue(String.class);
                String time = snapshot.child("time").getValue(String.class);
                String date = snapshot.child("date").getValue(String.class);

                if (reason != null && time != null && date != null) {
                    String notificationText = reason + ", " + time + " at " + sessionKey + " on " + date;
                    showNotification(context, sessionKey, notificationText);
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
}

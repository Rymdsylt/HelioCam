package com.summersoft.heliocam.notifs;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.lifecycle.LifecycleService;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class SoundNotifService extends LifecycleService {

    private static final String TAG = "SoundNotifService";
    private static final String CHANNEL_ID = "SoundNotifChannel";
    private static final String CHANNEL_NAME = "Sound Notifications";
    private static final String PREFS_NAME = "SoundNotifPrefs";
    private static final String PREF_KEY_OLD_NOTIFS = "old_notifs";
    private List<String> sessionKeys = new ArrayList<>();
    private SharedPreferences sharedPreferences;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Initialize SharedPreferences
        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        // Start the service as a foreground service with a persistent notification
        startForegroundService();

        // Start listening for notifications
        startListeningForNotifications();
        return super.onStartCommand(intent, flags, startId);
    }

    private void startForegroundService() {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        // Create a notification channel for Android O and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW);
            notificationManager.createNotificationChannel(channel);
        }

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("HELIOCAM")
                .setContentText("Listening for notifications.")
                .setSmallIcon(android.R.drawable.ic_notification_overlay)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();

        startForeground(1, notification);
    }

    private void startListeningForNotifications() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            String email = currentUser.getEmail();
            if (email != null) {
                String formattedEmail = email.replace(".", "_");

                // Fetch session keys asynchronously and then start listening for notifications
                fetchSessionKeys(() -> {
                    // Now that sessionKeys are fetched, we can start listening for notifications
                    listenToSessions(formattedEmail);
                });
            } else {
                Log.e(TAG, "User email is null.");
            }
        } else {
            Log.e(TAG, "No user is logged in.");
        }
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
                                                sessionKeys.add(sessionKey);
                                            }
                                        }
                                    }
                                }
                            }
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

    private void listenToSessions(String formattedEmail) {
        Set<String> oldNotifKeys = sharedPreferences.getStringSet(PREF_KEY_OLD_NOTIFS, null);

        // Only listen to new sessions and notifications
        for (String sessionKey : sessionKeys) {
            if (oldNotifKeys == null || !oldNotifKeys.contains(sessionKey)) {
                listenToSessionNotifications(formattedEmail, sessionKey);
            }
        }
    }

    private void listenToSessionNotifications(String formattedEmail, String sessionKey) {
        DatabaseReference sessionRef = FirebaseDatabase.getInstance()
                .getReference("users")
                .child(formattedEmail)
                .child("sessions")
                .child(sessionKey);

        // Fetch session details (including session_name) and notifications
        sessionRef.addListenerForSingleValueEvent(new com.google.firebase.database.ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot sessionSnapshot) {
                if (sessionSnapshot.exists()) {
                    String sessionName = sessionSnapshot.child("session_name").getValue(String.class);

                    if (sessionName != null) {
                        DatabaseReference notificationsRef = sessionRef.child("notifications");

                        notificationsRef.addChildEventListener(new com.google.firebase.database.ChildEventListener() {
                            @Override
                            public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
                                String notificationKey = snapshot.getKey();
                                String reason = snapshot.child("reason").getValue(String.class);
                                String time = snapshot.child("time").getValue(String.class);
                                String date = snapshot.child("date").getValue(String.class);                                if (notificationKey != null && reason != null && time != null && date != null) {
                                    // Check notification settings before showing notification
                                    boolean shouldShow = false;
                                    String reasonLower = reason.toLowerCase();
                                    
                                    if (reasonLower.contains("sound")) {
                                        shouldShow = com.summersoft.heliocam.ui.NotificationSettings.isSoundNotificationsEnabled(SoundNotifService.this);
                                        Log.d(TAG, "Sound notification - enabled: " + shouldShow);
                                    } else if (reasonLower.contains("person")) {
                                        shouldShow = com.summersoft.heliocam.ui.NotificationSettings.isPersonNotificationsEnabled(SoundNotifService.this);
                                        Log.d(TAG, "Person notification - enabled: " + shouldShow);
                                    } else {
                                        // For other types, default to checking all notifications
                                        shouldShow = com.summersoft.heliocam.ui.NotificationSettings.isNotificationTypeEnabled(SoundNotifService.this, "in_app");
                                        Log.d(TAG, "Other notification - enabled: " + shouldShow);
                                    }
                                    
                                    if (!shouldShow) {
                                        Log.d(TAG, "Notification disabled by settings, skipping: " + reason);
                                        return;
                                    }
                                    
                                    // Only show notifications that are not already in old notifications
                                    Set<String> oldNotifKeys = sharedPreferences.getStringSet(PREF_KEY_OLD_NOTIFS, null);
                                    if (oldNotifKeys == null || !oldNotifKeys.contains(notificationKey)) {
                                        // Format the notification text
                                        String notificationText = reason + ", " + time + " at " + sessionName + " on " + date;
                                        showNotification(notificationText, notificationKey);

                                        // Add this new notificationKey to SharedPreferences
                                        addToOldNotifications(notificationKey);
                                    }
                                }
                            }

                            @Override
                            public void onChildChanged(DataSnapshot snapshot, String previousChildName) {}
                            @Override
                            public void onChildRemoved(DataSnapshot snapshot) {}
                            @Override
                            public void onChildMoved(DataSnapshot snapshot, String previousChildName) {}
                            @Override
                            public void onCancelled(DatabaseError error) {
                                Log.e(TAG, "Notification listener cancelled: " + error.getMessage());
                            }
                        });
                    }
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.e(TAG, "Session listener cancelled: " + databaseError.getMessage());
            }
        });
    }

    private void showNotification(String message, String notificationKey) {
        // Get SharedPreferences to check for existing notifications
        Set<String> oldNotifKeys = sharedPreferences.getStringSet(PREF_KEY_OLD_NOTIFS, null);

        // If the notification key already exists in old_notifs, don't show the notification
        if (oldNotifKeys != null && oldNotifKeys.contains(notificationKey)) {
            Log.d(TAG, "Notification already shown, skipping: " + notificationKey);
            return;
        }

        // Create a secondary notification channel if it doesn't exist
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

            // Check if the channel already exists
            NotificationChannel secondaryChannel = notificationManager.getNotificationChannel("secondary_channel_id");
            if (secondaryChannel == null) {
                secondaryChannel = new NotificationChannel(
                        "secondary_channel_id", // Channel ID
                        "Secondary Notifications", // Channel Name
                        NotificationManager.IMPORTANCE_LOW // Set the importance as per your need
                );
                notificationManager.createNotificationChannel(secondaryChannel);
            }
        }

        // Proceed with showing the notification in the secondary channel
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        Notification notification = new NotificationCompat.Builder(this, "secondary_channel_id") // Use the secondary channel ID
                .setContentTitle("HelioCam")
                .setContentText(message)
                .setSmallIcon(android.R.drawable.ic_notification_overlay)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build();

        notificationManager.notify((int) System.currentTimeMillis(), notification);

        // After showing the notification, add the notificationKey to old_notifs to avoid showing it again
        addToOldNotifications(notificationKey);
    }



    private void addToOldNotifications(String notificationKey) {
        Set<String> oldNotifKeys = sharedPreferences.getStringSet(PREF_KEY_OLD_NOTIFS, null);
        if (oldNotifKeys != null) {
            oldNotifKeys.add(notificationKey);
        } else {
            oldNotifKeys = Set.of(notificationKey);
        }

        sharedPreferences.edit()
                .putStringSet(PREF_KEY_OLD_NOTIFS, oldNotifKeys)
                .apply();
    }

    interface Callback {
        void onSessionKeysFetched();
    }
}

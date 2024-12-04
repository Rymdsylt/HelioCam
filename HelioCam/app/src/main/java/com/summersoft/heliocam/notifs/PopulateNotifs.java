package com.summersoft.heliocam.notifs;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.summersoft.heliocam.R;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PopulateNotifs {

    private static final String TAG = "PopulateNotifs";
    private static final String PREFS_NAME = "NotificationPrefs";
    private static final String SHOWN_NOTIFICATIONS_KEY = "shown_notifications";
    private static final String FIRST_LAUNCH_KEY = "first_launch";

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
                                                sessionKeys.add(sessionKey);
                                            }
                                        }
                                    }
                                }
                            }
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

                SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                boolean isFirstLaunch = prefs.getBoolean(FIRST_LAUNCH_KEY, true);

                // Get the set of already shown notifications
                Set<String> shownNotifications = prefs.getStringSet(SHOWN_NOTIFICATIONS_KEY, new HashSet<>());
                // Get the set of deleted notifications
                Set<String> deletedNotifs = prefs.getStringSet("deleted_notifs", new HashSet<>());
                Set<String> allNotificationIds = new HashSet<>();

                for (String sessionKey : sessionKeys) {
                    DatabaseReference sessionRef = FirebaseDatabase.getInstance()
                            .getReference("users")
                            .child(formattedEmail)
                            .child("sessions")
                            .child(sessionKey);

                    sessionRef.addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(DataSnapshot sessionSnapshot) {
                            if (sessionSnapshot.exists()) {
                                String sessionName = sessionSnapshot.child("session_name").getValue(String.class);
                                if (sessionName != null) {
                                    DatabaseReference notificationsRef = sessionRef.child("notifications");

                                    notificationsRef.addListenerForSingleValueEvent(new ValueEventListener() {
                                        @Override
                                        public void onDataChange(DataSnapshot dataSnapshot) {
                                            if (dataSnapshot.exists()) {
                                                SharedPreferences.Editor editor = prefs.edit();
                                                boolean newNotificationFound = false;

                                                for (DataSnapshot notificationSnapshot : dataSnapshot.getChildren()) {
                                                    String notificationId = notificationSnapshot.getKey();
                                                    String reason = notificationSnapshot.child("reason").getValue(String.class);
                                                    String time = notificationSnapshot.child("time").getValue(String.class);
                                                    String date = notificationSnapshot.child("date").getValue(String.class);

                                                    if (notificationId != null && reason != null && time != null && date != null) {
                                                        allNotificationIds.add(notificationId);

                                                        // Skip this notification if it is in the "deleted_notifs" set
                                                        if (deletedNotifs.contains(notificationId)) {
                                                            continue;
                                                        }

                                                        // On first launch, save all notification IDs
                                                        if (isFirstLaunch) {
                                                            shownNotifications.add(notificationId);
                                                        }

                                                        // Only show notifications not already shown
                                                        if (!shownNotifications.contains(notificationId)) {
                                                            // Inflate notification card and populate it
                                                            View notificationCard = LayoutInflater.from(context)
                                                                    .inflate(R.layout.notifications_card, notificationContainer, false);

                                                            TextView titleView = notificationCard.findViewById(R.id.notification_title);
                                                            TextView dateView = notificationCard.findViewById(R.id.notification_date);
                                                            TextView timeView = notificationCard.findViewById(R.id.notification_time);
                                                            ImageView deleteButton = notificationCard.findViewById(R.id.delete_button);

                                                            titleView.setText(reason + " at " + sessionName);
                                                            dateView.setText("Date: " + date);
                                                            timeView.setText("Time: " + time);

                                                            // Set the delete button click listener
                                                            deleteButton.setOnClickListener(v -> onDeleteNotification(context, notificationId, notificationContainer));

                                                            notificationContainer.addView(notificationCard);
                                                            newNotificationFound = true;
                                                        }
                                                    }
                                                }

                                                if (newNotificationFound) {
                                                    editor.putStringSet(SHOWN_NOTIFICATIONS_KEY, shownNotifications);
                                                }

                                                if (isFirstLaunch) {
                                                    // Save all notification IDs during the first launch
                                                    editor.putStringSet(SHOWN_NOTIFICATIONS_KEY, allNotificationIds);
                                                    editor.putBoolean(FIRST_LAUNCH_KEY, false);
                                                }

                                                editor.apply();
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

    private void onDeleteNotification(Context context, String notificationId, ViewGroup notificationContainer) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        // Add the notificationId to "deleted_notifs"
        Set<String> deletedNotifs = prefs.getStringSet("deleted_notifs", new HashSet<>());
        deletedNotifs.add(notificationId);
        editor.putStringSet("deleted_notifs", deletedNotifs);
        editor.apply();

        // Optionally, you can log for debugging purposes
        Log.d(TAG, "Notification added to deleted_notifs: " + notificationId);

        // Refresh the notifications by clearing the container and fetching the notifications again
        notificationContainer.removeAllViews();
        fetchNotificationsForSessions(context, notificationContainer);
    }







    interface Callback {
        void onSessionKeysFetched();
    }
}

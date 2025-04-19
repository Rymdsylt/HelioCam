package com.summersoft.heliocam.notifs;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.summersoft.heliocam.R;
import com.summersoft.heliocam.ui.NotificationFragment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PopulateNotifs {

    private static final String TAG = "PopulateNotifs";
    private static final String PREFS_NAME = "NotificationPrefs";
    private static final String SHOWN_NOTIFICATIONS_KEY = "shown_notifications";
    private static final String DELETED_NOTIFICATIONS_KEY = "deleted_notifs";

    private List<String> sessionKeys = new ArrayList<>();
    // Keep track of fetched notifications to avoid duplicates

    private final Map<String, NotificationData> notificationMap = new HashMap<>();

    private static PopulateNotifs activeInstance;

    public void startPopulatingNotifs(Context context, ViewGroup notificationContainer) {
        // Remove padding from container
        notificationContainer.setPadding(0, 0, 0, 0);

        // Set the active instance to this one
        activeInstance = this;

        // Clear previous views
        notificationContainer.removeAllViews();

        // Clear previous notification data
        notificationMap.clear();

        // Show loading indicator if needed
        showLoadingState(context, notificationContainer, true);

        fetchSessionKeys(new Callback() {
            @Override
            public void onSessionKeysFetched() {
                if (sessionKeys.isEmpty()) {
                    // If no sessions found, show empty state
                    showEmptyState(context, notificationContainer);
                } else {
                    fetchNotificationsForSessions(context, notificationContainer);
                }
            }
        });


    }

    // Add this static method to get the active instance or create a new one
    public static PopulateNotifs getInstance() {
        if (activeInstance == null) {
            activeInstance = new PopulateNotifs();
        }
        return activeInstance;
    }

    private void showLoadingState(Context context, ViewGroup container, boolean isLoading) {
        if (isLoading) {
            // You can add a loading indicator here if needed
        } else {
            // Hide loading indicator
        }
    }

    private void showEmptyState(Context context, ViewGroup container) {
        showLoadingState(context, container, false);

        // Create an empty state view using the same approach
        View emptyView = LayoutInflater.from(context).inflate(R.layout.notifications_card, container, false);

        // Get the inner LinearLayout that has the proper styling
        LinearLayout innerLayout = (LinearLayout) ((ViewGroup) emptyView).getChildAt(0);

        // Remove it from the CardView parent
        ((ViewGroup) emptyView).removeView(innerLayout);

        // Set margins for the inner layout
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(8, 8, 8, 8);
        innerLayout.setLayoutParams(params);

        TextView titleView = innerLayout.findViewById(R.id.notification_title);
        TextView dateView = innerLayout.findViewById(R.id.notification_date);
        TextView timeView = innerLayout.findViewById(R.id.notification_time);

        titleView.setText("No Notifications");
        dateView.setText("You don't have any notifications yet");
        timeView.setText("");

        // Hide delete button for empty state
        innerLayout.findViewById(R.id.delete_button).setVisibility(View.GONE);

        container.addView(innerLayout);
    }

    private void fetchSessionKeys(Callback callback) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Log.w(TAG, "No user logged in");
            callback.onSessionKeysFetched();
            return;
        }

        String email = currentUser.getEmail();
        if (email == null) {
            Log.w(TAG, "User email is null");
            callback.onSessionKeysFetched();
            return;
        }

        String formattedEmail = email.replace(".", "_");
        DatabaseReference userRef = FirebaseDatabase.getInstance()
                .getReference("users")
                .child(formattedEmail);

        userRef.get().addOnCompleteListener(task -> {
            sessionKeys.clear(); // Clear previous session keys

            if (!task.isSuccessful()) {
                Log.e(TAG, "Failed to fetch data: ", task.getException());
                callback.onSessionKeysFetched();
                return;
            }

            DataSnapshot userSnapshot = task.getResult();
            if (!userSnapshot.exists()) {
                Log.w(TAG, "No data found for user.");
                callback.onSessionKeysFetched();
                return;
            }

            String currentDeviceInfo = android.os.Build.MANUFACTURER + " " + android.os.Build.DEVICE;

            // Fetch sessions from login info nodes
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

            // Fetch sessions directly from sessions node for this user (backup method)
            DataSnapshot sessionsNode = userSnapshot.child("sessions");
            if (sessionsNode.exists()) {
                for (DataSnapshot sessionSnap : sessionsNode.getChildren()) {
                    String sessionKey = sessionSnap.getKey();
                    if (sessionKey != null && !sessionKeys.contains(sessionKey)) {
                        sessionKeys.add(sessionKey);
                    }
                }
            }

            Log.d(TAG, "Fetched " + sessionKeys.size() + " session keys");
            callback.onSessionKeysFetched();
        });
    }

    private void fetchNotificationsForSessions(Context context, ViewGroup notificationContainer) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null || currentUser.getEmail() == null) {
            showEmptyState(context, notificationContainer);
            return;
        }

        String email = currentUser.getEmail();
        String formattedEmail = email.replace(".", "_");
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Set<String> deletedNotifs = prefs.getStringSet(DELETED_NOTIFICATIONS_KEY, new HashSet<>());

        Log.d(TAG, "Checking for notifications for user: " + formattedEmail);

        // Set pending requests counter: sessions + 1 for universal notifications
        final int[] pendingRequests = {sessionKeys.size() + 1};

        // ALWAYS check universal notifications first (regardless of sessions)
        DatabaseReference universalNotificationsRef = FirebaseDatabase.getInstance()
                .getReference("users")
                .child(formattedEmail)
                .child("universal_notifications");

        universalNotificationsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot universalSnapshot) {
                Log.d(TAG, "Universal notifications data received");
                if (universalSnapshot.exists()) {
                    int count = 0;
                    for (DataSnapshot notificationSnapshot : universalSnapshot.getChildren()) {
                        String notificationId = notificationSnapshot.getKey();
                        if (notificationId != null && !deletedNotifs.contains(notificationId)) {
                            String reason = notificationSnapshot.child("reason").getValue(String.class);
                            String time = notificationSnapshot.child("time").getValue(String.class);
                            String date = notificationSnapshot.child("date").getValue(String.class);

                            if (reason != null && time != null && date != null) {
                                NotificationData notification = new NotificationData(
                                        notificationId,
                                        reason,  // No session name needed
                                        date,
                                        time
                                );
                                notificationMap.put(notificationId, notification);
                                count++;
                            }
                        }
                    }
                    Log.d(TAG, "Found " + count + " universal notifications");
                } else {
                    Log.d(TAG, "No universal notifications found");
                }

                pendingRequests[0]--;
                if (pendingRequests[0] == 0) {
                    displayNotifications(context, notificationContainer);
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Log.e(TAG, "Error fetching universal notifications: " + error.getMessage());
                pendingRequests[0]--;
                if (pendingRequests[0] == 0) {
                    displayNotifications(context, notificationContainer);
                }
            }
        });

        // THEN also check session-specific notifications if any sessions exist
        if (!sessionKeys.isEmpty()) {
            for (String sessionKey : sessionKeys) {
                // Your existing code for session notifications
                DatabaseReference sessionRef = FirebaseDatabase.getInstance()
                        .getReference("users")
                        .child(formattedEmail)
                        .child("sessions")
                        .child(sessionKey);

                sessionRef.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot sessionSnapshot) {
                        // Your existing session notification processing code
                        String sessionName = sessionSnapshot.child("session_name").getValue(String.class);
                        if (sessionName != null) {
                            DataSnapshot notificationsNode = sessionSnapshot.child("notifications");
                            if (notificationsNode.exists()) {
                                for (DataSnapshot notificationSnapshot : notificationsNode.getChildren()) {
                                    // Process session notification (existing code)
                                    String notificationId = notificationSnapshot.getKey();
                                    if (notificationId != null && !deletedNotifs.contains(notificationId)) {
                                        String reason = notificationSnapshot.child("reason").getValue(String.class);
                                        String time = notificationSnapshot.child("time").getValue(String.class);
                                        String date = notificationSnapshot.child("date").getValue(String.class);

                                        if (reason != null && time != null && date != null) {
                                            NotificationData notification = new NotificationData(
                                                    notificationId,
                                                    reason + " at " + sessionName,
                                                    date,
                                                    time
                                            );
                                            notificationMap.put(notificationId, notification);
                                        }
                                    }
                                }
                            }
                        }

                        pendingRequests[0]--;
                        if (pendingRequests[0] == 0) {
                            displayNotifications(context, notificationContainer);
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {
                        Log.e(TAG, "Error fetching session details: " + databaseError.getMessage());
                        pendingRequests[0]--;
                        if (pendingRequests[0] == 0) {
                            displayNotifications(context, notificationContainer);
                        }
                    }
                });
            }
        } else {
            Log.d(TAG, "No sessions found, but still checking universal notifications");
        }
    }

    private void displayNotifications(Context context, ViewGroup notificationContainer) {
        showLoadingState(context, notificationContainer, false);
        notificationContainer.removeAllViews();

        if (notificationMap.isEmpty()) {
            showEmptyState(context, notificationContainer);
            return;
        }

        // Sort notifications by id (newest first since they're timestamp-based)
        List<NotificationData> sortedNotifications = new ArrayList<>(notificationMap.values());
        sortedNotifications.sort((n1, n2) -> n2.id.compareTo(n1.id));

        LayoutInflater inflater = LayoutInflater.from(context);

        for (NotificationData notification : sortedNotifications) {
            // Inflate the layout
            View notificationCard = inflater.inflate(R.layout.notifications_card, notificationContainer, false);

            // Get the inner LinearLayout that has the proper styling
            LinearLayout innerLayout = (LinearLayout) ((ViewGroup) notificationCard).getChildAt(0);

            // Remove it from the CardView parent
            ((ViewGroup) notificationCard).removeView(innerLayout);

            // Set margins for the inner layout
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            params.setMargins(8, 8, 8, 8);
            innerLayout.setLayoutParams(params);

            // Access views within the inner layout
            TextView titleView = innerLayout.findViewById(R.id.notification_title);
            TextView dateView = innerLayout.findViewById(R.id.notification_date);
            TextView timeView = innerLayout.findViewById(R.id.notification_time);
            ImageView deleteButton = innerLayout.findViewById(R.id.delete_button);

            // Set the data
            titleView.setText(notification.title);
            dateView.setText("Date: " + notification.date);
            timeView.setText("Time: " + notification.time);

            // Set delete button click listener
            deleteButton.setOnClickListener(v -> {
                // Remove this notification from view
                notificationContainer.removeView(innerLayout);

                // Add to deleted notifications list
                SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                Set<String> deletedNotifs = new HashSet<>(prefs.getStringSet(DELETED_NOTIFICATIONS_KEY, new HashSet<>()));
                deletedNotifs.add(notification.id);

                prefs.edit()
                        .putStringSet(DELETED_NOTIFICATIONS_KEY, deletedNotifs)
                        .apply();

                // Remove from our map
                notificationMap.remove(notification.id);

                // If no more notifications, show empty state
                if (notificationMap.isEmpty()) {
                    showEmptyState(context, notificationContainer);
                }
            });

            // Add the inner layout to the container
            notificationContainer.addView(innerLayout);
        }
    }


    // Add this method to PopulateNotifs class
    // In PopulateNotifs.java, modify the sendPasswordResetNotification method:
    public void sendPasswordResetNotification(Context context) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null || currentUser.getEmail() == null) {
            Log.w(TAG, "Cannot save notification: No user logged in");
            return;
        }

        String formattedEmail = currentUser.getEmail().replace(".", "_");
        String timeStamp = String.valueOf(System.currentTimeMillis());

        // Create date and time strings for the notification
        java.text.SimpleDateFormat dateFormat = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault());
        java.text.SimpleDateFormat timeFormat = new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault());
        java.util.Date now = new java.util.Date();
        String dateString = dateFormat.format(now);
        String timeString = timeFormat.format(now);

        // First, show toast notification
        Toast.makeText(context, "Password reset email sent", Toast.LENGTH_SHORT).show();

        // Then fetch session keys if needed
        if (sessionKeys.isEmpty()) {
            fetchSessionKeys(new Callback() {
                @Override
                public void onSessionKeysFetched() {
                    if (!sessionKeys.isEmpty()) {
                        String sessionKey = sessionKeys.get(0);
                        // Get session name if available
                        DatabaseReference sessionRef = FirebaseDatabase.getInstance()
                                .getReference("users")
                                .child(formattedEmail)
                                .child("sessions")
                                .child(sessionKey);

                        sessionRef.child("session_name").get().addOnCompleteListener(task -> {
                            String sessionName = "Default Session";
                            if (task.isSuccessful() && task.getResult().exists()) {
                                String fetchedName = task.getResult().getValue(String.class);
                                if (fetchedName != null) {
                                    sessionName = fetchedName;
                                }
                            }
                            savePasswordResetNotification(formattedEmail, sessionKey, timeStamp, dateString, timeString, sessionName);
                        });
                    }
                }
            });
        } else {
            // We already have session keys, use the first one
            String sessionKey = sessionKeys.get(0);

            // Get session name if available
            DatabaseReference sessionRef = FirebaseDatabase.getInstance()
                    .getReference("users")
                    .child(formattedEmail)
                    .child("sessions")
                    .child(sessionKey);

            sessionRef.child("session_name").get().addOnCompleteListener(task -> {
                String sessionName = "Default Session";
                if (task.isSuccessful() && task.getResult().exists()) {
                    String fetchedName = task.getResult().getValue(String.class);
                    if (fetchedName != null) {
                        sessionName = fetchedName;
                    }
                }
                savePasswordResetNotification(formattedEmail, sessionKey, timeStamp, dateString, timeString, sessionName);
            });
        }
    }

    // In PopulateNotifs.java, modify the savePasswordResetNotification method:
    private void savePasswordResetNotification(String formattedEmail, String sessionKey, String timeStamp,
                                               String dateString, String timeString, String sessionName) {
        // Save to both places - universal and session-specific

        // 1. Save to universal notifications
        DatabaseReference universalNotifRef = FirebaseDatabase.getInstance()
                .getReference("users")
                .child(formattedEmail)
                .child("universal_notifications")
                .child(timeStamp);

        Map<String, Object> notificationValues = new HashMap<>();
        notificationValues.put("reason", "Password reset email sent");
        notificationValues.put("date", dateString);
        notificationValues.put("time", timeString);

        universalNotifRef.setValue(notificationValues)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Universal notification saved successfully");
                    // Update local notification map with the new notification
                    NotificationData newNotification = new NotificationData(
                            timeStamp,
                            "Password reset email sent",
                            dateString,
                            timeString
                    );
                    notificationMap.put(timeStamp, newNotification);

                    // Refresh UI if needed
                    refreshNotificationUI();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to save universal notification", e);
                });

        // 2. Also save to session-specific location if we have a valid session key
        if (sessionKey != null && !sessionKey.isEmpty()) {
            // Your existing code for saving session-specific notifications
            // ...
        }
    }

    // Helper method to refresh UI
    private void refreshNotificationUI() {
        // If notification fragment is visible, refresh UI on main thread
        if (NotificationFragment.activeInstance != null) {
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                if (NotificationFragment.activeInstance.isVisible() &&
                        NotificationFragment.activeInstance.getView() != null) {

                    ViewGroup container = NotificationFragment.activeInstance.getView()
                            .findViewById(R.id.notifcation_card_container);

                    if (container != null && NotificationFragment.activeInstance.getContext() != null) {
                        // Force clear and repopulate the entire container
                        displayNotifications(NotificationFragment.activeInstance.getContext(), container);
                    }
                }
            });
        }
    }

    private static class NotificationData {
        String id;
        String title;
        String date;
        String time;

        NotificationData(String id, String title, String date, String time) {
            this.id = id;
            this.title = title;
            this.date = date;
            this.time = time;
        }
    }



    interface Callback {
        void onSessionKeysFetched();
    }


    public void debugFirebaseData(Context context) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null || currentUser.getEmail() == null) {
            Log.e(TAG, "Debug failed: No user logged in");
            return;
        }

        String formattedEmail = currentUser.getEmail().replace(".", "_");
        Log.d(TAG, "===== DEBUGGING FIREBASE NOTIFICATIONS =====");

        // Check universal notifications
        FirebaseDatabase.getInstance()
                .getReference("users")
                .child(formattedEmail)
                .child("universal_notifications")
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        DataSnapshot snapshot = task.getResult();
                        Log.d(TAG, "Universal notifications exist: " + snapshot.exists());
                        if (snapshot.exists()) {
                            Log.d(TAG, "Universal notification count: " + snapshot.getChildrenCount());
                            for (DataSnapshot notif : snapshot.getChildren()) {
                                Log.d(TAG, " - Notif ID: " + notif.getKey());
                                Log.d(TAG, "   Content: " + notif.getValue().toString());
                            }
                        }
                    } else {
                        Log.e(TAG, "Failed to check universal notifications", task.getException());
                    }
                });

        // Check if we have any sessions to look through
        Log.d(TAG, "Session keys count: " + sessionKeys.size());
        Log.d(TAG, "Session keys: " + sessionKeys.toString());

        // Check deleted notifications
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Set<String> deletedNotifs = prefs.getStringSet(DELETED_NOTIFICATIONS_KEY, new HashSet<>());
        Log.d(TAG, "Deleted notifications count: " + deletedNotifs.size());
        Log.d(TAG, "Deleted notifications: " + deletedNotifs.toString());
    }
}
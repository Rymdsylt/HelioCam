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

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.summersoft.heliocam.R;

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

    public void startPopulatingNotifs(Context context, ViewGroup notificationContainer) {
        // Remove padding from container
        notificationContainer.setPadding(0, 0, 0, 0);

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
        if (currentUser == null) {
            showEmptyState(context, notificationContainer);
            return;
        }

        String email = currentUser.getEmail();
        if (email == null) {
            showEmptyState(context, notificationContainer);
            return;
        }

        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Set<String> deletedNotifs = prefs.getStringSet(DELETED_NOTIFICATIONS_KEY, new HashSet<>());

        String formattedEmail = email.replace(".", "_");
        final int[] pendingRequests = {sessionKeys.size()};

        if (sessionKeys.isEmpty()) {
            showEmptyState(context, notificationContainer);
            return;
        }

        for (String sessionKey : sessionKeys) {
            DatabaseReference sessionRef = FirebaseDatabase.getInstance()
                    .getReference("users")
                    .child(formattedEmail)
                    .child("sessions")
                    .child(sessionKey);

            sessionRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot sessionSnapshot) {
                    String sessionName = sessionSnapshot.child("session_name").getValue(String.class);
                    if (sessionName != null) {
                        DataSnapshot notificationsNode = sessionSnapshot.child("notifications");
                        if (notificationsNode.exists()) {
                            for (DataSnapshot notificationSnapshot : notificationsNode.getChildren()) {
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
}
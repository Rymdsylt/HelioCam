package com.summersoft.heliocam.notifs;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.summersoft.heliocam.R;
import com.summersoft.heliocam.ui.NotificationFragment;
import com.summersoft.heliocam.ui.NotificationSettings;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
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
        // Null check at the beginning
        if (context == null || notificationContainer == null) {
            Log.e(TAG, "Cannot populate notifications with null context or container");
            return;
        }
        
        // Remove padding from container
        notificationContainer.setPadding(0, 0, 0, 0);

        // Set the active instance to this one
        activeInstance = this;

        // Clear previous views
        notificationContainer.removeAllViews();

        // Clear previous notification data
        notificationMap.clear();

        // Show loading indicator
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
        // First clear the container
        container.removeAllViews();
        
        // Inflate the empty notifications layout
        View emptyView = LayoutInflater.from(context).inflate(
                R.layout.empty_notifications, container, false);
        
        // Add it to the container
        container.addView(emptyView);
        
        // Hide the clear all button since there's nothing to clear
        if (context instanceof Activity) {
            View clearAllButton = ((Activity) context).findViewById(R.id.clear_all_button);
            if (clearAllButton != null) {
                clearAllButton.setVisibility(View.GONE);
            }
        }
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

        // ALWAYS check universal notifications first
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

        // THEN check session-specific notifications
        if (!sessionKeys.isEmpty()) {
            for (String sessionKey : sessionKeys) {
                Log.d(TAG, "Checking session: " + sessionKey);
                
                // Get session reference
                DatabaseReference sessionRef = FirebaseDatabase.getInstance()
                        .getReference("users")
                        .child(formattedEmail)
                        .child("sessions")
                        .child(sessionKey);

                sessionRef.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot sessionSnapshot) {
                        if (!sessionSnapshot.exists()) {
                            pendingRequests[0]--;
                            if (pendingRequests[0] == 0) {
                                displayNotifications(context, notificationContainer);
                            }
                            return;
                        }

                        String sessionName = sessionSnapshot.child("session_name").getValue(String.class);
                        if (sessionName == null) sessionName = "Unknown Session";

                        // ONLY check the notifications path that web app uses
                        DataSnapshot notifications = sessionSnapshot.child("notifications");
                        if (notifications.exists()) {
                            Log.d(TAG, "Found notifications for session: " + sessionName + ", count: " + notifications.getChildrenCount());
                            
                            for (DataSnapshot notificationSnapshot : notifications.getChildren()) {
                                String notificationId = notificationSnapshot.getKey();
                                if (notificationId != null && !deletedNotifs.contains(notificationId)) {
                                    // Process these as enhanced notifications
                                    processSessionNotification(notificationId, notificationSnapshot, sessionName);
                                }
                            }
                        } else {
                            Log.d(TAG, "No notifications found for session: " + sessionName);
                        }

                        // Also check detection_events path
                        DataSnapshot detectionEvents = sessionSnapshot.child("detection_events");
                        if (detectionEvents.exists()) {
                            Log.d(TAG, "Found detection events for session: " + sessionName + ", count: " + detectionEvents.getChildrenCount());
                            
                            for (DataSnapshot eventSnapshot : detectionEvents.getChildren()) {
                                String eventId = eventSnapshot.getKey();
                                if (eventId != null && !deletedNotifs.contains(eventId)) {
                                    processDetectionEvent(eventId, eventSnapshot, sessionName);
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

    // Enhanced displayNotifications method
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
            ImageView notificationIcon = innerLayout.findViewById(R.id.notification_icon);
            View deleteButton = innerLayout.findViewById(R.id.delete_button);
            
            // Set icon based on notification type with enhanced detection
            if (notification.metadata != null && notification.metadata.containsKey("detectionType")) {
                String type = (String) notification.metadata.get("detectionType");
                if ("sound".equals(type)) {
                    if (notificationIcon != null) notificationIcon.setImageResource(R.drawable.ic_sound);
                } else if ("person".equals(type)) {
                    if (notificationIcon != null) notificationIcon.setImageResource(R.drawable.ic_person);
                }
            } else {
                // Fallback detection from title text
                String title = notification.title.toLowerCase();
                if (title.contains("sound")) {
                    if (notificationIcon != null) notificationIcon.setImageResource(R.drawable.ic_sound);
                } else if (title.contains("person")) {
                    if (notificationIcon != null) notificationIcon.setImageResource(R.drawable.ic_person);
                }
            }

            // Set the basic data - with null checks
            if (titleView != null) titleView.setText(notification.title);
            if (dateView != null) dateView.setText(notification.date);
            if (timeView != null) timeView.setText(notification.time);

            // Enhanced metadata display
            if (notification.metadata != null && !notification.metadata.isEmpty()) {
                // Show the metadata container (now it's a MaterialCardView)
                View metadataContainer = innerLayout.findViewById(R.id.metadata_container);
                if (metadataContainer != null) {
                    metadataContainer.setVisibility(View.GONE); // Start hidden, will be shown via Details button
                    
                    // Set session name with enhanced formatting
                    TextView sessionNameView = innerLayout.findViewById(R.id.metadata_session_name);
                    if (sessionNameView != null) {
                        if (notification.metadata.containsKey("sessionName")) {
                            String sessionName = notification.metadata.get("sessionName").toString();
                            sessionNameView.setText(sessionName);
                        } else {
                            sessionNameView.setText("Unknown Session");
                        }
                    }
                    
                    // Set camera number with enhanced formatting
                    TextView cameraNumberView = innerLayout.findViewById(R.id.metadata_camera_number);
                    if (cameraNumberView != null) {
                        if (notification.metadata.containsKey("cameraNumber")) {
                            String cameraInfo = notification.metadata.get("cameraNumber").toString();
                            // Add camera icon to text if it doesn't already contain "Camera"
                            if (!cameraInfo.toLowerCase().contains("camera")) {
                                cameraInfo = "Camera " + cameraInfo;
                            }
                            cameraNumberView.setText(cameraInfo);
                        } else {
                            cameraNumberView.setText("Unknown Camera");
                        }
                    }
                    
                    // Set device info with better formatting
                    TextView deviceInfoView = innerLayout.findViewById(R.id.metadata_device_info);
                    if (deviceInfoView != null) {
                        if (notification.metadata.containsKey("deviceInfo")) {
                            String deviceInfo = notification.metadata.get("deviceInfo").toString();
                            // Truncate very long device names
                            if (deviceInfo.length() > 25) {
                                deviceInfo = deviceInfo.substring(0, 22) + "...";
                            }
                            deviceInfoView.setText(deviceInfo);
                        } else {
                            deviceInfoView.setText("Unknown Device");
                        }
                    }
                    
                    // Set user email with proper container handling
                    TextView userEmailView = innerLayout.findViewById(R.id.metadata_user_email);
                    View emailContainer = innerLayout.findViewById(R.id.metadata_email_container);
                    if (userEmailView != null && emailContainer != null) {
                        if (notification.metadata.containsKey("userEmail")) {
                            String userEmail = notification.metadata.get("userEmail").toString();
                            userEmailView.setText(userEmail);
                            emailContainer.setVisibility(View.VISIBLE);
                        } else if (notification.metadata.containsKey("hostEmail")) {
                            String hostEmail = notification.metadata.get("hostEmail").toString();
                            userEmailView.setText(hostEmail);
                            emailContainer.setVisibility(View.VISIBLE);
                        } else {
                            emailContainer.setVisibility(View.GONE);
                        }
                    }
                    
                    // Set detection type with enhanced styling and additional info
                    TextView detectionTypeView = innerLayout.findViewById(R.id.metadata_detection_type);
                    if (detectionTypeView != null) {
                        if (notification.metadata.containsKey("detectionType")) {
                            String detectionType = notification.metadata.get("detectionType").toString();
                            String displayText = detectionType.substring(0, 1).toUpperCase() + detectionType.substring(1);
                            
                            // Add count or amplitude info if available
                            if ("person".equals(detectionType) && notification.metadata.containsKey("personCount")) {
                                Object count = notification.metadata.get("personCount");
                                displayText += " (" + count + ")";
                            } else if ("sound".equals(detectionType) && notification.metadata.containsKey("amplitude")) {
                                displayText += " (High)";
                            }
                            
                            detectionTypeView.setText(displayText);
                            
                            // Set background drawable based on detection type
                            if ("sound".equals(detectionType)) {
                                detectionTypeView.setBackgroundResource(R.drawable.rounded_badge_red);
                            } else if ("person".equals(detectionType)) {
                                detectionTypeView.setBackgroundResource(R.drawable.rounded_badge_green);
                            } else {
                                detectionTypeView.setBackgroundResource(R.drawable.rounded_badge_gray);
                            }
                        } else {
                            detectionTypeView.setText("Unknown");
                            detectionTypeView.setBackgroundResource(R.drawable.rounded_badge_gray);
                        }
                    }
                }
            } else {
                // Hide metadata container if no metadata available
                View metadataContainer = innerLayout.findViewById(R.id.metadata_container);
                if (metadataContainer != null) {
                    metadataContainer.setVisibility(View.GONE);
                }
            }

            // Set delete button click listener - handles different button types
            if (deleteButton != null) {
                deleteButton.setOnClickListener(v -> {
                    // Remove notification code (unchanged)
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
            }

            // Set up view details button with enhanced functionality
            View viewDetailsButton = innerLayout.findViewById(R.id.view_details_button);
            if (viewDetailsButton != null) {
                NotificationFragment fragment = null;
                
                // Safer way to find the NotificationFragment
                if (context instanceof Activity) {
                    // Try to find fragment from activity's fragment manager
                    androidx.fragment.app.FragmentManager fragmentManager = 
                        ((androidx.fragment.app.FragmentActivity) context).getSupportFragmentManager();
                    fragment = (NotificationFragment) fragmentManager.findFragmentByTag("notification_fragment");
                } else if (NotificationFragment.activeInstance != null) {
                    // Use the static reference if available
                    fragment = NotificationFragment.activeInstance;
                }
                
                if (fragment != null) {
                    fragment.setUpViewDetailsButton(innerLayout, viewDetailsButton);
                }
            }

            // Add the inner layout to the container
            notificationContainer.addView(innerLayout);
        }
    }


    // Add this method to PopulateNotifs class
    public void sendPasswordResetNotification(Context context) {
        // Always show toast regardless of notification settings
        Toast.makeText(context, "Password reset email sent", Toast.LENGTH_SHORT).show();

        // Check if email notifications are enabled
        if (!NotificationSettings.isEmailNotificationsEnabled(context)) {
            return;  // Skip notification creation but still show toast
        }

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
        Map<String, Object> metadata;  // Add metadata field

        NotificationData(String id, String title, String date, String time) {
            this(id, title, date, time, null);
        }
        
        NotificationData(String id, String title, String date, String time, Map<String, Object> metadata) {
            this.id = id;
            this.title = title;
            this.date = date;
            this.time = time;
            this.metadata = metadata;
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

    // Add helper method to process old-style notifications
    private void processNotifications(DataSnapshot notificationsNode, String sessionName, Set<String> deletedNotifs) {
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

    // Enhanced processDetectionEvent method with better logging and error handling
    private void processDetectionEvent(String eventId, DataSnapshot eventSnapshot, String sessionName) {
        try {
            Log.d(TAG, "Processing detection event: " + eventId + " for session: " + sessionName);
            
            // Log the raw data structure for debugging
            Log.d(TAG, "Event data: " + eventSnapshot.getValue());
            
            String type = eventSnapshot.child("type").getValue(String.class);
            Long timestamp = eventSnapshot.child("timestamp").getValue(Long.class);
            Integer cameraNumber = eventSnapshot.child("cameraNumber").getValue(Integer.class);
            String deviceName = eventSnapshot.child("deviceName").getValue(String.class);
            String email = eventSnapshot.child("email").getValue(String.class);

            Log.d(TAG, "Event details - Type: " + type + ", Timestamp: " + timestamp + ", Camera: " + cameraNumber);

            if (type == null || timestamp == null) {
                Log.w(TAG, "Invalid detection event data for ID: " + eventId + " - missing type or timestamp");
                return;
            }

            // Check if notification type is enabled
            if ("sound".equals(type) && !NotificationSettings.isSoundNotificationsEnabled(requireContext())) {
                Log.d(TAG, "Sound notifications disabled, skipping event: " + eventId);
                return;
            }
            
            if ("person".equals(type) && !NotificationSettings.isPersonNotificationsEnabled(requireContext())) {
                Log.d(TAG, "Person notifications disabled, skipping event: " + eventId);
                return;
            }

            // Create a formatted date and time string from timestamp
            Date date = new Date(timestamp);
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
            String dateString = dateFormat.format(date);
            String timeString = timeFormat.format(date);

            // Create detection-type specific title
            String title = type.equals("sound") ? "Sound detected" : "Person detected";
            title += " at " + sessionName;
            
            // Add camera info if available
            if (cameraNumber != null) {
                title += " (Camera " + cameraNumber + ")";
            }

            // Create metadata map for enhanced display
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("detectionType", type);
            metadata.put("sessionName", sessionName);
            if (cameraNumber != null) metadata.put("cameraNumber", "Camera " + cameraNumber);
            if (deviceName != null) metadata.put("deviceInfo", deviceName);
            if (email != null) metadata.put("userEmail", email);

            // Create enhanced notification with metadata
            NotificationData notification = new NotificationData(
                    eventId,
                    title,
                    dateString,
                    timeString,
                    metadata
            );
            notificationMap.put(eventId, notification);
            
            Log.d(TAG, "Successfully processed detection event: " + type + " at " + sessionName);
        } catch (Exception e) {
            Log.e(TAG, "Error processing detection event: " + eventId, e);
        }
    }

    // Add this helper method to safely get context
    private Context requireContext() {
        // This is a workaround since we're not in a Fragment context
        // You'll need to pass context to this method or store it as a field
        return activeInstance != null && NotificationFragment.activeInstance != null ? 
               NotificationFragment.activeInstance.getContext() : null;
    }

    /**
     * Returns a set of all notification IDs currently in the notification map
     * Used by the Clear All functionality in NotificationFragment
     */
    public Set<String> getNotificationIds() {
        return new HashSet<>(notificationMap.keySet());
    }

    public void debugDetectionEvents(Context context) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null || currentUser.getEmail() == null) {
            Log.e(TAG, "Debug failed: No user logged in");
            return;
        }

        String formattedEmail = currentUser.getEmail().replace(".", "_");
        Log.d(TAG, "===== DEBUGGING DETECTION EVENTS =====");
        Log.d(TAG, "User email: " + formattedEmail);
        Log.d(TAG, "Session keys: " + sessionKeys.toString());

        // Check each session for detection events
        for (String sessionKey : sessionKeys) {
            Log.d(TAG, "Checking session: " + sessionKey);
            
            FirebaseDatabase.getInstance()
                    .getReference("users")
                    .child(formattedEmail)
                    .child("sessions")
                    .child(sessionKey)
                    .get()
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            DataSnapshot sessionSnapshot = task.getResult();
                            Log.d(TAG, "Session " + sessionKey + " exists: " + sessionSnapshot.exists());
                            
                            if (sessionSnapshot.exists()) {
                                String sessionName = sessionSnapshot.child("session_name").getValue(String.class);
                                Log.d(TAG, "Session name: " + sessionName);
                                
                                DataSnapshot detectionEvents = sessionSnapshot.child("detection_events");
                                Log.d(TAG, "Detection events exist: " + detectionEvents.exists());
                                Log.d(TAG, "Detection events count: " + detectionEvents.getChildrenCount());
                                
                                for (DataSnapshot event : detectionEvents.getChildren()) {
                                    Log.d(TAG, "Event: " + event.getKey() + " = " + event.getValue());
                                }
                            }
                        } else {
                            Log.e(TAG, "Failed to fetch session: " + sessionKey, task.getException());
                        }
                    });
        }
    }

    // Add this new method to process session notifications with enhanced metadata
    private void processSessionNotification(String notificationId, DataSnapshot notificationSnapshot, String sessionName) {
        try {
            String reason = notificationSnapshot.child("reason").getValue(String.class);
            String date = notificationSnapshot.child("date").getValue(String.class);
            String time = notificationSnapshot.child("time").getValue(String.class);
            Integer cameraNumber = notificationSnapshot.child("cameraNumber").getValue(Integer.class);
            String deviceInfo = notificationSnapshot.child("deviceInfo").getValue(String.class);
            
            // Get metadata if it exists (new format)
            DataSnapshot metadataSnapshot = notificationSnapshot.child("metadata");
            Map<String, Object> metadata = new HashMap<>();
            
            if (metadataSnapshot.exists()) {
                // Extract metadata from the notification
                for (DataSnapshot metaChild : metadataSnapshot.getChildren()) {
                    String key = metaChild.getKey();
                    Object value = metaChild.getValue();
                    if (key != null && value != null) {
                        metadata.put(key, value);
                    }
                }
            } else {
                // Create metadata from individual fields (legacy support)
                if (reason != null) {
                    if (reason.toLowerCase().contains("sound")) {
                        metadata.put("detectionType", "sound");
                    } else if (reason.toLowerCase().contains("person")) {
                        metadata.put("detectionType", "person");
                    }
                }
                metadata.put("sessionName", sessionName);
                if (cameraNumber != null) {
                    metadata.put("cameraNumber", "Camera " + cameraNumber);
                }
                if (deviceInfo != null) {
                    metadata.put("deviceInfo", deviceInfo);
                }
            }
            
            if (reason == null || date == null || time == null) {
                Log.w(TAG, "Invalid notification data for ID: " + notificationId);
                return;
            }
            
            // Determine detection type from metadata or reason
            String detectionType = "unknown";
            if (metadata.containsKey("detectionType")) {
                detectionType = (String) metadata.get("detectionType");
            } else if (reason.toLowerCase().contains("sound")) {
                detectionType = "sound";
            } else if (reason.toLowerCase().contains("person") || reason.toLowerCase().contains("detected")) {
                detectionType = "person";
            }
            
            // Check if notification type is enabled
            Context context = getContext();
            if (context != null) {
                if ("sound".equals(detectionType) && !NotificationSettings.isSoundNotificationsEnabled(context)) {
                    Log.d(TAG, "Sound notifications disabled, skipping notification: " + notificationId);
                    return;
                }
                
                if ("person".equals(detectionType) && !NotificationSettings.isPersonNotificationsEnabled(context)) {
                    Log.d(TAG, "Person notifications disabled, skipping notification: " + notificationId);
                    return;
                }
            }
            
            // Create enhanced title matching web app format
            String title = reason;
            if (!reason.toLowerCase().contains(sessionName.toLowerCase())) {
                title += " at " + sessionName;
            }
            
            // Ensure we have the session name in metadata
            metadata.put("sessionName", sessionName);
            
            // Create notification with enhanced metadata
            NotificationData notification = new NotificationData(
                    notificationId,
                    title,
                    date,
                    time,
                    metadata
            );
            notificationMap.put(notificationId, notification);
            
            Log.d(TAG, "Successfully processed enhanced session notification: " + detectionType + " at " + sessionName);
        } catch (Exception e) {
            Log.e(TAG, "Error processing session notification: " + notificationId, e);
        }
    }

    // Enhanced universal notifications processing
    private void handleUniversalNotifications(DataSnapshot universalSnapshot) {
        if (!universalSnapshot.exists()) return;
        
        SharedPreferences prefs = getContext() != null ? 
            getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) : null;
        Set<String> deletedNotifs = prefs != null ? 
            prefs.getStringSet(DELETED_NOTIFICATIONS_KEY, new HashSet<>()) : new HashSet<>();
        
        for (DataSnapshot notificationSnapshot : universalSnapshot.getChildren()) {
            String notificationId = notificationSnapshot.getKey();
            if (notificationId != null && !deletedNotifs.contains(notificationId)) {
                String reason = notificationSnapshot.child("reason").getValue(String.class);
                String time = notificationSnapshot.child("time").getValue(String.class);
                String date = notificationSnapshot.child("date").getValue(String.class);
                
                // Get metadata if it exists (enhanced format)
                DataSnapshot metadataSnapshot = notificationSnapshot.child("metadata");
                Map<String, Object> metadata = new HashMap<>();
                
                if (metadataSnapshot.exists()) {
                    for (DataSnapshot metaChild : metadataSnapshot.getChildren()) {
                        String key = metaChild.getKey();
                        Object value = metaChild.getValue();
                        if (key != null && value != null) {
                            metadata.put(key, value);
                        }
                    }
                }

                if (reason != null && time != null && date != null) {
                    // Determine detection type for filtering
                    String detectionType = "unknown";
                    if (metadata.containsKey("detectionType")) {
                        detectionType = (String) metadata.get("detectionType");
                    } else if (reason.toLowerCase().contains("sound")) {
                        detectionType = "sound";
                    } else if (reason.toLowerCase().contains("person")) {
                        detectionType = "person";
                    }
                    
                    // Check if notifications of this type are enabled
                    Context context = getContext();
                    if (context != null) {
                        if ("sound".equals(detectionType) && !NotificationSettings.isSoundNotificationsEnabled(context)) {
                            continue;
                        }
                        if ("person".equals(detectionType) && !NotificationSettings.isPersonNotificationsEnabled(context)) {
                            continue;
                        }
                    }
                    
                    NotificationData notification = new NotificationData(
                            notificationId,
                            reason,
                            date,
                            time,
                            metadata.isEmpty() ? null : metadata
                    );
                    notificationMap.put(notificationId, notification);
                }
            }
        }
    }

    // Helper method to get context safely
    private Context getContext() {
        return activeInstance != null && NotificationFragment.activeInstance != null ? 
               NotificationFragment.activeInstance.getContext() : null;
    }
}
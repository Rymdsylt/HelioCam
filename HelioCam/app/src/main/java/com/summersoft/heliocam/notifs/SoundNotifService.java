package com.summersoft.heliocam.notifs;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;

public class SoundNotifService extends Service {

    private static final String TAG = "SoundNotifService";
    private SoundNotifListener soundNotifListener;

    @Override
    public void onCreate() {
        super.onCreate();
        soundNotifListener = new SoundNotifListener();
        Log.d(TAG, "Service created");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Start listening for notifications
        soundNotifListener.startListeningForNotifications(getApplicationContext());

        // Run the service in the background
        return START_STICKY;  // Ensure the service restarts if it gets killed
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Cleanup and stop listening when the service is destroyed
        Log.d(TAG, "Service destroyed");
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        // Return null since this service is not designed for binding
        return null;
    }
}

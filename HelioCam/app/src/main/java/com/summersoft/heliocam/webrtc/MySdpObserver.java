package com.summersoft.heliocam.webrtc;

import android.util.Log;

import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;

public class MySdpObserver implements SdpObserver {
    private static final String TAG = "MySdpObserver";
    private final String operation;
    private final SdpCallback callback;

    public interface SdpCallback {
        void onSuccess(SessionDescription sessionDescription);
        void onFailure(String error);
    }

    public MySdpObserver(String operation) {
        this(operation, null);
    }

    public MySdpObserver(String operation, SdpCallback callback) {
        this.operation = operation;
        this.callback = callback;
    }

    @Override
    public void onCreateSuccess(SessionDescription sessionDescription) {
        Log.d(TAG, operation + ": onCreateSuccess");
        if (callback != null) {
            callback.onSuccess(sessionDescription);
        }
    }

    @Override
    public void onSetSuccess() {
        Log.d(TAG, operation + ": onSetSuccess");
    }

    @Override
    public void onCreateFailure(String error) {
        Log.e(TAG, operation + ": onCreateFailure: " + error);
        if (callback != null) {
            callback.onFailure(error);
        }
    }

    @Override
    public void onSetFailure(String error) {
        Log.e(TAG, operation + ": onSetFailure: " + error);
        if (callback != null) {
            callback.onFailure(error);
        }
    }
}
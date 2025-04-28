package com.summersoft.heliocam.webrtc;

import android.util.Log;

import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;

public class SimpleSdpObserver implements SdpObserver {
    private static final String TAG = "SimpleSdpObserver";

    @Override
    public void onCreateSuccess(SessionDescription sessionDescription) {
        Log.d(TAG, "onCreateSuccess");
    }

    @Override
    public void onSetSuccess() {
        Log.d(TAG, "onSetSuccess");
    }

    @Override
    public void onCreateFailure(String s) {
        Log.e(TAG, "onCreateFailure: " + s);
    }

    @Override
    public void onSetFailure(String s) {
        Log.e(TAG, "onSetFailure: " + s);
    }
}
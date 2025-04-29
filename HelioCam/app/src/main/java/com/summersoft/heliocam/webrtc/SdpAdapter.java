package com.summersoft.heliocam.webrtc;

import android.util.Log;

import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;

public class SdpAdapter implements SdpObserver {
    private final String tag;

    public SdpAdapter(String tag) {
        this.tag = tag;
    }

    @Override
    public void onCreateSuccess(SessionDescription sessionDescription) {
        Log.d(tag, "SdpAdapter:onCreateSuccess");
    }

    @Override
    public void onSetSuccess() {
        Log.d(tag, "SdpAdapter:onSetSuccess");
    }

    @Override
    public void onCreateFailure(String s) {
        Log.e(tag, "SdpAdapter:onCreateFailure: " + s);
    }

    @Override
    public void onSetFailure(String s) {
        Log.e(tag, "SdpAdapter:onSetFailure: " + s);
    }
}
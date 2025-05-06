package com.summersoft.heliocam.webrtc_utils;

import android.util.Log;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;

public class SdpAdapter implements SdpObserver {
    private String tag = "SDPAdapter";


    public SdpAdapter(String tag) {
        this.tag = tag;
    }

    @Override
    public void onCreateSuccess(SessionDescription sessionDescription) {
        Log.d(tag, "SDP creation success: " + sessionDescription.description);
    }

    @Override
    public void onSetSuccess() {
        Log.d(tag, "SDP set success");
    }

    @Override
    public void onCreateFailure(String s) {
        Log.e(tag, "SDP creation failure: " + s);
    }

    @Override
    public void onSetFailure(String s) {
        Log.e(tag, "SDP set failure: " + s);
    }
}

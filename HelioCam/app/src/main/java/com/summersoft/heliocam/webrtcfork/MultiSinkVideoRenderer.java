package com.summersoft.heliocam.webrtcfork;

import org.webrtc.VideoFrame;
import org.webrtc.VideoSink;

import java.util.List;

public class MultiSinkVideoRenderer implements VideoSink {
    private final List<VideoSink> sinks;

    public MultiSinkVideoRenderer(List<VideoSink> sinks) {
        this.sinks = sinks;
    }

    @Override
    public void onFrame(VideoFrame frame) {
        // Forward the frame to all sinks
        for (VideoSink sink : sinks) {
            sink.onFrame(frame);
        }
    }
}

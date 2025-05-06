/*package com.summersoft.heliocam.recording;

import android.content.Context;
import android.media.MediaRecorder;
import android.os.Environment;
import android.util.Log;

import org.webrtc.EglBase;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoTrack;
import android.view.Surface;
import org.webrtc.EglBase;


import com.summersoft.heliocam.webrtc_utils.RTCHost;

import java.io.IOException;

public class RecordHost {

    private static final String TAG = "RecordHost";
    private VideoTrack videoTrackToRecord;
    private SurfaceTextureHelper surfaceTextureHelper;
    private SurfaceViewRenderer surfaceViewRenderer; // Declare SurfaceViewRenderer

    private MediaRecorder mediaRecorder;
    private boolean isRecording = false;
    private String outputFilePath;
    private Context context;
    public RTCHost rtcHost;
    private EglBase rootEglBase;

    public RecordHost(Context context) {
        this.context = context;
        rootEglBase = EglBase.create();

    }

    // Toggle recording state (start/stop)
    public void toggleRecording() {


        if (isRecording) {
            stopRecording();
        } else {
            startRecording(rtcHost);
        }
    }

    // Start recording
    private void startRecording(RTCHost rtcHost) {
        this.videoTrackToRecord = rtcHost.getVideoTrack();
        this.surfaceTextureHelper = rtcHost.getSurface();
        this.surfaceViewRenderer = rtcHost.getRenderer();
        if (videoTrackToRecord != null) {
            // Proceed with recording logic here, e.g., setting up a recorder for videoTrackToRecord
            Log.d("RecordHost", "VideoTrack received. Starting recording...");
            // Implement your recording logic here using videoTrackToRecord
        } else {
            Log.e("RecordHost", "Failed to get video track.");
        }
        if (isRecording) {
            Log.w(TAG, "Recording is already in progress.");
            return;
        }

        try {
            // Set up MediaRecorder
            mediaRecorder = new MediaRecorder();

            // Configure MediaRecorder settings
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.DEFAULT); // Optional for video-only
            mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
            mediaRecorder.setVideoEncodingBitRate(5 * 1024 * 1024); // 5 Mbps
            mediaRecorder.setVideoFrameRate(30); // 30 fps
            mediaRecorder.setVideoSize(1280, 720); // 720p resolution

            // Set output file path
            outputFilePath = Environment.getExternalStorageDirectory().getAbsolutePath() +
                    "/recorded_video.mp4";
            mediaRecorder.setOutputFile(outputFilePath);

            // Prepare MediaRecorder
            mediaRecorder.prepare();

            // Create a Surface to capture video from the VideoTrack

        ///    Surface surface = surfaceViewRenderer.getSurface();

            // Set the surface for the MediaRecorder to record video
            mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);

            // Start listening to the video track
           videoTrackToRecord.addSink(surfaceViewRenderer);

            // Start recording
            mediaRecorder.start();
            isRecording = true;
            Log.d(TAG, "Recording started. Saving to: " + outputFilePath);

        } catch (IOException e) {
            Log.e(TAG, "Failed to start recording: " + e.getMessage());
            stopRecording();
        }
    }

    // Stop recording
    private void stopRecording() {
        if (!isRecording || mediaRecorder == null || videoTrackToRecord ==null) {
            Log.w(TAG, "Recording is not in progress.");
            return;
        }

        try {
            mediaRecorder.stop();
            mediaRecorder.release();
            mediaRecorder = null;
            isRecording = false;

            Log.d(TAG, "Recording stopped. Saved to: " + outputFilePath);

        } catch (Exception e) {
            Log.e(TAG, "Error while stopping recording: " + e.getMessage());
        }
    }

    public boolean isRecording() {
        return isRecording;
    }

    public String getOutputFilePath() {
        return outputFilePath;
    }
}
*/
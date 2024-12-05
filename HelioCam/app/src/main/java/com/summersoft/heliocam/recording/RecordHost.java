package com.summersoft.heliocam.recording;

import android.content.Context;
import android.media.MediaRecorder;
import android.os.Environment;
import android.util.Log;

import org.webrtc.SurfaceTextureHelper;
import org.webrtc.VideoTrack;

import java.io.IOException;

public class RecordHost {
    private static final String TAG = "RecordHost";

    private MediaRecorder mediaRecorder;
    private boolean isRecording = false;
    private String outputFilePath;
    private Context context;

    public RecordHost(Context context) {
        this.context = context;
    }

    // Toggle recording state (start/stop)
    public void toggleRecording(VideoTrack videoTrack, SurfaceTextureHelper surfaceTextureHelper) {
        if (isRecording) {
            stopRecording();
        } else {
            startRecording(videoTrack, surfaceTextureHelper);
        }
    }

    // Start recording
    private void startRecording(VideoTrack videoTrack, SurfaceTextureHelper surfaceTextureHelper) {
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

            // Get the Surface from MediaRecorder
            surfaceTextureHelper.startListening((frame) -> {
                // Add custom frame processing here if needed
            });

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
        if (!isRecording || mediaRecorder == null) {
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

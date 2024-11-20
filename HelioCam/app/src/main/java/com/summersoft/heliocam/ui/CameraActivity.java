package com.summersoft.heliocam.ui;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraVideoCapturer;
import org.webrtc.EglBase;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SurfaceTextureHelper;

import com.summersoft.heliocam.databinding.ActivityCameraBinding;  // Import the generated binding class

public class CameraActivity extends AppCompatActivity {

    private ActivityCameraBinding binding;  // Use the generated ViewBinding class
    private SurfaceViewRenderer cameraView;
    private PeerConnectionFactory peerConnectionFactory;
    private CameraVideoCapturer videoCapturer;
    private VideoTrack videoTrack;
    private EglBase rootEglBase;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCameraBinding.inflate(getLayoutInflater());  // Inflate the layout using ViewBinding
        setContentView(binding.getRoot());  // Set the root view

        cameraView = binding.cameraView;  // Access views directly through ViewBinding

        // Initialize WebRTC
        initializePeerConnectionFactory();

        // Check for camera permissions
        checkCameraPermission();
    }

    private void initializePeerConnectionFactory() {
        PeerConnectionFactory.InitializationOptions options =
                PeerConnectionFactory.InitializationOptions.builder(this)
                        .createInitializationOptions();
        PeerConnectionFactory.initialize(options);
        peerConnectionFactory = PeerConnectionFactory.builder().createPeerConnectionFactory();

        // Set up the EglBase (OpenGL context)
        rootEglBase = EglBase.create();
        cameraView.init(rootEglBase.getEglBaseContext(), null);  // Initialize SurfaceViewRenderer
    }

    private void checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA},
                    100);
        } else {
            // If permission is granted, proceed with camera setup
            initializeCamera();
        }
    }

    private void initializeCamera() {
        // Use Camera2Enumerator for API level >= 21
        Camera2Enumerator cameraEnumerator = new Camera2Enumerator(this);
        String cameraName = cameraEnumerator.getDeviceNames()[0]; // Choose the first camera

        // Create a camera capturer
        videoCapturer = createCameraCapturer(cameraEnumerator, cameraName);
        if (videoCapturer != null) {
            // Initialize the video source and video track
            VideoSource videoSource = peerConnectionFactory.createVideoSource(videoCapturer.isScreencast());
            videoCapturer.initialize(SurfaceTextureHelper.create("CaptureThread", rootEglBase.getEglBaseContext()), getApplicationContext(), videoSource.getCapturerObserver());
            videoTrack = peerConnectionFactory.createVideoTrack("videoTrack", videoSource);

            // Bind the video track to the SurfaceViewRenderer
            videoTrack.addSink(cameraView);

            // Start capturing video
            videoCapturer.startCapture(1280, 720, 30);  // Capture at 1280x720 resolution, 30 fps
        }
    }

    private CameraVideoCapturer createCameraCapturer(Camera2Enumerator cameraEnumerator, String cameraName) {
        if (cameraEnumerator.isFrontFacing(cameraName)) {
            return cameraEnumerator.createCapturer(cameraName, null);
        }
        return null;  // Return null for now, can handle back camera similarly
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (videoCapturer != null) {
            try {
                // Attempt to stop the capture
                videoCapturer.stopCapture();
            } catch (InterruptedException e) {
                // Handle the exception (maybe log it or show a message)
                e.printStackTrace();  // You could log this to your logcat or handle it differently
            } finally {
                // Cleanup resources even if an exception occurs
                videoCapturer.dispose();
            }
        }
        peerConnectionFactory.dispose();
        rootEglBase.release();
    }

}

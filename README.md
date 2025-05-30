# 📹 HelioCam - Android Surveillance Application

[![Android](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com/)
[![WebRTC](https://img.shields.io/badge/WebRTC-Enabled-blue.svg)](https://webrtc.org/)
[![Firebase](https://img.shields.io/badge/Backend-Firebase-orange.svg)](https://firebase.google.com/)
[![Java](https://img.shields.io/badge/Language-Java-red.svg)](https://www.java.com/)

> A native Android surveillance application that enables real-time multi-camera monitoring with AI-powered detection capabilities using WebRTC technology.

## 🌟 Features

### 🎥 **Real-Time Video Streaming**
- **Multi-camera support** (up to 4 simultaneous feeds)
- **WebRTC-based** peer-to-peer video communication
- **HD quality streaming** with adaptive bitrate
- **Low latency** real-time transmission

### 🤖 **Smart Detection**
- **AI-powered person detection** with confidence scoring
- **Sound detection** with customizable thresholds
- **Real-time alerts** and notifications
- **Event logging** with timestamps

### 🔒 **Session Management**
- **6-digit join codes** for easy session access
- **Firebase authentication** for secure user management
- **Session-based architecture** with role-based access
- **Real-time participant tracking**

### 📱 **User Interface**
- **Modern Material Design** interface
- **Grid layout** for multiple camera views
- **Focus mode** for individual camera feeds
- **Intuitive controls** for camera operations

### 🎛️ **Advanced Controls**
- **Camera switching** (front/back)
- **Audio mute/unmute** functionality
- **Video recording** with timestamp overlay

## 🚀 Getting Started

### Prerequisites

- **Android Studio** Arctic Fox or later
- **Android SDK** API level 21 or higher
- **Firebase account** for backend services
- **Metered TURN/STUN** servers (for WebRTC)

### Installation

1. **Clone the repository**
   ```bash
   git clone [repository-url]
   cd HelioCam
   ```

2. **Open in Android Studio**
   - Import the project into Android Studio
   - Wait for Gradle sync to complete

3. **Configure Firebase**
   - Create a new Firebase project
   - Add your `google-services.json` to the `app/` directory
   - Enable Authentication and Realtime Database

4. **Configure TURN/STUN servers**
   - Update the server credentials in `RTCJoiner.java` and `RTCHost.java`
   - Replace with your preferred TURN/STUN server provider credentials

5. **Build and run**
   ```bash
   ./gradlew assembleDebug
   ```

## 🏗️ Architecture

### Core Components

```
📦 HelioCam
├── 🎥 WebRTC Utils
│   ├── RTCHost.java          # Host-side WebRTC management
│   ├── RTCJoiner.java        # Camera-side WebRTC management
│   ├── PeerConnectionAdapter.java
│   └── SdpAdapter.java
├── 🤖 Detection
│   ├── PersonDetection.java  # AI person detection
│   └── SoundDetection.java   # Audio level monitoring
├── 📱 Activities
│   ├── MainActivity.java     # Entry point
│   ├── CameraActivity.java   # Camera interface
│   ├── WatchSessionActivity.java # Host monitoring
│   └── SessionPreviewActivity.java
├── 🛠️ Utils
│   ├── PermissionManager.java
│   └── DetectionDirectoryManager.java
└── 🎨 Resources
    ├── layouts/
    ├── drawables/
    └── values/
```

### Technology Stack

| Component | Technology | Purpose |
|-----------|------------|---------|
| **Frontend** | Android SDK (Java) | Native Android application |
| **Real-time Communication** | WebRTC | Peer-to-peer video streaming |
| **Backend** | Firebase Realtime Database | User management & signaling |
| **Authentication** | Firebase Auth | Secure user authentication |
| **Video Processing** | Camera2 API | Camera access and control |
| **AI Detection** | TensorFlow Lite | Person detection capabilities |

## 🔧 Configuration

### Firebase Setup

1. **Realtime Database Rules**
   ```json
   {
     "rules": {
       "users": {
         "$uid": {
           ".read": "$uid === auth.uid",
           ".write": "$uid === auth.uid"
         }
       },
       "session_codes": {
         ".read": "auth != null",
         ".write": "auth != null"
       }
     }
   }
   ```

2. **Authentication Methods**
   - Enable Email/Password authentication
   - Configure sign-in methods as needed

### WebRTC Configuration

Update TURN/STUN server credentials in the WebRTC classes:

```java
// In RTCJoiner.java and RTCHost.java
private List<PeerConnection.IceServer> getIceServers() {
    return Arrays.asList(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("turn:your-turn-server:80")
            .setUsername("YOUR_USERNAME")
            .setPassword("YOUR_PASSWORD")
            .createIceServer()
        // Add additional TURN servers as needed
    );
}
```

## 📊 Usage

### As a Host (Monitor)

1. **Create Session**
   - Launch the app and sign in
   - Create a new monitoring session
   - Share the 6-digit code with camera devices

2. **Monitor Cameras**
   - View up to 4 camera feeds simultaneously
   - Receive real-time detection alerts
   - Focus on individual cameras as needed

3. **Manage Detection**
   - Configure detection sensitivity
   - Review detection history
   - Export detection logs

### As a Camera (Joiner)

1. **Join Session**
   - Enter the 6-digit session code
   - Grant camera and microphone permissions
   - Wait for host approval

2. **Stream Video**
   - Camera automatically starts streaming
   - Switch between front/back cameras
   - Mute/unmute audio as needed

3. **Detection Features**
   - AI person detection runs automatically
   - Sound detection monitors audio levels
   - Alerts sent to host in real-time

## 🛡️ Permissions

The app requires the following Android permissions:

| Permission | Purpose |
|------------|---------|
| `CAMERA` | Video capture and streaming |
| `RECORD_AUDIO` | Audio capture and sound detection |
| `INTERNET` | WebRTC communication |
| `ACCESS_NETWORK_STATE` | Network connectivity checks |
| `WRITE_EXTERNAL_STORAGE` | Video recording storage |

## 🔍 Detection Features

### Person Detection
- **AI-powered detection** using optimized models
- **Confidence scoring** for accuracy
- **Real-time processing** of video frames
- **Customizable sensitivity** settings

### Sound Detection
- **Audio level monitoring** with threshold detection
- **Background noise filtering** for accuracy
- **Amplitude-based triggering** for alerts
- **Configurable sensitivity** levels

## 📈 Performance

### Optimizations
- **Adaptive video quality** based on network conditions
- **Efficient memory management** with proper resource disposal
- **Background processing** for detection algorithms
- **Battery optimization** with power-saving modes

### System Requirements
- **Minimum Android version**: API 21 (Android 5.0)
- **Recommended RAM**: 2GB or higher
- **Storage**: 100MB for app + additional for recordings
- **Network**: Stable internet connection for WebRTC

## 🤝 Contributing

We welcome contributions! Please see our [Contributing Guidelines](CONTRIBUTING.md) for details.

### Development Setup

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add some amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

### Code Style

- Follow [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html)
- Use meaningful variable and method names
- Add JavaDoc comments for public methods
- Write unit tests for new features

## 🐛 Known Issues

- [ ] Occasional WebRTC connection timeouts on poor networks
- [ ] Person detection accuracy varies with lighting conditions
- [ ] High battery usage during extended sessions

## 🙏 Acknowledgments

- **WebRTC** team for the excellent real-time communication framework
- **Firebase** for reliable backend services and authentication
- **TensorFlow Lite** team for mobile AI/ML capabilities
- **Android** development community for resources and support

---

<div align="center">

**HelioCam Android Surveillance Application**

*Real-time monitoring with AI-powered detection* 🔒📹

</div>

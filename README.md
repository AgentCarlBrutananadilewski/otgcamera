# Auspex

A modern, open-source USB OTG camera app for Android. No ads, no tracking, no sketchy permissions.

## Why

Every OTG camera app on the Play Store is either:
- Built on ancient APIs (Android 6-era)
- From unknown developers with questionable privacy practices
- Choked with interstitial and rewarded ads
- Missing basic manual controls
- Prone to crashing on modern Android versions (14+)

This project replaces that mess with a clean Kotlin + Compose app using modern Android APIs and build standards.

## Architecture

**Hybrid backend approach:**

1. **Camera2 API** (primary) — Used when the OEM has enabled external camera support (`LENS_FACING_EXTERNAL`). Clean, official, no native code needed.
2. **UVCCamera** (fallback) — Direct USB access via [alexey-pelykh/UVCCamera](https://github.com/alexey-pelykh/UVCCamera). Works on any USB OTG-capable device regardless of OEM camera support.

The UI layer is completely unaware of which backend is active — both implement the same `CameraInterface`.

```
┌─────────────────────────────┐
│       Compose UI Layer      │
└──────────┬──────────────────┘
           │ CameraInterface
     ┌─────┴─────┐
     ▼           ▼
┌─────────┐ ┌────────────┐
│Camera2  │ │UVCCamera   │
│Backend  │ │(JNI/V4L2)  │
└─────────┘ └────────────┘
```

## Tech Stack (2026 Standards)

- **Language:** Kotlin 2.3.10
- **Build System:** Gradle 9.6.1 + AGP 9.3.0 (Built-in Kotlin)
- **UI:** Jetpack Compose (2026.06.01 BOM) + Material 3 (Edge-to-Edge)
- **DI:** Hilt 2.60.1
- **Camera:** Camera2 API (primary), UVCCamera library (fallback)
- **Video:** MediaCodec (H.264) + MediaMuxer
- **Target SDK:** 37 (Android 17)
- **Min SDK:** 24 (Android 7.0)

## Key Features

- **Live Camera Preview**
- **Feed Capture** : Photo capture (JPEG), Video recording (H.264 MP4)
- **Manual Controls**: Fine-grained control over exposure, gain, white balance, focus, and image processing (brightness/contrast/etc).
- **Robust Hot-Plugging**: Real-time USB device detection
- **Diagnostics**: Built-in runtime event logger for troubleshooting hardware communication issues.
- **Modern Permissions**: Compliant with Android 13+ Notification permissions and Android 14+ Foreground Service types.

## Project Structure

```
app/src/main/kotlin/com/toyrobotworkshop/auspex/
├── AuspexApp.kt              # Application + Hilt
├── di/                       # Dependency injection modules
├── camera/                   # Camera abstraction layer
│   ├── CameraInterface.kt    # Unified interface
│   ├── CameraManager.kt      # Backend detection/factory
│   ├── camera2/              # Camera2 API backend
│   └── uvc/                  # UVCCamera JNI backend
├── ui/                       # Compose UI
│   ├── theme/                # Material 3 theme (dark/light/dynamic)
│   ├── main/                 # Screens (Camera, NoDevice, PreviewView)
│   ├── settings/             # Settings + diagnostics screen
│   └── navigation/           # Nav graph with auto-recovery logic
└── util/                     # Utilities
    ├── FileSaver.kt          # Cache-dir file output
    ├── UsbReceiver.kt        # USB plug/unplug detection
    ├── DiagnosticLogger.kt   # Runtime event logging
    └── RecordingService.kt   # Background recording service (Android 14+ compliant)
```

## License

Apache 2.0

# Auspex

A modern, open-source USB OTG camera app for Android. No ads, no tracking, no sketchy permissions.

## Why

Every OTG camera app on the Play Store is either:
- Built on ancient APIs (Android 6-era)
- From unknown developers with questionable privacy practices
- Choked with interstitial and rewarded ads
- Missing basic manual controls

This project replaces that mess with a clean Kotlin + Compose app using modern Android APIs.

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

## Tech Stack

- **Language:** Kotlin
- **UI:** Jetpack Compose + Material 3 (dynamic color support)
- **DI:** Hilt
- **Camera:** Camera2 API (primary), UVCCamera library (fallback)
- **Video:** MediaCodec (H.264) + MediaMuxer
- **Icons:** Material Icons Extended (Rounded style)
- **Target SDK:** 35 (Android 15)
- **Min SDK:** 24 (Android 7.0)

## Features

- Live camera preview
- Photo capture (JPEG)
- Video recording (H.264 MP4)
- Resolution selector
- Manual controls: exposure, gain/ISO, white balance, focus mode, brightness, contrast, saturation, sharpness
- Real-time USB device detection
- Background recording via foreground service
- Settings screen with build info, device info, and runtime diagnostics

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
│   ├── main/                 # Screens (Camera, NoDevice)
│   ├── settings/             # Settings + diagnostics screen
│   └── navigation/           # Nav graph
└── util/                     # Utilities
    ├── FileSaver.kt          # Cache-dir file output
    ├── UsbReceiver.kt        # USB plug/unplug detection
    ├── DiagnosticLogger.kt   # Runtime event logging
    └── RecordingService.kt   # Background recording service
```

## License

Apache 2.0

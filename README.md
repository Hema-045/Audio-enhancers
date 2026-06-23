AI Hearing Assistant - MVP

This workspace contains a Flutter app (UI) and native Android Kotlin service (audio foreground service) to capture microphone PCM and play to the system output (Bluetooth headset when connected).

Quick notes:
- Flutter UI in `lib/` with Start/Stop buttons.
- Method channel: `ai_hearing_assistant/audio` implemented in `MainActivity.kt`.
- Foreground service: `AudioForegroundService` starts `AudioController` which reads `AudioRecord` and writes to `AudioTrack`.

Build & run (on Android device with a Bluetooth headset connected):

1. Install Flutter SDK and Android toolchain.
2. From project root run:

```bash
flutter pub get
flutter run -d <device>
```

Permissions:
- The app will request microphone permission via `permission_handler` on Start.
- Ensure Bluetooth headset is paired and connected before starting.

Limitations / Notes:
- This MVP streams raw PCM from mic to AudioTrack without processing.
- Latency depends on device and buffer sizes; further tuning may be required.

# Light 🔦

A tiny standalone Android flashlight gesture service inspired by Motorola's double-chop gesture. This is an independent app and does not modify Boltt Shift.

## Behaviour
- Launch once to grant Camera permission and start the foreground service.
- Perform two deliberate sharp movements to toggle the rear LED on/off.
- Use the persistent notification's **Stop** action to stop listening.
- No network, location, analytics, ads, or third-party runtime libraries.

## Privacy and permissions
The app uses Camera permission only to control the LED, accelerometer data only in memory for gesture recognition, and a special-use foreground service to keep detection available while backgrounded. It does not capture photos or record sensor history. Android displays an ongoing notification while the service runs.

## Build
GitHub Actions builds a debug APK on every push to `main`. Download `app-debug.apk` from the workflow's **Artifacts** section.

## Notes
Screen-off sensor delivery depends on the phone's sensor hardware and vendor power management. The service uses a wake-up accelerometer when available and falls back to the standard accelerometer. Real-device testing is required to tune gesture thresholds and confirm background behaviour.

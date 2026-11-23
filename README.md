# Light 🔦

A standalone Motorola-style double-chop flashlight gesture service for Android.

## Use
1. Install the APK.
2. Add **Gesture torch** from Android Quick Settings tile editor.
3. Tap the tile once. Grant Camera permission when prompted.
4. Make two deliberate, quick opposite chopping movements to toggle the rear flashlight.
5. Tap the tile again, or use **Stop** in the ongoing notification, to stop detection.

The app has no launcher/app-drawer icon and no normal settings screen. The Quick Settings tile is its control surface. Android displays an ongoing notification while motion detection is active.

## Gesture detection
- Uses the linear-acceleration sensor when available; otherwise uses a gravity-filtered accelerometer.
- Requires two opposite-direction peaks with a short release between them.
- Rejects pocket movement using the proximity sensor when available.
- Uses a cooldown to prevent repeated toggles.
- Sensor readings stay in memory and are not uploaded.

## Permissions
Camera permission is used only for Android's torch API. A foreground service keeps sensor detection active in the background. Android may restrict sensor delivery under vendor battery management, so real-device testing is required.

## Build
GitHub Actions builds the standalone APK named light.apk on every push to main. Download it from the workflow run's Artifacts section.

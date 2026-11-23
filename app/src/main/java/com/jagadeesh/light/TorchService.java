package com.jagadeesh.light;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;

public final class TorchService extends Service implements SensorEventListener {
    private static final String TAG = "LightGesture";
    private static final String CHANNEL = "light_gesture";
    private static final int NOTIFICATION_ID = 7;
    private static final float PEAK_THRESHOLD = 8.0f;
    private static final float RELEASE_THRESHOLD = 2.4f;
    private static final float OPPOSITE_DOT_THRESHOLD = -0.45f;
    private static final long MIN_PEAK_GAP_MS = 110;
    private static final long MAX_PEAK_GAP_MS = 560;
    private static final long COOLDOWN_MS = 700;

    private static volatile boolean running;
    private SensorManager sensors;
    private Sensor motionSensor;
    private boolean linearAcceleration;
    private CameraManager camera;
    private String cameraId;
    private boolean torchOn;
    private boolean armed = true;
    private boolean pocketed;
    private long firstPeakAt;
    private long lastTriggerAt;
    private long belowReleaseSince;
    private float firstX, firstY, firstZ;
    private float gravityX, gravityY, gravityZ;
    private boolean gravityReady;

    private final CameraManager.TorchCallback torchCallback = new CameraManager.TorchCallback() {
        @Override public void onTorchModeChanged(String id, boolean enabled) {
            if (cameraId == null || cameraId.equals(id)) torchOn = enabled;
        }
    };

    public static boolean isRunning() { return running; }

    @Override public void onCreate() {
        super.onCreate();
        running = true;
        createChannel();
        startForeground(NOTIFICATION_ID, buildNotification());
        camera = (CameraManager) getSystemService(CAMERA_SERVICE);
        sensors = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (camera != null) {
            cameraId = findRearFlashCamera();
            camera.registerTorchCallback(torchCallback, null);
        }
        if (sensors != null) {
            motionSensor = sensors.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION, true);
            if (motionSensor == null) motionSensor = sensors.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
            if (motionSensor != null) {
                linearAcceleration = true;
            } else {
                motionSensor = sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER, true);
                if (motionSensor == null) motionSensor = sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            }
            Sensor proximity = sensors.getDefaultSensor(Sensor.TYPE_PROXIMITY);
            if (motionSensor != null) sensors.registerListener(this, motionSensor, SensorManager.SENSOR_DELAY_GAME);
            if (proximity != null) sensors.registerListener(this, proximity, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "STOP".equals(intent.getAction())) {
            setTorch(false);
            stopSelf();
            return START_NOT_STICKY;
        }
        return START_STICKY;
    }

    @Override public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_PROXIMITY) {
            pocketed = event.values.length > 0 && event.values[0] < event.sensor.getMaximumRange();
            if (pocketed) resetGesture();
            return;
        }
        if (motionSensor == null || event.sensor.getType() != motionSensor.getType()
                || event.values.length < 3 || pocketed) return;

        float x, y, z;
        if (linearAcceleration) {
            x = event.values[0]; y = event.values[1]; z = event.values[2];
        } else {
            final float alpha = 0.82f;
            if (!gravityReady) {
                gravityX = event.values[0]; gravityY = event.values[1]; gravityZ = event.values[2];
                gravityReady = true;
                return;
            }
            gravityX = alpha * gravityX + (1f - alpha) * event.values[0];
            gravityY = alpha * gravityY + (1f - alpha) * event.values[1];
            gravityZ = alpha * gravityZ + (1f - alpha) * event.values[2];
            x = event.values[0] - gravityX;
            y = event.values[1] - gravityY;
            z = event.values[2] - gravityZ;
        }

        long now = SystemClock.elapsedRealtime();
        float magnitude = (float) Math.sqrt(x * x + y * y + z * z);
        if (magnitude < RELEASE_THRESHOLD) {
            if (!armed) {
                if (belowReleaseSince == 0) belowReleaseSince = now;
                if (now - belowReleaseSince >= 35) armed = true;
            }
            if (firstPeakAt != 0 && now - firstPeakAt > MAX_PEAK_GAP_MS) resetGesture();
            return;
        }
        belowReleaseSince = 0;
        if (magnitude < PEAK_THRESHOLD || !armed) return;

        armed = false;
        float nx = x / magnitude, ny = y / magnitude, nz = z / magnitude;
        if (firstPeakAt == 0 || now - firstPeakAt > MAX_PEAK_GAP_MS) {
            storeFirstPeak(now, nx, ny, nz);
            return;
        }

        long gap = now - firstPeakAt;
        float directionDot = nx * firstX + ny * firstY + nz * firstZ;
        if (gap >= MIN_PEAK_GAP_MS && gap <= MAX_PEAK_GAP_MS
                && directionDot <= OPPOSITE_DOT_THRESHOLD) {
            resetGesture();
            if (now - lastTriggerAt >= COOLDOWN_MS) {
                lastTriggerAt = now;
                toggleTorch();
            }
        } else if (gap > MAX_PEAK_GAP_MS || directionDot > OPPOSITE_DOT_THRESHOLD) {
            storeFirstPeak(now, nx, ny, nz);
        }
    }

    private String findRearFlashCamera() {
        try {
            for (String id : camera.getCameraIdList()) {
                CameraCharacteristics c = camera.getCameraCharacteristics(id);
                Boolean flash = c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                Integer facing = c.get(CameraCharacteristics.LENS_FACING);
                if (Boolean.TRUE.equals(flash)
                        && (facing == null || facing == CameraCharacteristics.LENS_FACING_BACK)) return id;
            }
        } catch (Exception e) {
            Log.w(TAG, "Unable to find a flash camera", e);
        }
        return null;
    }

    private void storeFirstPeak(long time, float x, float y, float z) {
        firstPeakAt = time; firstX = x; firstY = y; firstZ = z;
    }

    private void resetGesture() {
        firstPeakAt = 0; armed = true; belowReleaseSince = 0;
    }

    private void toggleTorch() { setTorch(!torchOn); }

    private void setTorch(boolean enabled) {
        if (camera == null || cameraId == null) return;
        try {
            camera.setTorchMode(cameraId, enabled);
            torchOn = enabled;
        } catch (Exception e) {
            Log.w(TAG, "Torch change failed", e);
        }
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL, "Gesture torch", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Ongoing notification for chop gesture detection");
            channel.setSound(null, null);
            channel.enableVibration(false);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification() {
        Intent stop = new Intent(this, TorchService.class).setAction("STOP");
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pending = PendingIntent.getService(this, 8, stop, flags);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        return builder.setSmallIcon(android.R.drawable.ic_lock_idle_charging)
                .setContentTitle("Light gesture active")
                .setContentText("Double-chop to toggle flashlight")
                .setOngoing(true).setOnlyAlertOnce(true)
                .addAction(android.R.drawable.ic_media_pause, "Stop", pending)
                .build();
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) { }

    @Override public void onDestroy() {
        running = false;
        if (sensors != null) sensors.unregisterListener(this);
        if (camera != null) {
            try { camera.unregisterTorchCallback(torchCallback); } catch (Exception ignored) { }
        }
        setTorch(false);
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
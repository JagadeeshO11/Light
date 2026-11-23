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
import android.hardware.camera2.CameraManager;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;

public final class TorchService extends Service implements SensorEventListener {
    private static final String CHANNEL = "light_gesture";
    private static final int NOTIFICATION_ID = 7;
    private static final long MIN_PEAK_GAP_MS = 220;
    private static final long MAX_PEAK_GAP_MS = 900;
    private static final long COOLDOWN_MS = 1400;
    private static final float PEAK_THRESHOLD = 13.0f;

    private SensorManager sensors;
    private Sensor accelerometer;
    private CameraManager camera;
    private String cameraId;
    private boolean torchOn;
    private boolean peakArmed = true;
    private long firstPeakAt;
    private long lastTriggerAt;
    private float gravityX, gravityY, gravityZ;
    private boolean gravityReady;

    @Override public void onCreate() {
        super.onCreate();
        camera = (CameraManager) getSystemService(CAMERA_SERVICE);
        sensors = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (sensors != null) {
            if (Build.VERSION.SDK_INT >= 21) accelerometer = sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER, true);
            if (accelerometer == null) accelerometer = sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }
        createChannel();
        startForeground(NOTIFICATION_ID, buildNotification());
        if (accelerometer != null) sensors.registerListener(this, accelerometer, 40_000);
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
        if (event.sensor.getType() != Sensor.TYPE_ACCELEROMETER || event.values.length < 3) return;
        final float alpha = 0.82f;
        if (!gravityReady) {
            gravityX = event.values[0]; gravityY = event.values[1]; gravityZ = event.values[2]; gravityReady = true; return;
        }
        gravityX = alpha * gravityX + (1f - alpha) * event.values[0];
        gravityY = alpha * gravityY + (1f - alpha) * event.values[1];
        gravityZ = alpha * gravityZ + (1f - alpha) * event.values[2];
        float x = event.values[0] - gravityX;
        float y = event.values[1] - gravityY;
        float z = event.values[2] - gravityZ;
        float linearMagnitude = (float) Math.sqrt(x*x + y*y + z*z);
        long now = SystemClock.elapsedRealtime();

        if (now - lastTriggerAt < COOLDOWN_MS) return;
        if (linearMagnitude < PEAK_THRESHOLD * 0.55f) peakArmed = true;
        if (linearMagnitude >= PEAK_THRESHOLD && peakArmed) {
            peakArmed = false;
            if (firstPeakAt == 0 || now - firstPeakAt > MAX_PEAK_GAP_MS) {
                firstPeakAt = now;
            } else {
                long gap = now - firstPeakAt;
                if (gap >= MIN_PEAK_GAP_MS && gap <= MAX_PEAK_GAP_MS) {
                    firstPeakAt = 0;
                    lastTriggerAt = now;
                    toggleTorch();
                } else if (gap > MAX_PEAK_GAP_MS) {
                    firstPeakAt = now;
                }
            }
        }
    }

    private void toggleTorch() { setTorch(!torchOn); }

    private void setTorch(boolean enabled) {
        if (camera == null) return;
        try {
            if (cameraId == null) {
                for (String id : camera.getCameraIdList()) {
                    android.hardware.camera2.CameraCharacteristics c = camera.getCameraCharacteristics(id);
                    Boolean flash = c.get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE);
                    Integer facing = c.get(android.hardware.camera2.CameraCharacteristics.LENS_FACING);
                    if (Boolean.TRUE.equals(flash) && (facing == null || facing == android.hardware.camera2.CameraCharacteristics.LENS_FACING_BACK)) { cameraId = id; break; }
                }
            }
            if (cameraId != null) { camera.setTorchMode(cameraId, enabled); torchOn = enabled; }
        } catch (Exception ignored) { }
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL, "Gesture torch", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Required notification while chop gesture detection is active");
            channel.setSound(null, null);
            channel.enableVibration(false);
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(channel);
        }
    }

    private Notification buildNotification() {
        Intent stop = new Intent(this, TorchService.class).setAction("STOP");
        PendingIntent pending = PendingIntent.getService(this, 8, stop, PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        return b.setSmallIcon(android.R.drawable.ic_lock_idle_charging).setContentTitle("Light gesture active").setContentText("Double-chop to toggle flashlight").setOngoing(true).setOnlyAlertOnce(true).addAction(android.R.drawable.ic_media_pause, "Stop", pending).build();
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) { }
    @Override public void onDestroy() {
        if (sensors != null) sensors.unregisterListener(this);
        setTorch(false);
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}

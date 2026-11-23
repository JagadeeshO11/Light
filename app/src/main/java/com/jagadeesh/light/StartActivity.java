package com.jagadeesh.light;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

public final class StartActivity extends Activity {
    private static final int REQUEST_PERMISSIONS = 41;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            if (Build.VERSION.SDK_INT >= 33) {
                requestPermissions(new String[]{Manifest.permission.CAMERA, Manifest.permission.POST_NOTIFICATIONS},
                        REQUEST_PERMISSIONS);
            } else {
                requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_PERMISSIONS);
            }
        } else {
            startTorchService();
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQUEST_PERMISSIONS) {
            boolean cameraGranted = false;
            for (int i = 0; i < permissions.length && i < results.length; i++) {
                if (Manifest.permission.CAMERA.equals(permissions[i])) {
                    cameraGranted = results[i] == PackageManager.PERMISSION_GRANTED;
                    break;
                }
            }
            if (cameraGranted) startTorchService();
            else finish();
        }
    }

    private void startTorchService() {
        Intent intent = new Intent(this, TorchService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent);
        else startService(intent);
        finish();
    }
}
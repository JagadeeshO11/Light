package com.jagadeesh.light;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

public final class StartActivity extends Activity {
    private static final int REQUEST_CAMERA = 41;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA);
        } else {
            startTorchService();
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQUEST_CAMERA && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) startTorchService();
        finish();
    }

    private void startTorchService() {
        Intent intent = new Intent(this, TorchService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent); else startService(intent);
        finish();
    }
}

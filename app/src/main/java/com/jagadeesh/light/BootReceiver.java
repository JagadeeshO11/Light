package com.jagadeesh.light;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

public final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        boolean enabled = context.getSharedPreferences("light_settings", Context.MODE_PRIVATE)
                .getBoolean("always_active", false);
        if (!enabled || context.checkSelfPermission(Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) return;
        try {
            Intent service = new Intent(context, TorchService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(service);
            else context.startService(service);
        } catch (Exception e) {
            Log.w("LightGesture", "Auto-start after reboot was blocked by Android", e);
        }
    }
}

package com.jagadeesh.light;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public final class AlwaysActiveReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (!"DISABLE_ALWAYS".equals(intent.getAction())) return;
        context.getSharedPreferences("light_settings", Context.MODE_PRIVATE)
                .edit().putBoolean("always_active", false).apply();
        Intent update = new Intent(context, TorchService.class).setAction("UPDATE_NOTIFICATION");
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(update);
            else context.startService(update);
        } catch (Exception ignored) { }
    }
}

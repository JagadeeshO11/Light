package com.jagadeesh.light;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

public final class GestureTileService extends TileService {
    @Override public void onStartListening() {
        super.onStartListening();
        updateTileState();
    }

    @Override public void onClick() {
        super.onClick();
        if (TorchService.isRunning()) {
            Intent stop = new Intent(this, TorchService.class).setAction("STOP");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(stop);
            else startService(stop);
            Tile tile = getQsTile();
            if (tile != null) {
                tile.setState(Tile.STATE_INACTIVE);
                tile.updateTile();
            }
        } else {
            Intent launch = new Intent(this, StartActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (Build.VERSION.SDK_INT >= 34) {
                PendingIntent pending = PendingIntent.getActivity(this, 41, launch,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                startActivityAndCollapse(pending);
            } else {
                startActivityAndCollapse(launch);
            }
        }
    }

    private void updateTileState() {
        Tile tile = getQsTile();
        if (tile != null) {
            tile.setState(TorchService.isRunning() ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
            tile.setLabel("Gesture torch");
            tile.updateTile();
        }
    }
}
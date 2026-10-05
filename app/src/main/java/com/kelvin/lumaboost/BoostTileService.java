package com.kelvin.lumaboost;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/** Bloco nas configurações rápidas: abre o Luma e já inicia a otimização completa. */
@TargetApi(Build.VERSION_CODES.N)
public final class BoostTileService extends TileService {
    @Override
    public void onStartListening() {
        Tile tile = getQsTile();
        if (tile == null) {
            return;
        }
        tile.setState(Tile.STATE_INACTIVE);
        tile.setLabel("Luma Boost");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            long last = Prefs.lastOptimize(this);
            tile.setSubtitle(last == 0L ? "Otimizar" : "Otimizado " + Format.ago(last));
        }
        tile.updateTile();
    }

    @Override
    @SuppressLint("StartActivityAndCollapseDeprecated")
    @SuppressWarnings("deprecation")
    public void onClick() {
        Intent intent = new Intent(this, MainActivity.class)
                .setAction(MainActivity.ACTION_BOOST)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 1, intent,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        } else {
            startActivityAndCollapse(intent);
        }
    }
}

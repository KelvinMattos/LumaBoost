package com.kelvin.lumaboost;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Religa o monitoramento ativo depois de reiniciar o aparelho ou atualizar o app. */
public final class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            if (Prefs.monitorEnabled(context)) {
                MonitorService.start(context);
            }
            if (Prefs.autoClean(context)) {
                AutoCleanJob.schedule(context, true);
            }
        }
    }
}

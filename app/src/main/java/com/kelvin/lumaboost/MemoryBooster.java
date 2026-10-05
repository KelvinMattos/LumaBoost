package com.kelvin.lumaboost;

import android.app.ActivityManager;
import android.app.usage.UsageStats;
import android.content.Context;
import android.os.Build;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Libera RAM finalizando processos em segundo plano de outros apps.
 *
 * Usa {@link ActivityManager#killBackgroundProcesses(String)}, que encerra apenas processos em cache
 * e serviços em segundo plano: nunca o app em primeiro plano, música tocando ou alarmes agendados.
 * A RAM liberada é medida antes e depois, sem estimativas.
 */
final class MemoryBooster {
    static final class Result {
        long availableBefore;
        long availableAfter;
        long totalRam;
        int appsChecked;
        final List<String> recentApps = new ArrayList<>();
        boolean limitedBySystem;

        long freed() {
            return Math.max(0L, availableAfter - availableBefore);
        }
    }

    private MemoryBooster() {
    }

    /** Android 14+ só permite que um app finalize os próprios processos. */
    static boolean canKillOtherApps() {
        return Build.VERSION.SDK_INT < 34;
    }

    static Result boost(Context context) {
        ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        Result result = new Result();
        ActivityManager.MemoryInfo before = DeviceStats.memoryInfo(context);
        result.availableBefore = DeviceStats.availableRam(context);
        result.totalRam = before.totalMem;
        result.limitedBySystem = !canKillOtherApps();

        Set<String> skip = AppCatalog.essentialPackages(context);
        skip.addAll(Prefs.protectedApps(context));
        long recentWindow = System.currentTimeMillis() - 12L * 60L * 60L * 1000L;
        Map<String, UsageStats> usage = AppCatalog.usage(context, recentWindow);

        for (AppCatalog.AppEntry app : AppCatalog.launchable(context)) {
            if (skip.contains(app.packageName)) {
                continue;
            }
            result.appsChecked++;
            activityManager.killBackgroundProcesses(app.packageName);
            UsageStats stats = usage.get(app.packageName);
            if (stats != null && stats.getLastTimeUsed() >= recentWindow) {
                result.recentApps.add(app.label);
            }
        }
        // Pacotes sem ícone (serviços de terceiros) que rodaram recentemente.
        for (String packageName : usage.keySet()) {
            if (!skip.contains(packageName)) {
                activityManager.killBackgroundProcesses(packageName);
            }
        }

        Runtime.getRuntime().gc();
        // O kernel leva alguns instantes para devolver as páginas dos processos encerrados.
        Tasks.sleep(1800L);
        result.availableAfter = DeviceStats.availableRam(context);
        return result;
    }
}

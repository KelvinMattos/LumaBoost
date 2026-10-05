package com.kelvin.lumaboost;

import android.app.usage.StorageStats;
import android.app.usage.StorageStatsManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.Process;
import android.os.storage.StorageManager;
import android.provider.Settings;
import android.provider.Telephony;
import android.telecom.TelecomManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Lista os apps instalados visíveis ao Luma, com dados de uso e armazenamento. */
final class AppCatalog {
    static final long DAY_MS = 24L * 60L * 60L * 1000L;

    static final class AppEntry {
        String packageName;
        String label;
        ApplicationInfo info;
        boolean system;
        boolean stopped;
        long installTime;
        long lastUsed;
        long foregroundMs;
        long appBytes = -1L;
        long dataBytes = -1L;
        long cacheBytes = -1L;

        long totalBytes() {
            return Math.max(0L, appBytes) + Math.max(0L, dataBytes);
        }
    }

    private AppCatalog() {
    }

    /** Apps com ícone na tela inicial (do celular ou da TV), exceto o próprio Luma. */
    static List<AppEntry> launchable(Context context) {
        PackageManager pm = context.getPackageManager();
        boolean fireTv = FireTv.is(context);
        List<ResolveInfo> resolved = new ArrayList<>(pm.queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0));
        // Apps de TV (HBO Max, por exemplo) costumam declarar só o launcher da TV.
        resolved.addAll(pm.queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0));
        Map<String, AppEntry> byPackage = new LinkedHashMap<>();
        for (ResolveInfo info : resolved) {
            ApplicationInfo appInfo = info.activityInfo.applicationInfo;
            String pkg = appInfo.packageName;
            if (pkg.equals(context.getPackageName()) || byPackage.containsKey(pkg)
                    || (fireTv && FireTv.isSystemService(appInfo))) {
                continue;
            }
            AppEntry entry = new AppEntry();
            entry.packageName = pkg;
            entry.info = appInfo;
            entry.label = String.valueOf(appInfo.loadLabel(pm));
            entry.system = (appInfo.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0;
            entry.stopped = (appInfo.flags & ApplicationInfo.FLAG_STOPPED) != 0;
            try {
                PackageInfo packageInfo = pm.getPackageInfo(pkg, 0);
                entry.installTime = packageInfo.firstInstallTime;
            } catch (PackageManager.NameNotFoundException ignored) {
                entry.installTime = 0L;
            }
            byPackage.put(pkg, entry);
        }
        return new ArrayList<>(byPackage.values());
    }

    /** Pacotes que nunca devem ser finalizados: launcher, teclado, telefone, SMS e serviços centrais. */
    static Set<String> essentialPackages(Context context) {
        Set<String> essential = new HashSet<>();
        essential.add(context.getPackageName());
        essential.add("android");
        essential.add("com.android.systemui");
        essential.add("com.android.phone");
        essential.add("com.android.settings");
        essential.add("com.google.android.gms");
        essential.add("com.google.android.gsf");
        essential.add("com.android.server.telecom");
        essential.add("com.amazon.tv.settings.v2");
        PackageManager pm = context.getPackageManager();
        ResolveInfo home = pm.resolveActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
                PackageManager.MATCH_DEFAULT_ONLY);
        if (home != null && home.activityInfo != null) {
            essential.add(home.activityInfo.packageName);
        }
        String ime = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
        if (ime != null) {
            ComponentName component = ComponentName.unflattenFromString(ime);
            if (component != null) {
                essential.add(component.getPackageName());
            }
        }
        try {
            TelecomManager telecom = (TelecomManager) context.getSystemService(Context.TELECOM_SERVICE);
            if (telecom != null && telecom.getDefaultDialerPackage() != null) {
                essential.add(telecom.getDefaultDialerPackage());
            }
        } catch (RuntimeException ignored) {
            // Alguns fabricantes bloqueiam essa consulta.
        }
        String sms = Telephony.Sms.getDefaultSmsPackage(context);
        if (sms != null) {
            essential.add(sms);
        }
        return essential;
    }

    /** Estatísticas de uso agregadas desde {@code sinceMs}. Vazio quando não há acesso de uso. */
    static Map<String, UsageStats> usage(Context context, long sinceMs) {
        if (!Permissions.hasUsageAccess(context)) {
            return Collections.emptyMap();
        }
        UsageStatsManager manager = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
        try {
            Map<String, UsageStats> map = manager.queryAndAggregateUsageStats(sinceMs, System.currentTimeMillis());
            return map == null ? Collections.<String, UsageStats>emptyMap() : map;
        } catch (RuntimeException exception) {
            return Collections.emptyMap();
        }
    }

    static void attachUsage(Context context, List<AppEntry> apps, long sinceMs) {
        Map<String, UsageStats> usage = usage(context, sinceMs);
        for (AppEntry app : apps) {
            UsageStats stats = usage.get(app.packageName);
            if (stats != null) {
                app.lastUsed = stats.getLastTimeUsed();
                app.foregroundMs = stats.getTotalTimeInForeground();
            }
        }
    }

    /** Preenche tamanho de app, dados e cache (Android 8+ com acesso de uso). */
    static boolean attachStorage(Context context, List<AppEntry> apps) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || !Permissions.hasUsageAccess(context)) {
            return false;
        }
        StorageStatsManager manager = (StorageStatsManager) context.getSystemService(Context.STORAGE_STATS_SERVICE);
        for (AppEntry app : apps) {
            try {
                StorageStats stats = manager.queryStatsForPackage(StorageManager.UUID_DEFAULT, app.packageName, Process.myUserHandle());
                app.appBytes = stats.getAppBytes();
                app.dataBytes = stats.getDataBytes();
                app.cacheBytes = stats.getCacheBytes();
            } catch (Exception ignored) {
                // Pacote removido durante a leitura ou volume indisponível.
            }
        }
        return true;
    }

    /** Cache total de todos os apps do usuário (Android 8+ com acesso de uso), ou -1. */
    static long totalAppCache(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || !Permissions.hasUsageAccess(context)) {
            return -1L;
        }
        try {
            StorageStatsManager manager = (StorageStatsManager) context.getSystemService(Context.STORAGE_STATS_SERVICE);
            return manager.queryStatsForUser(StorageManager.UUID_DEFAULT, Process.myUserHandle()).getCacheBytes();
        } catch (Exception exception) {
            return -1L;
        }
    }
}

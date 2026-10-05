package com.kelvin.lumaboost;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

final class Prefs {
    private static final String FILE = "luma_prefs";
    private static final String KEY_PROTECTED = "protected_apps";
    private static final String KEY_AUTO_CLEAN = "auto_clean";
    private static final String KEY_TOTAL_FREED_STORAGE = "total_freed_storage";
    private static final String KEY_TOTAL_FREED_RAM = "total_freed_ram";
    private static final String KEY_LAST_OPTIMIZE = "last_optimize";
    private static final String KEY_LAST_AUTO = "last_auto_summary";
    private static final String ORIGINAL_PREFIX = "original_";
    private static final String KEY_MONITOR = "monitor";
    private static final String KEY_ONBOARDING = "onboarding_done";
    private static final String KEY_ROOT = "root_enabled";
    private static final String KEY_LAST_TRIM = "last_trim";
    private static final String KEY_LAST_JUNK = "last_junk";

    private Prefs() {
    }

    private static SharedPreferences get(Context context) {
        return context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    static Set<String> protectedApps(Context context) {
        return new HashSet<>(get(context).getStringSet(KEY_PROTECTED, new HashSet<String>()));
    }

    static void setProtected(Context context, String packageName, boolean value) {
        Set<String> set = protectedApps(context);
        if (value) {
            set.add(packageName);
        } else {
            set.remove(packageName);
        }
        get(context).edit().putStringSet(KEY_PROTECTED, set).apply();
    }

    static boolean autoClean(Context context) {
        return get(context).getBoolean(KEY_AUTO_CLEAN, false);
    }

    static void setAutoClean(Context context, boolean enabled) {
        get(context).edit().putBoolean(KEY_AUTO_CLEAN, enabled).apply();
    }

    static long totalFreedStorage(Context context) {
        return get(context).getLong(KEY_TOTAL_FREED_STORAGE, 0L);
    }

    static long totalFreedRam(Context context) {
        return get(context).getLong(KEY_TOTAL_FREED_RAM, 0L);
    }

    static synchronized void addFreed(Context context, long storageBytes, long ramBytes) {
        SharedPreferences prefs = get(context);
        prefs.edit()
                .putLong(KEY_TOTAL_FREED_STORAGE, prefs.getLong(KEY_TOTAL_FREED_STORAGE, 0L) + Math.max(0L, storageBytes))
                .putLong(KEY_TOTAL_FREED_RAM, prefs.getLong(KEY_TOTAL_FREED_RAM, 0L) + Math.max(0L, ramBytes))
                .apply();
    }

    static long lastOptimize(Context context) {
        return get(context).getLong(KEY_LAST_OPTIMIZE, 0L);
    }

    static void markOptimized(Context context) {
        get(context).edit().putLong(KEY_LAST_OPTIMIZE, System.currentTimeMillis()).apply();
    }

    static String lastAutoSummary(Context context) {
        return get(context).getString(KEY_LAST_AUTO, null);
    }

    static void setLastAutoSummary(Context context, String summary) {
        get(context).edit().putString(KEY_LAST_AUTO, summary).apply();
    }

    static boolean onboardingDone(Context context) {
        return get(context).getBoolean(KEY_ONBOARDING, false);
    }

    static void setOnboardingDone(Context context, boolean done) {
        get(context).edit().putBoolean(KEY_ONBOARDING, done).apply();
    }

    /** A pessoa aceitou ligar o assistente no tutorial (o Luma pode religá-lo sozinho no Fire TV). */
    static boolean assistantConsent(Context context) {
        return get(context).getBoolean("assistant_consent", false);
    }

    static void setAssistantConsent(Context context, boolean consent) {
        get(context).edit().putBoolean("assistant_consent", consent).apply();
    }

    static boolean monitorEnabled(Context context) {
        return get(context).getBoolean(KEY_MONITOR, false);
    }

    static void setMonitorEnabled(Context context, boolean enabled) {
        get(context).edit().putBoolean(KEY_MONITOR, enabled).apply();
    }

    static boolean rootEnabled(Context context) {
        return get(context).getBoolean(KEY_ROOT, false);
    }

    static void setRootEnabled(Context context, boolean enabled) {
        get(context).edit().putBoolean(KEY_ROOT, enabled).apply();
    }

    static long lastTrim(Context context) {
        return get(context).getLong(KEY_LAST_TRIM, 0L);
    }

    static void markTrim(Context context) {
        get(context).edit().putLong(KEY_LAST_TRIM, System.currentTimeMillis()).apply();
    }

    static long lastJunk(Context context) {
        return get(context).getLong(KEY_LAST_JUNK, 0L);
    }

    static void markJunk(Context context) {
        get(context).edit().putLong(KEY_LAST_JUNK, System.currentTimeMillis()).apply();
    }

    /** Guarda o valor original de uma configuração do sistema antes de otimizá-la. */
    static void saveOriginal(Context context, String key, String value) {
        SharedPreferences prefs = get(context);
        if (!prefs.contains(ORIGINAL_PREFIX + key)) {
            prefs.edit().putString(ORIGINAL_PREFIX + key, value).apply();
        }
    }

    static String original(Context context, String key) {
        return get(context).getString(ORIGINAL_PREFIX + key, null);
    }

    static void clearOriginal(Context context, String key) {
        get(context).edit().remove(ORIGINAL_PREFIX + key).apply();
    }
}

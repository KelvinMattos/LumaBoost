package com.kelvin.lumaboost;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Environment;
import android.os.StatFs;
import android.os.SystemClock;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

/** Fotografia do estado atual do aparelho. */
final class DeviceStats {
    long ramAvailable;
    long ramTotal;
    long ramThreshold;
    boolean lowMemory;
    long storageFree;
    long storageTotal;
    int cores;
    long cpuMaxKhz;
    long cpuCurrentKhz;
    float batteryTemperature = Float.NaN;
    int batteryLevel = -1;
    long uptimeMs;

    static DeviceStats read(Context context) {
        DeviceStats stats = new DeviceStats();
        ActivityManager.MemoryInfo memoryInfo = memoryInfo(context);
        stats.ramAvailable = availableRam(context);
        stats.ramTotal = Math.max(1L, memoryInfo.totalMem);
        stats.ramThreshold = memoryInfo.threshold;
        stats.lowMemory = memoryInfo.lowMemory;

        StatFs dataStats = new StatFs(Environment.getDataDirectory().getAbsolutePath());
        stats.storageFree = dataStats.getAvailableBytes();
        stats.storageTotal = Math.max(1L, dataStats.getTotalBytes());

        stats.cores = Math.max(1, Runtime.getRuntime().availableProcessors());
        long sum = 0L;
        int counted = 0;
        for (int i = 0; i < stats.cores; i++) {
            long max = readLong("/sys/devices/system/cpu/cpu" + i + "/cpufreq/cpuinfo_max_freq");
            stats.cpuMaxKhz = Math.max(stats.cpuMaxKhz, max);
            long current = readLong("/sys/devices/system/cpu/cpu" + i + "/cpufreq/scaling_cur_freq");
            if (current > 0L) {
                sum += current;
                counted++;
            }
        }
        stats.cpuCurrentKhz = counted > 0 ? sum / counted : 0L;

        Intent battery = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (battery != null) {
            int temperature = battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Integer.MIN_VALUE);
            if (temperature != Integer.MIN_VALUE) {
                stats.batteryTemperature = temperature / 10.0f;
            }
            int level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            if (level >= 0 && scale > 0) {
                stats.batteryLevel = Math.round(level * 100f / scale);
            }
        }
        stats.uptimeMs = SystemClock.elapsedRealtime();
        return stats;
    }

    static ActivityManager.MemoryInfo memoryInfo(Context context) {
        ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        activityManager.getMemoryInfo(memoryInfo);
        return memoryInfo;
    }

    /**
     * RAM realmente livre segundo o kernel (MemAvailable). O availMem do ActivityManager já conta a
     * memória de apps em cache como disponível e esconde o ganho de encerrá-los.
     */
    static long availableRam(Context context) {
        long kernel = readMemInfo("MemAvailable:");
        return kernel > 0L ? kernel : memoryInfo(context).availMem;
    }

    private static long readMemInfo(String key) {
        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/meminfo"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith(key)) {
                    String[] parts = line.substring(key.length()).trim().split("\\s+");
                    return Long.parseLong(parts[0]) * 1024L;
                }
            }
        } catch (Exception ignored) {
            // /proc/meminfo indisponível: usa o valor do ActivityManager.
        }
        return 0L;
    }

    static long freeStorage() {
        return new StatFs(Environment.getDataDirectory().getAbsolutePath()).getAvailableBytes();
    }

    int ramUsedPercent() {
        return clamp(Math.round((ramTotal - ramAvailable) * 100f / ramTotal));
    }

    int storageUsedPercent() {
        return clamp(Math.round((storageTotal - storageFree) * 100f / storageTotal));
    }

    /** Nota baseada em RAM disponível (60%) e espaço livre (40%). */
    int score() {
        float ramFreePercent = ramAvailable * 100f / ramTotal;
        float storageFreePercent = storageFree * 100f / storageTotal;
        int ramScore = clamp(Math.round(ramFreePercent * 2.2f));
        int storageScore = clamp(Math.round(storageFreePercent * 4f));
        int score = Math.round(ramScore * 0.6f + storageScore * 0.4f);
        if (lowMemory) {
            score = Math.min(score, 25);
        }
        return clamp(score);
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }

    private static long readLong(String path) {
        File file = new File(path);
        if (!file.canRead()) {
            return 0L;
        }
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line = reader.readLine();
            return line == null ? 0L : Long.parseLong(line.trim());
        } catch (Exception exception) {
            return 0L;
        }
    }
}

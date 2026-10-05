package com.kelvin.lumaboost;

import java.util.Locale;

final class Format {
    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

    private Format() {
    }

    static String bytes(long bytes) {
        double value = Math.max(0L, bytes);
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        int unit = 0;
        while (value >= 1024.0d && unit < units.length - 1) {
            value /= 1024.0d;
            unit++;
        }
        if (unit == 0 || value >= 100.0d) {
            return String.format(PT_BR, "%.0f %s", value, units[unit]);
        }
        if (value >= 10.0d && unit < 3) {
            return String.format(PT_BR, "%.0f %s", value, units[unit]);
        }
        return String.format(PT_BR, "%.1f %s", value, units[unit]);
    }

    static String duration(long milliseconds) {
        long totalMinutes = Math.max(0L, milliseconds / 60000L);
        long days = totalMinutes / 1440L;
        long hours = (totalMinutes % 1440L) / 60L;
        long minutes = totalMinutes % 60L;
        if (days > 0L) {
            return String.format(PT_BR, "%dd %dh", days, hours);
        }
        if (hours > 0L) {
            return String.format(PT_BR, "%dh %dmin", hours, minutes);
        }
        return String.format(PT_BR, "%dmin", minutes);
    }

    static String ago(long timestamp) {
        if (timestamp <= 0L) {
            return "sem uso recente";
        }
        long diff = Math.max(0L, System.currentTimeMillis() - timestamp);
        long minutes = diff / 60000L;
        if (minutes < 1L) {
            return "agora";
        }
        if (minutes < 60L) {
            return "há " + minutes + " min";
        }
        long hours = minutes / 60L;
        if (hours < 24L) {
            return "há " + hours + " h";
        }
        long days = hours / 24L;
        return days == 1L ? "há 1 dia" : "há " + days + " dias";
    }

    static String percent(int value) {
        return value + "%";
    }

    static String plural(int count, String singular, String plural) {
        return count + " " + (count == 1 ? singular : plural);
    }
}

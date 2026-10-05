package com.kelvin.lumaboost;

import android.app.WallpaperManager;
import android.app.admin.DevicePolicyManager;
import android.app.usage.UsageEvents;
import android.content.ComponentName;
import android.provider.Settings;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.os.Build;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Decide quais apps podem ser encerrados e quais ficam protegidos por padrão. */
final class Hibernator {
    /** Mensageiros, e-mail, alarmes e chamadas: encerrar faria perder notificações ou alarmes. */
    private static final String[] DEFAULT_PROTECTED = {
            "com.whatsapp", "com.whatsapp.w4b", "org.telegram.messenger", "org.thoughtcrime.securesms",
            "com.facebook.orca", "com.discord", "com.viber.voip", "jp.naver.line.android", "com.skype.raider",
            "com.microsoft.teams", "com.slack", "com.google.android.gm", "com.microsoft.office.outlook",
            "com.google.android.apps.messaging", "com.samsung.android.messaging", "com.google.android.apps.tachyon",
            "com.google.android.deskclock", "com.android.deskclock", "com.sec.android.app.clockpackage",
            "com.motorola.timeweatherwidget", "com.google.android.calendar", "com.google.android.dialer",
            "com.google.android.contacts", "com.google.android.apps.maps", "com.google.android.apps.walletnfcrel",
            "com.google.android.inputmethod.latin", "com.spotify.music", "com.spotify.tv.android", "com.amazon.bueller.music", "com.google.android.apps.youtube.music",
            "com.google.android.apps.wellbeing", "com.android.vending", "com.google.android.apps.authenticator2",
            "com.google.android.apps.photos", "com.google.android.apps.docs", "com.android.camera", "com.android.camera2",
            "com.samsung.android.messaging", "com.samsung.android.dialer", "com.samsung.android.app.contacts",
            "com.samsung.android.calendar", "com.google.android.apps.kids.familylinkhelper", "com.google.android.apps.kids.familylink"
    };

    private Hibernator() {
    }

    /** Trechos de nome de pacote de controle parental, segurança e gestão do aparelho (qualquer fabricante). */
    private static final String[] PROTECTED_KEYWORDS = {
            "familylink", "parental", "kidshome", "kidsplat", "kidsinstaller", "kidsmode", "screentime",
            "mdm", "knox", "devicepolicy", "enterprise", ".lool", "devicecare", "securitycenter", "security.center",
            "antivirus", "authenticator", "deskclock", "clockpackage", "alarm"
    };

    static boolean isDefaultProtected(String packageName) {
        for (String pkg : DEFAULT_PROTECTED) {
            if (pkg.equals(packageName)) {
                return true;
            }
        }
        String lower = packageName.toLowerCase(java.util.Locale.ROOT);
        for (String keyword : PROTECTED_KEYWORDS) {
            if (lower.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /** Apps ativos (não parados) que podem ser encerrados, já com uso anexado. */
    static List<AppCatalog.AppEntry> activeApps(Context context) {
        Set<String> essential = AppCatalog.essentialPackages(context);
        List<AppCatalog.AppEntry> result = new ArrayList<>();
        for (AppCatalog.AppEntry app : AppCatalog.launchable(context)) {
            if (!app.stopped && !essential.contains(app.packageName)) {
                result.add(app);
            }
        }
        AppCatalog.attachUsage(context, result, System.currentTimeMillis() - 3L * AppCatalog.DAY_MS);
        Collections.sort(result, new Comparator<AppCatalog.AppEntry>() {
            @Override
            public int compare(AppCatalog.AppEntry a, AppCatalog.AppEntry b) {
                if (a.lastUsed != b.lastUsed) {
                    return Long.compare(b.lastUsed, a.lastUsed);
                }
                return a.label.compareToIgnoreCase(b.label);
            }
        });
        return result;
    }

    /**
     * Seleção padrão: apps (inclusive pré-instalados como Chrome e YouTube) que não estão protegidos e não estão tocando música,
     * navegando ou conectados a um relógio (serviço em primeiro plano ativo).
     */
    static List<AppCatalog.AppEntry> defaultSelection(Context context, List<AppCatalog.AppEntry> active) {
        Set<String> protectedApps = Prefs.protectedApps(context);
        Set<String> foreground = packagesWithForegroundService(context);
        foreground.addAll(systemBoundPackages(context));
        List<AppCatalog.AppEntry> selected = new ArrayList<>();
        for (AppCatalog.AppEntry app : active) {
            if (isSelectedByDefault(app, protectedApps, foreground)) {
                selected.add(app);
            }
        }
        return selected;
    }

    static boolean isSelectedByDefault(AppCatalog.AppEntry app, Set<String> protectedApps, Set<String> foreground) {
        return (app.info.flags & android.content.pm.ApplicationInfo.FLAG_PERSISTENT) == 0
                && !protectedApps.contains(app.packageName)
                && !isDefaultProtected(app.packageName)
                && !foreground.contains(app.packageName);
    }

    /**
     * Apps ligados ao sistema que não podem parar sem quebrar algo: leem notificações (relógios,
     * pulseiras, Android Auto), são serviços de acessibilidade, administradores do aparelho,
     * VPN sempre ativa, assistente padrão ou papel de parede animado.
     */
    static Set<String> systemBoundPackages(Context context) {
        Set<String> bound = new HashSet<>();
        addComponents(bound, secure(context, "enabled_notification_listeners"));
        addComponents(bound, secure(context, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES));
        addComponents(bound, secure(context, "assistant"));
        addComponents(bound, secure(context, "voice_interaction_service"));
        addComponents(bound, secure(context, "always_on_vpn_app"));
        try {
            DevicePolicyManager policy = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
            List<ComponentName> admins = policy == null ? null : policy.getActiveAdmins();
            if (admins != null) {
                for (ComponentName admin : admins) {
                    bound.add(admin.getPackageName());
                }
            }
        } catch (RuntimeException ignored) {
            // Consulta bloqueada pelo fabricante.
        }
        try {
            WallpaperManager wallpaper = WallpaperManager.getInstance(context);
            if (wallpaper.getWallpaperInfo() != null) {
                bound.add(wallpaper.getWallpaperInfo().getPackageName());
            }
        } catch (RuntimeException ignored) {
            // Sem papel de parede animado.
        }
        bound.remove(context.getPackageName());
        return bound;
    }

    /** Lê uma configuração segura; chaves ocultas lançam SecurityException no Android 12+. */
    private static String secure(Context context, String key) {
        try {
            return Settings.Secure.getString(context.getContentResolver(), key);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static void addComponents(Set<String> target, String flattened) {
        if (flattened == null || flattened.isEmpty()) {
            return;
        }
        for (String entry : flattened.split(":")) {
            ComponentName component = ComponentName.unflattenFromString(entry);
            if (component != null) {
                target.add(component.getPackageName());
            } else if (!entry.trim().isEmpty() && !entry.contains("/")) {
                target.add(entry.trim());
            }
        }
    }

    /** Pacotes com serviço em primeiro plano ativo agora (música, navegação, relógio). Android 10+. */
    static Set<String> packagesWithForegroundService(Context context) {
        Set<String> running = new HashSet<>();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || !Permissions.hasUsageAccess(context)) {
            return running;
        }
        UsageStatsManager manager = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
        long now = System.currentTimeMillis();
        Map<String, Integer> balance = new HashMap<>();
        try {
            UsageEvents events = manager.queryEvents(now - 2L * AppCatalog.DAY_MS, now);
            UsageEvents.Event event = new UsageEvents.Event();
            while (events.hasNextEvent()) {
                events.getNextEvent(event);
                int type = event.getEventType();
                if (type == UsageEvents.Event.FOREGROUND_SERVICE_START) {
                    Integer value = balance.get(event.getPackageName());
                    balance.put(event.getPackageName(), (value == null ? 0 : value) + 1);
                } else if (type == UsageEvents.Event.FOREGROUND_SERVICE_STOP) {
                    Integer value = balance.get(event.getPackageName());
                    balance.put(event.getPackageName(), Math.max(0, (value == null ? 0 : value) - 1));
                }
            }
        } catch (RuntimeException ignored) {
            return running;
        }
        for (Map.Entry<String, Integer> entry : balance.entrySet()) {
            if (entry.getValue() > 0) {
                running.add(entry.getKey());
            }
        }
        return running;
    }
}

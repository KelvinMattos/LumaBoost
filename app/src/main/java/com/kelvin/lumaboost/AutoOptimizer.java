package com.kelvin.lumaboost;

import android.content.Context;
import android.os.Build;
import android.os.SystemClock;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Otimização sem interface: roda ao bloquear a tela, pelo botão da notificação (com root) e no
 * modo root completo. Tudo medido antes e depois.
 */
final class AutoOptimizer {
    interface Progress {
        void step(String label);
    }

    static final class Summary {
        long ramBefore;
        long ramAfter;
        long storageBefore;
        long storageAfter;
        int appsStopped;
        boolean root;
        final List<String> steps = new ArrayList<>();

        long ramFreed() {
            return Math.max(0L, ramAfter - ramBefore);
        }

        long storageFreed() {
            return Math.max(0L, storageAfter - storageBefore);
        }

        String text() {
            String text = "+" + Format.bytes(ramFreed()) + " de memória";
            if (storageFreed() > 0L) {
                text += " · +" + Format.bytes(storageFreed()) + " de espaço";
            }
            if (appsStopped > 0) {
                text += " · " + Format.plural(appsStopped, "app fechado", "apps fechados");
            }
            return text;
        }
    }

    private static final long TRIM_INTERVAL_MS = 6L * 60L * 60L * 1000L;
    private static final long JUNK_INTERVAL_MS = 60L * 60L * 1000L;

    private AutoOptimizer() {
    }

    /** Otimização rápida e silenciosa. Bloqueante: chame fora da thread principal. */
    static synchronized Summary runSilent(Context context) {
        Context app = context.getApplicationContext();
        Summary summary = new Summary();
        summary.ramBefore = DeviceStats.availableRam(app);
        summary.storageBefore = DeviceStats.freeStorage();
        long now = System.currentTimeMillis();

        if (RootShell.available(app)) {
            summary.root = true;
            List<String> commands = new ArrayList<>();
            List<AppCatalog.AppEntry> targets = rootTargets(app);
            for (AppCatalog.AppEntry target : targets) {
                commands.add("am force-stop " + RootShell.quote(target.packageName));
            }
            commands.add("am kill-all");
            commands.add("echo 1 > /proc/sys/vm/compact_memory");
            if (now - Prefs.lastTrim(app) > TRIM_INTERVAL_MS) {
                commands.add("pm trim-caches 999G");
                Prefs.markTrim(app);
            }
            RootShell.run(60, commands.toArray(new String[0]));
            summary.appsStopped = targets.size();
        } else {
            MemoryBooster.boost(app);
            CacheCleaner.trimSystemCache(app);
        }
        CacheCleaner.cleanOwnCaches(app);
        if (Permissions.hasAllFilesAccess(app) && now - Prefs.lastJunk(app) > JUNK_INTERVAL_MS) {
            JunkScanner.quickClean();
            Prefs.markJunk(app);
        }

        Tasks.sleep(1500L);
        summary.ramAfter = DeviceStats.availableRam(app);
        summary.storageAfter = DeviceStats.freeStorage();
        Prefs.addFreed(app, summary.storageFreed(), summary.ramFreed());
        Prefs.setLastAutoSummary(app, summary.text() + " · " + android.text.format.DateFormat.getTimeFormat(app).format(new java.util.Date()));
        return summary;
    }

    /**
     * Otimização root completa (manual): encerra apps, limpa todo o cache, compacta a memória,
     * executa TRIM no armazenamento e, opcionalmente, compila os apps com o ART.
     */
    static Summary runRootFull(Context context, boolean compileApps, Progress progress) {
        Context app = context.getApplicationContext();
        Summary summary = new Summary();
        summary.root = true;
        summary.ramBefore = DeviceStats.availableRam(app);
        summary.storageBefore = DeviceStats.freeStorage();

        notify(progress, "Fechando apps escondidos…");
        List<AppCatalog.AppEntry> targets = rootTargets(app);
        List<String> commands = new ArrayList<>();
        for (AppCatalog.AppEntry target : targets) {
            commands.add("am force-stop " + RootShell.quote(target.packageName));
        }
        commands.add("am kill-all");
        RootShell.run(60, commands.toArray(new String[0]));
        summary.appsStopped = targets.size();
        summary.steps.add(Format.plural(targets.size(), "app fechado", "apps fechados"));

        notify(progress, "Limpando o lixo de todos os apps…");
        long beforeTrim = DeviceStats.freeStorage();
        RootShell.run(120, "pm trim-caches 999G");
        Prefs.markTrim(app);
        summary.steps.add(Format.bytes(Math.max(0L, DeviceStats.freeStorage() - beforeTrim)) + " de lixo apagado de todos os apps");

        notify(progress, "Organizando a memória…");
        RootShell.run(30, "sync", "echo 1 > /proc/sys/vm/compact_memory");
        summary.steps.add("Memória organizada");

        notify(progress, "Deixando o armazenamento mais rápido…");
        RootShell.Result trim = RootShell.run(180, "sm fstrim");
        summary.steps.add(trim.ok() ? "Armazenamento otimizado" : "Armazenamento: não disponível neste celular");

        // Destrava os ajustes avançados (animações, busca Bluetooth) sem precisar de computador.
        RootShell.run(15, "pm grant " + app.getPackageName() + " android.permission.WRITE_SECURE_SETTINGS");

        if (compileApps && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            notify(progress, "Acelerando a abertura dos apps (pode levar alguns minutos)…");
            long start = SystemClock.elapsedRealtime();
            RootShell.Result dexopt = RootShell.run(1200, "cmd package bg-dexopt-job");
            summary.steps.add(dexopt.ok()
                    ? String.format(Locale.forLanguageTag("pt-BR"), "Apps acelerados em %d segundos", (SystemClock.elapsedRealtime() - start) / 1000L)
                    : "Aceleração dos apps: não disponível neste celular");
        }

        CacheCleaner.cleanOwnCaches(app);
        Tasks.sleep(1500L);
        summary.ramAfter = DeviceStats.availableRam(app);
        summary.storageAfter = DeviceStats.freeStorage();
        Prefs.addFreed(app, summary.storageFreed(), summary.ramFreed());
        Prefs.markOptimized(app);
        return summary;
    }

    /**
     * Apps a encerrar com root: a mesma seleção segura da otimização profunda, menos o app que estava
     * na tela por último (você volta a ele sem esperar recarregar).
     */
    static List<AppCatalog.AppEntry> rootTargets(Context app) {
        List<AppCatalog.AppEntry> active = Hibernator.activeApps(app);
        List<AppCatalog.AppEntry> selection = Hibernator.defaultSelection(app, active);
        String lastUsed = null;
        long lastTime = 0L;
        for (AppCatalog.AppEntry entry : active) {
            if (entry.lastUsed > lastTime) {
                lastTime = entry.lastUsed;
                lastUsed = entry.packageName;
            }
        }
        List<AppCatalog.AppEntry> result = new ArrayList<>();
        for (AppCatalog.AppEntry entry : selection) {
            if (!entry.packageName.equals(lastUsed)) {
                result.add(entry);
            }
        }
        return result;
    }

    private static void notify(final Progress progress, final String label) {
        if (progress == null) {
            return;
        }
        Tasks.main(new Runnable() {
            @Override
            public void run() {
                progress.step(label);
            }
        });
    }
}

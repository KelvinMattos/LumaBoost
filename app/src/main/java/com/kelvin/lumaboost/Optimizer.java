package com.kelvin.lumaboost;

import android.content.Context;
import android.os.SystemClock;

import java.util.ArrayList;
import java.util.List;

/**
 * Otimização completa em um toque. Todos os números do relatório são medidos, não estimados.
 *
 * Fluxo: medir → limpar armazenamento → liberar RAM (otimização profunda quando ativa, senão
 * finalização de processos em segundo plano) → medir de novo.
 */
final class Optimizer {
    interface Progress {
        void step(String label);
    }

    interface Callback {
        void done(Report report);
    }

    static final class Report {
        long startedAt;
        long ramBefore;
        long ramAfter;
        long ramTotal;
        long storageBefore;
        long storageAfter;
        long systemCacheFreed;
        long ownCacheFreed;
        long junkFreed;
        long allAppsCacheFreed = -1L;
        long deepCacheFreed;
        List<String> cacheClearedApps = new ArrayList<>();
        boolean junkSkipped;
        boolean deepStop;
        List<String> stoppedApps = new ArrayList<>();
        int alreadyStopped;
        int appsChecked;
        boolean ramLimited;
        boolean cancelled;
        boolean root;
        long durationMs;

        long ramFreed() {
            return Math.max(0L, ramAfter - ramBefore);
        }

        long storageFreed() {
            return Math.max(0L, storageAfter - storageBefore);
        }
    }

    private Optimizer() {
    }

    /** Executa tudo. O callback chega na thread principal. */
    static void run(final Context context, final Progress progress, final Callback callback) {
        run(context, progress, null, callback);
    }

    /** {@code seed} traz medições feitas antes (ex.: antes do diálogo de limpeza de cache do sistema). */
    static void run(final Context context, final Progress progress, final Report seed, final Callback callback) {
        final Context app = context.getApplicationContext();
        Tasks.run(new Tasks.Job<Report>() {
            @Override
            public Report run() {
                Report report = seed != null ? seed : new Report();
                if (report.startedAt == 0L) {
                    report.startedAt = SystemClock.elapsedRealtime();
                    report.storageBefore = DeviceStats.freeStorage();
                }
                report.ramBefore = DeviceStats.availableRam(app);
                report.ramTotal = DeviceStats.memoryInfo(app).totalMem;
                cleanStorage(app, report, progress);
                if (RootShell.available(app)) {
                    rootOptimize(app, report, progress);
                } else {
                    ForceStopService.awaitReady(app, 3000L);
                }
                return report;
            }
        }, new Tasks.Done<Report>() {
            @Override
            public void done(final Report report, Exception error) {
                if (report == null) {
                    callback.done(null);
                    return;
                }
                if (report.root) {
                    finish(app, report, callback);
                } else if (ForceStopService.isReady() && !ForceStopService.isBusy()) {
                    deepCache(app, report, progress, callback);
                } else {
                    legacyBoost(app, report, progress, callback);
                }
            }
        });
    }

    private static void cleanStorage(Context app, Report report, Progress progress) {
        notify(progress, "Tirando o lixo mais fácil…");
        report.systemCacheFreed = CacheCleaner.trimSystemCache(app);
        report.ownCacheFreed = CacheCleaner.cleanOwnCaches(app);
        if (Permissions.hasAllFilesAccess(app)) {
            notify(progress, "Apagando arquivos esquecidos…");
            report.junkFreed = JunkScanner.quickClean();
        } else {
            report.junkSkipped = true;
        }
    }

    /** Com root: encerra apps e apaga o cache de todos os apps direto pelo sistema, sem abrir telas. */
    private static void rootOptimize(Context app, Report report, Progress progress) {
        report.root = true;
        report.deepStop = true;
        notify(progress, "Fechando apps escondidos…");
        List<AppCatalog.AppEntry> targets = AutoOptimizer.rootTargets(app);
        List<String> commands = new ArrayList<>();
        for (AppCatalog.AppEntry target : targets) {
            commands.add("am force-stop " + RootShell.quote(target.packageName));
            report.stoppedApps.add(target.label);
        }
        commands.add("am kill-all");
        commands.add("echo 1 > /proc/sys/vm/compact_memory");
        RootShell.run(60, commands.toArray(new String[0]));
        notify(progress, "Limpando o lixo de todos os apps…");
        long before = DeviceStats.freeStorage();
        RootShell.run(120, "pm trim-caches 999G");
        Prefs.markTrim(app);
        report.deepCacheFreed = Math.max(0L, DeviceStats.freeStorage() - before);
        report.allAppsCacheFreed = report.deepCacheFreed;
    }

    /** Apps cujo cache vale a pena limpar, do maior para o menor (requer acesso ao uso). */
    static List<AppCatalog.AppEntry> appsWithCache(Context app, long minBytes) {
        List<AppCatalog.AppEntry> apps = AppCatalog.launchable(app);
        List<AppCatalog.AppEntry> result = new ArrayList<>();
        if (!AppCatalog.attachStorage(app, apps)) {
            return result;
        }
        for (AppCatalog.AppEntry entry : apps) {
            if (entry.cacheBytes >= minBytes) {
                result.add(entry);
            }
        }
        java.util.Collections.sort(result, new java.util.Comparator<AppCatalog.AppEntry>() {
            @Override
            public int compare(AppCatalog.AppEntry a, AppCatalog.AppEntry b) {
                return Long.compare(b.cacheBytes, a.cacheBytes);
            }
        });
        return result;
    }

    private static void deepCache(final Context app, final Report report, final Progress progress, final Callback callback) {
        notify(progress, "Limpando o lixo dos apps…");
        if (FireTv.is(app)) {
            // Uma única opção das Configurações da Amazon limpa o cache de todos os apps.
            final long before = DeviceStats.freeStorage();
            boolean started = ForceStopService.clearAllCaches(new ForceStopService.Listener() {
                @Override
                public void onFinished(ForceStopService.Result result) {
                    report.deepCacheFreed = Math.max(0L, DeviceStats.freeStorage() - before);
                    if (!result.cacheCleared.isEmpty()) {
                        report.allAppsCacheFreed = report.deepCacheFreed;
                    }
                    if (result.cancelled) {
                        report.cancelled = true;
                        report.deepStop = true;
                        finish(app, report, callback);
                    } else {
                        deepStop(app, report, progress, callback);
                    }
                }
            });
            if (!started) {
                deepStop(app, report, progress, callback);
            }
            return;
        }
        Tasks.run(new Tasks.Job<List<AppCatalog.AppEntry>>() {
            @Override
            public List<AppCatalog.AppEntry> run() {
                return appsWithCache(app, 30L * 1024L * 1024L);
            }
        }, new Tasks.Done<List<AppCatalog.AppEntry>>() {
            @Override
            public void done(List<AppCatalog.AppEntry> targets, Exception error) {
                if (targets == null || targets.isEmpty()) {
                    deepStop(app, report, progress, callback);
                    return;
                }
                final long before = DeviceStats.freeStorage();
                boolean started = ForceStopService.clearCaches(targets, new ForceStopService.Listener() {
                    @Override
                    public void onFinished(ForceStopService.Result result) {
                        report.cacheClearedApps = result.cacheCleared;
                        report.deepCacheFreed = Math.max(0L, DeviceStats.freeStorage() - before);
                        if (result.cancelled) {
                            report.cancelled = true;
                            report.deepStop = true;
                            finish(app, report, callback);
                        } else {
                            deepStop(app, report, progress, callback);
                        }
                    }
                });
                if (!started) {
                    deepStop(app, report, progress, callback);
                }
            }
        });
    }

    private static void deepStop(final Context app, final Report report, final Progress progress, final Callback callback) {
        notify(progress, "Fechando apps escondidos…");
        Tasks.run(new Tasks.Job<List<AppCatalog.AppEntry>>() {
            @Override
            public List<AppCatalog.AppEntry> run() {
                // No toque rápido, só os apps usados nas últimas 24 h: são eles que ocupam memória.
                // A aba Apps continua permitindo fechar todos os ativos.
                List<AppCatalog.AppEntry> selection = Hibernator.defaultSelection(app, Hibernator.activeApps(app));
                // No Fire TV os apps ficam abertos escondidos por dias (IPTV, players): fecha todos os ativos.
                // Os já parados ficam de fora sozinhos, então a partir da segunda vez a lista é curta.
                if (!Permissions.hasUsageAccess(app) || FireTv.is(app)) {
                    return selection;
                }
                long since = System.currentTimeMillis() - AppCatalog.DAY_MS;
                List<AppCatalog.AppEntry> recent = new ArrayList<>();
                for (AppCatalog.AppEntry entry : selection) {
                    if (entry.lastUsed >= since) {
                        recent.add(entry);
                    }
                }
                return recent;
            }
        }, new Tasks.Done<List<AppCatalog.AppEntry>>() {
            @Override
            public void done(List<AppCatalog.AppEntry> selection, Exception error) {
                if (selection == null) {
                    legacyBoost(app, report, progress, callback);
                    return;
                }
                if (selection.isEmpty()) {
                    report.deepStop = true;
                    finish(app, report, callback);
                    return;
                }
                report.appsChecked = selection.size();
                boolean started = ForceStopService.stopApps(selection, new ForceStopService.Listener() {
                    @Override
                    public void onFinished(ForceStopService.Result result) {
                        report.deepStop = true;
                        report.stoppedApps = result.stopped;
                        report.alreadyStopped = result.alreadyStopped;
                        report.cancelled = result.cancelled;
                        finish(app, report, callback);
                    }
                });
                if (!started) {
                    legacyBoost(app, report, progress, callback);
                }
            }
        });
    }

    private static void legacyBoost(final Context app, final Report report, Progress progress, final Callback callback) {
        notify(progress, "Liberando memória…");
        Tasks.run(new Tasks.Job<MemoryBooster.Result>() {
            @Override
            public MemoryBooster.Result run() {
                return MemoryBooster.boost(app);
            }
        }, new Tasks.Done<MemoryBooster.Result>() {
            @Override
            public void done(MemoryBooster.Result result, Exception error) {
                if (result != null) {
                    report.appsChecked = result.appsChecked;
                    report.ramLimited = result.limitedBySystem;
                }
                finish(app, report, callback);
            }
        });
    }

    private static void finish(final Context app, final Report report, final Callback callback) {
        Tasks.run(new Tasks.Job<Report>() {
            @Override
            public Report run() {
                // Dá tempo ao kernel de devolver a memória dos processos encerrados.
                Tasks.sleep(1500L);
                report.ramAfter = DeviceStats.availableRam(app);
                report.storageAfter = DeviceStats.freeStorage();
                report.durationMs = SystemClock.elapsedRealtime() - report.startedAt;
                Prefs.addFreed(app, report.storageFreed(), report.ramFreed());
                Prefs.markOptimized(app);
                return report;
            }
        }, new Tasks.Done<Report>() {
            @Override
            public void done(Report result, Exception error) {
                callback.done(result);
            }
        });
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

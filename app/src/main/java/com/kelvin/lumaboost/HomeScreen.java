package com.kelvin.lumaboost;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

final class HomeScreen extends Screen {
    static final int REQ_CLEAR_CACHE = 73;
    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refresher = new Runnable() {
        @Override
        public void run() {
            refreshStats();
            handler.postDelayed(this, 3000L);
        }
    };

    private GaugeView gauge;
    private TextView optimizeButton;
    private LinearLayout reportCard;
    private TextView statusLine;
    private TextView ramValue;
    private TextView storageValue;
    private TextView cpuValue;
    private TextView batteryValue;
    private TextView freedValue;
    private TextView uptimeValue;
    private LinearLayout permissionsCard;
    private TextView lastAuto;
    private boolean running;
    private Optimizer.Report seed;

    HomeScreen(MainActivity activity) {
        super(activity);
    }

    @Override
    void build() {
        boolean wide = Ui.twoColumns(activity);
        // Com a barra lateral larga, a marca já aparece nela: o título vira o nome da aba.
        header(Ui.expandedNav(activity) ? "Início" : "Luma Boost", "Seu " + Ui.device(activity) + " mais leve e rápido, com um toque. Você vê exatamente quanto ganhou.");
        split(1.05f);

        gauge = new GaugeView(activity);
        gauge.setLabel("SAÚDE DO " + Ui.device(activity).toUpperCase(java.util.Locale.ROOT));
        // Em tela grande o medidor ocupa a altura que sobrar na coluna, para tudo caber sem rolar.
        LinearLayout.LayoutParams gaugeParams = wide
                ? new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
                : new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(270));
        gauge.setMinimumHeight(dp(230));
        left.addView(gauge, gaugeParams);
        Ui.gap(left, wide ? 14 : 16);

        optimizeButton = Ui.button(activity, "Otimizar agora", "boost", true, new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startOptimization();
            }
        });
        optimizeButton.setGravity(Gravity.CENTER);
        optimizeButton.setTextSize(17);
        left.addView(optimizeButton, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(60)));
        Ui.gap(left, 10);

        statusLine = Ui.small(activity, "", Ui.MUTED);
        statusLine.setGravity(Gravity.CENTER);
        left.addView(statusLine, Ui.matchWrap());
        Ui.gap(left, wide ? 0 : 14);

        // Em tela grande o relatório abre no topo da coluna da direita, sempre à vista.
        reportCard = Ui.tintedCard(activity, Ui.GREEN_SOFT);
        reportCard.setVisibility(View.GONE);
        if (wide) {
            right.addView(reportCard, withBottomMargin(16));
        } else {
            left.addView(reportCard, Ui.matchWrap());
            Ui.gap(left, 4);
        }

        permissionsCard = Ui.card(activity);
        right.addView(permissionsCard, Ui.matchWrap());
        Ui.gap(right, wide ? 0 : 18);
        if (wide) {
            permissionsCard.setLayoutParams(withBottomMargin(18));
        }

        right.addView(Ui.section(activity, "Como está seu " + Ui.device(activity)));
        Ui.gap(right, wide ? 12 : 10);
        ramValue = statValue();
        storageValue = statValue();
        cpuValue = statValue();
        batteryValue = statValue();
        freedValue = statValue();
        uptimeValue = statValue();
        right.addView(statRow("ram", "Memória livre", ramValue, "storage", "Espaço livre", storageValue));
        Ui.gap(right, wide ? 10 : 12);
        if (FireTv.is(activity)) {
            // TV não tem bateria: o card mostraria 0% e 0 °C.
            right.addView(statRow("cpu", "Processador", cpuValue, "clock", "Ligado sem reiniciar", uptimeValue));
            Ui.gap(right, wide ? 10 : 12);
            right.addView(statCard("check", "Já liberado pelo Luma", freedValue, Ui.GREEN, Ui.GREEN_SOFT), Ui.matchWrap());
        } else {
            right.addView(statRow("cpu", "Processador", cpuValue, "battery", "Bateria", batteryValue));
            Ui.gap(right, wide ? 10 : 12);
            right.addView(statRow("check", "Já liberado pelo Luma", freedValue, "clock", "Ligado sem reiniciar", uptimeValue));
        }
        Ui.gap(right, 14);

        lastAuto = Ui.small(activity, "", Ui.MUTED);
        right.addView(lastAuto, Ui.matchWrap());

        if (FireTv.is(activity)) {
            // No controle remoto, a tela já abre com o botão principal selecionado.
            optimizeButton.post(new Runnable() {
                @Override
                public void run() {
                    if (optimizeButton.isAttachedToWindow()) {
                        optimizeButton.requestFocus();
                    }
                }
            });
        }
    }

    private LinearLayout.LayoutParams withBottomMargin(int bottomDp) {
        LinearLayout.LayoutParams params = Ui.matchWrap();
        params.setMargins(0, 0, 0, dp(bottomDp));
        return params;
    }

    @Override
    void onShow() {
        updatePermissions();
        handler.removeCallbacks(refresher);
        handler.post(refresher);
        String summary = Prefs.lastAutoSummary(activity);
        lastAuto.setText(summary == null ? "" : "Limpeza automática: " + summary);
        lastAuto.setVisibility(summary == null ? View.GONE : View.VISIBLE);
    }

    @Override
    void onHide() {
        handler.removeCallbacks(refresher);
    }

    private void refreshStats() {
        DeviceStats stats = DeviceStats.read(activity);
        if (!running) {
            gauge.setMetrics(stats.score(), "Memória " + stats.ramUsedPercent() + "% em uso · Espaço " + stats.storageUsedPercent() + "% em uso");
        }
        ramValue.setText(Format.bytes(stats.ramAvailable) + "\nde " + Format.bytes(stats.ramTotal));
        storageValue.setText(Format.bytes(stats.storageFree) + "\nde " + Format.bytes(stats.storageTotal));
        if (stats.cpuMaxKhz >= 100_000L) {
            cpuValue.setText(stats.cpuCurrentKhz >= 100_000L
                    ? String.format(PT_BR, "%d núcleos\n%.1f / %.1f GHz", stats.cores, stats.cpuCurrentKhz / 1_000_000f, stats.cpuMaxKhz / 1_000_000f)
                    : String.format(PT_BR, "%d núcleos\naté %.1f GHz", stats.cores, stats.cpuMaxKhz / 1_000_000f));
        } else {
            cpuValue.setText(stats.cores + " núcleos");
        }
        String battery = stats.batteryLevel >= 0 ? stats.batteryLevel + "%" : "—";
        if (!Float.isNaN(stats.batteryTemperature)) {
            battery += String.format(PT_BR, "\n%.1f °C", stats.batteryTemperature);
        }
        batteryValue.setText(battery);
        freedValue.setText(Format.bytes(Prefs.totalFreedStorage(activity)) + " de espaço\n"
                + Format.bytes(Prefs.totalFreedRam(activity)) + " de memória");
        uptimeValue.setText(Format.duration(stats.uptimeMs));
        if (!running) {
            long last = Prefs.lastOptimize(activity);
            statusLine.setText(last == 0L ? "Toque no botão para a primeira otimização." : "Última otimização " + Format.ago(last) + ".");
        }
    }

    void startOptimization() {
        if (running) {
            return;
        }
        running = true;
        optimizeButton.setEnabled(false);
        optimizeButton.setAlpha(0.6f);
        optimizeButton.setText("Otimizando…");
        reportCard.setVisibility(View.GONE);
        gauge.setLabel("CUIDANDO DO SEU " + Ui.device(activity).toUpperCase(java.util.Locale.ROOT));
        continueOptimization();
    }

    /**
     * O diálogo oficial de limpeza do cache externo vem por último: ao confirmar, o Android
     * desliga e religa os serviços de acessibilidade, o que interromperia a otimização profunda.
     */
    private void finishWithSystemCacheDialog(final Optimizer.Report report) {
        if (report.root || !CacheCleaner.canClearAllAppsCache(activity)) {
            completeOptimization(report);
            return;
        }
        seed = report;
        statusLine.setText("Só falta confirmar a limpeza final…");
        try {
            activity.startActivityForResult(CacheCleaner.clearAllAppsCacheIntent(), REQ_CLEAR_CACHE);
        } catch (RuntimeException exception) {
            seed = null;
            completeOptimization(report);
        }
    }

    @Override
    void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQ_CLEAR_CACHE || seed == null) {
            return;
        }
        final Optimizer.Report report = seed;
        seed = null;
        if (resultCode != Activity.RESULT_OK) {
            completeOptimization(report);
            return;
        }
        statusLine.setText("Terminando a limpeza…");
        Tasks.run(new Tasks.Job<Long>() {
            @Override
            public Long run() {
                // O sistema apaga em segundo plano: espera o espaço livre parar de crescer.
                long start = DeviceStats.freeStorage();
                long last = start;
                for (int i = 0; i < 12; i++) {
                    Tasks.sleep(500L);
                    long now = DeviceStats.freeStorage();
                    if (i >= 3 && now - last < 1024L * 1024L) {
                        break;
                    }
                    last = now;
                }
                long freed = Math.max(0L, DeviceStats.freeStorage() - start);
                Prefs.addFreed(activity, freed, 0L);
                return freed;
            }
        }, new Tasks.Done<Long>() {
            @Override
            public void done(Long freed, Exception error) {
                report.allAppsCacheFreed = freed == null ? 0L : freed;
                report.storageAfter = DeviceStats.freeStorage();
                completeOptimization(report);
            }
        });
    }

    private void completeOptimization(Optimizer.Report report) {
        running = false;
        optimizeButton.setEnabled(true);
        optimizeButton.setAlpha(1f);
        optimizeButton.setText("Otimizar novamente");
        gauge.setLabel("SAÚDE DO " + Ui.device(activity).toUpperCase(java.util.Locale.ROOT));
        showReport(report);
        refreshStats();
        updatePermissions();
    }

    private void continueOptimization() {
        Optimizer.run(activity, new Optimizer.Progress() {
            @Override
            public void step(String label) {
                statusLine.setText(label);
            }
        }, new Optimizer.Callback() {
            @Override
            public void done(Optimizer.Report report) {
                if (report == null) {
                    running = false;
                    optimizeButton.setEnabled(true);
                    optimizeButton.setAlpha(1f);
                    optimizeButton.setText("Otimizar agora");
                    gauge.setLabel("SAÚDE DO " + Ui.device(activity).toUpperCase(java.util.Locale.ROOT));
                    statusLine.setText("Algo deu errado. Tente de novo em instantes.");
                    return;
                }
                finishWithSystemCacheDialog(report);
            }
        });
    }

    private void showReport(Optimizer.Report report) {
        reportCard.removeAllViews();
        reportCard.setVisibility(View.VISIBLE);
        if (reportCard.getParent() instanceof View && ((View) reportCard.getParent()).getParent() instanceof android.widget.ScrollView) {
            ((android.widget.ScrollView) ((View) reportCard.getParent()).getParent()).smoothScrollTo(0, 0);
        }
        reportCard.addView(Ui.title(activity, "Pronto! Seu " + Ui.device(activity) + " está mais leve", 18));
        Ui.gap(reportCard, 10);

        String ram = report.ramFreed() >= 8L * 1024L * 1024L
                ? "+" + Format.bytes(report.ramFreed()) + " de memória livre  (" + Format.bytes(report.ramBefore)
                + " → " + Format.bytes(report.ramAfter) + ")"
                : "A memória já estava em ordem (" + Format.bytes(report.ramAfter) + " livres).";
        if (report.deepStop) {
            ram += report.stoppedApps.isEmpty()
                    ? "\nNenhum app estava pesando agora."
                    : "\n" + Format.plural(report.stoppedApps.size(), "app fechado", "apps fechados") + ": "
                    + ForceStopService.summary(toResult(report));
            if (report.cancelled) {
                ram += "\nVocê interrompeu no meio.";
            }
        } else {
            ram += "\nAtive o assistente do Luma abaixo para liberar bem mais.";
        }
        reportCard.addView(resultLine("ram", "Memória", ram));
        Ui.gap(reportCard, 8);
        long cache = report.systemCacheFreed + report.ownCacheFreed + Math.max(0L, report.allAppsCacheFreed) + report.deepCacheFreed;
        String cacheText;
        if (!report.cacheClearedApps.isEmpty()) {
            cacheText = Format.bytes(cache) + " de lixo apagado · " + Format.plural(report.cacheClearedApps.size(), "app limpo", "apps limpos")
                    + ": " + joinLimited(report.cacheClearedApps);
        } else if (report.allAppsCacheFreed >= 0L) {
            cacheText = cache > 0L ? Format.bytes(cache) + " de lixo apagado dos apps" : "Os apps já estavam limpos.";
        } else if (CacheCleaner.canClearAllAppsCache(activity) || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            cacheText = cache > 0L ? Format.bytes(cache) + " removidos" : "Os apps já estavam limpos.";
        } else {
            cacheText = (cache > 0L ? Format.bytes(cache) + " removidos. " : "")
                    + "Ative o assistente do Luma para limpar todos os apps.";
        }
        reportCard.addView(resultLine("clean", "Lixo dos apps", cacheText));
        Ui.gap(reportCard, 8);
        if (report.junkSkipped) {
            reportCard.addView(resultLine("storage", "Arquivos esquecidos",
                    "Permita o acesso aos arquivos para o Luma encontrar o que sobra."));
        } else {
            reportCard.addView(resultLine("storage", "Arquivos esquecidos",
                    report.junkFreed > 0L ? Format.bytes(report.junkFreed) + " removidos" : "Nada sobrando por aqui."));
        }
        Ui.gap(reportCard, 8);
        reportCard.addView(resultLine("check", "Espaço livre",
                Format.bytes(report.storageBefore) + " → " + Format.bytes(report.storageAfter)
                        + "  (+" + Format.bytes(report.storageFreed()) + ")"));
        statusLine.setText(String.format(PT_BR, "Feito em %.0f segundos.", report.durationMs / 1000f));
    }

    private static String joinLimited(java.util.List<String> names) {
        ForceStopService.Result result = new ForceStopService.Result();
        result.stopped.addAll(names);
        return ForceStopService.summary(result);
    }

    private static ForceStopService.Result toResult(Optimizer.Report report) {
        ForceStopService.Result result = new ForceStopService.Result();
        result.stopped.addAll(report.stoppedApps);
        return result;
    }

    private View resultLine(String icon, String title, String detail) {
        LinearLayout row = Ui.horizontal(activity);
        row.setGravity(Gravity.TOP);
        View iconView = Ui.iconBadge(activity, icon, Ui.GREEN, Ui.SURFACE, 32);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(32), dp(32));
        iconParams.setMargins(0, 0, dp(12), 0);
        row.addView(iconView, iconParams);
        LinearLayout texts = Ui.vertical(activity);
        texts.addView(Ui.title(activity, title, 14.5f));
        Ui.gap(texts, 3);
        texts.addView(Ui.small(activity, detail, Ui.TEXT));
        row.addView(texts, Ui.weight(1));
        return row;
    }

    void updatePermissions() {
        permissionsCard.removeAllViews();
        boolean files = Permissions.hasAllFilesAccess(activity);
        boolean usage = Permissions.hasUsageAccess(activity);
        boolean settings = Permissions.canWriteSettings(activity);
        boolean deep = ForceStopService.isEnabled(activity) || Prefs.rootEnabled(activity);
        if (files && usage && settings && deep) {
            permissionsCard.setVisibility(View.GONE);
            return;
        }
        permissionsCard.setVisibility(View.VISIBLE);
        permissionsCard.addView(Ui.title(activity, "Deixe o Luma trabalhar por completo", 16));
        Ui.gap(permissionsCard, 4);
        permissionsCard.addView(Ui.small(activity,
                "Cada permissão destrava uma parte da limpeza. Nada sai do seu " + Ui.device(activity) + ".", Ui.MUTED));
        if (!deep) {
            Ui.gap(permissionsCard, 12);
            permissionsCard.addView(permissionRow("Assistente do Luma",
                    "Fecha os apps que ficam escondidos e limpa o lixo de cada um.", new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            DeepStopSetup.show(activity);
                        }
                    }));
        }
        if (!files) {
            Ui.gap(permissionsCard, 12);
            permissionsCard.addView(permissionRow("Seus arquivos",
                    "Para encontrar arquivos esquecidos, instaladores velhos e pastas vazias.", new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            Guide.open(activity, Guide.FILES);
                        }
                    }));
        }
        if (!usage) {
            Ui.gap(permissionsCard, 12);
            permissionsCard.addView(permissionRow("Uso dos apps",
                    "Para saber quais apps você usa e quanto espaço cada um ocupa.", new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            Guide.open(activity, Guide.USAGE);
                        }
                    }));
        }
        if (!settings) {
            Ui.gap(permissionsCard, 12);
            permissionsCard.addView(permissionRow("Ajustes do " + Ui.device(activity),
                    "Para desligar o que gasta bateria à toa, quando você pedir.", new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            Guide.open(activity, Guide.SETTINGS);
                        }
                    }));
        }
    }

    private TextView statValue() {
        TextView value = Ui.title(activity, "—", 17);
        value.setLineSpacing(dp(3), 1.0f);
        return value;
    }

    private View statRow(String leftIcon, String leftLabel, TextView leftValue, String rightIcon, String rightLabel, TextView rightValue) {
        LinearLayout row = Ui.horizontal(activity);
        row.setGravity(Gravity.TOP);
        row.setBaselineAligned(false);
        LinearLayout.LayoutParams left = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
        left.setMargins(0, 0, dp(12), 0);
        View leftCard = statCard(leftIcon, leftLabel, leftValue, Ui.BLUE, Ui.BLUE_SOFT);
        View rightCard = statCard(rightIcon, rightLabel, rightValue, Ui.BLUE, Ui.BLUE_SOFT);
        if (!Ui.twoColumns(activity)) {
            leftCard.setMinimumHeight(dp(120));
            rightCard.setMinimumHeight(dp(120));
        }
        row.addView(leftCard, left);
        row.addView(rightCard, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
        return row;
    }

    private View statCard(String icon, String label, TextView value, int color, int fill) {
        if (Ui.twoColumns(activity)) {
            // Compacto em tela grande: ícone pequeno ao lado do nome e o número embaixo, na largura toda.
            LinearLayout card = Ui.card(activity);
            card.setPadding(dp(14), dp(12), dp(14), dp(12));
            LinearLayout top = Ui.horizontal(activity);
            LinearLayout.LayoutParams badge = new LinearLayout.LayoutParams(dp(28), dp(28));
            badge.setMargins(0, 0, dp(10), 0);
            top.addView(Ui.iconBadge(activity, icon, color, fill, 28), badge);
            TextView name = Ui.overline(activity, label, Ui.MUTED);
            name.setMaxLines(2);
            name.setLineSpacing(dp(2), 1f);
            top.addView(name, Ui.weight(1));
            card.addView(top, Ui.matchWrap());
            Ui.gap(card, 8);
            value.setTextSize(15f);
            card.addView(value, Ui.matchWrap());
            return card;
        }
        LinearLayout card = Ui.card(activity);
        card.addView(Ui.iconBadge(activity, icon, color, fill, 36), new LinearLayout.LayoutParams(dp(36), dp(36)));
        Ui.gap(card, 12);
        card.addView(Ui.overline(activity, label, Ui.MUTED));
        Ui.gap(card, 6);
        card.addView(value);
        return card;
    }
}

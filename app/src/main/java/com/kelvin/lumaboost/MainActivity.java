package com.kelvin.lumaboost;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.StatFs;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.Locale;

@SuppressLint("SetTextI18n")
public final class MainActivity extends Activity {
    private static final int BACKGROUND = Color.rgb(246, 247, 249);
    private static final int SURFACE = Color.WHITE;
    private static final int TEXT = Color.rgb(17, 24, 39);
    private static final int MUTED = Color.rgb(102, 112, 133);
    private static final int BLUE = Color.rgb(0, 122, 255);
    private static final int GREEN = Color.rgb(52, 199, 89);
    private static final int ORANGE = Color.rgb(255, 159, 10);
    private static final int RED = Color.rgb(255, 59, 48);
    private static final int BORDER = Color.rgb(229, 231, 235);

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            refreshStats();
            handler.postDelayed(this, 3500L);
        }
    };

    private MemoryOptimizer optimizer;
    private DeviceSampler sampler;
    private MemoryGaugeView gaugeView;
    private TextView statusTitle;
    private TextView statusDetail;
    private TextView ramFreeValue;
    private TextView ramUsedValue;
    private TextView cpuValue;
    private TextView heapValue;
    private TextView storageFreeValue;
    private TextView uptimeValue;
    private TextView lastActionValue;
    private LinearLayout signalsList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        optimizer = new MemoryOptimizer(getApplicationContext());
        sampler = new DeviceSampler(this);
        configureSystemBars();
        setContentView(createContent());
        refreshStats();
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.removeCallbacks(refreshRunnable);
        handler.post(refreshRunnable);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(refreshRunnable);
        super.onPause();
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        optimizer.releaseLocalPressure();
    }

    private View createContent() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(false);
        scrollView.setClipToPadding(false);
        scrollView.setBackgroundColor(BACKGROUND);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(24), dp(22), dp(28));
        scrollView.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        root.addView(createHeader());
        addGap(root, 18);

        gaugeView = new MemoryGaugeView(this);
        gaugeView.setElevation(dp(1));
        root.addView(gaugeView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(260)
        ));
        addGap(root, 14);

        root.addView(createStatusPanel());
        addGap(root, 18);

        root.addView(sectionTitle("Leitura atual"));
        addGap(root, 8);
        root.addView(createStatsGrid());
        addGap(root, 18);

        root.addView(sectionTitle("Ações rápidas"));
        addGap(root, 8);
        root.addView(actionButton("Otimizar agora", "boost", true, new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                runOptimization();
            }
        }));
        addGap(root, 10);
        root.addView(actionButton("Apps do sistema", "apps", false, new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openSystemScreen(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS);
            }
        }));
        addGap(root, 10);
        root.addView(actionButton("Armazenamento", "storage", false, new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openSystemScreen(Settings.ACTION_INTERNAL_STORAGE_SETTINGS);
            }
        }));
        addGap(root, 10);
        root.addView(actionButton("Bateria", "battery", false, new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openSystemScreen(Settings.ACTION_BATTERY_SAVER_SETTINGS);
            }
        }));
        addGap(root, 10);
        root.addView(actionButton("Atualizar leitura", "refresh", false, new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                refreshStats();
            }
        }));
        addGap(root, 18);

        root.addView(sectionTitle("Sinais do sistema"));
        addGap(root, 8);
        signalsList = new LinearLayout(this);
        signalsList.setOrientation(LinearLayout.VERTICAL);
        root.addView(signalsList, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        return scrollView;
    }

    private View createHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(this);
        title.setText("Luma Boost");
        title.setTextColor(TEXT);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 34);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setIncludeFontPadding(false);
        header.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView subtitle = new TextView(this);
        subtitle.setText("Performance limpa, monitor leve e controle seguro.");
        subtitle.setTextColor(MUTED);
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        subtitle.setIncludeFontPadding(true);
        subtitle.setLineSpacing(dp(1), 1.0f);
        header.addView(subtitle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        return header;
    }

    private View createStatusPanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(18), dp(16), dp(18), dp(16));
        panel.setBackground(rounded(SURFACE, BORDER, 1, 8));
        panel.setElevation(dp(1));

        statusTitle = new TextView(this);
        statusTitle.setTextColor(TEXT);
        statusTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        statusTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        statusTitle.setIncludeFontPadding(false);
        panel.addView(statusTitle);

        addGap(panel, 6);

        statusDetail = new TextView(this);
        statusDetail.setTextColor(MUTED);
        statusDetail.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        statusDetail.setLineSpacing(dp(2), 1.0f);
        panel.addView(statusDetail);

        addGap(panel, 12);

        lastActionValue = new TextView(this);
        lastActionValue.setText("Pronto para otimizar.");
        lastActionValue.setTextColor(BLUE);
        lastActionValue.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        lastActionValue.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        panel.addView(lastActionValue);

        return panel;
    }

    private View createStatsGrid() {
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);

        LinearLayout firstRow = new LinearLayout(this);
        firstRow.setOrientation(LinearLayout.HORIZONTAL);
        ramFreeValue = valueText();
        ramUsedValue = valueText();
        firstRow.addView(statCard("RAM livre", ramFreeValue), weightedCardParams(true));
        firstRow.addView(statCard("RAM usada", ramUsedValue), weightedCardParams(false));
        wrapper.addView(firstRow);

        addGap(wrapper, 10);

        LinearLayout secondRow = new LinearLayout(this);
        secondRow.setOrientation(LinearLayout.HORIZONTAL);
        cpuValue = valueText();
        heapValue = valueText();
        secondRow.addView(statCard("CPU do app", cpuValue), weightedCardParams(true));
        secondRow.addView(statCard("Heap do app", heapValue), weightedCardParams(false));
        wrapper.addView(secondRow);

        addGap(wrapper, 10);

        LinearLayout thirdRow = new LinearLayout(this);
        thirdRow.setOrientation(LinearLayout.HORIZONTAL);
        storageFreeValue = valueText();
        uptimeValue = valueText();
        thirdRow.addView(statCard("Armazenamento livre", storageFreeValue), weightedCardParams(true));
        thirdRow.addView(statCard("Sistema ativo", uptimeValue), weightedCardParams(false));
        wrapper.addView(thirdRow);

        return wrapper;
    }

    private LinearLayout.LayoutParams weightedCardParams(boolean withEndMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0,
                dp(96),
                1.0f
        );
        if (withEndMargin) {
            params.setMargins(0, 0, dp(10), 0);
        }
        return params;
    }

    private View statCard(String label, TextView value) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackground(rounded(SURFACE, BORDER, 1, 8));
        card.setElevation(dp(1));

        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTextColor(MUTED);
        labelView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        labelView.setIncludeFontPadding(false);
        card.addView(labelView);

        addGap(card, 8);
        card.addView(value);

        return card;
    }

    private TextView valueText() {
        TextView textView = new TextView(this);
        textView.setTextColor(TEXT);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 21);
        textView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        textView.setIncludeFontPadding(false);
        textView.setSingleLine(false);
        return textView;
    }

    private TextView sectionTitle(String text) {
        TextView title = new TextView(this);
        title.setText(text);
        title.setTextColor(TEXT);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setIncludeFontPadding(false);
        return title;
    }

    private View actionButton(String label, String iconType, boolean primary, View.OnClickListener listener) {
        TextView button = new TextView(this);
        button.setText(label);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setGravity(Gravity.CENTER_VERTICAL);
        button.setMinHeight(dp(56));
        button.setPadding(dp(18), 0, dp(18), 0);
        button.setTextColor(primary ? Color.WHITE : TEXT);
        button.setBackground(primary
                ? rounded(BLUE, BLUE, 1, 8)
                : rounded(SURFACE, BORDER, 1, 8));
        button.setCompoundDrawablePadding(dp(12));
        button.setCompoundDrawablesWithIntrinsicBounds(
                new ActionIconDrawable(iconType, primary ? Color.WHITE : BLUE, dp(22)),
                null,
                null,
                null
        );
        button.setOnClickListener(listener);
        button.setClickable(true);
        button.setFocusable(true);
        applySelectableForeground(button);

        return button;
    }

    private void refreshStats() {
        DeviceSnapshot snapshot = sampler.read();
        int pressure = snapshot.usedPercent();
        int score = snapshot.score();

        gaugeView.setMetrics(score, pressure, snapshot.lowMemory);
        ramFreeValue.setText(formatBytes(snapshot.availableBytes));
        ramUsedValue.setText(String.format(Locale.getDefault(), "%d%%", pressure));
        cpuValue.setText(formatCpu(snapshot.cpuPercent));
        heapValue.setText(formatBytes(snapshot.appHeapUsedBytes));
        storageFreeValue.setText(formatBytes(snapshot.storageAvailableBytes));
        uptimeValue.setText(formatDuration(snapshot.uptimeMs));

        if (snapshot.lowMemory || pressure >= 90) {
            statusTitle.setText("Pressão alta");
            statusDetail.setText("RAM disponível: " + formatBytes(snapshot.availableBytes)
                    + " de " + formatBytes(snapshot.totalBytes)
                    + ". Use os atalhos para revisar apps, bateria e armazenamento.");
        } else if (pressure >= 76) {
            statusTitle.setText("Atenção");
            statusDetail.setText("RAM disponível: " + formatBytes(snapshot.availableBytes)
                    + " de " + formatBytes(snapshot.totalBytes)
                    + ". Armazenamento livre: " + formatBytes(snapshot.storageAvailableBytes) + ".");
        } else {
            statusTitle.setText("Estável");
            statusDetail.setText("RAM disponível: " + formatBytes(snapshot.availableBytes)
                    + " de " + formatBytes(snapshot.totalBytes)
                    + ". O Luma está operando em modo leve e sem serviço persistente.");
        }

        updateSignals(snapshot);
    }

    private void updateSignals(DeviceSnapshot snapshot) {
        signalsList.removeAllViews();
        signalsList.addView(signalRow("Memória", signalTextForMemory(snapshot), colorForScore(snapshot.score())));
        addGap(signalsList, 8);
        signalsList.addView(signalRow("CPU local", cpuSignal(snapshot), cpuColor(snapshot)));
        addGap(signalsList, 8);
        signalsList.addView(signalRow("Armazenamento", storageSignal(snapshot), storageColor(snapshot)));
        addGap(signalsList, 8);
        signalsList.addView(signalRow("Recomendação", recommendationText(snapshot), BLUE));
        addGap(signalsList, 8);
        signalsList.addView(signalRow("Privacidade", "Sem permissões invasivas, sem monitor em segundo plano e sem coleta externa.", GREEN));
    }

    private String signalTextForMemory(DeviceSnapshot snapshot) {
        if (snapshot.lowMemory) {
            return "O Android marcou o dispositivo em baixa memória.";
        }
        if (snapshot.usedPercent() >= 90) {
            return "Uso alto de RAM detectado agora.";
        }
        if (snapshot.usedPercent() >= 76) {
            return "Uso moderado, com margem reduzida.";
        }
        return "Uso saudável para a leitura atual.";
    }

    private String cpuSignal(DeviceSnapshot snapshot) {
        if (snapshot.cpuPercent < 1.0d) {
            return "Consumo abaixo de 1% nesta amostra.";
        }
        return "Consumo de " + formatCpu(snapshot.cpuPercent) + " nesta amostra.";
    }

    private String storageSignal(DeviceSnapshot snapshot) {
        int freePercent = snapshot.storageFreePercent();
        if (freePercent <= 8) {
            return "Pouco espaço livre: " + formatBytes(snapshot.storageAvailableBytes)
                    + " de " + formatBytes(snapshot.storageTotalBytes) + ".";
        }
        if (freePercent <= 15) {
            return "Espaço livre moderado: " + formatBytes(snapshot.storageAvailableBytes)
                    + " disponíveis.";
        }
        return "Espaço saudável: " + formatBytes(snapshot.storageAvailableBytes)
                + " disponíveis.";
    }

    private String recommendationText(DeviceSnapshot snapshot) {
        if (snapshot.lowMemory || snapshot.usedPercent() >= 90) {
            return "Toque em otimizar e revise apps recentes na tela do sistema.";
        }
        if (snapshot.storageFreePercent() <= 15) {
            return "Abra Armazenamento para remover arquivos grandes e caches de outros apps.";
        }
        if (snapshot.cpuPercent >= 20.0d) {
            return "Aguarde uma nova leitura ou feche tarefas pesadas antes de jogar ou editar.";
        }
        return "Nenhuma ação urgente. Mantenha o app leve e atualize a leitura quando precisar.";
    }

    private int cpuColor(DeviceSnapshot snapshot) {
        if (snapshot.cpuPercent >= 20.0d) {
            return ORANGE;
        }
        return GREEN;
    }

    private int storageColor(DeviceSnapshot snapshot) {
        if (snapshot.storageFreePercent() <= 8) {
            return RED;
        }
        if (snapshot.storageFreePercent() <= 15) {
            return ORANGE;
        }
        return GREEN;
    }

    private View signalRow(String label, String value, int accent) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(12), dp(14), dp(12));
        row.setBackground(rounded(SURFACE, BORDER, 1, 8));

        View dot = new View(this);
        dot.setBackground(oval(accent));
        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dp(10), dp(10));
        dotParams.setMargins(0, 0, dp(12), 0);
        row.addView(dot, dotParams);

        LinearLayout textColumn = new LinearLayout(this);
        textColumn.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(this);
        title.setText(label);
        title.setTextColor(TEXT);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setIncludeFontPadding(false);
        textColumn.addView(title);

        addGap(textColumn, 4);

        TextView detail = new TextView(this);
        detail.setText(value);
        detail.setTextColor(MUTED);
        detail.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        detail.setLineSpacing(dp(1), 1.0f);
        textColumn.addView(detail);

        row.addView(textColumn, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1.0f
        ));

        return row;
    }

    private void runOptimization() {
        OptimizationResult result = optimizer.optimize();
        refreshStats();

        String cacheFreed = formatBytes(result.cacheBytesFreed);
        String heapReleased = formatBytes(Math.max(0L, result.heapBytesReleased));
        lastActionValue.setText("Liberado: " + cacheFreed + " em cache | heap: " + heapReleased + ".");

        Toast.makeText(
                this,
                "Otimização concluída",
                Toast.LENGTH_SHORT
        ).show();
    }

    private void openSystemScreen(String action) {
        Intent intent = new Intent(action);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(intent);
        } catch (RuntimeException exception) {
            Intent fallback = new Intent(Settings.ACTION_SETTINGS);
            fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                startActivity(fallback);
            } catch (RuntimeException fallbackException) {
                Toast.makeText(this, "Não foi possível abrir esta tela do sistema.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @SuppressWarnings("deprecation")
    private void configureSystemBars() {
        Window window = getWindow();
        window.setStatusBarColor(BACKGROUND);
        window.setNavigationBarColor(BACKGROUND);
        int flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        getWindow().getDecorView().setSystemUiVisibility(flags);
    }

    private void applySelectableForeground(View view) {
        TypedValue outValue = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
        view.setForeground(getResources().getDrawable(outValue.resourceId, getTheme()));
    }

    private GradientDrawable rounded(int fillColor, int strokeColor, int strokeWidthDp, float radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fillColor);
        drawable.setCornerRadius(dp(radiusDp));
        drawable.setStroke(dp(strokeWidthDp), strokeColor);
        return drawable;
    }

    private GradientDrawable oval(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(color);
        return drawable;
    }

    private void addGap(LinearLayout parent, int heightDp) {
        View gap = new View(this);
        parent.addView(gap, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(heightDp)
        ));
    }

    private int colorForScore(int score) {
        if (score >= 55) {
            return GREEN;
        }
        if (score >= 30) {
            return ORANGE;
        }
        return RED;
    }

    private String formatCpu(double cpuPercent) {
        if (cpuPercent < 1.0d) {
            return "<1%";
        }
        return String.format(Locale.getDefault(), "%.0f%%", Math.min(cpuPercent, 100.0d));
    }

    private String formatBytes(long bytes) {
        double value = Math.max(0L, bytes);
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        int unit = 0;
        while (value >= 1024.0d && unit < units.length - 1) {
            value /= 1024.0d;
            unit++;
        }
        if (unit == 0) {
            return String.format(Locale.getDefault(), "%.0f %s", value, units[unit]);
        }
        if (value >= 10.0d) {
            return String.format(Locale.getDefault(), "%.0f %s", value, units[unit]);
        }
        return String.format(Locale.getDefault(), "%.1f %s", value, units[unit]);
    }

    private String formatDuration(long milliseconds) {
        long totalMinutes = Math.max(0L, milliseconds / 60000L);
        long days = totalMinutes / 1440L;
        long hours = (totalMinutes % 1440L) / 60L;
        long minutes = totalMinutes % 60L;
        if (days > 0L) {
            return String.format(Locale.getDefault(), "%dd %dh", days, hours);
        }
        if (hours > 0L) {
            return String.format(Locale.getDefault(), "%dh %dm", hours, minutes);
        }
        return String.format(Locale.getDefault(), "%dm", minutes);
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class DeviceSampler {
        private final ActivityManager activityManager;
        private long lastCpuMs;
        private long lastWallMs;

        DeviceSampler(Context context) {
            activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        }

        DeviceSnapshot read() {
            ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
            activityManager.getMemoryInfo(memoryInfo);

            long nowCpuMs = Process.getElapsedCpuTime();
            long nowWallMs = SystemClock.elapsedRealtime();
            double cpuPercent = 0.0d;

            if (lastWallMs > 0L) {
                long wallDelta = Math.max(1L, nowWallMs - lastWallMs);
                long cpuDelta = Math.max(0L, nowCpuMs - lastCpuMs);
                int cores = Math.max(1, Runtime.getRuntime().availableProcessors());
                cpuPercent = (cpuDelta * 100.0d) / (wallDelta * cores);
            }

            lastCpuMs = nowCpuMs;
            lastWallMs = nowWallMs;

            Runtime runtime = Runtime.getRuntime();
            long appHeapUsed = runtime.totalMemory() - runtime.freeMemory();
            long appHeapMax = runtime.maxMemory();
            StatFs dataStats = new StatFs(Environment.getDataDirectory().getAbsolutePath());
            long storageAvailable = dataStats.getAvailableBytes();
            long storageTotal = dataStats.getTotalBytes();

            return new DeviceSnapshot(
                    memoryInfo.availMem,
                    memoryInfo.totalMem,
                    memoryInfo.threshold,
                    memoryInfo.lowMemory,
                    appHeapUsed,
                    appHeapMax,
                    cpuPercent,
                    storageAvailable,
                    storageTotal,
                    SystemClock.elapsedRealtime()
            );
        }
    }

    private static final class DeviceSnapshot {
        final long availableBytes;
        final long totalBytes;
        final long thresholdBytes;
        final boolean lowMemory;
        final long appHeapUsedBytes;
        final long appHeapMaxBytes;
        final double cpuPercent;
        final long storageAvailableBytes;
        final long storageTotalBytes;
        final long uptimeMs;

        DeviceSnapshot(
                long availableBytes,
                long totalBytes,
                long thresholdBytes,
                boolean lowMemory,
                long appHeapUsedBytes,
                long appHeapMaxBytes,
                double cpuPercent,
                long storageAvailableBytes,
                long storageTotalBytes,
                long uptimeMs
        ) {
            this.availableBytes = availableBytes;
            this.totalBytes = Math.max(1L, totalBytes);
            this.thresholdBytes = thresholdBytes;
            this.lowMemory = lowMemory;
            this.appHeapUsedBytes = appHeapUsedBytes;
            this.appHeapMaxBytes = appHeapMaxBytes;
            this.cpuPercent = cpuPercent;
            this.storageAvailableBytes = storageAvailableBytes;
            this.storageTotalBytes = Math.max(1L, storageTotalBytes);
            this.uptimeMs = uptimeMs;
        }

        int usedPercent() {
            long usedBytes = Math.max(0L, totalBytes - availableBytes);
            return clamp(Math.round((usedBytes * 100.0f) / totalBytes));
        }

        int score() {
            int score = 100 - usedPercent();
            if (lowMemory || availableBytes < thresholdBytes) {
                score = Math.min(score, 24);
            }
            return clamp(score);
        }

        int storageFreePercent() {
            return clamp(Math.round((storageAvailableBytes * 100.0f) / storageTotalBytes));
        }

        private static int clamp(int value) {
            return Math.max(0, Math.min(100, value));
        }
    }

    private static final class MemoryOptimizer {
        private final Context appContext;

        MemoryOptimizer(Context context) {
            appContext = context.getApplicationContext();
        }

        OptimizationResult optimize() {
            Runtime runtime = Runtime.getRuntime();
            long heapBefore = runtime.totalMemory() - runtime.freeMemory();
            long cacheFreed = cleanAppCaches();
            releaseLocalPressure();
            long heapAfter = runtime.totalMemory() - runtime.freeMemory();
            return new OptimizationResult(cacheFreed, heapBefore - heapAfter);
        }

        void releaseLocalPressure() {
            System.gc();
            System.runFinalization();
            Runtime.getRuntime().gc();
        }

        private long cleanAppCaches() {
            long freed = 0L;
            freed += deleteContents(appContext.getCacheDir());
            if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
                File[] externalCaches = appContext.getExternalCacheDirs();
                if (externalCaches != null) {
                    for (File cache : externalCaches) {
                        freed += deleteContents(cache);
                    }
                }
            }
            return freed;
        }

        private long deleteContents(File directory) {
            if (directory == null || !directory.exists() || !directory.isDirectory()) {
                return 0L;
            }
            File[] children = directory.listFiles();
            if (children == null) {
                return 0L;
            }
            long freed = 0L;
            for (File child : children) {
                freed += deleteRecursively(child);
            }
            return freed;
        }

        private long deleteRecursively(File file) {
            if (file == null || !file.exists()) {
                return 0L;
            }
            long freed = 0L;
            if (file.isDirectory()) {
                File[] children = file.listFiles();
                if (children != null) {
                    for (File child : children) {
                        freed += deleteRecursively(child);
                    }
                }
            } else {
                freed = Math.max(0L, file.length());
            }
            if (!file.delete() && !file.isDirectory()) {
                return 0L;
            }
            return freed;
        }
    }

    private static final class OptimizationResult {
        final long cacheBytesFreed;
        final long heapBytesReleased;

        OptimizationResult(long cacheBytesFreed, long heapBytesReleased) {
            this.cacheBytesFreed = cacheBytesFreed;
            this.heapBytesReleased = heapBytesReleased;
        }
    }

    private final class MemoryGaugeView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF cardRect = new RectF();
        private final RectF arcRect = new RectF();
        private int score = 100;
        private int usedPercent = 0;
        private boolean lowMemory;

        MemoryGaugeView(Context context) {
            super(context);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            setContentDescription("Indicador de folga do sistema");
        }

        void setMetrics(int score, int usedPercent, boolean lowMemory) {
            this.score = Math.max(0, Math.min(100, score));
            this.usedPercent = Math.max(0, Math.min(100, usedPercent));
            this.lowMemory = lowMemory;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float width = getWidth();
            float height = getHeight();
            cardRect.set(0, 0, width, height);

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(SURFACE);
            paint.setShadowLayer(dp(8), 0, dp(3), Color.argb(18, 15, 23, 42));
            canvas.drawRoundRect(cardRect, dp(8), dp(8), paint);
            paint.clearShadowLayer();

            float size = Math.min(width, height) - dp(72);
            float left = (width - size) / 2.0f;
            float top = dp(48);
            arcRect.set(left, top, left + size, top + size);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(14));
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setColor(Color.rgb(232, 236, 241));
            canvas.drawArc(arcRect, 140.0f, 260.0f, false, paint);

            paint.setColor(colorForScore(score));
            canvas.drawArc(arcRect, 140.0f, 260.0f * (score / 100.0f), false, paint);

            paint.setStrokeCap(Paint.Cap.BUTT);
            paint.setStyle(Paint.Style.FILL);
            paint.setTextAlign(Paint.Align.CENTER);

            paint.setColor(MUTED);
            paint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            paint.setTextSize(dp(13));
            canvas.drawText("FOLGA DO SISTEMA", width / 2.0f, dp(32), paint);

            paint.setColor(TEXT);
            paint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            paint.setTextSize(dp(56));
            Paint.FontMetrics numberMetrics = paint.getFontMetrics();
            float numberBase = arcRect.centerY() - (numberMetrics.ascent + numberMetrics.descent) / 2.0f - dp(4);
            canvas.drawText(String.valueOf(score), width / 2.0f, numberBase, paint);

            paint.setColor(MUTED);
            paint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            paint.setTextSize(dp(14));
            String caption = lowMemory ? "baixa memória" : "RAM usada: " + usedPercent + "%";
            canvas.drawText(caption, width / 2.0f, arcRect.bottom + dp(28), paint);
        }
    }

    private static final class ActionIconDrawable extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final String type;
        private final int color;
        private final int size;

        ActionIconDrawable(String type, int color, int size) {
            this.type = type;
            this.color = color;
            this.size = size;
            setBounds(0, 0, size, size);
        }

        @Override
        public void draw(Canvas canvas) {
            Rect bounds = getBounds();
            canvas.save();
            canvas.translate(bounds.left, bounds.top);
            canvas.scale(bounds.width() / 24.0f, bounds.height() / 24.0f);
            paint.setColor(color);
            paint.setStrokeWidth(2.0f);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);

            if ("boost".equals(type)) {
                paint.setStyle(Paint.Style.FILL);
                path.reset();
                path.moveTo(13.0f, 2.5f);
                path.lineTo(4.5f, 13.0f);
                path.lineTo(11.0f, 13.0f);
                path.lineTo(9.5f, 21.5f);
                path.lineTo(19.5f, 9.5f);
                path.lineTo(13.0f, 9.5f);
                path.close();
                canvas.drawPath(path, paint);
            } else if ("apps".equals(type)) {
                paint.setStyle(Paint.Style.STROKE);
                canvas.drawRoundRect(new RectF(4, 4, 10, 10), 1.6f, 1.6f, paint);
                canvas.drawRoundRect(new RectF(14, 4, 20, 10), 1.6f, 1.6f, paint);
                canvas.drawRoundRect(new RectF(4, 14, 10, 20), 1.6f, 1.6f, paint);
                canvas.drawRoundRect(new RectF(14, 14, 20, 20), 1.6f, 1.6f, paint);
            } else if ("storage".equals(type)) {
                paint.setStyle(Paint.Style.STROKE);
                canvas.drawRoundRect(new RectF(4, 6, 20, 19), 3.0f, 3.0f, paint);
                canvas.drawLine(7, 10, 17, 10, paint);
                canvas.drawLine(8, 15, 11, 15, paint);
            } else if ("battery".equals(type)) {
                paint.setStyle(Paint.Style.STROKE);
                canvas.drawRoundRect(new RectF(3, 7, 19, 17), 2.0f, 2.0f, paint);
                canvas.drawRoundRect(new RectF(20, 10, 22, 14), 1.0f, 1.0f, paint);
                paint.setStyle(Paint.Style.FILL);
                canvas.drawRoundRect(new RectF(6, 10, 14, 14), 1.0f, 1.0f, paint);
            } else {
                paint.setStyle(Paint.Style.STROKE);
                RectF arc = new RectF(5, 5, 19, 19);
                canvas.drawArc(arc, 35, 275, false, paint);
                paint.setStyle(Paint.Style.FILL);
                path.reset();
                path.moveTo(17.5f, 3.0f);
                path.lineTo(20.5f, 8.0f);
                path.lineTo(14.7f, 7.2f);
                path.close();
                canvas.drawPath(path, paint);
            }
            canvas.restore();
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
            paint.setColorFilter(colorFilter);
        }

        @Override
        @SuppressWarnings("deprecation")
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }

        @Override
        public int getIntrinsicWidth() {
            return size;
        }

        @Override
        public int getIntrinsicHeight() {
            return size;
        }
    }
}

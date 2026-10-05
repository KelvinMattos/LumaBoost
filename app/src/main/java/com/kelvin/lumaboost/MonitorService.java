package com.kelvin.lumaboost;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;

/**
 * Monitoramento ativo: notificação fixa com RAM e armazenamento, botão "Otimizar" e otimização
 * automática a cada bloqueio de tela.
 *
 * Com a tela bloqueada o Android não deixa a acessibilidade operar outras telas, então sem root a
 * otimização do bloqueio é a silenciosa (cache, temporários, processos em segundo plano). Com root
 * ela também encerra os apps em segundo plano com "am force-stop".
 */
public final class MonitorService extends Service {
    static final String ACTION_OPTIMIZE = "com.kelvin.lumaboost.monitor.OPTIMIZE";
    static final String ACTION_STOP = "com.kelvin.lumaboost.monitor.STOP";
    private static final String CHANNEL_ID = "monitor_ativo";
    private static final int NOTIFICATION_ID = 77;
    /** Espera após bloquear: se a pessoa desbloquear logo em seguida, nada é feito. */
    private static final long LOCK_DELAY_MS = 6000L;
    /** Intervalo mínimo entre otimizações automáticas, para não gastar bateria. */
    private static final long MIN_INTERVAL_MS = 3L * 60L * 1000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable lockOptimization = new Runnable() {
        @Override
        public void run() {
            optimize(false);
        }
    };
    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                handler.removeCallbacks(lockOptimization);
                if (SystemClock.elapsedRealtime() - lastRun > MIN_INTERVAL_MS) {
                    handler.postDelayed(lockOptimization, LOCK_DELAY_MS);
                }
            } else if (Intent.ACTION_SCREEN_ON.equals(action) || Intent.ACTION_USER_PRESENT.equals(action)) {
                handler.removeCallbacks(lockOptimization);
                refresh();
            }
        }
    };

    private long lastRun;
    private boolean running;
    private String status;

    static void start(Context context) {
        Intent intent = new Intent(context, MonitorService.class);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
        } catch (RuntimeException ignored) {
            // Início em segundo plano bloqueado pelo sistema; volta a tentar quando o app abrir.
        }
    }

    static void stop(Context context) {
        context.stopService(new Intent(context, MonitorService.class));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
        Notification notification = build();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(screenReceiver, filter);
        }
        if (Prefs.rootEnabled(this)) {
            // Confirma o root em segundo plano para o botão Otimizar da notificação agir sem abrir o app.
            Tasks.run(new Tasks.Job<Boolean>() {
                @Override
                public Boolean run() {
                    return RootShell.available(MonitorService.this);
                }
            }, new Tasks.Done<Boolean>() {
                @Override
                public void done(Boolean result, Exception error) {
                    refresh();
                }
            });
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            Prefs.setMonitorEnabled(this, false);
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }
        if (ACTION_OPTIMIZE.equals(action)) {
            optimize(true);
        } else {
            refresh();
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(lockOptimization);
        try {
            unregisterReceiver(screenReceiver);
        } catch (RuntimeException ignored) {
            // Já removido.
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void optimize(final boolean manual) {
        if (running) {
            return;
        }
        running = true;
        lastRun = SystemClock.elapsedRealtime();
        status = "Otimizando…";
        refresh();
        Tasks.run(new Tasks.Job<AutoOptimizer.Summary>() {
            @Override
            public AutoOptimizer.Summary run() {
                return AutoOptimizer.runSilent(MonitorService.this);
            }
        }, new Tasks.Done<AutoOptimizer.Summary>() {
            @Override
            public void done(AutoOptimizer.Summary summary, Exception error) {
                running = false;
                status = summary == null ? null : (manual ? "Otimizado agora: " : "Faxina ao bloquear: ") + summary.text();
                refresh();
            }
        });
    }

    private void refresh() {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        try {
            manager.notify(NOTIFICATION_ID, build());
        } catch (RuntimeException ignored) {
            // Notificações bloqueadas pelo usuário.
        }
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        manager.deleteNotificationChannel("monitor");
        // Importância padrão, mas sem som nem vibração: fica na área principal, não em "Silenciosas".
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Luma sempre ligado", NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription("Aviso fixo com a memória livre e o botão Otimizar.");
        channel.setSound(null, null);
        channel.enableVibration(false);
        channel.enableLights(false);
        channel.setShowBadge(false);
        manager.createNotificationChannel(channel);
    }

    @SuppressWarnings("deprecation")
    private Notification build() {
        DeviceStats stats = DeviceStats.read(this);
        String content = "Memória " + Format.bytes(stats.ramAvailable) + " · Espaço " + Format.bytes(stats.storageFree) + " livres";
        String detail = status != null ? status : lastSummary();

        // Com root a otimização roda aqui mesmo, sem abrir nada. Sem root, abre o app para a
        // otimização profunda (que precisa da tela).
        PendingIntent optimize = Prefs.rootEnabled(this) && RootShell.isGrantedCached()
                ? PendingIntent.getService(this, 11, new Intent(this, MonitorService.class).setAction(ACTION_OPTIMIZE), immutable())
                : activityIntent(MainActivity.ACTION_BOOST, 12);
        PendingIntent stop = PendingIntent.getService(this, 13, new Intent(this, MonitorService.class).setAction(ACTION_STOP), immutable());
        String title = "Seu " + Ui.device(this) + " está " + healthWord(stats.score());

        // Layout próprio: o botão Otimizar aparece mesmo com a notificação recolhida.
        android.widget.RemoteViews small = new android.widget.RemoteViews(getPackageName(), R.layout.notification_monitor);
        small.setTextViewText(R.id.notif_title, title);
        small.setTextViewText(R.id.notif_text, content);
        small.setTextViewText(R.id.notif_optimize, running ? "…" : "Otimizar");
        small.setOnClickPendingIntent(R.id.notif_optimize, optimize);
        android.widget.RemoteViews big = new android.widget.RemoteViews(getPackageName(), R.layout.notification_monitor);
        big.setTextViewText(R.id.notif_title, title);
        big.setTextViewText(R.id.notif_text, content);
        if (detail != null) {
            big.setTextViewText(R.id.notif_detail, detail);
            big.setViewVisibility(R.id.notif_detail, android.view.View.VISIBLE);
        }
        big.setTextViewText(R.id.notif_optimize, running ? "…" : "Otimizar");
        big.setOnClickPendingIntent(R.id.notif_optimize, optimize);

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this).setPriority(Notification.PRIORITY_DEFAULT).setSound(null).setVibrate(null);
        builder.setSmallIcon(R.drawable.ic_tile)
                .setContentTitle(title)
                .setContentText(content)
                .setOngoing(true)
                .setShowWhen(false)
                .setOnlyAlertOnce(true)
                .setColor(Ui.BLUE)
                .setContentIntent(activityIntent(Intent.ACTION_MAIN, 10))
                .addAction(new Notification.Action.Builder(Icon.createWithResource(this, R.drawable.ic_tile), "Desativar", stop).build());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            builder.setStyle(new Notification.DecoratedCustomViewStyle())
                    .setCustomContentView(small)
                    .setCustomBigContentView(big);
        } else {
            builder.setContent(small);
        }
        if (Build.VERSION.SDK_INT >= 31) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
        }
        return builder.build();
    }

    private static String healthWord(int score) {
        if (score >= 80) {
            return "em ótima forma";
        }
        if (score >= 55) {
            return "bem";
        }
        if (score >= 30) {
            return "um pouco pesado";
        }
        return "pesado: toque em Otimizar";
    }

    private String lastSummary() {
        String summary = Prefs.lastAutoSummary(this);
        return summary == null ? "Faço uma faxina toda vez que você bloquear a tela." : "Última: " + summary;
    }

    private PendingIntent activityIntent(String action, int requestCode) {
        Intent intent = new Intent(this, MainActivity.class)
                .setAction(action)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(this, requestCode, intent, immutable());
    }

    private static int immutable() {
        return PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
    }
}

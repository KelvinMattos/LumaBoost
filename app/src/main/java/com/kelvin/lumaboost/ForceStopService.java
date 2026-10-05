package com.kelvin.lumaboost;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Otimização profunda: abre a tela de informações de cada app escolhido e toca em "Forçar parada"
 * ou, na tela de armazenamento, em "Limpar cache".
 *
 * É a única forma, sem root, de realmente tirar outro app da memória ou apagar o cache interno dele
 * no Android atual. O serviço só age quando o usuário pede; não lê nem guarda conteúdo de outras telas.
 *
 * O Android pode desligar e religar o serviço a qualquer momento (por exemplo quando pacotes mudam).
 * Por isso o trabalho em andamento fica em {@link Job}, estático: uma nova instância continua de onde
 * a anterior parou.
 */
public final class ForceStopService extends AccessibilityService {
    interface Listener {
        void onFinished(Result result);
    }

    static final class Result {
        final List<String> stopped = new ArrayList<>();
        final List<String> cacheCleared = new ArrayList<>();
        int alreadyStopped;
        int failed;
        boolean cancelled;
    }

    static final int MODE_FORCE_STOP = 0;
    static final int MODE_CLEAR_CACHE = 1;
    /** Fire TV: "Limpar o cache de todos os aplicativos" em Configurações → Aplicativos, de uma vez. */
    static final int MODE_CLEAR_ALL_CACHE = 2;

    private static final int STATE_FIND_BUTTON = 0;
    private static final int STATE_CONFIRM = 1;
    private static final int STATE_FIND_STORAGE = 2;
    private static final int STATE_CLEAR_CACHE = 3;
    private static final int STATE_VERIFY_CACHE = 4;
    /** Ação concluída; aguardando a transição para o próximo app. Ignora eventos nesse intervalo. */
    private static final int STATE_WAIT_NEXT = 5;
    private static final int STATE_FIND_ALL_CACHE = 6;
    private static final int STATE_CONFIRM_ALL_CACHE = 7;
    private static final long FIND_TIMEOUT_MS = 4500L;
    private static final long CONFIRM_TIMEOUT_MS = 2500L;
    private static final long POLL_MS = 120L;
    /** Quanto esperar o Android religar o serviço antes de desistir do trabalho. */
    private static final long RECONNECT_TIMEOUT_MS = 10000L;

    /** Trabalho em andamento; sobrevive à troca de instância do serviço. */
    private static final class Job {
        final int mode;
        final List<AppCatalog.AppEntry> queue;
        final Listener listener;
        final Result result = new Result();
        int index = -1;
        int state;
        long deadline;
        int scrolls;
        long lastScroll;
        boolean cancelled;
        long disconnectedSince;
        /** Fire TV: posição do item a tocar e desde quando ela não muda (as listas se rearrumam ao abrir). */
        final Rect targetBounds = new Rect();
        long targetSince;

        Job(int mode, List<AppCatalog.AppEntry> queue, Listener listener) {
            this.mode = mode;
            this.queue = queue;
            this.listener = listener;
        }
    }

    private static final Handler HANDLER = new Handler(Looper.getMainLooper());
    private static final Runnable TICK = new Runnable() {
        @Override
        public void run() {
            tick();
        }
    };

    private static ForceStopService instance;
    private static Job job;

    // ---------- Guia de permissões ----------
    private static int guideStep = -1;
    private static long guideDeadline;
    private static boolean guideLeftApp;
    private static int guideScrolls;
    private static long guideLastScroll;
    private static final Runnable GUIDE_TICK = new Runnable() {
        @Override
        public void run() {
            guideTick();
        }
    };
    private GuideHighlightView guideView;

    private Set<String> forceStopLabels;
    private Set<String> okLabels;
    private Set<String> storageLabels;
    private Set<String> clearCacheLabels;
    private Set<String> clearDataLabels;
    private View overlay;
    private TextView overlayDetail;
    private ProgressBar overlayProgress;

    // ---------- API usada pelas telas ----------

    static boolean isEnabled(Context context) {
        String enabled = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabled == null) {
            return false;
        }
        ComponentName component = new ComponentName(context, ForceStopService.class);
        for (String entry : enabled.split(":")) {
            if (component.equals(ComponentName.unflattenFromString(entry))) {
                return true;
            }
        }
        return false;
    }

    static boolean isReady() {
        return instance != null;
    }

    static boolean isBusy() {
        return job != null;
    }

    /** Espera o serviço (ativado nas configurações) terminar de conectar. Bloqueante. */
    static boolean awaitReady(Context context, long timeoutMs) {
        if (!isEnabled(context)) {
            return false;
        }
        long end = SystemClock.uptimeMillis() + timeoutMs;
        while (instance == null && SystemClock.uptimeMillis() < end) {
            Tasks.sleep(100L);
        }
        return instance != null;
    }

    /** Começa a encerrar os apps. Retorna false se o serviço não estiver ativo. */
    static boolean stopApps(List<AppCatalog.AppEntry> apps, Listener listener) {
        return start(MODE_FORCE_STOP, apps, listener);
    }

    /** Toca em "Limpar cache" na tela de armazenamento de cada app. Nunca em "Limpar armazenamento". */
    static boolean clearCaches(List<AppCatalog.AppEntry> apps, Listener listener) {
        return start(MODE_CLEAR_CACHE, apps, listener);
    }

    /** Fire TV: limpa o cache de todos os apps pela opção das Configurações da Amazon. */
    static boolean clearAllCaches(Listener listener) {
        AppCatalog.AppEntry all = new AppCatalog.AppEntry();
        all.label = "Todos os apps";
        List<AppCatalog.AppEntry> queue = new ArrayList<>();
        queue.add(all);
        return start(MODE_CLEAR_ALL_CACHE, queue, listener);
    }

    /**
     * Liga o próprio assistente quando o Luma tem WRITE_SECURE_SETTINGS (concedida via ADB). É o caminho
     * no Fire TV, cujas Configurações não listam serviços de acessibilidade de outros apps.
     */
    static boolean enableSelf(Context context) {
        if (isEnabled(context) || !Permissions.hasSecureSettings(context)) {
            return isEnabled(context);
        }
        String self = new ComponentName(context, ForceStopService.class).flattenToString();
        String enabled = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        String value = enabled == null || enabled.trim().isEmpty() ? self : enabled + ":" + self;
        try {
            Settings.Secure.putString(context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, value);
            Settings.Secure.putInt(context.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, 1);
        } catch (RuntimeException exception) {
            return false;
        }
        return isEnabled(context);
    }

    private static boolean start(int mode, List<AppCatalog.AppEntry> apps, Listener listener) {
        if (instance == null || job != null) {
            return false;
        }
        job = new Job(mode, new ArrayList<>(apps), listener);
        instance.showOverlay();
        next();
        return true;
    }

    // ---------- Ciclo de vida ----------

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        loadLabels();
        // Garante a árvore completa (algumas chaves do Configurações são marcadas como "não importantes").
        android.accessibilityservice.AccessibilityServiceInfo info = getServiceInfo();
        if (info != null) {
            info.flags |= android.accessibilityservice.AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
                    | android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
                    | android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
            setServiceInfo(info);
        }
        if (Guide.pending == Guide.ASSISTANT) {
            // A pessoa acabou de ligar o assistente: volta ao tutorial sem precisar procurar o app.
            HANDLER.postDelayed(new Runnable() {
                @Override
                public void run() {
                    Guide.returnToApp(ForceStopService.this);
                }
            }, 700L);
        }
        if (guideStep >= 0) {
            HANDLER.removeCallbacks(GUIDE_TICK);
            HANDLER.postDelayed(GUIDE_TICK, 300L);
        }
        if (job != null) {
            // Religado no meio de um trabalho: o TICK reabre a tela do app atual e retoma.
            if (job.disconnectedSince == 0L) {
                job.disconnectedSince = SystemClock.uptimeMillis();
            }
            showOverlay();
            updateOverlay();
            HANDLER.removeCallbacks(TICK);
            HANDLER.postDelayed(TICK, 300L);
        }
    }

    @Override
    public boolean onUnbind(Intent intent) {
        detach();
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        detach();
        super.onDestroy();
    }

    private void detach() {
        hideOverlay();
        hideGuide();
        if (instance == this) {
            instance = null;
            if (job != null && job.disconnectedSince == 0L) {
                job.disconnectedSince = SystemClock.uptimeMillis();
            }
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // A automação usa sondagem curta da janela ativa; eventos só aceleram o próximo passo.
        if (job != null) {
            HANDLER.removeCallbacks(TICK);
            HANDLER.postDelayed(TICK, 40L);
        } else if (guideStep >= 0) {
            HANDLER.removeCallbacks(GUIDE_TICK);
            HANDLER.postDelayed(GUIDE_TICK, 120L);
        }
    }

    /**
     * Fire TV: enquanto o Luma trabalha, as teclas do controle não chegam às Configurações (um OK por engano
     * acionaria a linha focada). Voltar cancela, já que o painel de progresso não recebe foco.
     */
    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        Job current = job;
        if (current == null || !FireTv.is(this)) {
            return false;
        }
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
            current.cancelled = true;
            HANDLER.removeCallbacks(TICK);
            HANDLER.post(TICK);
        }
        return true;
    }

    @Override
    public void onInterrupt() {
        // Só interrompe "feedback" (fala/vibração), que este serviço não produz: não é um pedido de cancelamento.
    }

    /** Mostra "Toque aqui" sobre a chave certa nas configurações até a permissão ser liberada. */
    static void startGuide(int step) {
        guideStep = step;
        guideLeftApp = false;
        guideScrolls = 0;
        guideDeadline = SystemClock.uptimeMillis() + 120000L;
        HANDLER.removeCallbacks(GUIDE_TICK);
        HANDLER.postDelayed(GUIDE_TICK, 600L);
    }

    static void stopGuide() {
        guideStep = -1;
        HANDLER.removeCallbacks(GUIDE_TICK);
        if (instance != null) {
            instance.hideGuide();
        }
    }

    private static void guideTick() {
        if (guideStep < 0) {
            return;
        }
        ForceStopService service = instance;
        if (service == null) {
            HANDLER.postDelayed(GUIDE_TICK, 500L);
            return;
        }
        if (Guide.isGranted(service, guideStep)) {
            stopGuide();
            Guide.returnToApp(service);
            return;
        }
        if (SystemClock.uptimeMillis() > guideDeadline || job != null) {
            stopGuide();
            return;
        }
        AccessibilityNodeInfo root = service.getRootInActiveWindow();
        boolean inLuma = root != null && service.getPackageName().contentEquals(safe(root.getPackageName()));
        if (inLuma) {
            if (guideLeftApp) {
                // A pessoa voltou para o Luma sem liberar: encerra o guia.
                stopGuide();
                return;
            }
            // As configurações ainda estão abrindo.
            HANDLER.postDelayed(GUIDE_TICK, 300L);
            return;
        }
        if (root != null) {
            guideLeftApp = true;
            service.guideTarget(root);
        }
        HANDLER.postDelayed(GUIDE_TICK, 400L);
    }

    /**
     * Aponta para a chave do Luma: na linha que tem o nome do app (telas em lista), ou para a única
     * chave da tela (páginas só do Luma). Se o Luma não está visível na lista, rola até aparecer.
     */
    private void guideTarget(AccessibilityNodeInfo root) {
        Rect bounds = new Rect();
        AccessibilityNodeInfo label = findAppLabel(root, 0);
        // 1) Lista com várias chaves: a chave da linha onde está escrito "Luma Boost".
        if (label != null) {
            AccessibilityNodeInfo row = label;
            AccessibilityNodeInfo toggle = null;
            for (int i = 0; i < 6 && row != null && toggle == null; i++) {
                toggle = findToggle(row, 0);
                if (toggle == null) {
                    row = row.getParent();
                }
            }
            if (toggle != null && countToggles(row, 0) == 1) {
                if (!toggle.isChecked()) {
                    toggle.getBoundsInScreen(bounds);
                    showGuide(bounds, "Toque aqui para permitir");
                }
                return;
            }
        }
        // 2) Página só do Luma: a única chave da tela.
        if (countToggles(root, 0) == 1) {
            AccessibilityNodeInfo only = findToggle(root, 0);
            if (only != null && !only.isChecked()) {
                only.getBoundsInScreen(bounds);
                showGuide(bounds, "Toque aqui para permitir");
            }
            return;
        }
        // 3) Nome do Luma visível mas sem chave por perto.
        if (label != null) {
            label.getBoundsInScreen(bounds);
            AccessibilityNodeInfo scroller = detailPane(root);
            boolean inList = scroller != root && scroller.getChildCount() > 4;
            // Numa lista longa é preciso entrar no Luma; numa página só dele, a chave fica logo abaixo do nome.
            showGuide(bounds, inList ? "Toque em Luma Boost" : "Ligue a chave logo abaixo");
            return;
        }
        // 4) Luma fora da tela: rola a lista até ele aparecer.
        long now = SystemClock.uptimeMillis();
        if (guideScrolls < 30 && now - guideLastScroll > 700L) {
            guideLastScroll = now;
            guideScrolls++;
            scrollForward(detailPane(root));
            showGuide(null, "Procurando o Luma Boost na lista…");
        } else {
            showGuide(null, "Procure por Luma Boost na lista");
        }
    }

    private AccessibilityNodeInfo findAppLabel(AccessibilityNodeInfo node, int depth) {
        if (node == null || depth > 40) {
            return null;
        }
        String text = safe(node.getText()).trim();
        String name = getString(R.string.app_name);
        if (text.equalsIgnoreCase(name) || text.startsWith(name + " ")) {
            return node;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo found = findAppLabel(node.getChild(i), depth + 1);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /** Quantas chaves distintas há na tela (nós sobrepostos, comuns no Compose, contam como uma). */
    private static int countToggles(AccessibilityNodeInfo node, int depth) {
        List<AccessibilityNodeInfo> toggles = new ArrayList<>();
        collectToggles(node, toggles, depth);
        List<Rect> distinct = new ArrayList<>();
        for (AccessibilityNodeInfo toggle : toggles) {
            Rect bounds = new Rect();
            toggle.getBoundsInScreen(bounds);
            boolean merged = false;
            for (Rect other : distinct) {
                if (Rect.intersects(other, bounds)) {
                    other.union(bounds);
                    merged = true;
                    break;
                }
            }
            if (!merged) {
                distinct.add(bounds);
            }
        }
        return distinct.size();
    }

    private static void collectToggles(AccessibilityNodeInfo node, List<AccessibilityNodeInfo> out, int depth) {
        if (node == null || depth > 40) {
            return;
        }
        // Listas recarregadas depois da abertura deixam o cache de acessibilidade velho: atualiza.
        node.refresh();
        if (isToggle(node)) {
            out.add(node);
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collectToggles(node.getChild(i), out, depth + 1);
        }
    }

    private static boolean isToggle(AccessibilityNodeInfo node) {
        CharSequence className = node.getClassName();
        return node.isCheckable() && node.isVisibleToUser() && className != null
                && (className.toString().contains("Switch") || className.toString().contains("Toggle"));
    }

    /** A chave visível da tela; entre nós sobrepostos, a menor (o botão em si, não a linha). */
    private static AccessibilityNodeInfo findToggle(AccessibilityNodeInfo node, int depth) {
        List<AccessibilityNodeInfo> toggles = new ArrayList<>();
        collectToggles(node, toggles, depth);
        AccessibilityNodeInfo best = null;
        long bestArea = Long.MAX_VALUE;
        Rect bounds = new Rect();
        for (AccessibilityNodeInfo toggle : toggles) {
            toggle.getBoundsInScreen(bounds);
            long area = (long) bounds.width() * bounds.height();
            if (area < bestArea) {
                bestArea = area;
                best = toggle;
            }
        }
        return best;
    }

    private void showGuide(Rect bounds, String text) {
        if (guideView == null) {
            guideView = new GuideHighlightView(this);
            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT);
            try {
                ((WindowManager) getSystemService(WINDOW_SERVICE)).addView(guideView, params);
            } catch (RuntimeException ignored) {
                guideView = null;
                return;
            }
        }
        guideView.point(bounds, text);
    }

    private void hideGuide() {
        if (guideView == null) {
            return;
        }
        guideView.stop();
        try {
            ((WindowManager) getSystemService(WINDOW_SERVICE)).removeView(guideView);
        } catch (RuntimeException ignored) {
            // Já removida.
        }
        guideView = null;
    }

    // ---------- Máquina de estados ----------

    private static void next() {
        HANDLER.removeCallbacks(TICK);
        Job current = job;
        if (current == null) {
            return;
        }
        current.index++;
        if (current.cancelled || current.index >= current.queue.size()) {
            finish();
            return;
        }
        if (instance == null) {
            // Sem serviço no momento: o TICK espera a religação e abre o app atual depois.
            current.index--;
            HANDLER.postDelayed(TICK, 250L);
            return;
        }
        openCurrent(current);
    }

    private static void openCurrent(Job current) {
        current.disconnectedSince = 0L;
        AppCatalog.AppEntry app = current.queue.get(current.index);
        instance.updateOverlay();
        Intent intent = current.mode == MODE_CLEAR_ALL_CACHE
                ? FireTv.applicationsIntent()
                : new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + app.packageName));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK
                | Intent.FLAG_ACTIVITY_NO_HISTORY | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        try {
            instance.startActivity(intent);
        } catch (RuntimeException exception) {
            current.result.failed++;
            HANDLER.post(new Runnable() {
                @Override
                public void run() {
                    next();
                }
            });
            return;
        }
        current.state = current.mode == MODE_CLEAR_ALL_CACHE ? STATE_FIND_ALL_CACHE
                : current.mode == MODE_CLEAR_CACHE ? STATE_FIND_STORAGE : STATE_FIND_BUTTON;
        current.scrolls = 0;
        current.lastScroll = 0L;
        current.targetBounds.setEmpty();
        // O primeiro app espera mais: o app Configurações pode estar abrindo do zero.
        current.deadline = SystemClock.uptimeMillis() + FIND_TIMEOUT_MS + (current.index == 0 ? 3000L : 0L);
        HANDLER.postDelayed(TICK, 250L);
    }

    private static void tick() {
        Job current = job;
        if (current == null) {
            return;
        }
        if (current.cancelled) {
            finish();
            return;
        }
        if (instance == null) {
            long since = current.disconnectedSince == 0L ? SystemClock.uptimeMillis() : current.disconnectedSince;
            current.disconnectedSince = since;
            if (SystemClock.uptimeMillis() - since > RECONNECT_TIMEOUT_MS) {
                current.cancelled = true;
                finish();
            } else {
                HANDLER.postDelayed(TICK, 250L);
            }
            return;
        }
        if (current.index < 0) {
            next();
            return;
        }
        if (current.disconnectedSince != 0L) {
            // Acabou de religar: reabre a tela do app atual para garantir o estado.
            current.disconnectedSince = 0L;
            openCurrent(current);
            return;
        }
        instance.step(current);
    }

    private void step(final Job current) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        long now = SystemClock.uptimeMillis();
        AppCatalog.AppEntry app = current.queue.get(current.index);
        boolean foreign = root != null && !getPackageName().contentEquals(safe(root.getPackageName()));

        switch (current.state) {
            case STATE_FIND_BUTTON: {
                boolean settled = now > current.deadline - FIND_TIMEOUT_MS + 1500L;
                if (foreign && (settled || showsApp(root, app))) {
                    AccessibilityNodeInfo button = findExact(root, forceStopLabels);
                    if (button == null) {
                        List<AccessibilityNodeInfo> byId = root.findAccessibilityNodeInfosByViewId("com.android.settings:id/force_stop_button");
                        button = byId == null || byId.isEmpty() ? null : byId.get(0);
                    }
                    if (button != null) {
                        if (!button.isEnabled()) {
                            current.result.alreadyStopped++;
                            next();
                            return;
                        }
                        if (settled(current, button) && click(button)) {
                            if (FireTv.is(this)) {
                                // O Fire TV para o app na hora, sem diálogo de confirmação.
                                current.result.stopped.add(app.label);
                                current.state = STATE_WAIT_NEXT;
                                HANDLER.removeCallbacks(TICK);
                                HANDLER.postDelayed(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (job == current && current.state == STATE_WAIT_NEXT) {
                                            next();
                                        }
                                    }
                                }, 400L);
                                return;
                            }
                            current.state = STATE_CONFIRM;
                            current.deadline = now + CONFIRM_TIMEOUT_MS;
                            HANDLER.postDelayed(TICK, POLL_MS);
                            return;
                        }
                    }
                }
                break;
            }
            case STATE_CONFIRM: {
                AccessibilityNodeInfo ok = root == null ? null : findConfirm(root);
                if (ok != null && click(ok)) {
                    current.result.stopped.add(app.label);
                    current.state = STATE_WAIT_NEXT;
                    HANDLER.removeCallbacks(TICK);
                    HANDLER.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (job == current && current.state == STATE_WAIT_NEXT) {
                                next();
                            }
                        }
                    }, 150L);
                    return;
                }
                break;
            }
            case STATE_FIND_STORAGE: {
                boolean settled = now > current.deadline - FIND_TIMEOUT_MS + 1500L;
                if (foreign && (settled || showsApp(root, app))) {
                    // Fire TV e alguns fabricantes: "Limpar cache" já fica na página do app.
                    AccessibilityNodeInfo direct = findClearCache(root);
                    if (direct != null && showsApp(root, app)) {
                        current.state = STATE_CLEAR_CACHE;
                        current.deadline = now + FIND_TIMEOUT_MS;
                        HANDLER.postDelayed(TICK, POLL_MS);
                        return;
                    }
                    // Em tablets o Configurações abre em dois painéis: procura só no painel do app.
                    AccessibilityNodeInfo pane = detailPane(root);
                    AccessibilityNodeInfo entry = findExact(pane, storageLabels);
                    if (entry != null && click(entry)) {
                        current.state = STATE_CLEAR_CACHE;
                        current.deadline = now + FIND_TIMEOUT_MS;
                        HANDLER.postDelayed(TICK, 300L);
                        return;
                    }
                    if (entry == null && settled && current.scrolls < 8 && now - current.lastScroll > 450L) {
                        current.lastScroll = now;
                        current.scrolls++;
                        if (scrollForward(pane)) {
                            current.deadline = Math.max(current.deadline, now + 1500L);
                        }
                    }
                }
                break;
            }
            case STATE_CLEAR_CACHE: {
                AccessibilityNodeInfo button = root == null ? null : findClearCache(root);
                if (button != null) {
                    if (button.isEnabled()) {
                        if (settled(current, button) && click(button)) {
                            current.state = STATE_VERIFY_CACHE;
                            current.deadline = now + 1500L;
                            HANDLER.postDelayed(TICK, 200L);
                            return;
                        }
                    } else if (now > current.deadline - FIND_TIMEOUT_MS + 2500L) {
                        // Botão desabilitado depois do cálculo dos tamanhos: cache já está vazio.
                        next();
                        return;
                    }
                }
                break;
            }
            case STATE_VERIFY_CACHE: {
                AccessibilityNodeInfo button = root == null ? null : findClearCache(root);
                if (button == null || !button.isEnabled() || now > current.deadline) {
                    current.result.cacheCleared.add(app.label);
                    next();
                    return;
                }
                break;
            }
            case STATE_FIND_ALL_CACHE: {
                if (foreign) {
                    AccessibilityNodeInfo entry = findAllCacheEntry(root, 0);
                    if (entry != null && settled(current, entry) && click(entry)) {
                        current.state = STATE_CONFIRM_ALL_CACHE;
                        current.deadline = now + CONFIRM_TIMEOUT_MS + 1500L;
                        HANDLER.postDelayed(TICK, 300L);
                        return;
                    }
                }
                break;
            }
            case STATE_CONFIRM_ALL_CACHE: {
                // Só confirma o diálogo que fala de cache: nunca um de "Limpar dados".
                // Depois de um toque o Android segue tratando a lista como janela ativa: procura o diálogo em todas.
                AccessibilityNodeInfo dialog = cacheDialog(root);
                AccessibilityNodeInfo ok = dialog != null ? findConfirm(dialog) : null;
                if (ok != null && settled(current, ok) && click(ok)) {
                    current.result.cacheCleared.add(app.label);
                    current.state = STATE_WAIT_NEXT;
                    HANDLER.removeCallbacks(TICK);
                    // O Fire TV apaga em segundo plano: espera um pouco antes de medir.
                    HANDLER.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (job == current && current.state == STATE_WAIT_NEXT) {
                                next();
                            }
                        }
                    }, 2500L);
                    return;
                }
                break;
            }
            case STATE_WAIT_NEXT:
                return;
            default:
                break;
        }
        if (now > current.deadline) {
            current.result.failed++;
            next();
            return;
        }
        HANDLER.postDelayed(TICK, POLL_MS);
    }

    private static void finish() {
        HANDLER.removeCallbacks(TICK);
        Job finished = job;
        job = null;
        if (finished == null) {
            return;
        }
        finished.result.cancelled = finished.cancelled;
        ForceStopService service = instance;
        if (service != null) {
            service.hideOverlay();
            // Sem GLOBAL_ACTION_BACK: ele é assíncrono e fecharia o próprio Luma depois de reaberto.
            // As telas de detalhes usam NO_HISTORY e somem sozinhas quando o Luma volta à frente.
            Intent back = new Intent(service, MainActivity.class);
            back.setAction(MainActivity.ACTION_RETURN);
            back.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            try {
                service.startActivity(back);
            } catch (RuntimeException ignored) {
                // O usuário volta manualmente.
            }
        }
        if (finished.listener != null) {
            finished.listener.onFinished(finished.result);
        }
    }

    // ---------- Busca de elementos ----------

    /** Confere que a tela aberta é a do app certo (o nome aparece no cabeçalho). */
    private static boolean showsApp(AccessibilityNodeInfo root, AppCatalog.AppEntry app) {
        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(app.label);
        if (nodes != null && !nodes.isEmpty()) {
            return true;
        }
        Set<String> label = new LinkedHashSet<>();
        label.add(app.label);
        return walkExact(root, label, 0) != null;
    }

    /** Nó cujo texto é exatamente um dos rótulos (sem diferenciar maiúsculas). */
    private static AccessibilityNodeInfo findExact(AccessibilityNodeInfo root, Set<String> labels) {
        for (String label : labels) {
            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(label);
            if (nodes == null) {
                continue;
            }
            for (AccessibilityNodeInfo node : nodes) {
                if (label.equalsIgnoreCase(safe(node.getText()).trim())) {
                    return node;
                }
            }
        }
        // Telas em Jetpack Compose (Android 15+) nem sempre respondem à busca nativa: percorre a árvore.
        return walkExact(root, labels, 0);
    }

    private static AccessibilityNodeInfo walkExact(AccessibilityNodeInfo node, Set<String> labels, int depth) {
        if (node == null || depth > 40) {
            return null;
        }
        String text = safe(node.getText()).trim();
        if (text.isEmpty()) {
            text = safe(node.getContentDescription()).trim();
        }
        if (!text.isEmpty()) {
            for (String label : labels) {
                if (label.equalsIgnoreCase(text)) {
                    return node;
                }
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo found = walkExact(node.getChild(i), labels, depth + 1);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /** "Limpar cache", com trava dupla para nunca acertar "Limpar armazenamento/dados". */
    private AccessibilityNodeInfo findClearCache(AccessibilityNodeInfo root) {
        AccessibilityNodeInfo node = findExact(root, clearCacheLabels);
        if (node == null) {
            return null;
        }
        String text = safe(node.getText()).trim();
        for (String forbidden : clearDataLabels) {
            if (forbidden.equalsIgnoreCase(text)) {
                return null;
            }
        }
        return node;
    }

    private AccessibilityNodeInfo findConfirm(AccessibilityNodeInfo root) {
        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId("android:id/button1");
        if (nodes != null) {
            for (AccessibilityNodeInfo node : nodes) {
                String text = safe(node.getText()).trim();
                if (okLabels.contains(text.toLowerCase(Locale.ROOT)) || forceStopLabels.contains(text)) {
                    return node;
                }
            }
        }
        // Busca por texto só com rótulos de confirmação: "Forçar parada" casaria com o botão da página.
        Set<String> confirmWords = new LinkedHashSet<>();
        for (String label : okLabels) {
            if (!forceStopLabels.contains(label) && !label.contains("parada") && !label.contains("stop")) {
                confirmWords.add(label);
            }
        }
        AccessibilityNodeInfo ok = findExact(root, confirmWords);
        return ok != null && ok.isEnabled() ? ok : null;
    }

    /** Item "Limpar o cache de todos os aplicativos" (Fire TV), sem nunca casar com "Limpar dados". */
    private AccessibilityNodeInfo findAllCacheEntry(AccessibilityNodeInfo node, int depth) {
        if (node == null || depth > 40) {
            return null;
        }
        String text = safe(node.getText()).trim().toLowerCase(Locale.ROOT);
        if (text.contains("cache") && !text.contains("dados") && !text.contains("data")
                && (text.contains("todos") || text.contains("todas") || text.contains(" all") || text.contains("tous") || text.contains("alle"))) {
            return node;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo found = findAllCacheEntry(node.getChild(i), depth + 1);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /** Raiz da janela com o diálogo de limpeza de cache (a ativa ou outra das janelas da tela). */
    private AccessibilityNodeInfo cacheDialog(AccessibilityNodeInfo active) {
        if (active != null && mentionsCache(active, 0)) {
            return active;
        }
        List<android.view.accessibility.AccessibilityWindowInfo> windows = getWindows();
        if (windows == null) {
            return null;
        }
        for (android.view.accessibility.AccessibilityWindowInfo window : windows) {
            AccessibilityNodeInfo windowRoot = window.getRoot();
            if (windowRoot != null && !getPackageName().contentEquals(safe(windowRoot.getPackageName()))
                    && mentionsCache(windowRoot, 0)) {
                return windowRoot;
            }
        }
        return null;
    }

    /** O diálogo aberto fala de cache (título ou mensagem). */
    private static boolean mentionsCache(AccessibilityNodeInfo node, int depth) {
        if (node == null || depth > 30) {
            return false;
        }
        String id = node.getViewIdResourceName();
        if (id != null && (id.endsWith(":id/alertTitle") || id.endsWith(":id/message"))
                && safe(node.getText()).toLowerCase(Locale.ROOT).contains("cache")) {
            return true;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (mentionsCache(node.getChild(i), depth + 1)) {
                return true;
            }
        }
        return false;
    }

    /** Painel de conteúdo: em telas divididas, a lista rolável mais à direita; senão, a tela toda. */
    private static AccessibilityNodeInfo detailPane(AccessibilityNodeInfo root) {
        List<AccessibilityNodeInfo> scrollables = new ArrayList<>();
        collectScrollables(root, scrollables, 0);
        if (scrollables.size() < 2) {
            return root;
        }
        AccessibilityNodeInfo best = null;
        Rect bestBounds = new Rect();
        Rect bounds = new Rect();
        for (AccessibilityNodeInfo node : scrollables) {
            node.getBoundsInScreen(bounds);
            if (best == null || bounds.left > bestBounds.left
                    || (bounds.left == bestBounds.left && bounds.width() * bounds.height() > bestBounds.width() * bestBounds.height())) {
                best = node;
                bestBounds.set(bounds);
            }
        }
        return best == null ? root : best;
    }

    private static void collectScrollables(AccessibilityNodeInfo node, List<AccessibilityNodeInfo> out, int depth) {
        if (node == null || depth > 30) {
            return;
        }
        if (node.isScrollable() && node.isVisibleToUser()) {
            out.add(node);
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collectScrollables(node.getChild(i), out, depth + 1);
        }
    }

    private static boolean scrollForward(AccessibilityNodeInfo node) {
        if (node == null) {
            return false;
        }
        if (node.isScrollable() && node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) {
            return true;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (scrollForward(node.getChild(i))) {
                return true;
            }
        }
        return false;
    }

    /**
     * No Fire TV o toque vai por coordenadas: só toca quando o item está parado no mesmo lugar há um tempo.
     * As Configurações da Amazon inserem linhas logo depois de abrir ("Permissões", por exemplo), e um toque
     * na posição antiga acertaria a linha vizinha ("Iniciar aplicativo", "Desinstalar"…).
     */
    private boolean settled(Job current, AccessibilityNodeInfo node) {
        if (!FireTv.is(this)) {
            return true;
        }
        AccessibilityNodeInfo target = node;
        while (target != null && !target.isClickable()) {
            target = target.getParent();
        }
        if (target == null) {
            return false;
        }
        target.refresh();
        Rect bounds = new Rect();
        target.getBoundsInScreen(bounds);
        long now = SystemClock.uptimeMillis();
        if (!bounds.equals(current.targetBounds)) {
            current.targetBounds.set(bounds);
            current.targetSince = now;
            return false;
        }
        return now - current.targetSince >= 450L;
    }

    private boolean click(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo target = node;
        while (target != null && !target.isClickable()) {
            target = target.getParent();
        }
        if (target == null || !target.isEnabled()) {
            return false;
        }
        if (FireTv.is(this)) {
            // As listas das Configurações da Amazon ignoram ACTION_CLICK (só reagem ao OK do controle),
            // mas aceitam toque: toca no centro do item, lido agora para não acertar a linha vizinha.
            return tap(target);
        }
        return target.performAction(AccessibilityNodeInfo.ACTION_CLICK);
    }

    private boolean tap(AccessibilityNodeInfo target) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return target.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        }
        target.refresh();
        Rect bounds = new Rect();
        target.getBoundsInScreen(bounds);
        if (!target.isVisibleToUser() || bounds.width() <= 0 || bounds.height() <= 0) {
            return false;
        }
        Path path = new Path();
        path.moveTo(bounds.exactCenterX(), bounds.exactCenterY());
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0L, 60L))
                .build();
        return dispatchGesture(gesture, null, null);
    }

    /** Textos dos botões no idioma do aparelho, lidos do próprio app Configurações. */
    private void loadLabels() {
        forceStopLabels = new LinkedHashSet<>();
        okLabels = new LinkedHashSet<>();
        storageLabels = new LinkedHashSet<>();
        clearCacheLabels = new LinkedHashSet<>();
        clearDataLabels = new LinkedHashSet<>();
        PackageManager pm = getPackageManager();
        try {
            Resources settings = pm.getResourcesForApplication("com.android.settings");
            for (String name : new String[]{"force_stop", "force_stop_dlg_title"}) {
                int id = settings.getIdentifier(name, "string", "com.android.settings");
                if (id != 0) {
                    String value = settings.getString(id).replace("?", "").trim();
                    if (!value.isEmpty()) {
                        forceStopLabels.add(value);
                    }
                }
            }
            addResource(settings, storageLabels, "storage_settings_for_app", "storage_settings", "storage_label");
            addResource(settings, clearCacheLabels, "clear_cache_btn_text");
            addResource(settings, clearDataLabels, "clear_user_data_text", "clear_storage_btn_text", "clear_data_btn_text");
            int okId = settings.getIdentifier("dlg_ok", "string", "com.android.settings");
            if (okId != 0) {
                okLabels.add(settings.getString(okId).toLowerCase(Locale.ROOT));
            }
        } catch (PackageManager.NameNotFoundException | Resources.NotFoundException ignored) {
            // Fabricante com app de configurações próprio: usa os textos conhecidos abaixo.
        }
        String[] forceStop = {"Forçar parada", "Forçar a interrupção", "Forçar paragem", "Force stop", "Forzar detención", "Forzar cierre",
                "Forcer l'arrêt", "Beenden erzwingen", "Arresto forzato", "Остановить", "强行停止", "結束運作", "Durdurmaya zorla"};
        for (String label : forceStop) {
            forceStopLabels.add(label);
        }
        String[] storage = {"Armazenamento e cache", "Armazenamento", "Storage & cache", "Storage", "Almacenamiento y caché", "Almacenamiento"};
        for (String label : storage) {
            storageLabels.add(label);
        }
        String[] clearCache = {"Limpar cache", "Clear cache", "Borrar caché", "Vider le cache", "Cache leeren", "Svuota cache"};
        for (String label : clearCache) {
            clearCacheLabels.add(label);
        }
        String[] clearData = {"Limpar armazenamento", "Limpar dados", "Clear storage", "Clear data", "Borrar almacenamiento",
                "Borrar datos", "Effacer les données", "Speicherinhalt löschen", "Cancella dati"};
        for (String label : clearData) {
            clearDataLabels.add(label);
        }
        okLabels.add(getString(android.R.string.ok).toLowerCase(Locale.ROOT));
        okLabels.add("ok");
        okLabels.add("aceitar");
        // Fire TV: os diálogos das Configurações da Amazon confirmam com "Confirmar" (botão da direita).
        okLabels.add("confirmar");
        okLabels.add("confirm");
        okLabels.add("forçar parada");
        okLabels.add("force stop");
    }

    private static void addResource(Resources resources, Set<String> target, String... names) {
        for (String name : names) {
            int id = resources.getIdentifier(name, "string", "com.android.settings");
            if (id != 0) {
                try {
                    String value = resources.getString(id).trim();
                    if (!value.isEmpty()) {
                        target.add(value);
                    }
                } catch (Resources.NotFoundException ignored) {
                    // Recurso ausente nesta versão.
                }
            }
        }
    }

    // ---------- Sobreposição de progresso ----------

    private void showOverlay() {
        if (overlay != null || job == null) {
            return;
        }
        WindowManager windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        Ui.applyTheme(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(Ui.alpha(Ui.BACKGROUND, 0.93f));
        root.setClickable(!FireTv.is(this));

        LinearLayout card = Ui.card(this);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView title = Ui.title(this, job.mode == MODE_FORCE_STOP ? "Fechando apps escondidos" : "Limpando o lixo dos apps", 18);
        title.setGravity(Gravity.CENTER);
        card.addView(title, Ui.matchWrap());
        Ui.gap(card, 10);
        overlayDetail = Ui.body(this, "");
        overlayDetail.setGravity(Gravity.CENTER);
        card.addView(overlayDetail, Ui.matchWrap());
        Ui.gap(card, 14);
        overlayProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        overlayProgress.setMax(Math.max(1, job.queue.size()));
        overlayProgress.setProgressTintList(android.content.res.ColorStateList.valueOf(Ui.BLUE));
        overlayProgress.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(Ui.TRACK));
        card.addView(overlayProgress, Ui.matchWrap());
        Ui.gap(card, 14);
        boolean tv = FireTv.is(this);
        if (tv) {
            TextView hint = Ui.small(this, "Aperte Voltar no controle para cancelar", Ui.MUTED);
            hint.setGravity(Gravity.CENTER);
            card.addView(hint, Ui.matchWrap());
        } else {
            TextView cancel = Ui.pill(this, "Cancelar", false, new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (job != null) {
                        job.cancelled = true;
                    }
                }
            });
            card.addView(cancel);
        }
        LinearLayout.LayoutParams cardParams = Ui.matchWrap();
        cardParams.setMargins(Ui.dp(this, 28), 0, Ui.dp(this, 28), 0);
        // Em telas grandes o cartão fica centralizado com largura de diálogo.
        if (Ui.large(this)) {
            cardParams.width = Ui.dp(this, 480);
        }
        root.addView(card, cardParams);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        // Fire TV: o toque simulado nas Configurações precisa atravessar o painel.
                        | (tv ? WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE : 0),
                PixelFormat.TRANSLUCENT);
        try {
            windowManager.addView(root, params);
            overlay = root;
        } catch (RuntimeException ignored) {
            overlay = null;
        }
    }

    private void updateOverlay() {
        Job current = job;
        if (overlay == null || current == null || current.index < 0 || current.index >= current.queue.size()) {
            return;
        }
        overlayDetail.setText(current.queue.get(current.index).label + "\n" + (current.index + 1) + " de " + current.queue.size());
        overlayProgress.setProgress(current.index + 1);
    }

    private void hideOverlay() {
        if (overlay == null) {
            return;
        }
        try {
            ((WindowManager) getSystemService(WINDOW_SERVICE)).removeView(overlay);
        } catch (RuntimeException ignored) {
            // Já removida.
        }
        overlay = null;
    }

    private static String safe(CharSequence text) {
        return text == null ? "" : text.toString();
    }

    static String summary(Result result) {
        if (result.stopped.isEmpty()) {
            return "Nenhum app precisou ser fechado.";
        }
        List<String> names = result.stopped.size() > 6 ? result.stopped.subList(0, 6) : result.stopped;
        String text = TextUtils.join(", ", names);
        if (result.stopped.size() > 6) {
            text += " e mais " + (result.stopped.size() - 6);
        }
        return text;
    }
}

package com.kelvin.lumaboost;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class MainActivity extends Activity {
    static final String ACTION_BOOST = "com.kelvin.lumaboost.BOOST";
    static final String ACTION_CLEAN = "com.kelvin.lumaboost.CLEAN";
    static final String ACTION_RETURN = "com.kelvin.lumaboost.RETURN";
    private static final String STATE_TAB = "tab";

    private final String[] tabLabels = {"Início", "Limpeza", "Apps", "Ajustes"};
    private final String[] tabIcons = {"boost", "clean", "apps", "tune"};
    private Screen[] screens;
    private FrameLayout container;
    private LinearLayout nav;
    private int current = -1;
    private Onboarding onboarding;
    private boolean resumed;
    private String layoutKey;
    private static final int NAV_HEIGHT_DP = 72;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Ui.applyTheme(this);
        if (Ui.dark()) {
            setTheme(R.style.AppTheme_Dark);
        }
        super.onCreate(savedInstanceState);
        configureSystemBars();
        screens = new Screen[]{new HomeScreen(this), new CleanScreen(this), new AppsScreen(this), new TweaksScreen(this)};
        buildChrome();

        int tab = savedInstanceState == null ? 0 : savedInstanceState.getInt(STATE_TAB, 0);
        if (!Prefs.onboardingDone(this)) {
            showOnboarding();
        } else {
            select(tab);
            handleIntent(getIntent());
        }
        if (Prefs.autoClean(this)) {
            AutoCleanJob.schedule(this, true);
        }
        if (ForceStopService.isEnabled(this)) {
            // Ligado pela pessoa (tutorial ou ADB): conta como consentimento para religar depois.
            Prefs.setAssistantConsent(this, true);
        } else if (FireTv.is(this) && Prefs.assistantConsent(this)) {
            // O Android desliga o assistente quando o Luma é forçado a parar; no Fire TV não há tela para religar.
            ForceStopService.enableSelf(this);
        }
        if (Prefs.monitorEnabled(this)) {
            MonitorService.start(this);
        }
    }

    /**
     * Monta a moldura do app: no celular, conteúdo com a barra de abas embaixo; em tablets e TVs,
     * navegação lateral (trilho de ícones ou barra com nomes, conforme a largura) e conteúdo ao lado.
     */
    private void buildChrome() {
        layoutKey = layoutKey();
        final boolean large = Ui.large(this);
        container = new FrameLayout(this);
        final LinearLayout root;
        if (large) {
            root = Ui.horizontal(this);
            root.setGravity(Gravity.NO_GRAVITY);
            nav = Ui.vertical(this);
            nav.setBackgroundColor(Ui.SURFACE);
            int width = Ui.expandedNav(this) ? 256 : 96;
            root.addView(nav, new LinearLayout.LayoutParams(Ui.dp(this, width), ViewGroup.LayoutParams.MATCH_PARENT));
            View divider = new View(this);
            divider.setBackgroundColor(Ui.BORDER);
            root.addView(divider, new LinearLayout.LayoutParams(1, ViewGroup.LayoutParams.MATCH_PARENT));
            root.addView(container, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        } else {
            root = Ui.vertical(this);
            root.addView(container, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
            View divider = new View(this);
            divider.setBackgroundColor(Ui.BORDER);
            root.addView(divider, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
            nav = Ui.horizontal(this);
            nav.setBackgroundColor(Ui.SURFACE);
            root.addView(nav, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, NAV_HEIGHT_DP)));
        }
        root.setBackgroundColor(Ui.BACKGROUND);
        setContentView(root);
        // Desenha de borda a borda em todas as versões (obrigatório no Android 15+) e recua o conteúdo das barras.
        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            @SuppressWarnings("deprecation")
            public WindowInsets onApplyWindowInsets(View view, WindowInsets insets) {
                int left;
                int top;
                int right;
                int bottom;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                    left = bars.left;
                    top = bars.top;
                    right = bars.right;
                    bottom = bars.bottom;
                } else {
                    left = insets.getSystemWindowInsetLeft();
                    top = insets.getSystemWindowInsetTop();
                    right = insets.getSystemWindowInsetRight();
                    bottom = insets.getSystemWindowInsetBottom();
                }
                if (large) {
                    // A barra lateral desce por trás da barra de status; o conteúdo começa abaixo dela.
                    view.setPadding(left, 0, right, 0);
                    nav.setPadding(nav.getPaddingLeft(), top + Ui.dp(MainActivity.this, 20), nav.getPaddingRight(), bottom);
                    container.setPadding(0, top, 0, bottom);
                } else {
                    view.setPadding(left, top, right, 0);
                    nav.setPadding(0, 0, 0, bottom);
                    ViewGroup.LayoutParams params = nav.getLayoutParams();
                    params.height = Ui.dp(MainActivity.this, NAV_HEIGHT_DP) + bottom;
                    nav.setLayoutParams(params);
                }
                return insets;
            }
        });
        if (onboarding != null) {
            nav.setVisibility(View.GONE);
        }
    }

    private String layoutKey() {
        return Ui.large(this) + "/" + Ui.twoColumns(this) + "/" + Ui.expandedNav(this);
    }

    /** A tela gira sem recriar a Activity: se mudou entre uma e duas colunas, remonta as views. */
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (layoutKey().equals(layoutKey)) {
            return;
        }
        int tab = current;
        if (current >= 0) {
            screens[current].onHide();
        }
        for (Screen screen : screens) {
            screen.reset();
        }
        current = -1;
        buildChrome();
        if (onboarding != null) {
            onboarding = new Onboarding(this, onboarding.index());
            container.addView(onboarding.view(), new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        } else {
            select(Math.max(0, tab));
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent == null) {
            return;
        }
        if (onboarding != null) {
            return;
        }
        if (ACTION_BOOST.equals(intent.getAction())) {
            select(0);
            ((HomeScreen) screens[0]).startOptimization();
        } else if (ACTION_CLEAN.equals(intent.getAction())) {
            select(1);
        }
        intent.setAction(Intent.ACTION_MAIN);
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        if (onboarding != null) {
            onboarding.onResume();
        } else if (current >= 0) {
            screens[current].onShow();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (onboarding != null) {
            onboarding.onResume();
        } else if (current >= 0) {
            screens[current].onShow();
        }
    }

    /** Mostra o tutorial no lugar das abas (primeira abertura ou "Ver o tutorial de novo"). */
    void showOnboarding() {
        if (current >= 0) {
            screens[current].onHide();
        }
        current = -1;
        onboarding = new Onboarding(this);
        container.removeAllViews();
        container.addView(onboarding.view(), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        nav.setVisibility(View.GONE);
    }

    void finishOnboarding(boolean optimizeNow) {
        Prefs.setOnboardingDone(this, true);
        onboarding = null;
        nav.setVisibility(View.VISIBLE);
        select(0);
        if (optimizeNow) {
            ((HomeScreen) screens[0]).startOptimization();
        }
    }

    @Override
    protected void onPause() {
        resumed = false;
        if (current >= 0) {
            screens[current].onHide();
        }
        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_TAB, current);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        for (Screen screen : screens) {
            screen.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public void onBackPressed() {
        if (onboarding == null && current != 0) {
            select(0);
            return;
        }
        super.onBackPressed();
    }

    void select(int index) {
        if (index == current) {
            return;
        }
        if (current >= 0) {
            screens[current].onHide();
        }
        current = index;
        container.removeAllViews();
        container.addView(screens[index].view(), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        renderNav();
        if (resumed) {
            screens[index].onShow();
        }
    }

    private void renderNav() {
        nav.removeAllViews();
        boolean large = Ui.large(this);
        boolean expanded = Ui.expandedNav(this);
        if (large) {
            nav.setGravity(expanded ? Gravity.NO_GRAVITY : Gravity.CENTER_HORIZONTAL);
            int side = Ui.dp(this, expanded ? 20 : 12);
            nav.setPadding(side, nav.getPaddingTop(), side, nav.getPaddingBottom());
            nav.addView(brand(expanded));
            Ui.gap(nav, expanded ? 32 : 28);
        }
        for (int i = 0; i < tabLabels.length; i++) {
            final int index = i;
            View item = large && expanded ? sideItem(i) : stackedItem(i, large);
            item.setContentDescription(tabLabels[i]);
            item.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    select(index);
                }
            });
            if (large) {
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, expanded ? 54 : 76));
                params.setMargins(0, 0, 0, Ui.dp(this, expanded ? 6 : 8));
                nav.addView(item, params);
            } else {
                nav.addView(item, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
            }
        }
    }

    /** Logo do Luma no topo da navegação lateral. */
    private View brand(boolean expanded) {
        LinearLayout row = Ui.horizontal(this);
        row.setGravity(expanded ? Gravity.CENTER_VERTICAL : Gravity.CENTER);
        FrameLayout logo = new FrameLayout(this);
        logo.setBackground(Ui.gradient(this, 14));
        logo.setElevation(Ui.dp(this, 4));
        View bolt = new View(this);
        bolt.setBackground(new IconDrawable("boost", Color.WHITE, Ui.dp(this, 24)));
        FrameLayout.LayoutParams boltParams = new FrameLayout.LayoutParams(Ui.dp(this, 24), Ui.dp(this, 24));
        boltParams.gravity = Gravity.CENTER;
        logo.addView(bolt, boltParams);
        row.addView(logo, new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44)));
        if (expanded) {
            LinearLayout texts = Ui.vertical(this);
            texts.setPadding(Ui.dp(this, 12), 0, 0, 0);
            texts.addView(Ui.title(this, "Luma Boost", 18));
            Ui.gap(texts, 3);
            texts.addView(Ui.small(this, "Otimizador do " + Ui.device(this), Ui.MUTED));
            row.addView(texts, Ui.weight(1));
        }
        return row;
    }

    /** Item da barra lateral larga: ícone e nome lado a lado, com fundo no selecionado. */
    private View sideItem(int i) {
        boolean selected = i == current;
        int color = selected ? Ui.BLUE : Ui.MUTED;
        TextView item = new TextView(this);
        item.setText(tabLabels[i]);
        item.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        item.setTextColor(selected ? Ui.BLUE : Ui.TEXT);
        item.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), 0);
        item.setCompoundDrawablePadding(Ui.dp(this, 14));
        item.setCompoundDrawablesWithIntrinsicBounds(new IconDrawable(tabIcons[i], color, Ui.dp(this, 22)), null, null, null);
        if (selected) {
            item.setBackground(Ui.rounded(this, Ui.BLUE_SOFT, Ui.BLUE_SOFT, 0, 14));
        }
        Ui.selectable(item, 14);
        return item;
    }

    /** Item com ícone sobre o nome (barra de baixo e trilho lateral), com uma pílula atrás do ícone selecionado. */
    private View stackedItem(int i, boolean rail) {
        boolean selected = i == current;
        int color = selected ? Ui.BLUE : Ui.MUTED;
        LinearLayout item = Ui.vertical(this);
        item.setGravity(Gravity.CENTER);
        FrameLayout indicator = new FrameLayout(this);
        if (selected) {
            indicator.setBackground(Ui.rounded(this, Ui.BLUE_SOFT, Ui.BLUE_SOFT, 0, 16));
        }
        View icon = new View(this);
        icon.setBackground(new IconDrawable(tabIcons[i], color, Ui.dp(this, 22)));
        FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(Ui.dp(this, 22), Ui.dp(this, 22));
        iconParams.gravity = Gravity.CENTER;
        indicator.addView(icon, iconParams);
        item.addView(indicator, new LinearLayout.LayoutParams(Ui.dp(this, 60), Ui.dp(this, 32)));
        TextView label = new TextView(this);
        label.setText(tabLabels[i]);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        label.setTextColor(selected ? Ui.TEXT : Ui.MUTED);
        label.setTypeface(Typeface.create("sans-serif-medium", selected ? Typeface.BOLD : Typeface.NORMAL));
        label.setGravity(Gravity.CENTER);
        label.setPadding(0, Ui.dp(this, 4), 0, 0);
        item.addView(label, Ui.matchWrap());
        Ui.selectable(item, rail ? 16 : 0);
        return item;
    }

    @SuppressWarnings("deprecation")
    private void configureSystemBars() {
        Window window = getWindow();
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? Color.TRANSPARENT : Color.BLACK);
        window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Ui.BACKGROUND));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.setNavigationBarContrastEnforced(false);
        }
        int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
        if (!Ui.dark()) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !Ui.dark()) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        window.getDecorView().setSystemUiVisibility(flags);
    }
}

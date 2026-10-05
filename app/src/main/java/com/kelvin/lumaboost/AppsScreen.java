package com.kelvin.lumaboost;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class AppsScreen extends Screen {
    private static final int TAB_ACTIVE = 0;
    private static final int TAB_HEAVY = 1;
    private static final int TAB_UNUSED = 2;
    private static final long UNUSED_MS = 30L * AppCatalog.DAY_MS;

    private LinearLayout actionCard;
    private TextView actionButton;
    private TextView actionResult;
    private LinearLayout usageCard;
    private LinearLayout tabs;
    private LinearLayout list;
    private TextView listHint;
    private List<AppCatalog.AppEntry> apps;
    private List<AppCatalog.AppEntry> active;
    private Set<String> foreground = new HashSet<>();
    private Set<String> selected;
    private int tab = TAB_ACTIVE;
    private boolean loading;
    private boolean stopping;

    AppsScreen(MainActivity activity) {
        super(activity);
    }

    @Override
    void build() {
        header("Apps", "Veja quem está pesando no seu " + Ui.device(activity) + " e feche ou remova o que você não usa.");

        split(0.85f);
        actionCard = Ui.card(activity);
        left.addView(actionCard, Ui.matchWrap());
        Ui.gap(left, 14);

        usageCard = Ui.card(activity);
        usageCard.addView(permissionRow("Uso dos apps",
                "Para mostrar quando você usou cada app e quanto espaço ele ocupa.", new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        Guide.open(activity, Guide.USAGE);
                    }
                }));
        left.addView(usageCard, Ui.matchWrap());
        Ui.gap(left, 14);

        tabs = Ui.horizontal(activity);
        right.addView(tabs, Ui.matchWrap());
        Ui.gap(right, 10);
        listHint = Ui.small(activity, "", Ui.MUTED);
        right.addView(listHint, Ui.matchWrap());
        Ui.gap(right, 10);
        list = Ui.vertical(activity);
        right.addView(list, Ui.matchWrap());
        renderTabs();
    }

    @Override
    void onShow() {
        usageCard.setVisibility(Permissions.hasUsageAccess(activity) ? View.GONE : View.VISIBLE);
        renderActionCard();
        load();
    }

    // ---------- Encerrar apps ----------

    private void renderActionCard() {
        actionCard.removeAllViews();
        actionCard.addView(Ui.iconBadge(activity, "ram", Ui.BLUE, Ui.BLUE_SOFT, 44), new LinearLayout.LayoutParams(dp(44), dp(44)));
        Ui.gap(actionCard, 12);
        actionCard.addView(Ui.title(activity, "Apps abertos em segundo plano", 17));
        Ui.gap(actionCard, 6);
        boolean deep = ForceStopService.isEnabled(activity);
        actionCard.addView(Ui.small(activity, deep
                ? "Os apps marcados abaixo são fechados de verdade e só voltam quando você abrir de novo."
                : "Para fechar apps de verdade, o Luma precisa do assistente ligado.",
                Ui.MUTED));
        Ui.gap(actionCard, 12);
        if (!deep) {
            TextView enable = Ui.button(activity, "Ativar assistente do Luma", "shield", true, new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    DeepStopSetup.show(activity);
                }
            });
            enable.setGravity(Gravity.CENTER);
            actionCard.addView(enable, Ui.matchWrap());
        } else {
            actionButton = Ui.button(activity, "Fechar marcados", "ram", true, new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    stopSelected();
                }
            });
            actionButton.setGravity(Gravity.CENTER);
            actionCard.addView(actionButton, Ui.matchWrap());
            updateActionButton();
        }
        actionResult = Ui.small(activity, "", Ui.GREEN);
        actionResult.setPadding(0, dp(10), 0, 0);
        actionResult.setVisibility(View.GONE);
        actionCard.addView(actionResult);
    }

    private void updateActionButton() {
        if (actionButton == null || !ForceStopService.isEnabled(activity)) {
            return;
        }
        int count = selected == null ? 0 : countSelectedActive();
        actionButton.setText(stopping ? "Fechando…" : count == 0 ? "Nenhum app marcado" : "Fechar " + Format.plural(count, "app", "apps"));
        actionButton.setEnabled(!stopping && count > 0);
        actionButton.setAlpha(!stopping && count > 0 ? 1f : 0.55f);
    }

    private int countSelectedActive() {
        int count = 0;
        if (active != null) {
            for (AppCatalog.AppEntry app : active) {
                if (selected.contains(app.packageName)) {
                    count++;
                }
            }
        }
        return count;
    }

    private void stopSelected() {
        if (active == null || stopping) {
            return;
        }
        if (!ForceStopService.isReady()) {
            if (ForceStopService.isEnabled(activity)) {
                android.widget.Toast.makeText(activity, "O assistente está voltando. Tente de novo em instantes.", android.widget.Toast.LENGTH_SHORT).show();
            } else {
                DeepStopSetup.show(activity);
            }
            return;
        }
        final List<AppCatalog.AppEntry> targets = new ArrayList<>();
        for (AppCatalog.AppEntry app : active) {
            if (selected.contains(app.packageName)) {
                targets.add(app);
            }
        }
        if (targets.isEmpty()) {
            return;
        }
        stopping = true;
        updateActionButton();
        final long ramBefore = DeviceStats.availableRam(activity);
        boolean started = ForceStopService.stopApps(targets, new ForceStopService.Listener() {
            @Override
            public void onFinished(final ForceStopService.Result result) {
                Tasks.run(new Tasks.Job<Long>() {
                    @Override
                    public Long run() {
                        Tasks.sleep(1500L);
                        return DeviceStats.availableRam(activity);
                    }
                }, new Tasks.Done<Long>() {
                    @Override
                    public void done(Long ramAfter, Exception error) {
                        stopping = false;
                        long after = ramAfter == null ? ramBefore : ramAfter;
                        long freed = Math.max(0L, after - ramBefore);
                        Prefs.addFreed(activity, 0L, freed);
                        actionResult.setVisibility(View.VISIBLE);
                        actionResult.setText(Format.plural(result.stopped.size(), "app fechado", "apps fechados")
                                + " · +" + Format.bytes(freed) + " de memória livre (" + Format.bytes(ramBefore) + " → " + Format.bytes(after) + ")"
                                + (result.failed > 0 ? "\n" + result.failed + " não puderam ser fechados." : ""));
                        selected = null;
                        load();
                    }
                });
            }
        });
        if (!started) {
            stopping = false;
            updateActionButton();
        }
    }

    // ---------- Lista ----------

    private void renderTabs() {
        tabs.removeAllViews();
        String[] labels = {"Abertos", "Mais pesados", "Esquecidos"};
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            TextView pill = Ui.pill(activity, labels[i], tab == i, new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    tab = index;
                    renderTabs();
                    renderList();
                }
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            params.setMargins(0, 0, dp(8), 0);
            tabs.addView(pill, params);
        }
    }

    private void load() {
        if (loading) {
            return;
        }
        loading = true;
        if (apps == null) {
            list.removeAllViews();
            list.addView(Ui.body(activity, "Carregando seus apps…"));
        }
        Tasks.run(new Tasks.Job<Object[]>() {
            @Override
            public Object[] run() {
                List<AppCatalog.AppEntry> all = AppCatalog.launchable(activity);
                AppCatalog.attachUsage(activity, all, System.currentTimeMillis() - 60L * AppCatalog.DAY_MS);
                AppCatalog.attachStorage(activity, all);
                List<AppCatalog.AppEntry> running = Hibernator.activeApps(activity);
                Set<String> fg = Hibernator.packagesWithForegroundService(activity);
                fg.addAll(Hibernator.systemBoundPackages(activity));
                List<AppCatalog.AppEntry> defaults = Hibernator.defaultSelection(activity, running);
                return new Object[]{all, running, fg, defaults};
            }
        }, new Tasks.Done<Object[]>() {
            @Override
            @SuppressWarnings("unchecked")
            public void done(Object[] result, Exception error) {
                loading = false;
                if (result != null) {
                    apps = (List<AppCatalog.AppEntry>) result[0];
                    active = (List<AppCatalog.AppEntry>) result[1];
                    foreground = (Set<String>) result[2];
                    if (selected == null) {
                        selected = new HashSet<>();
                        for (AppCatalog.AppEntry app : (List<AppCatalog.AppEntry>) result[3]) {
                            selected.add(app.packageName);
                        }
                    }
                }
                renderList();
                updateActionButton();
            }
        });
    }

    private void renderList() {
        list.removeAllViews();
        if (apps == null) {
            return;
        }
        boolean usage = Permissions.hasUsageAccess(activity);
        List<AppCatalog.AppEntry> filtered = new ArrayList<>();
        long now = System.currentTimeMillis();
        if (tab == TAB_ACTIVE) {
            filtered.addAll(active);
            listHint.setText(Format.plural(active.size(), "app aberto", "apps abertos")
                    + " podendo rodar escondidos. Mensageiros, alarmes e apps tocando música ficam de fora.");
        } else if (tab == TAB_HEAVY) {
            filtered.addAll(apps);
            Collections.sort(filtered, new Comparator<AppCatalog.AppEntry>() {
                @Override
                public int compare(AppCatalog.AppEntry a, AppCatalog.AppEntry b) {
                    return Long.compare(b.totalBytes(), a.totalBytes());
                }
            });
            listHint.setText(usage ? "Do que mais ocupa espaço para o que menos ocupa." : "Permita ver o uso dos apps para medir o tamanho de cada um.");
        } else {
            for (AppCatalog.AppEntry app : apps) {
                boolean oldInstall = app.installTime > 0L && now - app.installTime > UNUSED_MS;
                boolean notUsed = app.lastUsed == 0L || now - app.lastUsed > UNUSED_MS;
                if (usage && oldInstall && notUsed) {
                    filtered.add(app);
                }
            }
            Collections.sort(filtered, new Comparator<AppCatalog.AppEntry>() {
                @Override
                public int compare(AppCatalog.AppEntry a, AppCatalog.AppEntry b) {
                    if (a.system != b.system) {
                        return a.system ? 1 : -1;
                    }
                    return Long.compare(b.totalBytes(), a.totalBytes());
                }
            });
            listHint.setText(usage ? Format.plural(filtered.size(), "app esquecido", "apps esquecidos") + " há mais de 30 dias. Que tal remover e ganhar espaço?"
                    : "Permita ver o uso dos apps para encontrar os esquecidos.");
        }

        Set<String> protectedApps = Prefs.protectedApps(activity);
        int index = 0;
        for (final AppCatalog.AppEntry app : filtered) {
            if (index > 0) {
                Ui.gap(list, 8);
            }
            list.addView(buildRow(app, protectedApps.contains(app.packageName)), Ui.matchWrap());
            index++;
        }
        if (filtered.isEmpty()) {
            list.addView(Ui.body(activity, tab == TAB_UNUSED && usage ? "Nenhum app esquecido. Ótimo!"
                    : tab == TAB_ACTIVE ? "Nenhum app aberto escondido. Tudo leve!" : "Nada para mostrar."));
        }
    }

    private View buildRow(final AppCatalog.AppEntry app, final boolean isProtected) {
        StringBuilder detail = new StringBuilder();
        if (app.lastUsed > 0L) {
            detail.append("Usado ").append(Format.ago(app.lastUsed));
        } else if (Permissions.hasUsageAccess(activity)) {
            detail.append("Não usado há um tempo");
        } else {
            detail.append(app.system ? "Veio com o celular" : "App instalado");
        }
        if (app.appBytes >= 0L) {
            detail.append(" · ").append(Format.bytes(app.totalBytes()));
            if (app.cacheBytes > 0L) {
                detail.append(" (lixo ").append(Format.bytes(app.cacheBytes)).append(')');
            }
        }
        if (isProtected || Hibernator.isDefaultProtected(app.packageName)) {
            detail.append("\nProtegido: o Luma não fecha este app");
        } else if (foreground.contains(app.packageName)) {
            detail.append("\nEm uso agora (música, relógio ou navegação)");
        }

        CheckBox check = null;
        if (tab == TAB_ACTIVE && selected != null) {
            check = new CheckBox(activity);
            check.setChecked(selected.contains(app.packageName));
            check.setContentDescription("Fechar " + app.label);
            check.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton button, boolean checked) {
                    if (checked) {
                        selected.add(app.packageName);
                    } else {
                        selected.remove(app.packageName);
                    }
                    updateActionButton();
                }
            });
        }

        LinearLayout actions = Ui.horizontal(activity);
        TextView details = Ui.pill(activity, app.system ? "Abrir detalhes" : "Detalhes", false, new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Permissions.openAppDetails(activity, app.packageName);
            }
        });
        actions.addView(details);
        if (!app.system && tab != TAB_ACTIVE) {
            TextView uninstall = Ui.pill(activity, "Desinstalar", false, new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Intent intent = new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + app.packageName));
                    if (!Permissions.start(activity, intent)) {
                        Permissions.openAppDetails(activity, app.packageName);
                    }
                }
            });
            uninstall.setTextColor(Ui.RED);
            addWithMargin(actions, uninstall);
        }
        TextView protect = Ui.pill(activity, isProtected ? "Desproteger" : "Proteger", false, new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Prefs.setProtected(activity, app.packageName, !isProtected);
                if (selected != null) {
                    if (!isProtected) {
                        selected.remove(app.packageName);
                    }
                }
                renderList();
                updateActionButton();
            }
        });
        if (Hibernator.isDefaultProtected(app.packageName)) {
            protect.setText("Sempre protegido");
            protect.setEnabled(false);
            protect.setAlpha(0.7f);
        }
        addWithMargin(actions, protect);
        return appRow(activity, app, detail.toString(), check, actions);
    }

    private void addWithMargin(LinearLayout parent, View child) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(dp(8), 0, 0, 0);
        parent.addView(child, params);
    }

    /** Linha de app reutilizada pelas telas: ícone, nome, detalhe e ações. */
    static View appRow(MainActivity activity, AppCatalog.AppEntry app, String detail, View trailing, View actions) {
        LinearLayout card = Ui.card(activity);
        LinearLayout row = Ui.horizontal(activity);
        ImageView icon = new ImageView(activity);
        try {
            Drawable drawable = activity.getPackageManager().getApplicationIcon(app.packageName);
            icon.setImageDrawable(drawable);
        } catch (PackageManager.NameNotFoundException ignored) {
            icon.setImageDrawable(new IconDrawable("apps", Ui.MUTED, Ui.dp(activity, 40)));
        }
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(Ui.dp(activity, 44), Ui.dp(activity, 44));
        iconParams.setMargins(0, 0, Ui.dp(activity, 14), 0);
        row.addView(icon, iconParams);
        LinearLayout texts = Ui.vertical(activity);
        TextView name = Ui.title(activity, app.label, 15);
        name.setSingleLine(true);
        texts.addView(name);
        Ui.gap(texts, 3);
        texts.addView(Ui.small(activity, detail, Ui.MUTED));
        row.addView(texts, Ui.weight(1));
        if (trailing != null) {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            params.setMargins(Ui.dp(activity, 8), 0, 0, 0);
            row.addView(trailing, params);
        }
        card.addView(row);
        if (actions != null) {
            Ui.gap(card, 10);
            card.addView(actions);
        }
        return card;
    }
}

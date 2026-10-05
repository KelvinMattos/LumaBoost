package com.kelvin.lumaboost;

import android.os.Build;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

final class TweaksScreen extends Screen {
    private LinearLayout performanceList;
    private TextView performanceTitle;
    private LinearLayout resourcesList;
    private Switch autoCleanSwitch;
    private Switch monitorSwitch;
    private LinearLayout rootContainer;

    TweaksScreen(MainActivity activity) {
        super(activity);
    }

    @Override
    void build() {
        final boolean tv = FireTv.is(activity);
        header("Ajustes", tv
                ? "Pequenas mudanças que deixam o Fire TV mais ágil. Dá para desfazer quando quiser."
                : "Pequenas mudanças que deixam o celular mais ágil e a bateria durando mais. Dá para desfazer quando quiser.");

        // Lado a lado no celular; empilhados na coluna mais estreita das telas grandes.
        boolean stacked = Ui.twoColumns(activity);
        LinearLayout actions = stacked ? Ui.vertical(activity) : Ui.horizontal(activity);
        TextView apply = Ui.button(activity, "Usar os recomendados", "check", true, new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                int changed = SystemTweaks.applyRecommended(activity);
                Toast.makeText(activity, changed == 0 ? "Já está tudo no ponto" : Format.plural(changed, "ajuste feito", "ajustes feitos"), Toast.LENGTH_SHORT).show();
                renderTweaks();
            }
        });
        apply.setGravity(Gravity.CENTER);
        apply.setCompoundDrawables(null, null, null, null);
        LinearLayout.LayoutParams applyParams = stacked ? Ui.matchWrap() : Ui.weight(1.3f);
        applyParams.setMargins(0, 0, stacked ? 0 : dp(10), stacked ? dp(10) : 0);
        actions.addView(apply, applyParams);
        TextView restore = Ui.button(activity, "Desfazer tudo", null, false, new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                int restored = SystemTweaks.restoreAll(activity);
                Toast.makeText(activity, restored == 0 ? "Nada para desfazer" : Format.plural(restored, "ajuste desfeito", "ajustes desfeitos"), Toast.LENGTH_SHORT).show();
                renderTweaks();
            }
        });
        restore.setGravity(Gravity.CENTER);
        actions.addView(restore, stacked ? Ui.matchWrap() : Ui.weight(1f));
        split(1f);
        left.addView(actions, Ui.matchWrap());
        Ui.gap(left, 24);

        performanceTitle = Ui.section(activity, "Mais agilidade");
        left.addView(performanceTitle);
        performanceList = Ui.vertical(activity);
        performanceList.setPadding(0, dp(10), 0, dp(24));
        left.addView(performanceList, Ui.matchWrap());

        left.addView(Ui.section(activity, tv ? "Recursos" : "Bateria"));
        Ui.gap(left, 10);
        resourcesList = Ui.vertical(activity);
        left.addView(resourcesList, Ui.matchWrap());
        Ui.gap(left, 24);

        right.addView(Ui.section(activity, "No piloto automático"));
        Ui.gap(right, 10);
        LinearLayout autoCard = Ui.card(activity);
        monitorSwitch = new Switch(activity);
        autoCard.addView(switchRow("Luma sempre ligado",
                tv ? "Um aviso fixo mostra como está a memória. E toda vez que o Fire TV entra em repouso, o Luma faz uma faxina rápida sozinho."
                        : "Um aviso fixo mostra como está a memória e deixa o botão Otimizar sempre à mão. "
                        + "E toda vez que você bloqueia a tela, o Luma faz uma faxina rápida sozinho.",
                monitorSwitch));
        Ui.gap(autoCard, 14);
        autoCleanSwitch = new Switch(activity);
        autoCard.addView(switchRow("Faxina diária",
                tv ? "Uma vez por dia, com o Fire TV parado, o Luma limpa o que sobrou sem você precisar abrir o app."
                        : "Uma vez por dia, enquanto o celular carrega, o Luma limpa o que sobrou sem você precisar abrir o app.",
                autoCleanSwitch));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && !tv) {
            Ui.gap(autoCard, 12);
            autoCard.addView(Ui.small(activity,
                    "Dica: puxe a barra do topo, toque no lápis e adicione o botão \"Luma Boost\" para otimizar sem abrir o app.",
                    Ui.MUTED));
        }
        right.addView(autoCard, Ui.matchWrap());
        Ui.gap(right, 24);

        right.addView(Ui.section(activity, "Para aparelhos com root"));
        Ui.gap(right, 10);
        rootContainer = Ui.vertical(activity);
        right.addView(rootContainer, Ui.matchWrap());
        Ui.gap(right, 24);

        right.addView(Ui.section(activity, "Atalhos"));
        Ui.gap(right, 10);
        right.addView(Ui.button(activity, "Ver o tutorial de novo", "check", false, new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                activity.showOnboarding();
            }
        }), Ui.matchWrap());
        Ui.gap(right, 10);
        if (!tv) {
            right.addView(Ui.button(activity, "Economia de bateria do celular", "battery", false, new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Permissions.openSystemScreen(activity, Settings.ACTION_BATTERY_SAVER_SETTINGS);
                }
            }), Ui.matchWrap());
            Ui.gap(right, 10);
        }
        right.addView(Ui.button(activity, "Armazenamento do " + Ui.device(activity), "storage", false, new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Permissions.openSystemScreen(activity, Settings.ACTION_INTERNAL_STORAGE_SETTINGS);
            }
        }), Ui.matchWrap());
        Ui.gap(right, 10);
        right.addView(Ui.button(activity, "Todos os apps", "apps", false, new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Permissions.openSystemScreen(activity, Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS);
            }
        }), Ui.matchWrap());
    }

    @Override
    void onShow() {
        renderTweaks();
        renderMonitor();
        renderRoot();
        autoCleanSwitch.setOnCheckedChangeListener(null);
        autoCleanSwitch.setChecked(Prefs.autoClean(activity));
        autoCleanSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton button, boolean checked) {
                Prefs.setAutoClean(activity, checked);
                AutoCleanJob.schedule(activity, checked);
                Toast.makeText(activity, checked ? "Faxina diária ligada" : "Faxina diária desligada", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void renderMonitor() {
        monitorSwitch.setOnCheckedChangeListener(null);
        monitorSwitch.setChecked(Prefs.monitorEnabled(activity));
        monitorSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton button, boolean checked) {
                Prefs.setMonitorEnabled(activity, checked);
                if (checked) {
                    if (Build.VERSION.SDK_INT >= 33 && activity.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                            != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        activity.requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 51);
                    }
                    MonitorService.start(activity);
                    Toast.makeText(activity, "Luma sempre ligado", Toast.LENGTH_SHORT).show();
                } else {
                    MonitorService.stop(activity);
                    Toast.makeText(activity, "Luma desligado do modo automático", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void renderRoot() {
        rootContainer.removeAllViews();
        LinearLayout card = Ui.card(activity);
        rootContainer.addView(card, Ui.matchWrap());
        if (!RootShell.isSuPresent()) {
            card.addView(Ui.title(activity, "Seu " + Ui.device(activity) + " não tem root", 15));
            Ui.gap(card, 4);
            card.addView(Ui.small(activity, "Tudo bem, quase ninguém tem! Root é um acesso especial que alguns usuários avançados liberam. "
                    + "Se o seu " + Ui.device(activity) + " tiver, o Luma consegue limpar ainda mais, em silêncio.", Ui.MUTED));
            return;
        }
        final Switch rootSwitch = new Switch(activity);
        rootSwitch.setChecked(Prefs.rootEnabled(activity));
        card.addView(switchRow("Usar o acesso root",
                "O Luma fecha apps e limpa tudo em silêncio, sem abrir nenhuma tela, até quando a tela está bloqueada. "
                        + "Só usa recursos oficiais do Android: nada que possa danificar o celular.",
                rootSwitch));
        rootSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(final CompoundButton button, boolean checked) {
                if (!checked) {
                    Prefs.setRootEnabled(activity, false);
                    renderRoot();
                    return;
                }
                button.setEnabled(false);
                Tasks.run(new Tasks.Job<Boolean>() {
                    @Override
                    public Boolean run() {
                        return RootShell.requestAccess();
                    }
                }, new Tasks.Done<Boolean>() {
                    @Override
                    public void done(Boolean ok, Exception error) {
                        boolean granted = Boolean.TRUE.equals(ok);
                        Prefs.setRootEnabled(activity, granted);
                        Toast.makeText(activity, granted ? "Pronto, acesso root liberado" : "O acesso root não foi liberado", Toast.LENGTH_SHORT).show();
                        if (granted && Prefs.monitorEnabled(activity)) {
                            MonitorService.start(activity);
                        }
                        renderRoot();
                        renderTweaks();
                    }
                });
            }
        });
        if (!Prefs.rootEnabled(activity)) {
            return;
        }
        Ui.gap(card, 14);
        final android.widget.CheckBox compile = new android.widget.CheckBox(activity);
        compile.setText("Também acelerar a abertura dos apps (leva alguns minutos; melhor com o celular carregando).");
        compile.setTextSize(13f);
        compile.setTextColor(Ui.TEXT);
        card.addView(compile, Ui.matchWrap());
        Ui.gap(card, 10);
        final TextView status = Ui.small(activity, "", Ui.GREEN);
        final TextView run = Ui.button(activity, "Faxina completa", "boost", true, null);
        run.setGravity(Gravity.CENTER);
        run.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                run.setEnabled(false);
                run.setAlpha(0.6f);
                status.setTextColor(Ui.MUTED);
                status.setText("Começando…");
                final boolean withCompile = compile.isChecked();
                Tasks.run(new Tasks.Job<AutoOptimizer.Summary>() {
                    @Override
                    public AutoOptimizer.Summary run() {
                        return AutoOptimizer.runRootFull(activity, withCompile, new AutoOptimizer.Progress() {
                            @Override
                            public void step(String label) {
                                status.setText(label);
                            }
                        });
                    }
                }, new Tasks.Done<AutoOptimizer.Summary>() {
                    @Override
                    public void done(AutoOptimizer.Summary summary, Exception error) {
                        run.setEnabled(true);
                        run.setAlpha(1f);
                        status.setTextColor(Ui.GREEN);
                        if (summary == null) {
                            status.setText("Algo deu errado. Tente de novo.");
                            return;
                        }
                        StringBuilder text = new StringBuilder(summary.text());
                        for (String step : summary.steps) {
                            text.append("\n• ").append(step);
                        }
                        status.setText(text.toString());
                        renderTweaks();
                    }
                });
            }
        });
        card.addView(run, Ui.matchWrap());
        Ui.gap(card, 8);
        card.addView(status, Ui.matchWrap());
    }

    private void renderTweaks() {
        performanceList.removeAllViews();
        // Animações e Bluetooth só podem ser mudados com root (que libera a permissão sozinho).
        boolean performance = Permissions.hasSecureSettings(activity);
        performanceTitle.setVisibility(performance ? View.VISIBLE : View.GONE);
        performanceList.setVisibility(performance ? View.VISIBLE : View.GONE);
        if (performance) {
            renderTweakList(performanceList, SystemTweaks.performance());
        }

        resourcesList.removeAllViews();
        if (!Permissions.canWriteSettings(activity)) {
            LinearLayout card = Ui.card(activity);
            card.addView(permissionRow("Ajustes do celular",
                    "Para o Luma mudar o tempo de tela, o brilho, os sons e a vibração.", new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            Guide.open(activity, Guide.SETTINGS);
                        }
                    }));
            resourcesList.addView(card, Ui.matchWrap());
            Ui.gap(resourcesList, 10);
        }
        renderTweakList(resourcesList, SystemTweaks.resources(activity));
    }

    private void renderTweakList(LinearLayout parent, List<SystemTweaks.Tweak> tweaks) {
        LinearLayout card = Ui.card(activity);
        boolean first = true;
        for (final SystemTweaks.Tweak tweak : tweaks) {
            if (!first) {
                View divider = new View(activity);
                divider.setBackgroundColor(Ui.BORDER);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1);
                params.setMargins(0, dp(12), 0, dp(12));
                card.addView(divider, params);
            }
            first = false;
            final Switch toggle = new Switch(activity);
            final boolean available = tweak.available(activity);
            toggle.setChecked(available && tweak.isOptimized(activity));
            toggle.setEnabled(available);
            toggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton button, boolean checked) {
                    try {
                        tweak.set(activity, checked);
                    } catch (RuntimeException exception) {
                        Toast.makeText(activity, "Seu " + Ui.device(activity) + " não deixou mudar esse ajuste.", Toast.LENGTH_SHORT).show();
                    }
                    button.setOnCheckedChangeListener(null);
                    button.setChecked(tweak.isOptimized(activity));
                    button.setOnCheckedChangeListener(this);
                }
            });
            String description = tweak.description;
            if (!available) {
                description += "\nPermita os ajustes do celular acima para usar.";
            }
            View row = switchRow(tweak.title, description, toggle);
            row.setAlpha(available ? 1f : 0.55f);
            card.addView(row);
        }
        parent.addView(card, Ui.matchWrap());
    }

    private View switchRow(String title, String detail, Switch toggle) {
        LinearLayout row = Ui.horizontal(activity);
        LinearLayout texts = Ui.vertical(activity);
        texts.addView(Ui.title(activity, title, 15));
        Ui.gap(texts, 3);
        texts.addView(Ui.small(activity, detail, Ui.MUTED));
        row.addView(texts, Ui.weight(1));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(dp(12), 0, 0, 0);
        row.addView(toggle, params);
        return row;
    }
}

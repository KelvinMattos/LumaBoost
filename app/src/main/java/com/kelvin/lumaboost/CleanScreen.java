package com.kelvin.lumaboost;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

final class CleanScreen extends Screen {
    static final int REQ_CLEAR_ALL_CACHE = 72;
    private static final int MAX_ITEMS_SHOWN = 80;

    private TextView cacheValue;
    private TextView cacheDetail;
    private TextView cacheButton;
    private TextView cacheResult;
    private LinearLayout junkContainer;
    private LinearLayout topCacheList;
    private TextView cleanSelectedButton;
    private List<JunkScanner.Category> categories = new ArrayList<>();
    private JunkScanner scanner;
    private long freeBeforeSystemClear;
    private boolean busy;

    CleanScreen(MainActivity activity) {
        super(activity);
    }

    @Override
    void build() {
        header("Limpeza", "Tire do caminho o que só ocupa espaço.");
        split(1f);

        left.addView(Ui.section(activity, "Lixo dos apps"));
        Ui.gap(left, 10);
        LinearLayout cacheCard = Ui.card(activity);
        LinearLayout cacheTop = Ui.horizontal(activity);
        cacheTop.addView(Ui.iconBadge(activity, "clean", Ui.BLUE, Ui.BLUE_SOFT, 44), new LinearLayout.LayoutParams(dp(44), dp(44)));
        LinearLayout cacheTexts = Ui.vertical(activity);
        cacheTexts.setPadding(dp(14), 0, 0, 0);
        cacheTexts.addView(Ui.overline(activity, "Acumulado nos apps", Ui.MUTED));
        Ui.gap(cacheTexts, 6);
        cacheValue = Ui.title(activity, "—", 26);
        cacheTexts.addView(cacheValue);
        cacheTop.addView(cacheTexts, Ui.weight(1));
        cacheCard.addView(cacheTop, Ui.matchWrap());
        Ui.gap(cacheCard, 12);
        cacheDetail = Ui.small(activity, "", Ui.MUTED);
        cacheCard.addView(cacheDetail);
        Ui.gap(cacheCard, 14);
        cacheButton = Ui.button(activity, "Limpar todos os apps", "clean", true, new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                clearAllCache();
            }
        });
        cacheButton.setGravity(Gravity.CENTER);
        cacheCard.addView(cacheButton, Ui.matchWrap());
        cacheResult = Ui.small(activity, "", Ui.GREEN);
        cacheResult.setVisibility(View.GONE);
        cacheResult.setPadding(0, dp(10), 0, 0);
        cacheCard.addView(cacheResult);
        left.addView(cacheCard, Ui.matchWrap());
        Ui.gap(left, 24);

        left.addView(Ui.section(activity, "Quem mais acumula lixo"));
        Ui.gap(left, 10);
        topCacheList = Ui.vertical(activity);
        left.addView(topCacheList, Ui.matchWrap());
        Ui.gap(left, 24);

        right.addView(Ui.section(activity, "Arquivos esquecidos"));
        Ui.gap(right, 10);
        junkContainer = Ui.vertical(activity);
        right.addView(junkContainer, Ui.matchWrap());
    }

    @Override
    void onShow() {
        refreshCacheCard();
        if (categories.isEmpty() && !busy) {
            renderJunkIntro();
        } else if (!busy && junkContainer.getChildCount() == 0) {
            // Views remontadas (a tela girou): mostra de novo o resultado da última busca.
            renderCategories(scanner == null ? 0 : scanner.scannedFiles.get());
        }
    }

    @Override
    void onHide() {
        if (scanner != null) {
            scanner.cancel();
        }
    }

    // ---------- Cache dos apps ----------

    private void refreshCacheCard() {
        if (!Permissions.hasUsageAccess(activity)) {
            cacheValue.setText("Lixo acumulado");
            cacheDetail.setText("Permita ver o uso dos apps para o Luma medir o lixo de cada um.");
        } else {
            cacheValue.setText("Medindo…");
        }
        if (ForceStopService.isEnabled(activity)) {
            cacheDetail.setText("São arquivos temporários que os apps guardam e não precisam. Suas contas, conversas e fotos continuam onde estão.");
        } else if (CacheCleaner.canClearAllAppsCache(activity)) {
            cacheDetail.setText("Para limpar tudo, ative o assistente do Luma na tela inicial. Sem ele, o Android só deixa limpar uma parte.");
        } else {
            cacheDetail.setText("Para limpar tudo, ative o assistente do Luma na tela inicial. Sem ele, o Android só deixa limpar uma parte.");
        }
        Tasks.run(new Tasks.Job<List<AppCatalog.AppEntry>>() {
            @Override
            public List<AppCatalog.AppEntry> run() {
                List<AppCatalog.AppEntry> apps = AppCatalog.launchable(activity);
                if (!AppCatalog.attachStorage(activity, apps)) {
                    return null;
                }
                return apps;
            }
        }, new Tasks.Done<List<AppCatalog.AppEntry>>() {
            @Override
            public void done(List<AppCatalog.AppEntry> apps, Exception error) {
                renderCacheData(apps);
            }
        });
    }

    private void renderCacheData(List<AppCatalog.AppEntry> apps) {
        topCacheList.removeAllViews();
        if (apps == null) {
            LinearLayout card = Ui.card(activity);
            card.addView(permissionRow("Acesso ao uso", "Para medir o lixo de cada app.", new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Guide.open(activity, Guide.USAGE);
                }
            }));
            topCacheList.addView(card, Ui.matchWrap());
            cacheValue.setText("Lixo acumulado");
            return;
        }
        long total = AppCatalog.totalAppCache(activity);
        if (FireTv.is(activity)) {
            // O total do sistema inclui o cache dos serviços da Amazon, que nenhuma limpeza alcança: soma só os apps.
            total = 0L;
            for (AppCatalog.AppEntry app : apps) {
                total += Math.max(0L, app.cacheBytes);
            }
        }
        cacheValue.setText(Format.bytes(Math.max(0L, total)) + " de lixo");
        Collections.sort(apps, new Comparator<AppCatalog.AppEntry>() {
            @Override
            public int compare(AppCatalog.AppEntry a, AppCatalog.AppEntry b) {
                return Long.compare(b.cacheBytes, a.cacheBytes);
            }
        });
        int shown = 0;
        for (final AppCatalog.AppEntry app : apps) {
            if (shown >= 8 || app.cacheBytes < 1024L * 1024L) {
                break;
            }
            if (shown > 0) {
                Ui.gap(topCacheList, 8);
            }
            topCacheList.addView(AppsScreen.appRow(activity, app, Format.bytes(app.cacheBytes) + " de lixo",
                    Ui.pill(activity, "Detalhes", false, new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            Permissions.openAppDetails(activity, app.packageName);
                        }
                    }), null), Ui.matchWrap());
            shown++;
        }
        if (shown == 0) {
            topCacheList.addView(Ui.body(activity, "Nenhum app acumulando lixo. Tudo limpinho!"));
        }
    }

    private void clearAllCache() {
        if (busy) {
            return;
        }
        if (ForceStopService.isReady() && Permissions.hasUsageAccess(activity)) {
            deepClearCache();
            return;
        }
        if (CacheCleaner.canClearAllAppsCache(activity)) {
            freeBeforeSystemClear = DeviceStats.freeStorage();
            try {
                activity.startActivityForResult(CacheCleaner.clearAllAppsCacheIntent(), REQ_CLEAR_ALL_CACHE);
                return;
            } catch (RuntimeException exception) {
                // Sem diálogo do sistema: cai para a limpeza do cache excedente.
            }
        }
        cacheButton.setEnabled(false);
        cacheButton.setText("Limpando…");
        Tasks.run(new Tasks.Job<Long>() {
            @Override
            public Long run() {
                long before = DeviceStats.freeStorage();
                CacheCleaner.trimSystemCache(activity);
                CacheCleaner.cleanOwnCaches(activity);
                return Math.max(0L, DeviceStats.freeStorage() - before);
            }
        }, new Tasks.Done<Long>() {
            @Override
            public void done(Long freed, Exception error) {
                cacheButton.setEnabled(true);
                cacheButton.setText("Limpar todos os apps");
                long value = freed == null ? 0L : freed;
                Prefs.addFreed(activity, value, 0L);
                showCacheResult(value, true);
            }
        });
    }

    /** Limpeza real do cache interno: o serviço toca em "Limpar cache" em cada app com cache. */
    private void deepClearCache() {
        busy = true;
        cacheButton.setEnabled(false);
        cacheButton.setText("Preparando…");
        final long before = DeviceStats.freeStorage();
        Tasks.run(new Tasks.Job<List<AppCatalog.AppEntry>>() {
            @Override
            public List<AppCatalog.AppEntry> run() {
                CacheCleaner.cleanOwnCaches(activity);
                return Optimizer.appsWithCache(activity, 1024L * 1024L);
            }
        }, new Tasks.Done<List<AppCatalog.AppEntry>>() {
            @Override
            public void done(List<AppCatalog.AppEntry> targets, Exception error) {
                final boolean allAtOnce = FireTv.is(activity);
                if (!allAtOnce && (targets == null || targets.isEmpty())) {
                    busy = false;
                    cacheButton.setEnabled(true);
                    cacheButton.setText("Limpar todos os apps");
                    showCacheResult(0L, false);
                    return;
                }
                ForceStopService.Listener listener = new ForceStopService.Listener() {
                    @Override
                    public void onFinished(final ForceStopService.Result result) {
                        Tasks.run(new Tasks.Job<Long>() {
                            @Override
                            public Long run() {
                                Tasks.sleep(800L);
                                return Math.max(0L, DeviceStats.freeStorage() - before);
                            }
                        }, new Tasks.Done<Long>() {
                            @Override
                            public void done(Long freed, Exception error) {
                                busy = false;
                                cacheButton.setEnabled(true);
                                cacheButton.setText("Limpar todos os apps");
                                long value = freed == null ? 0L : freed;
                                Prefs.addFreed(activity, value, 0L);
                                showCacheResult(value, false);
                                if (allAtOnce) {
                                    if (result.cacheCleared.isEmpty()) {
                                        cacheResult.setTextColor(Ui.ORANGE);
                                        cacheResult.setText("Não consegui confirmar a limpeza nas Configurações. Tente de novo.");
                                    } else {
                                        cacheResult.setText(value >= 1024L * 1024L
                                                ? Format.bytes(value) + " apagados do cache de todos os apps."
                                                : "Cache de todos os apps limpo. Quase nada estava acumulado.");
                                    }
                                } else {
                                    cacheResult.append("\n" + Format.plural(result.cacheCleared.size(), "app limpo", "apps limpos")
                                            + (result.cancelled ? " (você interrompeu)." : "."));
                                }
                            }
                        });
                    }
                };
                // Fire TV: uma opção das Configurações limpa o cache de todos os apps de uma vez.
                boolean started = allAtOnce ? ForceStopService.clearAllCaches(listener) : ForceStopService.clearCaches(targets, listener);
                if (!started) {
                    busy = false;
                    cacheButton.setEnabled(true);
                    cacheButton.setText("Limpar todos os apps");
                }
            }
        });
    }

    @Override
    void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQ_CLEAR_ALL_CACHE) {
            return;
        }
        if (resultCode != Activity.RESULT_OK) {
            cacheResult.setVisibility(View.VISIBLE);
            cacheResult.setTextColor(Ui.MUTED);
            cacheResult.setText("Tudo bem, nada foi apagado.");
            return;
        }
        cacheResult.setVisibility(View.VISIBLE);
        cacheResult.setTextColor(Ui.MUTED);
        cacheResult.setText("Limpando…");
        // O sistema apaga de forma assíncrona; espera o espaço livre estabilizar antes de medir.
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                long freed = Math.max(0L, DeviceStats.freeStorage() - freeBeforeSystemClear);
                Prefs.addFreed(activity, freed, 0L);
                showCacheResult(freed, false);
                refreshCacheCard();
            }
        }, 2500L);
    }

    private void showCacheResult(long freed, boolean suggestPermission) {
        cacheResult.setVisibility(View.VISIBLE);
        cacheResult.setTextColor(Ui.GREEN);
        String text = freed > 0L ? Format.bytes(freed) + " de espaço liberado." : "Já estava tudo limpo.";
        if (suggestPermission) {
            text += " Ative o assistente do Luma para limpar ainda mais.";
        }
        cacheResult.setText(text);
        refreshCacheCard();
    }

    // ---------- Arquivos desnecessários ----------

    private void renderJunkIntro() {
        junkContainer.removeAllViews();
        LinearLayout card = Ui.card(activity);
        if (!Permissions.hasAllFilesAccess(activity)) {
            card.addView(permissionRow("Seus arquivos",
                    "Para o Luma encontrar arquivos esquecidos, instaladores velhos e pastas vazias.", new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            Guide.open(activity, Guide.FILES);
                        }
                    }));
        } else {
            card.addView(Ui.iconBadge(activity, "search", Ui.BLUE, Ui.BLUE_SOFT, 44), new LinearLayout.LayoutParams(dp(44), dp(44)));
            Ui.gap(card, 12);
            card.addView(Ui.body(activity, "O Luma procura o que está sobrando e separa o que pode sair sem medo do que vale você dar uma olhada antes."));
            Ui.gap(card, 12);
            card.addView(Ui.button(activity, "Procurar arquivos esquecidos", "search", false, new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    startScan();
                }
            }), Ui.matchWrap());
        }
        junkContainer.addView(card, Ui.matchWrap());
    }

    private void startScan() {
        busy = true;
        junkContainer.removeAllViews();
        LinearLayout card = Ui.card(activity);
        final TextView progress = Ui.body(activity, "Procurando…");
        card.addView(progress);
        junkContainer.addView(card, Ui.matchWrap());
        scanner = new JunkScanner();
        final JunkScanner current = scanner;
        final Handler handler = new Handler(Looper.getMainLooper());
        final Runnable ticker = new Runnable() {
            @Override
            public void run() {
                progress.setText("Procurando… " + current.scannedFiles.get() + " arquivos olhados");
                handler.postDelayed(this, 250L);
            }
        };
        handler.post(ticker);
        Tasks.run(new Tasks.Job<List<JunkScanner.Category>>() {
            @Override
            public List<JunkScanner.Category> run() {
                return current.scan();
            }
        }, new Tasks.Done<List<JunkScanner.Category>>() {
            @Override
            public void done(List<JunkScanner.Category> result, Exception error) {
                handler.removeCallbacks(ticker);
                busy = false;
                if (error != null || result == null) {
                    progress.setText("Não deu para terminar a busca: " + (error == null ? "tente de novo" : error.getMessage()));
                    return;
                }
                categories = result;
                renderCategories(current.scannedFiles.get());
            }
        });
    }

    private void renderCategories(int scannedFiles) {
        junkContainer.removeAllViews();
        if (categories.isEmpty()) {
            LinearLayout card = Ui.tintedCard(activity, Ui.GREEN_SOFT);
            card.addView(Ui.title(activity, "Nada sobrando por aqui", 16));
            Ui.gap(card, 4);
            card.addView(Ui.small(activity, scannedFiles + " arquivos olhados.", Ui.TEXT));
            junkContainer.addView(card, Ui.matchWrap());
            Ui.gap(junkContainer, 10);
            junkContainer.addView(Ui.button(activity, "Procurar de novo", "search", false, new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    startScan();
                }
            }), Ui.matchWrap());
            return;
        }
        LinearLayout top = Ui.horizontal(activity);
        top.addView(Ui.small(activity, scannedFiles + " arquivos olhados.", Ui.MUTED), Ui.weight(1));
        top.addView(Ui.pill(activity, "Procurar de novo", false, new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startScan();
            }
        }));
        junkContainer.addView(top, Ui.matchWrap());
        Ui.gap(junkContainer, 8);
        for (JunkScanner.Category category : categories) {
            junkContainer.addView(categoryCard(category), Ui.matchWrap());
            Ui.gap(junkContainer, 10);
        }
        cleanSelectedButton = Ui.button(activity, "", "clean", true, new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                deleteSelected();
            }
        });
        cleanSelectedButton.setGravity(Gravity.CENTER);
        junkContainer.addView(cleanSelectedButton, Ui.matchWrap());
        updateCleanButton();
    }

    private View categoryCard(final JunkScanner.Category category) {
        final LinearLayout card = Ui.card(activity);
        LinearLayout top = Ui.horizontal(activity);
        final CheckBox check = new CheckBox(activity);
        check.setChecked(category.selectedCount() == category.items.size());
        top.addView(check);
        LinearLayout texts = Ui.vertical(activity);
        texts.addView(Ui.title(activity, category.title, 15.5f));
        Ui.gap(texts, 3);
        final TextView meta = Ui.small(activity, "", category.safe ? Ui.GREEN : Ui.ORANGE);
        texts.addView(meta);
        top.addView(texts, Ui.weight(1));
        TextView size = Ui.title(activity, Format.bytes(category.size()), 15);
        top.addView(size);
        card.addView(top);
        Ui.gap(card, 6);
        card.addView(Ui.small(activity, category.description, Ui.MUTED));
        Ui.gap(card, 8);

        final LinearLayout itemList = Ui.vertical(activity);
        itemList.setVisibility(View.GONE);
        final TextView toggle = Ui.pill(activity, "Ver " + Format.plural(category.items.size(), "item", "itens"), false, null);
        toggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                boolean show = itemList.getVisibility() != View.VISIBLE;
                if (show && itemList.getChildCount() == 0) {
                    fillItems(itemList, category, check, meta);
                }
                itemList.setVisibility(show ? View.VISIBLE : View.GONE);
                toggle.setText(show ? "Esconder lista" : "Ver " + Format.plural(category.items.size(), "item", "itens"));
            }
        });
        LinearLayout toggleRow = Ui.horizontal(activity);
        toggleRow.addView(toggle);
        card.addView(toggleRow);
        card.addView(itemList, Ui.matchWrap());

        updateMeta(category, meta);
        check.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton button, boolean checked) {
                category.selectAll(checked);
                for (int i = 0; i < itemList.getChildCount(); i++) {
                    View child = itemList.getChildAt(i);
                    if (child instanceof CheckBox) {
                        ((CheckBox) child).setChecked(checked);
                    }
                }
                updateMeta(category, meta);
                updateCleanButton();
            }
        });
        return card;
    }

    private void fillItems(LinearLayout itemList, final JunkScanner.Category category, final CheckBox categoryCheck, final TextView meta) {
        String root = Environment.getExternalStorageDirectory().getAbsolutePath();
        int shown = 0;
        for (final JunkScanner.Item item : category.items) {
            if (shown >= MAX_ITEMS_SHOWN) {
                itemList.addView(Ui.small(activity, "+ " + (category.items.size() - shown) + " itens a mais", Ui.MUTED));
                break;
            }
            CheckBox box = new CheckBox(activity);
            String path = item.file.getAbsolutePath().replace(root, "");
            box.setText(path + (item.size > 0L ? "  ·  " + Format.bytes(item.size) : ""));
            box.setTextSize(12.5f);
            box.setTextColor(Ui.TEXT);
            box.setChecked(item.selected);
            box.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton button, boolean checked) {
                    item.selected = checked;
                    updateMeta(category, meta);
                    updateCleanButton();
                }
            });
            itemList.addView(box, Ui.matchWrap());
            shown++;
        }
    }

    private void updateMeta(JunkScanner.Category category, TextView meta) {
        String safety = category.safe ? "Pode apagar sem medo" : "Dê uma olhada antes";
        meta.setText(safety + " · " + category.selectedCount() + " de " + category.items.size() + " selecionados");
    }

    private void updateCleanButton() {
        if (cleanSelectedButton == null) {
            return;
        }
        long total = 0L;
        int count = 0;
        for (JunkScanner.Category category : categories) {
            total += category.selectedSize();
            count += category.selectedCount();
        }
        cleanSelectedButton.setText(count == 0 ? "Nada marcado" : "Limpar " + Format.bytes(total) + " (" + Format.plural(count, "item", "itens") + ")");
        cleanSelectedButton.setEnabled(count > 0);
        cleanSelectedButton.setAlpha(count > 0 ? 1f : 0.5f);
    }

    private void deleteSelected() {
        if (busy) {
            return;
        }
        busy = true;
        cleanSelectedButton.setEnabled(false);
        cleanSelectedButton.setText("Limpando…");
        final List<JunkScanner.Category> target = categories;
        Tasks.run(new Tasks.Job<long[]>() {
            @Override
            public long[] run() {
                long before = DeviceStats.freeStorage();
                long deleted = JunkScanner.delete(target);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    Tasks.sleep(600L);
                }
                return new long[]{deleted, Math.max(0L, DeviceStats.freeStorage() - before)};
            }
        }, new Tasks.Done<long[]>() {
            @Override
            public void done(long[] result, Exception error) {
                busy = false;
                long freed = result == null ? 0L : Math.max(result[0], result[1]);
                Prefs.addFreed(activity, freed, 0L);
                Toast.makeText(activity, Format.bytes(freed) + " liberados", Toast.LENGTH_LONG).show();
                List<JunkScanner.Category> remaining = new ArrayList<>();
                for (JunkScanner.Category category : categories) {
                    if (!category.items.isEmpty()) {
                        remaining.add(category);
                    }
                }
                categories = remaining;
                renderCategories(scanner == null ? 0 : scanner.scannedFiles.get());
                LinearLayout done = Ui.tintedCard(activity, Ui.GREEN_SOFT);
                done.addView(Ui.title(activity, Format.bytes(freed) + " liberados", 16));
                LinearLayout.LayoutParams params = Ui.matchWrap();
                params.setMargins(0, 0, 0, dp(10));
                junkContainer.addView(done, 0, params);
            }
        });
    }
}

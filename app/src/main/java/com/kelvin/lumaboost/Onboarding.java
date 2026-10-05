package com.kelvin.lumaboost;

import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Tutorial de primeira abertura: apresenta o app e guia cada permissão até o fim. */
final class Onboarding {
    private static final int WELCOME = -1;
    private static final int DONE = -2;

    private final MainActivity activity;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<Integer> pages = new ArrayList<>();
    private final View root;
    private final ScrollView scroller;
    private final LinearLayout content;
    /** Onde vão ícone, título e texto: o painel em degradê nas telas grandes, ou a própria coluna no celular. */
    private final LinearLayout side;
    private final boolean wide;
    private int index;
    private boolean advancing;

    Onboarding(MainActivity activity) {
        this(activity, 0);
    }

    Onboarding(MainActivity activity, int startIndex) {
        this.activity = activity;
        pages.add(WELCOME);
        for (int step = Guide.ASSISTANT; step <= Guide.NOTIFICATIONS; step++) {
            if (Guide.needed(step)) {
                pages.add(step);
            }
        }
        pages.add(DONE);
        content = Ui.vertical(activity);
        wide = Ui.twoColumns(activity);
        if (wide) {
            // Duas metades: apresentação em degradê à esquerda, instruções e botões à direita.
            LinearLayout split = Ui.horizontal(activity);
            split.setGravity(Gravity.NO_GRAVITY);
            split.setBackgroundColor(Ui.BACKGROUND);
            int pad = Ui.dp(activity, 24);
            split.setPadding(pad, pad, pad, pad);
            side = Ui.vertical(activity);
            side.setGravity(Gravity.CENTER);
            side.setBackground(Ui.gradient(activity, 28));
            int inner = Ui.dp(activity, 48);
            side.setPadding(inner, inner, inner, inner);
            LinearLayout.LayoutParams sideParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
            sideParams.setMargins(0, 0, Ui.dp(activity, 24), 0);
            split.addView(side, sideParams);
            scroller = new ScrollView(activity);
            scroller.setFillViewport(true);
            scroller.setVerticalScrollBarEnabled(false);
            content.setGravity(Gravity.CENTER_VERTICAL);
            content.setPadding(Ui.dp(activity, 24), Ui.dp(activity, 24), Ui.dp(activity, 24), Ui.dp(activity, 24));
            scroller.addView(content, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            split.addView(scroller, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
            root = split;
        } else {
            scroller = Ui.scroll(activity, content);
            side = content;
            root = scroller;
        }
        index = Math.max(0, Math.min(startIndex, pages.size() - 1));
        render();
    }

    int index() {
        return index;
    }

    View view() {
        return root;
    }

    /** Ao voltar das configurações: se a permissão foi liberada, comemora e avança sozinho. */
    void onResume() {
        int page = pages.get(index);
        if (page >= 0 && Guide.isGranted(activity, page) && !advancing) {
            advancing = true;
            render();
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    advancing = false;
                    next();
                }
            }, 900L);
        } else {
            render();
        }
    }

    private void next() {
        if (index < pages.size() - 1) {
            index++;
        }
        render();
        scroller.scrollTo(0, 0);
    }

    private void render() {
        content.removeAllViews();
        if (side != content) {
            side.removeAllViews();
        }
        int page = pages.get(index);
        int total = pages.size() - 2;

        LinearLayout dots = Ui.horizontal(activity);
        dots.setGravity(Gravity.CENTER);
        for (int i = 1; i <= total; i++) {
            View dot = new View(activity);
            boolean done = i < index || (i == index && page >= 0 && Guide.isGranted(activity, page));
            int color = wide
                    ? (i == index ? Color.WHITE : done ? Color.rgb(134, 239, 172) : Ui.alpha(Color.WHITE, 0.35f))
                    : (i == index ? Ui.BLUE : done ? Ui.GREEN : Ui.TRACK);
            dot.setBackground(Ui.rounded(activity, color, 0, 0, 4));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(Ui.dp(activity, i == index ? 22 : 8), Ui.dp(activity, 8));
            params.setMargins(Ui.dp(activity, 3), 0, Ui.dp(activity, 3), 0);
            dots.addView(dot, params);
        }
        if (page != WELCOME && page != DONE) {
            side.addView(dots, Ui.matchWrap());
            Ui.gap(side, 24);
        } else if (!wide) {
            Ui.gap(content, 32);
        }

        if (page == WELCOME) {
            hero("boost", Ui.BLUE);
            title("Oi! Eu sou o Luma");
            body("Vou deixar seu " + Ui.device(activity) + " mais leve e rápido: fecho os apps que ficam escondidos, limpo o lixo que eles acumulam e "
                    + "encontro arquivos esquecidos.\n\nPara isso, preciso de algumas permissões. Vou te mostrar onde tocar em cada uma. "
                    + "Leva menos de um minuto.");
            Ui.gap(content, 8);
            note("Nada sai do seu " + Ui.device(activity) + ". O Luma não tem anúncios, não pede conta e nem usa internet.");
            primary("Vamos lá", new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    next();
                }
            });
            secondary("Agora não, quero só olhar", new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    activity.finishOnboarding(false);
                }
            });
            return;
        }
        if (page == DONE) {
            hero("check", Ui.GREEN);
            title(Guide.allGranted(activity) ? "Tudo pronto!" : "Quase lá!");
            body(Guide.allGranted(activity)
                    ? "O Luma já pode cuidar do seu " + Ui.device(activity) + " por completo. Que tal a primeira faxina agora?"
                    : "Algumas permissões ficaram para depois. Tudo bem: o Luma funciona assim mesmo e você pode liberar o resto quando quiser, pela tela inicial.");
            primary("Fazer minha primeira otimização", new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    activity.finishOnboarding(true);
                }
            });
            secondary("Ir para o app", new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    activity.finishOnboarding(false);
                }
            });
            return;
        }

        final int step = page;
        boolean granted = Guide.isGranted(activity, step);
        hero(icon(step), granted ? Ui.GREEN : Ui.BLUE);
        TextView counter = Ui.overline(activity, "Passo " + index + " de " + total, wide ? Ui.alpha(Color.WHITE, 0.8f) : Ui.MUTED);
        counter.setGravity(Gravity.CENTER);
        side.addView(counter, Ui.matchWrap());
        Ui.gap(side, 10);
        title(title(step));
        body(description(step));
        Ui.gap(content, 4);

        if (granted) {
            LinearLayout ok = Ui.tintedCard(activity, Ui.GREEN_SOFT);
            ok.setGravity(Gravity.CENTER);
            TextView text = Ui.title(activity, "Liberado! Obrigado", 16);
            text.setTextColor(Ui.GREEN);
            text.setGravity(Gravity.CENTER);
            ok.addView(text, Ui.matchWrap());
            content.addView(ok, Ui.matchWrap());
            Ui.gap(content, 16);
            primary("Continuar", new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    next();
                }
            });
            return;
        }

        boolean adb = Guide.needsAdb(activity, step);
        LinearLayout how = Ui.card(activity);
        how.addView(Ui.title(activity, "Como fazer", 15));
        Ui.gap(how, 8);
        String[] steps = adb ? howToAdb(step) : howTo(step);
        for (int i = 0; i < steps.length; i++) {
            LinearLayout row = Ui.horizontal(activity);
            row.setGravity(Gravity.TOP);
            TextView number = Ui.title(activity, String.valueOf(i + 1), 13);
            number.setTextColor(Color.WHITE);
            number.setGravity(Gravity.CENTER);
            number.setBackground(Ui.oval(Ui.BLUE));
            LinearLayout.LayoutParams numberParams = new LinearLayout.LayoutParams(Ui.dp(activity, 24), Ui.dp(activity, 24));
            numberParams.setMargins(0, 0, Ui.dp(activity, 12), 0);
            row.addView(number, numberParams);
            TextView text = Ui.body(activity, steps[i]);
            text.setTextColor(Ui.TEXT);
            row.addView(text, Ui.weight(1));
            how.addView(row, Ui.matchWrap());
            if (i < steps.length - 1) {
                Ui.gap(how, 10);
            }
        }
        if (adb && !(step == Guide.ASSISTANT && Permissions.hasSecureSettings(activity))) {
            Ui.gap(how, 12);
            TextView commands = Ui.small(activity, FireTv.adbCommands(activity), Ui.TEXT);
            commands.setTypeface(android.graphics.Typeface.MONOSPACE);
            commands.setTextIsSelectable(false);
            commands.setPadding(Ui.dp(activity, 12), Ui.dp(activity, 10), Ui.dp(activity, 12), Ui.dp(activity, 10));
            commands.setBackground(Ui.rounded(activity, Ui.BACKGROUND, Ui.BORDER, 1, 8));
            how.addView(commands, Ui.matchWrap());
        }
        content.addView(how, Ui.matchWrap());
        Ui.gap(content, 12);
        if (step == Guide.ASSISTANT) {
            note("Eu só aperto \"Forçar parada\" e \"Limpar cache\" quando você pedir. Não leio, não guardo e não envio nada da sua tela, "
                    + "e nunca toco em \"Limpar armazenamento\". Mensageiros e alarmes nunca são fechados.");
        }
        String action = step == Guide.ASSISTANT ? "Concordo, quero ativar" : step == Guide.NOTIFICATIONS ? "Quero o botão na barra" : "Me mostre onde tocar";
        if (adb) {
            action = step == Guide.ASSISTANT && Permissions.hasSecureSettings(activity) ? "Concordo, quero ativar" : "Já rodei os comandos";
        }
        primary(action,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if (step == Guide.NOTIFICATIONS) {
                            Prefs.setMonitorEnabled(activity, true);
                            MonitorService.start(activity);
                        }
                        Guide.open(activity, step);
                        if (step == Guide.NOTIFICATIONS || Guide.needsAdb(activity, step)) {
                            // Sem sair do app: confere na hora se ficou liberado.
                            onResume();
                        }
                    }
                });
        secondary("Pular este passo", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                next();
            }
        });
    }

    private static String icon(int step) {
        switch (step) {
            case Guide.ASSISTANT:
                return "shield";
            case Guide.USAGE:
                return "apps";
            case Guide.FILES:
                return "storage";
            case Guide.SETTINGS:
                return "tune";
            default:
                return "boost";
        }
    }

    private String title(int step) {
        switch (step) {
            case Guide.ASSISTANT:
                return "Ligue o assistente do Luma";
            case Guide.USAGE:
                return "Deixe o Luma ver o uso dos apps";
            case Guide.FILES:
                return "Encontrar arquivos esquecidos";
            case Guide.SETTINGS:
                return "Cuidar dos ajustes do " + Ui.device(activity);
            default:
                return "Botão Otimizar sempre à mão";
        }
    }

    private static String description(int step) {
        switch (step) {
            case Guide.ASSISTANT:
                return "É ele que faz a mágica: fecha os apps escondidos e limpa o lixo de cada um, tocando nos botões das "
                        + "configurações do Android por você. O Android não deixa nenhum app fazer isso de outro jeito.";
            case Guide.USAGE:
                return "Assim eu sei quais apps você usa, quanto espaço cada um ocupa e quais estão tocando música, para nunca fechá-los.";
            case Guide.FILES:
                return "Para achar instaladores velhos, downloads esquecidos, pastas vazias e a lixeira de fotos. Você revisa tudo antes de apagar.";
            case Guide.SETTINGS:
                return "Para desligar o que gasta bateria à toa, como a tela acesa por muito tempo e a vibração ao tocar, só quando você quiser.";
            default:
                return "Deixe um aviso fixo com a memória livre e o botão Otimizar. E a cada bloqueio de tela o Luma faz uma faxina rápida.";
        }
    }

    private static String[] howTo(int step) {
        switch (step) {
            case Guide.ASSISTANT:
                return new String[]{
                        "Toque no botão abaixo. Vou abrir a tela de Acessibilidade.",
                        "Toque em \"Luma Boost\". Às vezes ele fica dentro de \"Aplicativos instalados\" ou \"Serviços instalados\".",
                        "Ligue a chave e toque em \"Permitir\". Eu volto sozinho para cá."
                };
            case Guide.NOTIFICATIONS:
                return new String[]{
                        "Toque no botão abaixo.",
                        "Na janelinha que aparecer, toque em \"Permitir\"."
                };
            default:
                return new String[]{
                        "Toque no botão abaixo. Vou abrir a tela certa.",
                        "Um círculo azul vai mostrar exatamente onde tocar.",
                        "Ligue a chave. Eu volto sozinho para cá."
                };
        }
    }

    /** Fire TV: as Configurações não mostram estas chaves; a liberação é feita uma vez pelo computador. */
    private String[] howToAdb(int step) {
        if (step == Guide.ASSISTANT && Permissions.hasSecureSettings(activity)) {
            return new String[]{
                    "Toque no botão abaixo.",
                    "Eu ligo o assistente sozinho e já sigo para o próximo passo."
            };
        }
        return new String[]{
                "O Fire TV não mostra esta chave nas Configurações. Ative \"Depuração ADB\" em Meu Fire TV → Opções do desenvolvedor.",
                "No computador, na mesma rede, rode \"adb connect\" com o IP do Fire TV e depois os comandos abaixo.",
                "Volte aqui e toque em \"Já rodei os comandos\"."
        };
    }

    private void hero(String icon, int color) {
        View circle = new View(activity);
        circle.setBackground(Ui.oval(wide ? Ui.alpha(Color.WHITE, 0.18f) : color == Ui.GREEN ? Ui.GREEN_SOFT : Ui.BLUE_SOFT));
        LinearLayout wrapper = Ui.vertical(activity);
        wrapper.setGravity(Gravity.CENTER);
        android.widget.FrameLayout frame = new android.widget.FrameLayout(activity);
        frame.addView(circle, new android.widget.FrameLayout.LayoutParams(Ui.dp(activity, 96), Ui.dp(activity, 96)));
        View glyph = new View(activity);
        glyph.setBackground(new IconDrawable(icon, wide ? Color.WHITE : color, Ui.dp(activity, 44)));
        android.widget.FrameLayout.LayoutParams glyphParams = new android.widget.FrameLayout.LayoutParams(Ui.dp(activity, 44), Ui.dp(activity, 44));
        glyphParams.gravity = Gravity.CENTER;
        frame.addView(glyph, glyphParams);
        wrapper.addView(frame, new LinearLayout.LayoutParams(Ui.dp(activity, 96), Ui.dp(activity, 96)));
        side.addView(wrapper, Ui.matchWrap());
        Ui.gap(side, 24);
    }

    private void title(String text) {
        TextView view = Ui.title(activity, text, wide ? 32 : 26);
        view.setGravity(Gravity.CENTER);
        view.setLineSpacing(Ui.dp(activity, 2), 1f);
        if (wide) {
            view.setTextColor(Color.WHITE);
        }
        side.addView(view, Ui.matchWrap());
        Ui.gap(side, 14);
    }

    private void body(String text) {
        TextView view = Ui.body(activity, text);
        view.setTextSize(16);
        view.setGravity(Gravity.CENTER);
        if (wide) {
            view.setTextColor(Ui.alpha(Color.WHITE, 0.88f));
            view.setMaxWidth(Ui.dp(activity, 520));
        }
        side.addView(view, Ui.matchWrap());
        Ui.gap(side, wide ? 0 : 20);
    }

    private void note(String text) {
        TextView view = Ui.small(activity, text, Ui.MUTED);
        view.setGravity(Gravity.CENTER);
        content.addView(view, Ui.matchWrap());
        Ui.gap(content, 20);
    }

    private void primary(String label, View.OnClickListener listener) {
        final TextView button = Ui.button(activity, label, null, true, listener);
        button.setGravity(Gravity.CENTER);
        content.addView(button, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(activity, 56)));
        Ui.gap(content, 8);
        // Com controle remoto, cada página já abre com o botão principal selecionado.
        button.post(new Runnable() {
            @Override
            public void run() {
                if (button.isAttachedToWindow()) {
                    button.requestFocus();
                }
            }
        });
    }

    private void secondary(String label, View.OnClickListener listener) {
        TextView link = Ui.title(activity, label, 15);
        link.setTextColor(Ui.BLUE);
        link.setGravity(Gravity.CENTER);
        link.setPadding(0, Ui.dp(activity, 12), 0, Ui.dp(activity, 12));
        link.setOnClickListener(listener);
        Ui.selectable(link);
        content.addView(link, Ui.matchWrap());
    }
}

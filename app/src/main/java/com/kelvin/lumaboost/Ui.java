package com.kelvin.lumaboost;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Fábrica de componentes visuais compartilhados pelas telas. */
final class Ui {
    // Paleta: começa no tema claro e troca para o escuro em applyTheme (TV e modo noturno).
    static int BACKGROUND;
    static int SURFACE;
    static int SURFACE_ALT;
    static int TEXT;
    static int MUTED;
    static int BLUE;
    static int BLUE_SOFT;
    static int VIOLET;
    static int GREEN;
    static int GREEN_SOFT;
    static int ORANGE;
    static int ORANGE_SOFT;
    static int RED;
    static int BORDER;
    static int TRACK;
    /** Início e fim do degradê dos destaques (cartão de saúde, botão principal, painel do tutorial). */
    static int HERO_START;
    static int HERO_END;
    /** Contorno do item focado pelo controle remoto: visível tanto no claro quanto no escuro. */
    static final int FOCUS = Color.rgb(255, 159, 10);

    private static boolean dark;

    static {
        palette(false);
    }

    private Ui() {
    }

    /** Escolhe a paleta: escura na TV (padrão de apps de sala) e quando o sistema está no modo noturno. */
    static void applyTheme(Context context) {
        palette(isDark(context));
    }

    static boolean isDark(Context context) {
        int night = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return FireTv.is(context) || night == Configuration.UI_MODE_NIGHT_YES;
    }

    static boolean dark() {
        return dark;
    }

    private static void palette(boolean useDark) {
        dark = useDark;
        if (useDark) {
            BACKGROUND = Color.rgb(10, 14, 26);
            SURFACE = Color.rgb(20, 26, 44);
            SURFACE_ALT = Color.rgb(29, 37, 62);
            TEXT = Color.rgb(241, 245, 249);
            MUTED = Color.rgb(148, 160, 184);
            BLUE = Color.rgb(92, 140, 255);
            BLUE_SOFT = Color.rgb(31, 43, 82);
            VIOLET = Color.rgb(160, 120, 255);
            GREEN = Color.rgb(52, 211, 153);
            GREEN_SOFT = Color.rgb(16, 52, 44);
            ORANGE = Color.rgb(251, 191, 36);
            ORANGE_SOFT = Color.rgb(58, 46, 18);
            RED = Color.rgb(248, 113, 113);
            BORDER = Color.rgb(36, 45, 72);
            TRACK = Color.rgb(39, 49, 80);
            HERO_START = Color.rgb(59, 91, 253);
            HERO_END = Color.rgb(139, 72, 245);
        } else {
            BACKGROUND = Color.rgb(244, 246, 251);
            SURFACE = Color.WHITE;
            SURFACE_ALT = Color.rgb(238, 241, 247);
            TEXT = Color.rgb(15, 23, 42);
            MUTED = Color.rgb(100, 116, 139);
            BLUE = Color.rgb(47, 107, 255);
            BLUE_SOFT = Color.rgb(232, 239, 255);
            VIOLET = Color.rgb(124, 77, 255);
            GREEN = Color.rgb(22, 163, 74);
            GREEN_SOFT = Color.rgb(231, 247, 237);
            ORANGE = Color.rgb(217, 119, 6);
            ORANGE_SOFT = Color.rgb(255, 244, 224);
            RED = Color.rgb(220, 38, 38);
            BORDER = Color.rgb(230, 234, 242);
            TRACK = Color.rgb(231, 235, 243);
            HERO_START = Color.rgb(47, 107, 255);
            HERO_END = Color.rgb(124, 77, 255);
        }
    }

    // ---------- Tamanho de tela ----------

    /** Tablet ou TV: navegação lateral no lugar da barra de baixo. */
    static boolean large(Context context) {
        return FireTv.is(context) || context.getResources().getConfiguration().smallestScreenWidthDp >= 600;
    }

    /** Tela grande com largura para duas colunas lado a lado. */
    static boolean twoColumns(Context context) {
        return large(context) && context.getResources().getConfiguration().screenWidthDp >= 720;
    }

    /** Barra lateral com nomes (telas bem largas, como a TV) ou trilho só com ícones e legendas. */
    static boolean expandedNav(Context context) {
        return large(context) && context.getResources().getConfiguration().screenWidthDp >= 940;
    }

    static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    // ---------- Fundos ----------

    static GradientDrawable rounded(Context context, int fill, int stroke, int strokeDp, float radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(context, radiusDp));
        if (strokeDp > 0) {
            drawable.setStroke(dp(context, strokeDp), stroke);
        }
        return drawable;
    }

    static GradientDrawable gradient(Context context, float radiusDp) {
        GradientDrawable drawable = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{HERO_START, HERO_END});
        drawable.setCornerRadius(dp(context, radiusDp));
        return drawable;
    }

    static GradientDrawable oval(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(color);
        return drawable;
    }

    static int alpha(int color, float alpha) {
        return Color.argb(Math.round(255 * alpha), Color.red(color), Color.green(color), Color.blue(color));
    }

    // ---------- Estrutura ----------

    static void gap(LinearLayout parent, int heightDp) {
        View gap = new View(parent.getContext());
        parent.addView(gap, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(parent.getContext(), heightDp)));
    }

    static LinearLayout vertical(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    static LinearLayout horizontal(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        return layout;
    }

    static LinearLayout card(Context context) {
        LinearLayout card = vertical(context);
        card.setPadding(dp(context, 18), dp(context, 16), dp(context, 18), dp(context, 16));
        card.setBackground(rounded(context, SURFACE, BORDER, 1, 18));
        return card;
    }

    static LinearLayout tintedCard(Context context, int fill) {
        LinearLayout card = vertical(context);
        card.setPadding(dp(context, 18), dp(context, 16), dp(context, 18), dp(context, 16));
        card.setBackground(rounded(context, fill, fill, 0, 18));
        return card;
    }

    /** Ícone dentro de um quadrado arredondado com fundo suave, usado nos cartões. */
    static View iconBadge(Context context, String icon, int color, int fill, int sizeDp) {
        android.widget.FrameLayout badge = new android.widget.FrameLayout(context);
        badge.setBackground(rounded(context, fill, fill, 0, sizeDp * 0.32f));
        View glyph = new View(context);
        int glyphSize = dp(context, sizeDp * 0.55f);
        glyph.setBackground(new IconDrawable(icon, color, glyphSize));
        android.widget.FrameLayout.LayoutParams params = new android.widget.FrameLayout.LayoutParams(glyphSize, glyphSize);
        params.gravity = Gravity.CENTER;
        badge.addView(glyph, params);
        return badge;
    }

    // ---------- Textos ----------

    static TextView title(Context context, String text, float sp) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextColor(TEXT);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        view.setTypeface(Typeface.create(sp >= 22 ? "sans-serif" : "sans-serif-medium", sp >= 22 ? Typeface.BOLD : Typeface.NORMAL));
        if (sp >= 22) {
            view.setLetterSpacing(-0.02f);
        }
        view.setIncludeFontPadding(false);
        return view;
    }

    static TextView body(Context context, String text) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextColor(MUTED);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        view.setLineSpacing(dp(context, 3), 1.0f);
        return view;
    }

    static TextView small(Context context, String text, int color) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextColor(color);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        view.setLineSpacing(dp(context, 2), 1.0f);
        return view;
    }

    static TextView section(Context context, String text) {
        TextView view = title(context, text, 17);
        view.setPadding(dp(context, 2), 0, 0, 0);
        return view;
    }

    /** Rótulo pequeno em caixa alta, acima de números e seções. */
    static TextView overline(Context context, String text, int color) {
        TextView view = new TextView(context);
        view.setText(text.toUpperCase(java.util.Locale.forLanguageTag("pt-BR")));
        view.setTextColor(color);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        view.setLetterSpacing(0.06f);
        view.setHyphenationFrequency(android.text.Layout.HYPHENATION_FREQUENCY_NONE);
        view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        view.setIncludeFontPadding(false);
        return view;
    }

    // ---------- Botões ----------

    static TextView button(Context context, String label, String icon, boolean primary, View.OnClickListener listener) {
        TextView button = new TextView(context);
        button.setText(label);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setGravity(Gravity.CENTER_VERTICAL);
        button.setMinHeight(dp(context, 56));
        button.setPadding(dp(context, 20), 0, dp(context, 20), 0);
        button.setTextColor(primary ? Color.WHITE : TEXT);
        if (primary) {
            GradientDrawable fill = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{HERO_START, HERO_END});
            fill.setCornerRadius(dp(context, 16));
            button.setBackground(fill);
            button.setElevation(dp(context, 3));
            button.setStateListAnimator(null);
        } else {
            button.setBackground(rounded(context, SURFACE, BORDER, 1, 16));
        }
        if (icon != null) {
            button.setCompoundDrawablePadding(dp(context, 12));
            button.setCompoundDrawablesWithIntrinsicBounds(
                    new IconDrawable(icon, primary ? Color.WHITE : BLUE, dp(context, 22)), null, null, null);
        }
        button.setOnClickListener(listener);
        selectable(button, 16);
        return button;
    }

    static TextView pill(Context context, String label, boolean primary, View.OnClickListener listener) {
        TextView button = new TextView(context);
        button.setText(label);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(dp(context, 38));
        button.setPadding(dp(context, 16), 0, dp(context, 16), 0);
        button.setTextColor(primary ? Color.WHITE : BLUE);
        button.setBackground(primary
                ? rounded(context, BLUE, BLUE, 0, 19)
                : rounded(context, BLUE_SOFT, BLUE_SOFT, 0, 19));
        button.setOnClickListener(listener);
        selectable(button, 19);
        return button;
    }

    static View dot(Context context, int color) {
        View dot = new View(context);
        dot.setBackground(oval(color));
        return dot;
    }

    static void selectable(View view) {
        selectable(view, 18);
    }

    /**
     * Toque com ripple e, para controle remoto/teclado (Fire TV), destaque forte no item focado:
     * contorno laranja e um leve brilho no fundo, desenhados dentro do próprio item. Nada cresce
     * para fora, então o foco nunca é cortado por colunas, cartões ou pela rolagem.
     */
    static void selectable(final View view, float radiusDp) {
        Context context = view.getContext();
        // Ripple recortado no mesmo arredondado do item: o padrão do Android pinta um retângulo no foco.
        Drawable ripple = new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(dark ? Color.argb(40, 255, 255, 255) : Color.argb(28, 15, 23, 42)),
                null, rounded(context, Color.WHITE, 0, 0, radiusDp));
        int glow = dark ? Color.argb(34, 255, 255, 255) : Color.argb(18, 15, 23, 42);
        StateListDrawable focus = new StateListDrawable();
        focus.addState(new int[]{android.R.attr.state_focused}, rounded(context, glow, FOCUS, 3, radiusDp));
        focus.addState(new int[0], new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        view.setForeground(new LayerDrawable(new Drawable[]{ripple, focus}));
        view.setClickable(true);
        view.setFocusable(true);
    }

    // ---------- Layout ----------

    static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    static LinearLayout.LayoutParams weight(float weight) {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight);
    }

    static ScrollView scroll(final Context context, final LinearLayout content) {
        ScrollView scrollView = new ScrollView(context);
        scrollView.setClipToPadding(false);
        scrollView.setBackgroundColor(BACKGROUND);
        scrollView.setVerticalScrollBarEnabled(false);
        final boolean wide = twoColumns(context);
        final int gutter = dp(context, wide ? 32 : 18);
        content.setPadding(gutter, dp(context, wide ? 32 : 20), gutter, dp(context, 28));
        // Coluna única confortável de ler; com duas colunas, um limite maior para não esticar demais.
        final int maxWidth = dp(context, wide ? 1280 : 680);
        scrollView.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View view, int left, int top, int right, int bottom,
                                       int oldLeft, int oldTop, int oldRight, int oldBottom) {
                int side = Math.max(gutter, (right - left - maxWidth) / 2);
                if (content.getPaddingLeft() != side) {
                    content.setPadding(side, content.getPaddingTop(), side, content.getPaddingBottom());
                }
            }
        });
        scrollView.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return scrollView;
    }

    /**
     * Página de tela grande: ocupa exatamente a altura da tela, sem rolar. O título fica no topo
     * e cada coluna rola sozinha só se o conteúdo dela não couber (ver {@link #column}).
     */
    static LinearLayout page(final Context context, final LinearLayout content) {
        content.setBackgroundColor(BACKGROUND);
        final int gutter = dp(context, 32);
        final int maxWidth = dp(context, 1280);
        content.setPadding(gutter, dp(context, 24), gutter, dp(context, 12));
        content.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View view, int left, int top, int right, int bottom,
                                       int oldLeft, int oldTop, int oldRight, int oldBottom) {
                int side = Math.max(gutter, (right - left - maxWidth) / 2);
                if (content.getPaddingLeft() != side) {
                    content.setPadding(side, content.getPaddingTop(), side, content.getPaddingBottom());
                }
            }
        });
        return content;
    }

    /** Coluna com rolagem própria, que preenche a altura disponível. */
    static ScrollView column(Context context, LinearLayout column) {
        ScrollView scrollView = new ScrollView(context);
        scrollView.setFillViewport(true);
        scrollView.setVerticalScrollBarEnabled(false);
        scrollView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scrollView.setClipToPadding(false);
        // Pequena folga para a sombra dos botões e o contorno de foco nas bordas da coluna.
        column.setPadding(dp(context, 4), dp(context, 4), dp(context, 4), dp(context, 16));
        scrollView.addView(column, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return scrollView;
    }

    /** "Fire TV", "tablet" ou "celular", para os textos falarem do aparelho certo. */
    static String device(Context context) {
        if (FireTv.is(context)) {
            return "Fire TV";
        }
        return context.getResources().getConfiguration().smallestScreenWidthDp >= 600 ? "tablet" : "celular";
    }

    static int colorForScore(int score) {
        if (score >= 55) {
            return GREEN;
        }
        if (score >= 30) {
            return ORANGE;
        }
        return RED;
    }

    /** Uma palavra para a saúde do aparelho, mostrada no medidor. */
    static String healthWord(int score) {
        if (score >= 75) {
            return "Ótimo";
        }
        if (score >= 55) {
            return "Bem";
        }
        if (score >= 30) {
            return "Pode melhorar";
        }
        return "Precisa de ajuda";
    }
}

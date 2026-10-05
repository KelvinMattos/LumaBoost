package com.kelvin.lumaboost;

import android.content.Intent;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Uma aba do app. Constrói sua própria árvore de views. */
abstract class Screen {
    final MainActivity activity;
    final LinearLayout content;
    /**
     * Colunas da tela. Em tablets e TVs ficam lado a lado; no celular as duas apontam para
     * {@link #content} e tudo fica numa coluna só, na ordem esquerda e depois direita.
     */
    LinearLayout left;
    LinearLayout right;
    private View view;

    Screen(MainActivity activity) {
        this.activity = activity;
        this.content = Ui.vertical(activity);
    }

    final View view() {
        if (view == null) {
            // Telas grandes: a página cabe na altura da tela e as colunas rolam por dentro.
            view = Ui.twoColumns(activity) ? Ui.page(activity, content) : Ui.scroll(activity, content);
            left = content;
            right = content;
            build();
        }
        return view;
    }

    /** Descarta as views (ex.: a tela girou e mudou de uma para duas colunas). O estado da tela continua. */
    final void reset() {
        if (view instanceof ScrollView) {
            ((ScrollView) view).removeAllViews();
        }
        view = null;
        content.removeAllViews();
    }

    abstract void build();

    void onShow() {
    }

    void onHide() {
    }

    void onActivityResult(int requestCode, int resultCode, Intent data) {
    }

    void header(String title, String subtitle) {
        boolean wide = Ui.twoColumns(activity);
        TextView heading = Ui.title(activity, title, 30);
        content.addView(heading);
        Ui.gap(content, wide ? 6 : 8);
        TextView sub = Ui.body(activity, subtitle);
        sub.setTextSize(wide ? 14 : 15);
        sub.setMaxWidth(dp(720));
        content.addView(sub, Ui.matchWrap());
        Ui.gap(content, wide ? 18 : 20);
    }

    /**
     * Divide o resto da tela em duas colunas quando há espaço. A da esquerda pesa {@code leftWeight}
     * e a da direita 1. No celular não faz nada.
     */
    void split(float leftWeight) {
        if (!Ui.twoColumns(activity)) {
            return;
        }
        LinearLayout row = Ui.horizontal(activity);
        row.setGravity(Gravity.TOP);
        row.setBaselineAligned(false);
        left = Ui.vertical(activity);
        right = Ui.vertical(activity);
        LinearLayout.LayoutParams leftParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, leftWeight);
        leftParams.setMargins(-dp(4), 0, dp(20), 0);
        row.addView(Ui.column(activity, left), leftParams);
        LinearLayout.LayoutParams rightParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
        rightParams.setMargins(0, 0, -dp(4), 0);
        row.addView(Ui.column(activity, right), rightParams);
        // As colunas ocupam o resto da altura da tela, nunca mais que ela.
        content.addView(row, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
    }

    int dp(float value) {
        return Ui.dp(activity, value);
    }

    /** Linha de permissão com botão "Permitir". */
    View permissionRow(String title, String detail, View.OnClickListener onAllow) {
        LinearLayout row = Ui.horizontal(activity);
        LinearLayout texts = Ui.vertical(activity);
        texts.addView(Ui.title(activity, title, 15));
        Ui.gap(texts, 3);
        texts.addView(Ui.small(activity, detail, Ui.MUTED));
        row.addView(texts, Ui.weight(1));
        TextView allow = Ui.pill(activity, "Permitir", true, onAllow);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(dp(12), 0, 0, 0);
        row.addView(allow, params);
        return row;
    }
}

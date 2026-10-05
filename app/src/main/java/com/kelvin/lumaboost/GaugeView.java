package com.kelvin.lumaboost;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.View;

/** Medidor circular da saúde do aparelho (0–100), num cartão em degradê. */
final class GaugeView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF cardRect = new RectF();
    private final RectF arcRect = new RectF();
    private final RectF pill = new RectF();
    private final android.graphics.Path clip = new android.graphics.Path();
    private float shownScore = 0f;
    private int score = 0;
    private String label = "SAÚDE DO CELULAR";
    private String caption = "";
    private ValueAnimator animator;
    private LinearGradient background;
    private int backgroundWidth;
    private int backgroundHeight;

    GaugeView(Context context) {
        super(context);
        setContentDescription("Saúde do celular");
    }

    void setMetrics(int newScore, String newCaption) {
        score = Math.max(0, Math.min(100, newScore));
        caption = newCaption;
        setContentDescription(label + " " + score + " de 100. " + newCaption);
        if (animator != null) {
            animator.cancel();
        }
        animator = ValueAnimator.ofFloat(shownScore, score);
        animator.setDuration(700L);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                shownScore = (float) animation.getAnimatedValue();
                invalidate();
            }
        });
        animator.start();
    }

    void setLabel(String newLabel) {
        label = newLabel;
        invalidate();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animator != null) {
            animator.cancel();
        }
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth();
        float height = getHeight();
        Context context = getContext();
        float radius = Ui.dp(context, 24);
        cardRect.set(0, 0, width, height);

        if (background == null || backgroundWidth != getWidth() || backgroundHeight != getHeight()) {
            background = new LinearGradient(0, 0, width, height, Ui.HERO_START, Ui.HERO_END, Shader.TileMode.CLAMP);
            backgroundWidth = getWidth();
            backgroundHeight = getHeight();
        }
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(background);
        canvas.drawRoundRect(cardRect, radius, radius, paint);
        paint.setShader(null);

        // Círculos translúcidos de enfeite, recortados pelo cartão.
        canvas.save();
        clip.reset();
        clip.addRoundRect(cardRect, radius, radius, android.graphics.Path.Direction.CW);
        canvas.clipPath(clip);
        paint.setColor(Color.argb(28, 255, 255, 255));
        canvas.drawCircle(width * 0.92f, height * 0.08f, height * 0.42f, paint);
        paint.setColor(Color.argb(18, 255, 255, 255));
        canvas.drawCircle(width * 0.04f, height * 0.98f, height * 0.36f, paint);
        canvas.restore();

        float size = Math.min(width - Ui.dp(context, 48), height - Ui.dp(context, 104));
        float left = (width - size) / 2.0f;
        float top = Ui.dp(context, 50);
        arcRect.set(left, top, left + size, top + size);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Ui.dp(context, 14));
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(Color.argb(60, 255, 255, 255));
        canvas.drawArc(arcRect, 140.0f, 260.0f, false, paint);

        int rounded = Math.round(shownScore);
        paint.setColor(Color.WHITE);
        canvas.drawArc(arcRect, 140.0f, Math.max(0.5f, 260.0f * (shownScore / 100.0f)), false, paint);

        paint.setStrokeCap(Paint.Cap.BUTT);
        paint.setStyle(Paint.Style.FILL);
        paint.setTextAlign(Paint.Align.CENTER);

        paint.setColor(Color.argb(210, 255, 255, 255));
        paint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        paint.setTextSize(Ui.dp(context, 12));
        paint.setLetterSpacing(0.1f);
        canvas.drawText(label, width / 2.0f, Ui.dp(context, 32), paint);
        paint.setLetterSpacing(0f);

        paint.setColor(Color.WHITE);
        paint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        paint.setTextSize(Math.min(Ui.dp(context, 64), size * 0.34f));
        Paint.FontMetrics metrics = paint.getFontMetrics();
        float base = arcRect.centerY() - (metrics.ascent + metrics.descent) / 2.0f - Ui.dp(context, 8);
        canvas.drawText(String.valueOf(rounded), width / 2.0f, base, paint);

        // Palavra da saúde numa pílula translúcida, logo abaixo do número.
        String word = Ui.healthWord(rounded);
        paint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        paint.setTextSize(Ui.dp(context, 13));
        float wordWidth = paint.measureText(word);
        float pillTop = base + Ui.dp(context, 10);
        float pillHeight = Ui.dp(context, 24);
        float dotSize = Ui.dp(context, 8);
        float pillWidth = wordWidth + dotSize + Ui.dp(context, 26);
        pill.set(width / 2f - pillWidth / 2f, pillTop, width / 2f + pillWidth / 2f, pillTop + pillHeight);
        paint.setColor(Color.argb(46, 255, 255, 255));
        canvas.drawRoundRect(pill, pillHeight / 2f, pillHeight / 2f, paint);
        paint.setColor(scoreDot(rounded));
        canvas.drawCircle(pill.left + Ui.dp(context, 10) + dotSize / 2f, pill.centerY(), dotSize / 2f, paint);
        paint.setColor(Color.WHITE);
        paint.setTextAlign(Paint.Align.LEFT);
        Paint.FontMetrics small = paint.getFontMetrics();
        canvas.drawText(word, pill.left + Ui.dp(context, 16) + dotSize, pill.centerY() - (small.ascent + small.descent) / 2f, paint);

        paint.setTextAlign(Paint.Align.CENTER);
        paint.setColor(Color.argb(225, 255, 255, 255));
        paint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        paint.setTextSize(Ui.dp(context, 13.5f));
        canvas.drawText(caption, width / 2.0f, height - Ui.dp(context, 22), paint);
    }

    /** Cores vivas que se destacam no degradê azul-violeta. */
    private static int scoreDot(int score) {
        if (score >= 55) {
            return Color.rgb(74, 222, 128);
        }
        if (score >= 30) {
            return Color.rgb(253, 186, 116);
        }
        return Color.rgb(252, 129, 129);
    }
}

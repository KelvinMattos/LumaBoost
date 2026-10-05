package com.kelvin.lumaboost;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

/** Destaque "Toque aqui" desenhado por cima das telas do Android durante o tutorial. Não bloqueia toques. */
final class GuideHighlightView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect target = new Rect();
    private final RectF ring = new RectF();
    private final RectF bubble = new RectF();
    private final int[] location = new int[2];
    private final ValueAnimator pulse;
    private float phase;
    private String message = "Toque aqui";
    private boolean hasTarget;

    GuideHighlightView(Context context) {
        super(context);
        pulse = ValueAnimator.ofFloat(0f, 1f);
        pulse.setDuration(1100L);
        pulse.setRepeatCount(ValueAnimator.INFINITE);
        pulse.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                phase = (float) animation.getAnimatedValue();
                invalidate();
            }
        });
        pulse.start();
    }

    /** {@code screenBounds} em coordenadas da tela, como vêm do AccessibilityNodeInfo. */
    void point(Rect screenBounds, String text) {
        hasTarget = screenBounds != null && !screenBounds.isEmpty();
        if (hasTarget) {
            target.set(screenBounds);
        }
        message = text;
        invalidate();
    }

    void stop() {
        pulse.cancel();
    }

    @Override
    protected void onDetachedFromWindow() {
        pulse.cancel();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float density = getResources().getDisplayMetrics().density;
        getLocationOnScreen(location);

        paint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        paint.setTextSize(15 * density);
        float textWidth = paint.measureText(message);
        float padH = 16 * density;
        float bubbleH = 44 * density;

        float bubbleTop;
        float centerX;
        if (hasTarget) {
            float left = target.left - location[0];
            float top = target.top - location[1];
            float right = target.right - location[0];
            float bottom = target.bottom - location[1];
            float grow = (6 + 10 * phase) * density;
            ring.set(left - grow, top - grow, right + grow, bottom + grow);
            float radius = Math.min(ring.height(), ring.width()) / 2f;

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(4 * density);
            paint.setColor(Color.argb(Math.round(255 * (1f - phase)), 0, 122, 255));
            canvas.drawRoundRect(ring, radius, radius, paint);
            ring.set(left - 6 * density, top - 6 * density, right + 6 * density, bottom + 6 * density);
            paint.setColor(Ui.BLUE);
            canvas.drawRoundRect(ring, radius, radius, paint);

            centerX = (left + right) / 2f;
            boolean below = top < getHeight() * 0.45f;
            bubbleTop = below ? bottom + 22 * density : top - 22 * density - bubbleH;
        } else {
            centerX = getWidth() / 2f;
            bubbleTop = getHeight() - bubbleH - 120 * density;
        }

        float bubbleW = textWidth + padH * 2;
        float bubbleLeft = Math.max(12 * density, Math.min(getWidth() - bubbleW - 12 * density, centerX - bubbleW / 2f));
        bubble.set(bubbleLeft, bubbleTop, bubbleLeft + bubbleW, bubbleTop + bubbleH);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Ui.BLUE);
        paint.setShadowLayer(8 * density, 0, 2 * density, Color.argb(70, 0, 0, 0));
        canvas.drawRoundRect(bubble, bubbleH / 2f, bubbleH / 2f, paint);
        paint.clearShadowLayer();
        paint.setColor(Color.WHITE);
        paint.setTextAlign(Paint.Align.CENTER);
        Paint.FontMetrics metrics = paint.getFontMetrics();
        canvas.drawText(message, bubble.centerX(), bubble.centerY() - (metrics.ascent + metrics.descent) / 2f, paint);
        paint.setTextAlign(Paint.Align.LEFT);
    }
}

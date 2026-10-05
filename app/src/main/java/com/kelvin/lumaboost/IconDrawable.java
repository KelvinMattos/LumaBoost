package com.kelvin.lumaboost;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/** Ícones vetoriais desenhados em uma grade 24x24, sem dependências de recursos. */
final class IconDrawable extends Drawable {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();
    private final String type;
    private final int size;
    private int color;

    IconDrawable(String type, int color, int size) {
        this.type = type;
        this.color = color;
        this.size = size;
        setBounds(0, 0, size, size);
    }

    void setColor(int color) {
        this.color = color;
        invalidateSelf();
    }

    @Override
    public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        canvas.save();
        canvas.translate(bounds.left, bounds.top);
        canvas.scale(bounds.width() / 24.0f, bounds.height() / 24.0f);
        paint.setColor(color);
        paint.setStrokeWidth(2.0f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStyle(Paint.Style.STROKE);

        switch (type) {
            case "boost":
                paint.setStyle(Paint.Style.FILL);
                path.reset();
                path.moveTo(13.0f, 2.5f);
                path.lineTo(4.5f, 13.0f);
                path.lineTo(11.0f, 13.0f);
                path.lineTo(9.5f, 21.5f);
                path.lineTo(19.5f, 9.5f);
                path.lineTo(13.0f, 9.5f);
                path.close();
                canvas.drawPath(path, paint);
                break;
            case "clean":
                canvas.drawLine(4, 6.5f, 20, 6.5f, paint);
                canvas.drawLine(9.5f, 6.5f, 10, 3.5f, paint);
                canvas.drawLine(10, 3.5f, 14, 3.5f, paint);
                canvas.drawLine(14, 3.5f, 14.5f, 6.5f, paint);
                path.reset();
                path.moveTo(6, 6.5f);
                path.lineTo(7.2f, 20);
                path.lineTo(16.8f, 20);
                path.lineTo(18, 6.5f);
                canvas.drawPath(path, paint);
                canvas.drawLine(10, 10.5f, 10.3f, 16.5f, paint);
                canvas.drawLine(14, 10.5f, 13.7f, 16.5f, paint);
                break;
            case "apps":
                rect.set(4, 4, 10, 10);
                canvas.drawRoundRect(rect, 1.6f, 1.6f, paint);
                rect.set(14, 4, 20, 10);
                canvas.drawRoundRect(rect, 1.6f, 1.6f, paint);
                rect.set(4, 14, 10, 20);
                canvas.drawRoundRect(rect, 1.6f, 1.6f, paint);
                rect.set(14, 14, 20, 20);
                canvas.drawRoundRect(rect, 1.6f, 1.6f, paint);
                break;
            case "tune":
                canvas.drawLine(4, 7, 20, 7, paint);
                canvas.drawLine(4, 17, 20, 17, paint);
                paint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(9, 7, 2.6f, paint);
                canvas.drawCircle(15, 17, 2.6f, paint);
                break;
            case "storage":
                rect.set(4, 6, 20, 19);
                canvas.drawRoundRect(rect, 3.0f, 3.0f, paint);
                canvas.drawLine(7, 10, 17, 10, paint);
                canvas.drawLine(8, 15, 11, 15, paint);
                break;
            case "battery":
                rect.set(3, 7, 19, 17);
                canvas.drawRoundRect(rect, 2.0f, 2.0f, paint);
                rect.set(20, 10, 22, 14);
                canvas.drawRoundRect(rect, 1.0f, 1.0f, paint);
                paint.setStyle(Paint.Style.FILL);
                rect.set(6, 10, 14, 14);
                canvas.drawRoundRect(rect, 1.0f, 1.0f, paint);
                break;
            case "ram":
                rect.set(4, 7, 20, 17);
                canvas.drawRoundRect(rect, 1.6f, 1.6f, paint);
                canvas.drawLine(8, 17, 8, 20, paint);
                canvas.drawLine(12, 17, 12, 20, paint);
                canvas.drawLine(16, 17, 16, 20, paint);
                canvas.drawLine(8, 4, 8, 7, paint);
                canvas.drawLine(12, 4, 12, 7, paint);
                canvas.drawLine(16, 4, 16, 7, paint);
                break;
            case "shield":
                path.reset();
                path.moveTo(12, 3);
                path.lineTo(19, 6);
                path.lineTo(19, 11.5f);
                path.cubicTo(19, 16, 16, 19.5f, 12, 21);
                path.cubicTo(8, 19.5f, 5, 16, 5, 11.5f);
                path.lineTo(5, 6);
                path.close();
                canvas.drawPath(path, paint);
                canvas.drawLine(9, 12, 11.2f, 14.2f, paint);
                canvas.drawLine(11.2f, 14.2f, 15.5f, 9.8f, paint);
                break;
            case "cpu":
                rect.set(6, 6, 18, 18);
                canvas.drawRoundRect(rect, 2.0f, 2.0f, paint);
                rect.set(9.5f, 9.5f, 14.5f, 14.5f);
                canvas.drawRect(rect, paint);
                canvas.drawLine(10, 3, 10, 6, paint);
                canvas.drawLine(14, 3, 14, 6, paint);
                canvas.drawLine(10, 18, 10, 21, paint);
                canvas.drawLine(14, 18, 14, 21, paint);
                canvas.drawLine(3, 10, 6, 10, paint);
                canvas.drawLine(3, 14, 6, 14, paint);
                canvas.drawLine(18, 10, 21, 10, paint);
                canvas.drawLine(18, 14, 21, 14, paint);
                break;
            case "clock":
                canvas.drawCircle(12, 12, 8.5f, paint);
                canvas.drawLine(12, 7.5f, 12, 12, paint);
                canvas.drawLine(12, 12, 15, 14, paint);
                break;
            case "check":
                canvas.drawLine(5, 12.5f, 10, 17.5f, paint);
                canvas.drawLine(10, 17.5f, 19.5f, 7, paint);
                break;
            case "search":
                canvas.drawCircle(10.5f, 10.5f, 6, paint);
                canvas.drawLine(15, 15, 20, 20, paint);
                break;
            default:
                rect.set(5, 5, 19, 19);
                canvas.drawArc(rect, 35, 275, false, paint);
                paint.setStyle(Paint.Style.FILL);
                path.reset();
                path.moveTo(17.5f, 3.0f);
                path.lineTo(20.5f, 8.0f);
                path.lineTo(14.7f, 7.2f);
                path.close();
                canvas.drawPath(path, paint);
                break;
        }
        canvas.restore();
    }

    @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        paint.setColorFilter(colorFilter);
    }

    @Override
    @SuppressWarnings("deprecation")
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    @Override
    public int getIntrinsicWidth() {
        return size;
    }

    @Override
    public int getIntrinsicHeight() {
        return size;
    }
}

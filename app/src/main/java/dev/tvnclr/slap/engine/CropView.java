package dev.tvnclr.slap.engine;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

/**
 * Area quadrada de ajuste: comeca preenchendo o quadrado (recorte automatico centralizado);
 * arrastar move, pinca da zoom, toque duplo alterna entre preencher e encaixar inteiro.
 */
public class CropView extends View {

    private Bitmap src;
    private final Matrix m = new Matrix();
    private final Paint bmpPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Paint check = new Paint();
    private final Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean fillMode = true;
    private final ScaleGestureDetector scaler;
    private final GestureDetector gestures;
    private float lastX, lastY;
    private boolean dragging;
    private final Path clip = new Path();
    private Runnable onChange;
    private int colorA = 0xFF25232F, colorB = 0xFF1B1A22, colorText = 0xFFA6A2B6;
    private String placeholder = "";

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    /** Theme colors: border, the two checkerboard tones behind transparency, placeholder text. */
    public void setColors(int borderColor, int checkA, int checkB, int text, String loading) {
        border.setColor(borderColor);
        colorA = checkA;
        colorB = checkB;
        colorText = text;
        placeholder = loading;
        invalidate();
    }

    public CropView(Context c) {
        super(c);
        border.setStyle(Paint.Style.STROKE);
        border.setColor(0xFF2C2A37);
        border.setStrokeWidth(dp(1.5f));
        scaler = new ScaleGestureDetector(c, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector d) {
                float f = d.getScaleFactor();
                float cur = currentScale();
                float min = fitScale() * 0.25f, max = fillScale() * 8f;
                float target = Math.max(min, Math.min(max, cur * f));
                f = target / cur;
                m.postScale(f, f, d.getFocusX(), d.getFocusY());
                changed();
                return true;
            }
        });
        gestures = new GestureDetector(c, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                setFillMode(!fillMode);
                return true;
            }
        });
    }

    public void setSource(Bitmap b, boolean keepTransform) {
        boolean sameSize = src != null && b != null && src.getWidth() == b.getWidth() && src.getHeight() == b.getHeight();
        src = b;
        if (!(keepTransform && sameSize)) {
            reset();
            changed();
        } else {
            invalidate();
        }
    }

    /** Avisado sempre que o enquadramento muda (arrastar, zoom, encaixar). */
    public void setOnChange(Runnable r) {
        onChange = r;
    }

    private void changed() {
        invalidate();
        if (onChange != null) onChange.run();
    }

    /** Matriz atual: coordenadas do bitmap de origem para a view. */
    public Matrix displayMatrix() {
        return new Matrix(m);
    }

    public int sourceWidth() {
        return src == null ? 0 : src.getWidth();
    }

    public int sourceHeight() {
        return src == null ? 0 : src.getHeight();
    }

    public boolean isFillMode() {
        return fillMode;
    }

    public void setFillMode(boolean fill) {
        fillMode = fill;
        reset();
        changed();
    }

    private float fitScale() {
        int s = getWidth();
        if (src == null || s == 0) return 1f;
        return Math.min((float) s / src.getWidth(), (float) s / src.getHeight());
    }

    private float fillScale() {
        int s = getWidth();
        if (src == null || s == 0) return 1f;
        return Math.max((float) s / src.getWidth(), (float) s / src.getHeight());
    }

    private float currentScale() {
        float[] v = new float[9];
        m.getValues(v);
        return (float) Math.hypot(v[Matrix.MSCALE_X], v[Matrix.MSKEW_Y]);
    }

    private void reset() {
        m.reset();
        int s = getWidth();
        if (src == null || s == 0) return;
        float sc = fillMode ? fillScale() : fitScale();
        m.postScale(sc, sc);
        m.postTranslate((s - src.getWidth() * sc) / 2f, (s - src.getHeight() * sc) / 2f);
    }

    /** Matriz que leva coordenadas do bitmap de origem para o quadrado de saida (size x size). */
    public Matrix outputMatrix(int size) {
        Matrix out = new Matrix(m);
        int s = getWidth();
        if (s > 0) out.postScale((float) size / s, (float) size / s);
        return out;
    }

    @Override
    protected void onMeasure(int w, int h) {
        int width = MeasureSpec.getSize(w);
        setMeasuredDimension(width, width);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        float r = dp(22);
        clip.reset();
        clip.addRoundRect(new RectF(0, 0, w, h), r, r, Path.Direction.CW);
        reset();
        changed();
    }

    @Override
    protected void onDraw(Canvas c) {
        int s = getWidth();
        c.save();
        c.clipPath(clip);
        int cell = Math.max(8, s / 16);
        for (int y = 0; y < s; y += cell) {
            for (int x = 0; x < s; x += cell) {
                check.setColor(((x / cell + y / cell) % 2 == 0) ? colorA : colorB);
                c.drawRect(x, y, Math.min(s, x + cell), Math.min(s, y + cell), check);
            }
        }
        if (src != null && !src.isRecycled()) c.drawBitmap(src, m, bmpPaint);
        c.restore();
        float r = dp(22), half = border.getStrokeWidth() / 2f;
        c.drawRoundRect(new RectF(half, half, s - half, s - half), r, r, border);
        if (src == null) {
            Paint t = new Paint(Paint.ANTI_ALIAS_FLAG);
            t.setColor(colorText);
            t.setTextSize(s / 18f);
            t.setTextAlign(Paint.Align.CENTER);
            c.drawText(placeholder, s / 2f, s / 2f, t);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (src == null) return false;
        getParent().requestDisallowInterceptTouchEvent(true);
        scaler.onTouchEvent(e);
        gestures.onTouchEvent(e);
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastX = e.getX();
                lastY = e.getY();
                dragging = true;
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                dragging = false;
                break;
            case MotionEvent.ACTION_MOVE:
                if (dragging && e.getPointerCount() == 1 && !scaler.isInProgress()) {
                    m.postTranslate(e.getX() - lastX, e.getY() - lastY);
                    changed();
                }
                lastX = e.getX();
                lastY = e.getY();
                break;
            case MotionEvent.ACTION_POINTER_UP:
                // volta a arrastar com o dedo que sobrou
                int idx = e.getActionIndex() == 0 ? 1 : 0;
                lastX = e.getX(idx);
                lastY = e.getY(idx);
                dragging = true;
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                getParent().requestDisallowInterceptTouchEvent(false);
                break;
            default:
                break;
        }
        return true;
    }
}

package dev.tvnclr.slap.engine;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import java.util.ArrayList;
import java.util.List;

import dev.tvnclr.slap.R;

/**
 * Draws the text boxes over the crop square and handles their gestures: one finger moves a box,
 * two fingers scale and rotate it, a tap opens it for editing and dropping it on the bin deletes
 * it. Touches that miss every box fall through to the crop view underneath.
 */
public class TextLayersView extends View {

    public interface Listener {
        /** A box was tapped (index in the list). */
        void onTap(int index);

        /** A box was moved, scaled, rotated, brought to the front or deleted. */
        void onChanged();
    }

    private List<TextLayer> layers = new ArrayList<>();
    private Listener listener;
    private int hidden = -1;

    private int active = -1;
    private int idA = -1, idB = -1;
    private float lastX, lastY;
    private float startSpan, startAngle, startSize, startRot;
    private float downX, downY;
    private long downAt;
    private boolean moved, pinching, overBin;

    private final float density = getResources().getDisplayMetrics().density;
    private final int slop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
    private final Paint binPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Drawable binIcon;

    public TextLayersView(Context c) {
        super(c);
        Fonts.init(c);
        binIcon = c.getDrawable(R.drawable.ic_text_trash);
    }

    /** The list is shared with the caller: the view edits its items in place. */
    public void setLayers(List<TextLayer> l) {
        layers = l == null ? new ArrayList<>() : l;
        invalidate();
    }

    public void setListener(Listener l) {
        listener = l;
    }

    /** Hides one box (the one open in the full-screen editor); -1 shows all. */
    public void setHidden(int index) {
        hidden = index;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        int s = getWidth();
        if (s == 0) return;
        for (int i = 0; i < layers.size(); i++) if (i != hidden) layers.get(i).draw(c, s);
        if (active >= 0 && moved && !pinching) drawBin(c, s);
    }

    private float binRadius() {
        return 26 * density;
    }

    private float binCx() {
        return getWidth() / 2f;
    }

    private float binCy() {
        return getHeight() - binRadius() - 14 * density;
    }

    private void drawBin(Canvas c, int s) {
        float r = binRadius() * (overBin ? 1.18f : 1f);
        binPaint.setColor(overBin ? 0xE6E5484D : 0x99000000);
        c.drawCircle(binCx(), binCy(), r, binPaint);
        if (binIcon != null) {
            int half = Math.round(12 * density);
            binIcon.setBounds(Math.round(binCx()) - half, Math.round(binCy()) - half, Math.round(binCx()) + half, Math.round(binCy()) + half);
            binIcon.setTint(Color.WHITE);
            binIcon.draw(c);
        }
    }

    private int topmostAt(float px, float py) {
        int s = getWidth();
        for (int i = layers.size() - 1; i >= 0; i--) {
            if (i != hidden && layers.get(i).hit(px, py, s, 12 * density)) return i;
        }
        return -1;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int s = getWidth();
        if (s == 0 || !isEnabled()) return false;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                int i = topmostAt(e.getX(), e.getY());
                if (i < 0) return false;
                // bring the touched box to the front
                if (i != layers.size() - 1) {
                    layers.add(layers.remove(i));
                    i = layers.size() - 1;
                }
                active = i;
                idA = e.getPointerId(0);
                idB = -1;
                lastX = downX = e.getX();
                lastY = downY = e.getY();
                downAt = e.getEventTime();
                moved = pinching = overBin = false;
                getParent().requestDisallowInterceptTouchEvent(true);
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_POINTER_DOWN: {
                if (active < 0 || idB >= 0) return true;
                idB = e.getPointerId(e.getActionIndex());
                startPinch(e);
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                if (active < 0) return false;
                TextLayer t = layers.get(active);
                int a = e.findPointerIndex(idA);
                if (a < 0) return true;
                if (idB >= 0 && e.findPointerIndex(idB) >= 0) {
                    int b = e.findPointerIndex(idB);
                    float span = dist(e.getX(a), e.getY(a), e.getX(b), e.getY(b));
                    float ang = angle(e.getX(a), e.getY(a), e.getX(b), e.getY(b));
                    if (startSpan > 0) t.size = clamp(startSize * span / startSpan, TextLayer.MIN_SIZE, TextLayer.MAX_SIZE);
                    t.rotation = snapRotation(startRot + ang - startAngle);
                    float fx = (e.getX(a) + e.getX(b)) / 2f, fy = (e.getY(a) + e.getY(b)) / 2f;
                    t.x = clamp(t.x + (fx - lastX) / s, 0f, 1f);
                    t.y = clamp(t.y + (fy - lastY) / s, 0f, 1f);
                    lastX = fx;
                    lastY = fy;
                    moved = true;
                } else {
                    float px = e.getX(a), py = e.getY(a);
                    if (!moved && dist(px, py, downX, downY) > slop) moved = true;
                    if (moved) {
                        t.x = clamp(t.x + (px - lastX) / s, 0f, 1f);
                        t.y = clamp(t.y + (py - lastY) / s, 0f, 1f);
                        boolean over = dist(px, py, binCx(), binCy()) < binRadius() * 1.6f;
                        if (over != overBin) {
                            overBin = over;
                            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                        }
                    }
                    lastX = px;
                    lastY = py;
                }
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_POINTER_UP: {
                if (active < 0) return true;
                int gone = e.getPointerId(e.getActionIndex());
                if (gone == idA) {
                    idA = idB;
                } else if (gone != idB) {
                    return true;
                }
                idB = -1;
                pinching = true; // keep the bin hidden until the next fresh drag
                int a = e.findPointerIndex(idA);
                if (a >= 0) {
                    lastX = e.getX(a);
                    lastY = e.getY(a);
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                if (active < 0) return false;
                boolean cancel = e.getActionMasked() == MotionEvent.ACTION_CANCEL;
                int i = active;
                if (!cancel && overBin && !pinching) {
                    layers.remove(i);
                } else if (!cancel && !moved && e.getEventTime() - downAt < 350 && listener != null) {
                    resetTouch();
                    listener.onTap(i);
                    return true;
                }
                resetTouch();
                if (listener != null) listener.onChanged();
                return true;
            }
            default:
                return active >= 0;
        }
    }

    private void startPinch(MotionEvent e) {
        int a = e.findPointerIndex(idA), b = e.findPointerIndex(idB);
        if (a < 0 || b < 0) return;
        TextLayer t = layers.get(active);
        startSpan = dist(e.getX(a), e.getY(a), e.getX(b), e.getY(b));
        startAngle = angle(e.getX(a), e.getY(a), e.getX(b), e.getY(b));
        startSize = t.size;
        startRot = t.rotation;
        lastX = (e.getX(a) + e.getX(b)) / 2f;
        lastY = (e.getY(a) + e.getY(b)) / 2f;
        pinching = true;
        overBin = false;
    }

    private void resetTouch() {
        active = -1;
        idA = idB = -1;
        moved = pinching = overBin = false;
        getParent().requestDisallowInterceptTouchEvent(false);
        invalidate();
    }

    /** Sticks to straight angles within 4 degrees, like most editors. */
    private static float snapRotation(float deg) {
        float d = ((deg % 360f) + 540f) % 360f - 180f;
        for (float straight : new float[] {-180f, -90f, 0f, 90f, 180f}) if (Math.abs(d - straight) < 4f) return straight;
        return d;
    }

    private static float dist(float x1, float y1, float x2, float y2) {
        return (float) Math.hypot(x2 - x1, y2 - y1);
    }

    private static float angle(float x1, float y1, float x2, float y2) {
        return (float) Math.toDegrees(Math.atan2(y2 - y1, x2 - x1));
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}

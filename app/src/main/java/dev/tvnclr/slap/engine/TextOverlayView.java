package dev.tvnclr.slap.engine;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

/**
 * Draws the text box over the crop area and lets the user drag it. Touches that miss the text fall
 * through to the crop view underneath (pan and zoom keep working).
 */
public class TextOverlayView extends View {

    public interface OnMoved {
        void moved(float x, float y);
    }

    private TextLayer layer = new TextLayer();
    private OnMoved onMoved;
    private boolean dragging;
    private float lastX, lastY;

    public TextOverlayView(Context c) {
        super(c);
    }

    public void setLayer(TextLayer l) {
        layer = l == null ? new TextLayer() : l;
        invalidate();
    }

    public void setOnMoved(OnMoved m) {
        onMoved = m;
    }

    @Override
    protected void onDraw(Canvas c) {
        int s = getWidth();
        if (s > 0) layer.draw(c, s);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int s = getWidth();
        if (s == 0 || layer.isEmpty() || !isEnabled()) return false;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                RectF b = layer.bounds(s);
                float pad = 16 * getResources().getDisplayMetrics().density;
                b.inset(-pad, -pad);
                if (!b.contains(e.getX(), e.getY())) return false;
                dragging = true;
                lastX = e.getX();
                lastY = e.getY();
                getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            }
            case MotionEvent.ACTION_MOVE:
                if (!dragging) return false;
                layer.x = clamp(layer.x + (e.getX() - lastX) / s);
                layer.y = clamp(layer.y + (e.getY() - lastY) / s);
                lastX = e.getX();
                lastY = e.getY();
                invalidate();
                if (onMoved != null) onMoved.moved(layer.x, layer.y);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                getParent().requestDisallowInterceptTouchEvent(false);
                return true;
            default:
                return dragging;
        }
    }

    private static float clamp(float v) {
        return Math.max(0.05f, Math.min(0.95f, v));
    }
}

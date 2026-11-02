package dev.tvnclr.slap.engine;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * A text box drawn on top of the sticker. Position and size are fractions of the sticker side, so
 * the same layer draws identically on the 512 px output and on the editor preview.
 */
public final class TextLayer {

    /** Font families with the style that reads best on a sticker; index = {@link #font}. */
    private static final String[] FAMILIES = {"sans-serif-black", "sans-serif", "sans-serif-condensed", "serif", "monospace", "casual", "cursive"};
    private static final int[] STYLES = {Typeface.NORMAL, Typeface.BOLD, Typeface.BOLD, Typeface.BOLD, Typeface.BOLD, Typeface.NORMAL, Typeface.BOLD};
    public static final int FONT_COUNT = FAMILIES.length;

    public String text = "";
    public int font = 0;
    /** Letter height as a fraction of the side. */
    public float size = 0.12f;
    public int color = Color.WHITE;
    public boolean outline = true;
    /** Center of the box, as fractions of the side. */
    public float x = 0.5f, y = 0.82f;

    public boolean isEmpty() {
        return text == null || text.trim().isEmpty();
    }

    public TextLayer copy() {
        TextLayer t = new TextLayer();
        t.text = text;
        t.font = font;
        t.size = size;
        t.color = color;
        t.outline = outline;
        t.x = x;
        t.y = y;
        return t;
    }

    public static Typeface typeface(int font) {
        int i = Math.max(0, Math.min(FONT_COUNT - 1, font));
        return Typeface.create(FAMILIES[i], STYLES[i]);
    }

    private StaticLayout layout(TextPaint paint, float side) {
        int width = Math.max(1, Math.round(side * 0.94f));
        return StaticLayout.Builder.obtain(text, 0, text.length(), paint, width)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setIncludePad(false)
                .build();
    }

    private TextPaint paint(float side) {
        TextPaint p = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        p.setTypeface(typeface(font));
        p.setTextSize(size * side);
        return p;
    }

    /** Where the text box sits for a square of [side] px (for touch handling). */
    public RectF bounds(float side) {
        if (isEmpty()) return new RectF();
        StaticLayout l = layout(paint(side), side);
        float w = 0;
        for (int i = 0; i < l.getLineCount(); i++) w = Math.max(w, l.getLineWidth(i));
        float h = l.getHeight();
        float cx = x * side, cy = y * side;
        return new RectF(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f);
    }

    /** Draws the text centered at (x, y) on a square canvas of [side] px. */
    public void draw(Canvas c, float side) {
        if (isEmpty()) return;
        TextPaint p = paint(side);
        StaticLayout fill = layout(p, side);
        float top = y * side - fill.getHeight() / 2f;
        float left = x * side - fill.getWidth() / 2f;
        c.save();
        c.translate(left, top);
        if (outline) {
            TextPaint sp = paint(side);
            sp.setStyle(Paint.Style.STROKE);
            sp.setStrokeJoin(Paint.Join.ROUND);
            sp.setStrokeWidth(size * side * 0.16f);
            sp.setColor(outlineColor(color));
            layout(sp, side).draw(c);
        }
        p.setColor(color);
        fill.draw(c);
        c.restore();
    }

    /** Dark outline for light text and the other way around. */
    public static int outlineColor(int color) {
        double lum = (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255.0;
        return lum > 0.5 ? 0xFF111111 : 0xFFFFFFFF;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("text", text);
        o.put("font", font);
        o.put("size", size);
        o.put("color", color);
        o.put("outline", outline);
        o.put("x", x);
        o.put("y", y);
        return o;
    }

    public static TextLayer fromJson(JSONObject o) {
        TextLayer t = new TextLayer();
        if (o == null) return t;
        t.text = o.optString("text", "");
        t.font = o.optInt("font", 0);
        t.size = (float) o.optDouble("size", 0.12);
        t.color = o.optInt("color", Color.WHITE);
        t.outline = o.optBoolean("outline", true);
        t.x = (float) o.optDouble("x", 0.5);
        t.y = (float) o.optDouble("y", 0.82);
        return t;
    }
}

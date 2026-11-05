package dev.tvnclr.slap.engine;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * One text box on top of the sticker. Position and size are fractions of the sticker side, so the
 * same layer draws identically on the 512 px output and on the editor preview.
 */
public final class TextLayer {

    public static final int ALIGN_LEFT = 0, ALIGN_CENTER = 1, ALIGN_RIGHT = 2;
    /** Plain text, text with a contrasting outline, solid box in the chosen color, see-through box. */
    public static final int STYLE_PLAIN = 0, STYLE_OUTLINE = 1, STYLE_BOX = 2, STYLE_GLASS = 3;
    public static final int STYLE_COUNT = 4;

    /** Lines wrap past this many em, so scaling the box never changes its shape. */
    private static final float WRAP_EM = 9f;
    public static final float MIN_SIZE = 0.03f, MAX_SIZE = 0.6f;

    public String text = "";
    public String font = Fonts.ALL[0].id;
    /** Letter size as a fraction of the side. */
    public float size = 0.12f;
    public int color = Color.WHITE;
    public int align = ALIGN_CENTER;
    public int style = STYLE_OUTLINE;
    /** Center of the box as fractions of the side, and its rotation in degrees. */
    public float x = 0.5f, y = 0.5f, rotation = 0f;

    public boolean isEmpty() {
        return text == null || text.trim().isEmpty();
    }

    public TextLayer copy() {
        TextLayer t = new TextLayer();
        t.text = text;
        t.font = font;
        t.size = size;
        t.color = color;
        t.align = align;
        t.style = style;
        t.x = x;
        t.y = y;
        t.rotation = rotation;
        return t;
    }

    // ── colors per style (also used by the full-screen editor) ──

    /** Black or white, whichever reads on [color]. */
    public static int contrast(int color) {
        double lum = (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255.0;
        return lum > 0.55 ? 0xFF111111 : 0xFFFFFFFF;
    }

    public int fillColor() {
        return style == STYLE_BOX ? contrast(color) : color;
    }

    /** Outline color, or 0 when the style has none. */
    public int strokeColor() {
        return style == STYLE_OUTLINE ? contrast(color) : 0;
    }

    /** Box color, or 0 when the style has none. */
    public int boxColor() {
        if (style == STYLE_BOX) return color;
        if (style == STYLE_GLASS) return (contrast(color) & 0x00FFFFFF) | 0x99000000;
        return 0;
    }

    // ── layout ──

    private Layout.Alignment alignment() {
        if (align == ALIGN_LEFT) return Layout.Alignment.ALIGN_NORMAL;
        if (align == ALIGN_RIGHT) return Layout.Alignment.ALIGN_OPPOSITE;
        return Layout.Alignment.ALIGN_CENTER;
    }

    private TextPaint paint(float side) {
        TextPaint p = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        p.setTypeface(Fonts.typeface(font));
        p.setTextSize(size * side);
        return p;
    }

    private StaticLayout layout(TextPaint p, int width) {
        return StaticLayout.Builder.obtain(text, 0, text.length(), p, Math.max(1, width))
                .setAlignment(alignment())
                .setIncludePad(false)
                .build();
    }

    /** Layout as wide as its longest line, so the alignment and the box follow the text. */
    private StaticLayout fitted(TextPaint p, float side) {
        StaticLayout wide = layout(p, Math.round(size * side * WRAP_EM));
        float w = 0;
        for (int i = 0; i < wide.getLineCount(); i++) w = Math.max(w, wide.getLineWidth(i));
        return layout(p, (int) Math.ceil(w));
    }

    private float padX(float side) {
        return boxColor() != 0 ? size * side * 0.38f : size * side * 0.1f;
    }

    private float padY(float side) {
        return boxColor() != 0 ? size * side * 0.18f : size * side * 0.06f;
    }

    /** Half width and half height of the box, unrotated, in px for a square of [side]. */
    public float[] halfSize(float side) {
        if (isEmpty()) return new float[] {0, 0};
        StaticLayout l = fitted(paint(side), side);
        return new float[] {l.getWidth() / 2f + padX(side), l.getHeight() / 2f + padY(side)};
    }

    /** Whether point (px, py) of a square of [side] falls on this box, with [slop] px to spare. */
    public boolean hit(float px, float py, float side, float slop) {
        if (isEmpty()) return false;
        float[] h = halfSize(side);
        double a = Math.toRadians(-rotation);
        float dx = px - x * side, dy = py - y * side;
        float lx = (float) (dx * Math.cos(a) - dy * Math.sin(a));
        float ly = (float) (dx * Math.sin(a) + dy * Math.cos(a));
        return Math.abs(lx) <= h[0] + slop && Math.abs(ly) <= h[1] + slop;
    }

    /** Draws the box centered at (x, y), rotated, on a square canvas of [side] px. */
    public void draw(Canvas c, float side) {
        if (isEmpty()) return;
        TextPaint p = paint(side);
        StaticLayout fill = fitted(p, side);
        float w = fill.getWidth(), h = fill.getHeight();
        c.save();
        c.translate(x * side, y * side);
        c.rotate(rotation);
        int box = boxColor();
        if (box != 0) {
            Paint bp = new Paint(Paint.ANTI_ALIAS_FLAG);
            bp.setColor(box);
            float r = size * side * 0.32f;
            c.drawRoundRect(new RectF(-w / 2f - padX(side), -h / 2f - padY(side), w / 2f + padX(side), h / 2f + padY(side)), r, r, bp);
        }
        c.translate(-w / 2f, -h / 2f);
        int stroke = strokeColor();
        if (stroke != 0) {
            TextPaint sp = paint(side);
            sp.setStyle(Paint.Style.STROKE);
            sp.setStrokeJoin(Paint.Join.ROUND);
            sp.setStrokeWidth(size * side * 0.16f);
            sp.setColor(stroke);
            layout(sp, (int) w).draw(c);
        }
        p.setColor(fillColor());
        fill.draw(c);
        c.restore();
    }

    // ── storage ──

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("text", text);
        o.put("fontId", font);
        o.put("size", size);
        o.put("color", color);
        o.put("align", align);
        o.put("style", style);
        o.put("x", x);
        o.put("y", y);
        o.put("rotation", rotation);
        return o;
    }

    public static TextLayer fromJson(JSONObject o) {
        TextLayer t = new TextLayer();
        if (o == null) return t;
        t.text = o.optString("text", "");
        // the first editor stored a font index and an outline switch
        t.font = o.has("fontId") ? o.optString("fontId", t.font) : Fonts.legacy(o.optInt("font", 0));
        t.size = (float) o.optDouble("size", 0.12);
        t.color = o.optInt("color", Color.WHITE);
        t.align = o.optInt("align", ALIGN_CENTER);
        t.style = o.has("style") ? o.optInt("style", STYLE_OUTLINE) : (o.optBoolean("outline", true) ? STYLE_OUTLINE : STYLE_PLAIN);
        t.x = (float) o.optDouble("x", 0.5);
        t.y = (float) o.optDouble("y", 0.5);
        t.rotation = (float) o.optDouble("rotation", 0);
        return t;
    }

    public static JSONArray toJson(List<TextLayer> layers) throws JSONException {
        JSONArray a = new JSONArray();
        for (TextLayer t : layers) if (!t.isEmpty()) a.put(t.toJson());
        return a;
    }

    /** Reads the "texts" list of saved settings, or the single "text" of the first editor. */
    public static List<TextLayer> listFrom(JSONObject settings) {
        List<TextLayer> out = new ArrayList<>();
        if (settings == null) return out;
        JSONArray a = settings.optJSONArray("texts");
        if (a != null) {
            for (int i = 0; i < a.length(); i++) {
                TextLayer t = fromJson(a.optJSONObject(i));
                if (!t.isEmpty()) out.add(t);
            }
        } else {
            TextLayer t = fromJson(settings.optJSONObject("text"));
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }
}

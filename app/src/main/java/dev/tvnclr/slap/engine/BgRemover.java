package dev.tvnclr.slap.engine;

import android.graphics.Bitmap;

import java.util.Arrays;

/**
 * Removes a flat background: finds the background color on the border and makes transparent what
 * has that color and touches the border (or, with "everywhere", everything with that color), then
 * softens the outline.
 *
 * <p>The work happens on plain arrays in {@link #solidPixels} so it is unit tested on the JVM. The
 * mask is a single boolean array ("keep" = belongs to the sticker) from start to end, and each pass
 * is its own small method: an earlier version flipped the mask between byte and boolean arrays inside
 * one big method, and on an arm64 phone (Android 17) the compiled code of that method stopped
 * clearing anything after the first runs, while the interpreter got it right.
 */
final class BgRemover {

    /** Default tolerance (largest difference per channel, 0 to 255). */
    static final int DEFAULT_TOL = 40;

    private BgRemover() {}

    /** A new bitmap with the background transparent; a copy when the border is already transparent. */
    static Bitmap solid(Bitmap src, int tol, boolean everywhere) {
        return solid(src, tol, everywhere, 0);
    }

    /** Background color of a frame (most common border color), or 0 when the border is transparent. */
    static int referenceColor(Bitmap src) {
        int w = src.getWidth(), h = src.getHeight();
        int[] px = new int[w * h];
        src.getPixels(px, 0, w, 0, 0, w, h);
        return borderColor(px, w, h);
    }

    /**
     * Same as {@link #solid(Bitmap, int, boolean)} with a fixed background color [ref] (0 = find it
     * on this frame). GIFs pass the color of their first frame, so the cutout does not flicker.
     */
    static Bitmap solid(Bitmap src, int tol, boolean everywhere, int ref) {
        int w = src.getWidth(), h = src.getHeight();
        int[] px = new int[w * h];
        src.getPixels(px, 0, w, 0, 0, w, h);
        solidPixels(px, w, h, tol, everywhere, ref);
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        out.setHasAlpha(true);
        out.setPixels(px, 0, w, 0, 0, w, h);
        return out;
    }

    /** The removal on ARGB pixels, in place. [ref] 0 = take it from the border. */
    static void solidPixels(int[] px, int w, int h, int tol, boolean everywhere, int ref) {
        if (ref == 0) ref = borderColor(px, w, h);
        if (ref == 0) return;
        boolean[] keep = everywhere ? keepAllBut(px, ref, tol) : keepUnreached(px, w, h, ref, tol);
        int n = w * h;
        int small = Math.max(16, Math.round(n * 0.0008f));
        MaskOps.dropSpecks(keep, w, h, small, 0f);
        MaskOps.fillHoles(keep, w, h, small);
        feather(px, keep, w, h, ref, tol);
        MaskOps.clearOutside(px, keep);
    }

    static int dist(int a, int b) {
        int dr = Math.abs(((a >> 16) & 0xFF) - ((b >> 16) & 0xFF));
        int dg = Math.abs(((a >> 8) & 0xFF) - ((b >> 8) & 0xFF));
        int db = Math.abs((a & 0xFF) - (b & 0xFF));
        return Math.max(dr, Math.max(dg, db));
    }

    static boolean isBg(int c, int ref, int tol) {
        return (c >>> 24) < 16 || dist(c, ref) <= tol;
    }

    /**
     * Most common color among the opaque border pixels (grouped in 32 levels per channel), or 0 when
     * there is none or when see-through border pixels outnumber it (the background is already gone,
     * as in a PNG with a transparent background and a subject touching the edge).
     */
    static int borderColor(int[] px, int w, int h) {
        int[] count = new int[32 * 32 * 32];
        long[] sr = new long[count.length], sg = new long[count.length], sb = new long[count.length];
        int best = -1, clear = 0;
        for (int i = 0; i < 2 * (w + h); i++) {
            int x, y;
            if (i < w) { x = i; y = 0; }
            else if (i < 2 * w) { x = i - w; y = h - 1; }
            else if (i < 2 * w + h) { x = 0; y = i - 2 * w; }
            else { x = w - 1; y = i - 2 * w - h; }
            int c = px[y * w + x];
            if ((c >>> 24) < 128) {
                clear++;
                continue;
            }
            int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
            int k = (r >> 3) << 10 | (g >> 3) << 5 | (b >> 3);
            count[k]++;
            sr[k] += r;
            sg[k] += g;
            sb[k] += b;
            if (best < 0 || count[k] > count[best]) best = k;
        }
        if (best < 0 || clear >= count[best]) return 0;
        int n = count[best];
        return 0xFF000000 | (int) (sr[best] / n) << 16 | (int) (sg[best] / n) << 8 | (int) (sb[best] / n);
    }

    /** "Everywhere": keeps only the pixels that do not look like the background. */
    static boolean[] keepAllBut(int[] px, int ref, int tol) {
        boolean[] keep = new boolean[px.length];
        for (int i = 0; i < px.length; i++) keep[i] = !isBg(px[i], ref, tol);
        return keep;
    }

    /** Flood fill from every border pixel that looks like the background (4-neighbourhood); keeps the rest. */
    static boolean[] keepUnreached(int[] px, int w, int h, int ref, int tol) {
        boolean[] keep = new boolean[w * h];
        Arrays.fill(keep, true);
        int[] q = new int[w * h];
        int tail = 0;
        for (int x = 0; x < w; x++) {
            tail = seed(px, keep, q, tail, x, ref, tol);
            tail = seed(px, keep, q, tail, (h - 1) * w + x, ref, tol);
        }
        for (int y = 0; y < h; y++) {
            tail = seed(px, keep, q, tail, y * w, ref, tol);
            tail = seed(px, keep, q, tail, y * w + w - 1, ref, tol);
        }
        int head = 0;
        while (head < tail) {
            int i = q[head++];
            int x = i % w;
            if (x > 0) tail = seed(px, keep, q, tail, i - 1, ref, tol);
            if (x < w - 1) tail = seed(px, keep, q, tail, i + 1, ref, tol);
            if (i >= w) tail = seed(px, keep, q, tail, i - w, ref, tol);
            if (i < w * (h - 1)) tail = seed(px, keep, q, tail, i + w, ref, tol);
        }
        return keep;
    }

    private static int seed(int[] px, boolean[] keep, int[] q, int tail, int i, int ref, int tol) {
        if (keep[i] && isBg(px[i], ref, tol)) {
            keep[i] = false;
            q[tail++] = i;
        }
        return tail;
    }

    /** Kept pixels on the outline that look like the background become see-through, so no jagged rim is left. */
    static void feather(int[] px, boolean[] keep, int w, int h, int ref, int tol) {
        int soft = Math.max(8, tol);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                if (!keep[i]) continue;
                boolean edge = (x > 0 && !keep[i - 1]) || (x < w - 1 && !keep[i + 1])
                        || (y > 0 && !keep[i - w]) || (y < h - 1 && !keep[i + w]);
                if (!edge) continue;
                int d = dist(px[i], ref) - tol;
                if (d >= soft) continue;
                int a = (px[i] >>> 24) * Math.max(0, d) / soft;
                px[i] = (a << 24) | (px[i] & 0xFFFFFF);
            }
        }
    }
}
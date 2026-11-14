package dev.tvnclr.slap.engine;

/**
 * Cleanup for cutout masks (plain arrays, no Android types, so it is unit tested on the JVM).
 * A mask is a w*h array, row by row; "on" means the pixel belongs to the sticker.
 */
final class MaskOps {

    private MaskOps() {}

    /** Share of pixels that are (almost) fully transparent, alpha below 16. */
    static float transparentFraction(int[] argb) {
        if (argb.length == 0) return 0f;
        int n = 0;
        for (int c : argb) if ((c >>> 24) < 16) n++;
        return n / (float) argb.length;
    }

    /** Share of mask values that are (almost) fully opaque, 240 or more. */
    static float opaqueFraction(byte[] alpha) {
        if (alpha.length == 0) return 0f;
        int n = 0;
        for (byte b : alpha) if ((b & 0xFF) >= 240) n++;
        return n / (float) alpha.length;
    }

    /**
     * Labels the 4-connected regions where {@code on[i] == want}. Fills [label] with 1-based labels
     * (0 for the other value) and returns the size of each region (index = label - 1) plus, in
     * [touches], whether the region reaches the image border.
     */
    static int[] regions(boolean[] on, boolean want, int w, int h, int[] label, boolean[][] touchesOut) {
        int n = w * h;
        int[] q = new int[n];
        int[] sizes = new int[16];
        boolean[] touches = new boolean[16];
        int count = 0;
        java.util.Arrays.fill(label, 0);
        for (int start = 0; start < n; start++) {
            if (on[start] != want || label[start] != 0) continue;
            if (count == sizes.length) {
                sizes = java.util.Arrays.copyOf(sizes, count * 2);
                touches = java.util.Arrays.copyOf(touches, count * 2);
            }
            int id = ++count;
            int head = 0, tail = 0, size = 0;
            boolean edge = false;
            label[start] = id;
            q[tail++] = start;
            while (head < tail) {
                int i = q[head++];
                size++;
                int x = i % w, y = i / w;
                if (x == 0 || y == 0 || x == w - 1 || y == h - 1) edge = true;
                if (x > 0 && on[i - 1] == want && label[i - 1] == 0) { label[i - 1] = id; q[tail++] = i - 1; }
                if (x < w - 1 && on[i + 1] == want && label[i + 1] == 0) { label[i + 1] = id; q[tail++] = i + 1; }
                if (y > 0 && on[i - w] == want && label[i - w] == 0) { label[i - w] = id; q[tail++] = i - w; }
                if (y < h - 1 && on[i + w] == want && label[i + w] == 0) { label[i + w] = id; q[tail++] = i + w; }
            }
            sizes[id - 1] = size;
            touches[id - 1] = edge;
        }
        if (touchesOut != null) touchesOut[0] = java.util.Arrays.copyOf(touches, count);
        return java.util.Arrays.copyOf(sizes, count);
    }

    /**
     * Turns off the kept regions smaller than [minArea] pixels and those smaller than
     * [fracOfLargest] of the biggest region (0 to skip that test). Returns how many pixels changed.
     */
    static int dropSpecks(boolean[] on, int w, int h, int minArea, float fracOfLargest) {
        int[] label = new int[w * h];
        int[] sizes = regions(on, true, w, h, label, null);
        if (sizes.length == 0) return 0;
        int largest = 0;
        for (int s : sizes) largest = Math.max(largest, s);
        int limit = Math.max(minArea, Math.round(largest * fracOfLargest));
        int changed = 0;
        for (int i = 0; i < on.length; i++) {
            if (on[i] && label[i] > 0 && sizes[label[i] - 1] < limit && sizes[label[i] - 1] != largest) {
                on[i] = false;
                changed++;
            }
        }
        return changed;
    }

    /** Turns on the holes (regions that do not reach the border) of at most [maxArea] pixels. */
    static int fillHoles(boolean[] on, int w, int h, int maxArea) {
        int[] label = new int[w * h];
        boolean[][] touches = new boolean[1][];
        int[] sizes = regions(on, false, w, h, label, touches);
        int changed = 0;
        for (int i = 0; i < on.length; i++) {
            int l = label[i];
            if (!on[i] && l > 0 && !touches[0][l - 1] && sizes[l - 1] <= maxArea) {
                on[i] = true;
                changed++;
            }
        }
        return changed;
    }

    /**
     * Mixes each frame's confidence with its neighbours (weights 1-2-1), so a cutout made frame by
     * frame does not flicker. [loop] wraps around, since stickers loop.
     */
    static byte[][] smoothInTime(byte[][] conf, boolean loop) {
        int n = conf.length;
        byte[][] out = new byte[n][];
        for (int t = 0; t < n; t++) {
            byte[] a = conf[prev(t, n, loop)], b = conf[t], c = conf[next(t, n, loop)];
            byte[] o = new byte[b.length];
            for (int i = 0; i < b.length; i++) {
                o[i] = (byte) (((a[i] & 0xFF) + 2 * (b[i] & 0xFF) + (c[i] & 0xFF) + 2) / 4);
            }
            out[t] = o;
        }
        return out;
    }

    private static int prev(int t, int n, boolean loop) {
        return t > 0 ? t - 1 : (loop ? n - 1 : 0);
    }

    private static int next(int t, int n, boolean loop) {
        return t < n - 1 ? t + 1 : (loop ? 0 : n - 1);
    }

    /**
     * Confidence (0..255) to final alpha: a soft step between [lo] and [hi], then the specks and
     * small holes of the solid part are cleaned up.
     */
    static byte[] confidenceToAlpha(byte[] conf, int w, int h, int lo, int hi, int minArea, float fracOfLargest, int maxHole) {
        int n = w * h;
        boolean[] on = new boolean[n];
        for (int i = 0; i < n; i++) on[i] = (conf[i] & 0xFF) >= (lo + hi) / 2;
        dropSpecks(on, w, h, minArea, fracOfLargest);
        fillHoles(on, w, h, maxHole);
        byte[] out = new byte[n];
        for (int i = 0; i < n; i++) {
            int c = conf[i] & 0xFF;
            int a;
            if (!on[i]) {
                // outside the kept shape only the soft edge right next to it survives
                a = nearOn(on, i, w, h) ? step(c, lo, hi) / 2 : 0;
            } else {
                a = Math.max(step(c, lo, hi), nearOff(on, i, w, h) ? 0 : 255);
            }
            out[i] = (byte) a;
        }
        return out;
    }

    private static int step(int c, int lo, int hi) {
        if (c <= lo) return 0;
        if (c >= hi) return 255;
        float t = (c - lo) / (float) (hi - lo);
        return Math.round(255 * t * t * (3 - 2 * t));
    }

    private static boolean nearOn(boolean[] on, int i, int w, int h) {
        int x = i % w, y = i / w;
        return (x > 0 && on[i - 1]) || (x < w - 1 && on[i + 1]) || (y > 0 && on[i - w]) || (y < h - 1 && on[i + w]);
    }

    private static boolean nearOff(boolean[] on, int i, int w, int h) {
        int x = i % w, y = i / w;
        return (x > 0 && !on[i - 1]) || (x < w - 1 && !on[i + 1]) || (y > 0 && !on[i - w]) || (y < h - 1 && !on[i + w]);
    }

    /** Makes the pixels outside the kept shape fully transparent. */
    static void clearOutside(int[] argb, boolean[] keep) {
        for (int i = 0; i < argb.length; i++) {
            if (!keep[i]) argb[i] = 0;
        }
    }

    /**
     * Multiplies the alpha of [argb] ([w] x [h], not premultiplied) by [mask] ([mw] x [mh], 0..255),
     * scaled to the image with bilinear filtering. Pixels that end up fully transparent become 0.
     */
    static void applyAlpha(int[] argb, int w, int h, byte[] mask, int mw, int mh) {
        int[] x0 = new int[w], x1 = new int[w];
        float[] fx = new float[w];
        for (int x = 0; x < w; x++) {
            float sx = Math.max(0f, Math.min(mw - 1f, (x + 0.5f) * mw / w - 0.5f));
            x0[x] = (int) sx;
            x1[x] = Math.min(x0[x] + 1, mw - 1);
            fx[x] = sx - x0[x];
        }
        for (int y = 0; y < h; y++) {
            float sy = Math.max(0f, Math.min(mh - 1f, (y + 0.5f) * mh / h - 0.5f));
            int y0 = (int) sy;
            int r0 = y0 * mw, r1 = Math.min(y0 + 1, mh - 1) * mw;
            float fy = sy - y0;
            for (int x = 0; x < w; x++) {
                float top = (mask[r0 + x0[x]] & 0xFF) * (1f - fx[x]) + (mask[r0 + x1[x]] & 0xFF) * fx[x];
                float bottom = (mask[r1 + x0[x]] & 0xFF) * (1f - fx[x]) + (mask[r1 + x1[x]] & 0xFF) * fx[x];
                int m = Math.round(top * (1f - fy) + bottom * fy);
                int i = y * w + x;
                int c = argb[i];
                int a = ((c >>> 24) * m + 127) / 255;
                argb[i] = a == 0 ? 0 : (a << 24) | (c & 0xFFFFFF);
            }
        }
    }
}
package dev.tvnclr.slap.engine;

import android.graphics.Bitmap;

/**
 * Tira fundo liso: descobre a cor do fundo pelas bordas e deixa transparente tudo o que tem essa cor
 * e encosta na borda (ou, com "em toda a imagem", tudo o que tem essa cor). Suaviza o contorno.
 */
final class BgRemover {

    /** Tolerancia padrao (diferenca maxima por canal, 0 a 255). */
    static final int DEFAULT_TOL = 40;

    private BgRemover() {}

    /** Devolve um bitmap novo com o fundo transparente; se a borda ja for transparente, devolve uma copia. */
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
        if (ref == 0) ref = borderColor(px, w, h);
        if (ref != 0) {
            byte[] gone = new byte[w * h]; // 1 = fundo
            if (everywhere) {
                for (int i = 0; i < px.length; i++) if (isBg(px[i], ref, tol)) gone[i] = 1;
            } else {
                flood(px, gone, w, h, ref, tol);
            }
            cleanup(gone, w, h);
            feather(px, gone, w, h, ref, tol);
            for (int i = 0; i < px.length; i++) if (gone[i] == 1) px[i] = 0;
        }
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        out.setPixels(px, 0, w, 0, 0, w, h);
        return out;
    }

    /** Removes the loose specks the color match leaves behind and closes pinholes in the subject. */
    private static void cleanup(byte[] gone, int w, int h) {
        int n = w * h;
        boolean[] keep = new boolean[n];
        for (int i = 0; i < n; i++) keep[i] = gone[i] == 0;
        int small = Math.max(16, Math.round(n * 0.0008f));
        MaskOps.dropSpecks(keep, w, h, small, 0f);
        MaskOps.fillHoles(keep, w, h, small);
        for (int i = 0; i < n; i++) gone[i] = (byte) (keep[i] ? 0 : 1);
    }

    private static int dist(int a, int b) {
        int dr = Math.abs(((a >> 16) & 0xFF) - ((b >> 16) & 0xFF));
        int dg = Math.abs(((a >> 8) & 0xFF) - ((b >> 8) & 0xFF));
        int db = Math.abs((a & 0xFF) - (b & 0xFF));
        return Math.max(dr, Math.max(dg, db));
    }

    private static boolean isBg(int c, int ref, int tol) {
        return (c >>> 24) < 16 || dist(c, ref) <= tol;
    }

    /** Cor mais comum entre os pixels opacos da borda (agrupada em 32 niveis por canal), ou 0 se nao houver. */
    private static int borderColor(int[] px, int w, int h) {
        int[] count = new int[32 * 32 * 32];
        long[] sr = new long[count.length], sg = new long[count.length], sb = new long[count.length];
        int best = -1;
        for (int i = 0; i < 2 * (w + h); i++) {
            int x, y;
            if (i < w) { x = i; y = 0; }
            else if (i < 2 * w) { x = i - w; y = h - 1; }
            else if (i < 2 * w + h) { x = 0; y = i - 2 * w; }
            else { x = w - 1; y = i - 2 * w - h; }
            int c = px[y * w + x];
            if ((c >>> 24) < 128) continue;
            int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
            int k = (r >> 3) << 10 | (g >> 3) << 5 | (b >> 3);
            count[k]++;
            sr[k] += r;
            sg[k] += g;
            sb[k] += b;
            if (best < 0 || count[k] > count[best]) best = k;
        }
        if (best < 0) return 0;
        int n = count[best];
        return 0xFF000000 | (int) (sr[best] / n) << 16 | (int) (sg[best] / n) << 8 | (int) (sb[best] / n);
    }

    /** Preenche a partir de todos os pixels da borda que parecem fundo (vizinhanca de 4). */
    private static void flood(int[] px, byte[] gone, int w, int h, int ref, int tol) {
        int[] q = new int[w * h];
        int head = 0, tail = 0;
        for (int x = 0; x < w; x++) {
            tail = seed(px, gone, q, tail, x, ref, tol);
            tail = seed(px, gone, q, tail, (h - 1) * w + x, ref, tol);
        }
        for (int y = 0; y < h; y++) {
            tail = seed(px, gone, q, tail, y * w, ref, tol);
            tail = seed(px, gone, q, tail, y * w + w - 1, ref, tol);
        }
        while (head < tail) {
            int i = q[head++];
            int x = i % w;
            if (x > 0) tail = seed(px, gone, q, tail, i - 1, ref, tol);
            if (x < w - 1) tail = seed(px, gone, q, tail, i + 1, ref, tol);
            if (i >= w) tail = seed(px, gone, q, tail, i - w, ref, tol);
            if (i < w * (h - 1)) tail = seed(px, gone, q, tail, i + w, ref, tol);
        }
    }

    private static int seed(int[] px, byte[] gone, int[] q, int tail, int i, int ref, int tol) {
        if (gone[i] == 0 && isBg(px[i], ref, tol)) {
            gone[i] = 1;
            q[tail++] = i;
        }
        return tail;
    }

    /** Pixels de contorno parecidos com o fundo ficam semitransparentes, para nao sobrar serrilhado. */
    private static void feather(int[] px, byte[] gone, int w, int h, int ref, int tol) {
        int soft = Math.max(8, tol);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                if (gone[i] != 0) continue;
                boolean edge = (x > 0 && gone[i - 1] == 1) || (x < w - 1 && gone[i + 1] == 1)
                        || (y > 0 && gone[i - w] == 1) || (y < h - 1 && gone[i + w] == 1);
                if (!edge) continue;
                int d = dist(px[i], ref) - tol;
                if (d >= soft) continue;
                int a = (px[i] >>> 24) * Math.max(0, d) / soft;
                px[i] = (a << 24) | (px[i] & 0xFFFFFF);
            }
        }
    }
}

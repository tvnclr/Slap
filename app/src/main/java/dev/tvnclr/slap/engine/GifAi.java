package dev.tvnclr.slap.engine;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;


import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * AI cutout for every frame of a GIF. The model only works on single images, so each frame is cut
 * on its own and the masks are then mixed with their neighbours and cleaned up, which removes the
 * flicker and the loose specks a frame-by-frame cutout has. Computed once and reused by the
 * preview and by the final sticker.
 */
public final class GifAi {

    /** One alpha mask per GIF frame, all of the same (small) size. */
    public static final class Masks {
        final int w, h;
        final byte[][] alpha;

        Masks(int w, int h, byte[][] alpha) {
            this.w = w;
            this.h = h;
            this.alpha = alpha;
        }

        public int count() {
            return alpha.length;
        }
    }

    public interface Progress {
        void on(int frame, int total);
    }

    /** Frames are scaled down to this side for the model; the mask is scaled back up. */
    private static final int MODEL_SIDE = 384;
    private static final int MAX_FRAMES = 300;

    private GifAi() {}

    /**
     * Blocking: runs the engine picked in the settings on every frame. Call off the main thread,
     * after a single-image cutout got the engine ready (model download, errors).
     */
    public static Masks compute(Context c, byte[] gif, int frames, Progress pr) throws Exception {
        Segmenter seg = Segmenters.create(c);
        CountDownLatch ready = new CountDownLatch(1);
        String[] prepError = new String[1];
        seg.prepare(c, msg -> {}, e -> {
            prepError[0] = e;
            ready.countDown();
        });
        if (!ready.await(60, TimeUnit.SECONDS)) {
            seg.close();
            throw new IllegalStateException("AI engine not ready");
        }
        if (prepError[0] != null) {
            seg.close();
            throw new IllegalStateException(prepError[0]);
        }
        List<byte[]> conf = new ArrayList<>();
        int[] dims = new int[2];
        Exception[] err = new Exception[1];
        try {
            GifDecoder.decode(gif, (argb, w, h, delay, idx) -> {
                if (pr != null) pr.on(idx + 1, Math.min(frames, MAX_FRAMES));
                Bitmap full = Bitmap.createBitmap(argb, w, h, Bitmap.Config.ARGB_8888);
                try {
                    Segmenter.Mask m = seg.segment(full, MODEL_SIDE);
                    if (!conf.isEmpty() && (m.w != dims[0] || m.h != dims[1])) return false;
                    conf.add(m.conf);
                    dims[0] = m.w;
                    dims[1] = m.h;
                } catch (Exception e) {
                    err[0] = e;
                    return false;
                } finally {
                    full.recycle();
                }
                return conf.size() < MAX_FRAMES;
            });
        } finally {
            seg.close();
        }
        if (err[0] != null) throw err[0];
        if (conf.isEmpty()) throw new IllegalStateException("no frames");
        int w = dims[0], h = dims[1];
        byte[][] smooth = MaskOps.smoothInTime(conf.toArray(new byte[0][]), true);
        byte[][] alpha = new byte[smooth.length][];
        for (int t = 0; t < smooth.length; t++) alpha[t] = cleanAlpha(smooth[t], w, h);
        return new Masks(w, h, alpha);
    }

    /** Model confidence to the final alpha, the same cleanup for photos and GIF frames. */
    static byte[] cleanAlpha(byte[] conf, int w, int h) {
        int n = w * h;
        int minArea = Math.max(16, Math.round(n * 0.002f));
        int maxHole = Math.max(16, Math.round(n * 0.01f));
        return MaskOps.confidenceToAlpha(conf, w, h, 90, 170, minArea, 0.12f, maxHole);
    }

    /** Model confidence buffer as bytes 0..255. */
    static byte[] toBytes(FloatBuffer fb, int n) {
        byte[] m = new byte[n];
        if (fb == null) return m;
        fb.rewind();
        for (int i = 0; i < n && fb.hasRemaining(); i++) {
            float v = fb.get();
            m[i] = (byte) Math.round(Math.max(0f, Math.min(1f, v)) * 255f);
        }
        return m;
    }

    /**
     * Frame [idx] with its mask applied (a new bitmap unless [frame] is mutable ARGB, which is
     * reused; [frame] is recycled when a new one was made). Done on the pixels: drawing an ALPHA_8
     * mask with DST_IN left the frames untouched on the devices tested.
     */
    public static Bitmap apply(Bitmap frame, Masks m, int idx) {
        if (m == null || m.alpha.length == 0) return frame;
        int w = frame.getWidth(), h = frame.getHeight();
        int[] px = new int[w * h];
        frame.getPixels(px, 0, w, 0, 0, w, h);
        MaskOps.applyAlpha(px, w, h, m.alpha[Math.min(idx, m.alpha.length - 1)], m.w, m.h);
        Bitmap out = frame.isMutable() && frame.getConfig() == Bitmap.Config.ARGB_8888 ? frame : Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        out.setHasAlpha(true);
        out.setPixels(px, 0, w, 0, 0, w, h);
        if (out != frame) frame.recycle();
        return out;
    }
}

package dev.tvnclr.slap.engine;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;

import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation;
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentationResult;
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenter;
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

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
     * Blocking: runs the model on every frame. Call off the main thread, after a single-image
     * cutout made sure the model is installed.
     */
    public static Masks compute(byte[] gif, int frames, Progress pr) throws Exception {
        SubjectSegmenter seg = SubjectSegmentation.getClient(
                new SubjectSegmenterOptions.Builder().enableForegroundConfidenceMask().build());
        List<byte[]> conf = new ArrayList<>();
        int[] dims = new int[2];
        Exception[] err = new Exception[1];
        try {
            GifDecoder.decode(gif, (argb, w, h, delay, idx) -> {
                if (pr != null) pr.on(idx + 1, Math.min(frames, MAX_FRAMES));
                float k = Math.min(1f, MODEL_SIDE / (float) Math.max(w, h));
                int mw = Math.max(1, Math.round(w * k)), mh = Math.max(1, Math.round(h * k));
                if (!conf.isEmpty() && (mw != dims[0] || mh != dims[1])) return false;
                Bitmap full = Bitmap.createBitmap(argb, w, h, Bitmap.Config.ARGB_8888);
                Bitmap small = k < 1f ? Bitmap.createScaledBitmap(full, mw, mh, true) : full;
                try {
                    SubjectSegmentationResult r = Tasks.await(seg.process(InputImage.fromBitmap(small, 0)));
                    FloatBuffer fb = r.getForegroundConfidenceMask();
                    byte[] m = new byte[mw * mh];
                    if (fb != null) {
                        fb.rewind();
                        for (int i = 0; i < m.length && fb.hasRemaining(); i++) {
                            float v = fb.get();
                            m[i] = (byte) Math.round(Math.max(0f, Math.min(1f, v)) * 255f);
                        }
                    }
                    conf.add(m);
                    dims[0] = mw;
                    dims[1] = mh;
                } catch (Exception e) {
                    err[0] = e;
                    return false;
                } finally {
                    if (small != full) small.recycle();
                    full.recycle();
                }
                return conf.size() < MAX_FRAMES;
            });
        } finally {
            try {
                seg.close();
            } catch (Exception ignored) {
            }
        }
        if (err[0] != null) throw err[0];
        if (conf.isEmpty()) throw new IllegalStateException("no frames");
        int w = dims[0], h = dims[1], n = w * h;
        byte[][] smooth = MaskOps.smoothInTime(conf.toArray(new byte[0][]), true);
        byte[][] alpha = new byte[smooth.length][];
        int minArea = Math.max(16, Math.round(n * 0.002f));
        int maxHole = Math.max(16, Math.round(n * 0.01f));
        for (int t = 0; t < smooth.length; t++) {
            alpha[t] = MaskOps.confidenceToAlpha(smooth[t], w, h, 90, 170, minArea, 0.12f, maxHole);
        }
        return new Masks(w, h, alpha);
    }

    /** Frame [idx] with its mask applied (a new bitmap; [frame] is recycled when a copy was needed). */
    public static Bitmap apply(Bitmap frame, Masks m, int idx) {
        if (m == null || m.alpha.length == 0) return frame;
        Bitmap mask = alphaBitmap(m.alpha[Math.min(idx, m.alpha.length - 1)], m.w, m.h);
        Bitmap out = frame.isMutable() && frame.getConfig() == Bitmap.Config.ARGB_8888 ? frame : frame.copy(Bitmap.Config.ARGB_8888, true);
        Canvas c = new Canvas(out);
        Paint p = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
        p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        c.drawBitmap(mask, null, new Rect(0, 0, out.getWidth(), out.getHeight()), p);
        mask.recycle();
        if (out != frame) frame.recycle();
        return out;
    }

    private static Bitmap alphaBitmap(byte[] a, int w, int h) {
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ALPHA_8);
        int stride = b.getRowBytes();
        ByteBuffer buf = ByteBuffer.allocate(stride * h);
        for (int y = 0; y < h; y++) {
            buf.position(y * stride);
            buf.put(a, y * w, w);
        }
        buf.rewind();
        b.copyPixelsFromBuffer(buf);
        return b;
    }
}

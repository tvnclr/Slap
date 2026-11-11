package dev.tvnclr.slap.engine;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import dev.tvnclr.slap.R;

/**
 * AI cutout of one image with the engine picked in the settings ({@link Segmenters}): the model's
 * confidence mask goes through the same cleanup as GIF frames (specks dropped, pinholes closed)
 * and is scaled back onto the photo. Callbacks arrive on the main thread.
 */
final class AiCutout {

    /** Largest side given to engines that take any size (ML Kit). */
    private static final int MODEL_SIDE = 1024;
    private static final Executor WORK = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    interface Callback {
        /** The image with a transparent background, or null and an error message. */
        void done(Bitmap fg, String error);
    }

    private AiCutout() {}

    static void cut(Context c, Bitmap src, StickerEncoder.Progress progress, Callback cb) {
        Segmenter seg = Segmenters.create(c);
        seg.prepare(c, progress, err -> {
            if (err != null) {
                seg.close();
                cb.done(null, err);
                return;
            }
            WORK.execute(() -> run(c, seg, src, cb));
        });
    }

    private static void run(Context c, Segmenter seg, Bitmap src, Callback cb) {
        Bitmap fg = null;
        String msg = null;
        try {
            Segmenter.Mask m = seg.segment(src, MODEL_SIDE);
            byte[] alpha = GifAi.cleanAlpha(m.conf, m.w, m.h);
            boolean any = false;
            for (byte b : alpha) if (b != 0) { any = true; break; }
            // a drawing on a plain background can come back as one big "subject": nothing removed
            boolean whole = MaskOps.opaqueFraction(alpha) > 0.97f;
            if (any && !whole) {
                Bitmap copy = src.copy(Bitmap.Config.ARGB_8888, true);
                fg = GifAi.apply(copy, new GifAi.Masks(m.w, m.h, new byte[][] {alpha}), 0);
            } else {
                msg = c.getString(whole ? R.string.ai_whole_image : R.string.ai_nothing_found);
            }
        } catch (Exception e) {
            msg = c.getString(R.string.ai_failed, String.valueOf(e.getMessage()));
        } finally {
            seg.close();
        }
        Bitmap result = fg;
        String error = msg;
        MAIN.post(() -> cb.done(result, error));
    }
}

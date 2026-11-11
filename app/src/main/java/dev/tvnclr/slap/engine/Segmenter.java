package dev.tvnclr.slap.engine;

import android.content.Context;
import android.graphics.Bitmap;

/**
 * An on-device "subject vs background" model. Engines: U^2-Net through LiteRT (free, bundled, in
 * every build) and Google ML Kit (only where Google Play services may be used; the F-Droid build
 * leaves its class out, see {@link Segmenters}).
 */
public interface Segmenter {

    /** Confidence that each pixel belongs to the subject, 0..255, row by row, [w] x [h]. */
    final class Mask {
        public final int w, h;
        public final byte[] conf;

        public Mask(int w, int h, byte[] conf) {
            this.w = w;
            this.h = h;
            this.conf = conf;
        }
    }

    interface Ready {
        /** null when the engine can run, otherwise a message for the user. Called on the main thread. */
        void ready(String error);
    }

    /** Stable id stored in the settings ("u2net", "mlkit"). */
    String id();

    /**
     * Gets the engine ready (ML Kit may download its model from Google Play first) and reports
     * progress text through [progress]. [done] is called on the main thread.
     */
    void prepare(Context c, StickerEncoder.Progress progress, Ready done);

    /**
     * Blocking: runs the model on [src]. [maxSide] caps the size given to engines that take any
     * size; fixed-size engines ignore it. The mask covers the whole image and is scaled onto it
     * by the caller. Call off the main thread, after {@link #prepare}.
     */
    Mask segment(Bitmap src, int maxSide) throws Exception;

    void close();
}

package dev.tvnclr.slap.engine;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;

import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

/**
 * U^2-Net (u2netp, Xuebin Qin et al., Apache-2.0) through LiteRT. Bundled in the APK, works
 * offline and without Google Play services. The model file comes from tools/convert-u2netp.sh.
 */
final class U2NetSegmenter implements Segmenter {

    static final String ID = "u2net";
    private static final String MODEL = "models/u2netp.tflite";
    /** The network takes a 320 x 320 RGB image; the photo is stretched to it and back. */
    static final int SIDE = 320;

    private Interpreter interpreter;
    private ByteBuffer input, output;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void prepare(Context c, StickerEncoder.Progress progress, Ready done) {
        String error = null;
        try {
            open(c);
        } catch (Exception | UnsatisfiedLinkError e) {
            error = c.getString(dev.tvnclr.slap.R.string.ai_failed, String.valueOf(e.getMessage()));
        }
        String err = error;
        new Handler(Looper.getMainLooper()).post(() -> done.ready(err));
    }

    private synchronized void open(Context c) throws Exception {
        if (interpreter != null) return;
        try (AssetFileDescriptor fd = c.getAssets().openFd(MODEL);
             FileInputStream in = new FileInputStream(fd.getFileDescriptor());
             FileChannel ch = in.getChannel()) {
            MappedByteBuffer model = ch.map(FileChannel.MapMode.READ_ONLY, fd.getStartOffset(), fd.getDeclaredLength());
            Interpreter.Options o = new Interpreter.Options();
            o.setNumThreads(Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors())));
            interpreter = new Interpreter(model, o);
        }
        input = ByteBuffer.allocateDirect(SIDE * SIDE * 3 * 4).order(ByteOrder.nativeOrder());
        output = ByteBuffer.allocateDirect(SIDE * SIDE * 4).order(ByteOrder.nativeOrder());
    }

    @Override
    public synchronized Mask segment(Bitmap src, int maxSide) throws Exception {
        if (interpreter == null) throw new IllegalStateException("U2Net not prepared");
        Bitmap in = src.getConfig() == Bitmap.Config.ARGB_8888 ? src : src.copy(Bitmap.Config.ARGB_8888, false);
        Bitmap s = Bitmap.createScaledBitmap(in, SIDE, SIDE, true);
        int[] px = new int[SIDE * SIDE];
        s.getPixels(px, 0, SIDE, 0, 0, SIDE, SIDE);
        if (s != in) s.recycle();
        if (in != src) in.recycle();
        float[] x = U2NetMath.normalize(px);
        input.rewind();
        input.asFloatBuffer().put(x);
        output.rewind();
        interpreter.run(input, output);
        output.rewind();
        float[] y = new float[SIDE * SIDE];
        output.asFloatBuffer().get(y);
        return new Mask(SIDE, SIDE, U2NetMath.minMaxToBytes(y));
    }

    @Override
    public synchronized void close() {
        if (interpreter != null) interpreter.close();
        interpreter = null;
    }
}

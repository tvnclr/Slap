package dev.tvnclr.slap.engine;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Test bench, not part of the user flow: not exported and not linked from any screen, so only
 * adb with root can start it ({@code am start -n dev.tvnclr.slap/.engine.BenchActivity}). Runs
 * every file in {@code files/bench/in} through each background method and writes the results,
 * timings and the share of transparent pixels to {@code files/bench/out}.
 */
public final class BenchActivity extends Activity {

    private static final String TAG = "Slap/bench";
    private TextView view;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        view = new TextView(this);
        view.setPadding(48, 160, 48, 48);
        view.setText("bench running");
        setContentView(view);
        Context app = getApplicationContext();
        new Thread(() -> {
            String msg;
            try {
                msg = run(app);
            } catch (Throwable t) {
                Log.e(TAG, "bench failed", t);
                msg = "bench failed: " + t;
            }
            String m = msg;
            runOnUiThread(() -> view.setText(m));
        }, "bench").start();
    }


    /**
     * Self-test of the cutout pieces on synthetic images, repeated so the runtime compiles the
     * code mid-way (a compiled-code-only failure showed up on an arm64 phone). Logged and written
     * to diag.txt; every line ends in OK or FAIL.
     */
    static String diag(Context c) throws Exception {
        StringBuilder r = new StringBuilder();
        int[] sizes = {64, 720};
        for (int size : sizes) {
            int[] px = new int[size * size];
            int lo = size / 4, hi = size * 3 / 4;
            for (int i = 0; i < px.length; i++) {
                int x = i % size, y = i / size;
                px[i] = (x >= lo && x < hi && y >= lo && y < hi) ? 0xFF0000FF : 0xFFFF0000;
            }
            Bitmap b = Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888);
            for (int round = 0; round < 8; round++) {
                for (boolean all : new boolean[] {false, true}) {
                    Bitmap o = BgRemover.solid(b, 40, all);
                    float clear = StickerMaker.transparentFraction(o);
                    o.recycle();
                    r.append(String.format(Locale.ROOT, "solid %dpx round %d everywhere=%b clear=%.3f %s%n", size, round, all, clear,
                            Math.abs(clear - 0.75f) < 0.01f ? "OK" : "FAIL"));
                }
            }
        }
        int w = 64, h = 64;
        byte[] a = new byte[w * h];
        for (int i = 0; i < a.length; i++) a[i] = (byte) ((i % w) < 32 ? 255 : 0);
        for (int round = 0; round < 8; round++) {
            Bitmap frame = Bitmap.createBitmap(w * 4, h * 4, Bitmap.Config.ARGB_8888);
            frame.eraseColor(0xFF00FF00);
            Bitmap cut = GifAi.apply(frame, new GifAi.Masks(w, h, new byte[][] {a}), 0);
            float clear = StickerMaker.transparentFraction(cut);
            r.append(String.format(Locale.ROOT, "mask apply round %d clear=%.3f %s%n", round, clear, Math.abs(clear - 0.5f) < 0.02f ? "OK" : "FAIL"));
            cut.recycle();
        }
        return r.toString();
    }
    private static String run(Context c) throws Exception {
        File root = new File(c.getFilesDir(), "bench");
        File in = new File(root, "in"), out = new File(root, "out");
        out.mkdirs();
        String d = diag(c);
        for (String line : d.split("\n")) Log.i(TAG, "diag " + line);
        try (FileWriter fw = new FileWriter(new File(root, "diag.txt"))) {
            fw.write(d);
        }
        for (File f : out.listFiles()) f.delete();
        File[] files = in.listFiles();
        if (files == null || files.length == 0) return "no files in " + in;
        Arrays.sort(files);
        List<String> engines = Segmenters.available();
        String picked = Segmenters.selected(c);
        StringBuilder tsv = new StringBuilder("file\tkind\tmethod\tms\tclear\talphaFile\tbytes\tmd5\tnote\n");
        try {
            for (File f : files) {
                Log.i(TAG, "file " + f.getName());
                StickerMaker.Source s;
                try {
                    s = StickerMaker.open(c, Uri.fromFile(f));
                } catch (Exception e) {
                    tsv.append(f.getName()).append("\t?\topen\t0\t0\t-\t0\t-\t").append(e).append('\n');
                    continue;
                }
                String base = f.getName().replaceAll("\\.[^.]+$", "");
                String kind = s.kind.name();
                int w = s.first.getWidth(), h = s.first.getHeight();
                Matrix fit = new Matrix();
                float k = StickerMaker.SIZE / (float) Math.max(w, h);
                fit.setScale(k, k);
                fit.postTranslate((StickerMaker.SIZE - w * k) / 2f, (StickerMaker.SIZE - h * k) / 2f);
                boolean animated = s.kind == StickerMaker.Kind.GIF;

                // the preview step: flat color, both modes
                for (boolean all : new boolean[] {false, true}) {
                    String method = all ? "flat_everywhere" : "flat";
                    long t0 = System.nanoTime();
                    Bitmap r = StickerMaker.removeSolid(s.first, StickerMaker.DEFAULT_TOLERANCE, all);
                    long ms = (System.nanoTime() - t0) / 1_000_000;
                    float clear = StickerMaker.transparentFraction(r);
                    png(r, new File(out, base + "." + method + ".png"));
                    String sticker = animated ? sticker(c, s, fit, w, h, true, all, null, out, base + "." + method)
                            : sticker(c, s, r, fit, w, h, out, base + "." + method);
                    tsv.append(row(f, kind, method, ms, clear, sticker, ""));
                    r.recycle();
                }
                // AI with every engine in this build
                for (String id : engines) {
                    Segmenters.select(c, id);
                    String method = "ai_" + id;
                    long t0 = System.nanoTime();
                    Bitmap[] fg = new Bitmap[1];
                    String[] err = new String[1];
                    CountDownLatch done = new CountDownLatch(1);
                    StickerMaker.cutWithAi(c, s.first, m -> {}, (bmp, e) -> {
                        fg[0] = bmp;
                        err[0] = e;
                        done.countDown();
                    });
                    if (!done.await(120, TimeUnit.SECONDS)) err[0] = "timeout";
                    long ms = (System.nanoTime() - t0) / 1_000_000;
                    if (fg[0] == null) {
                        tsv.append(row(f, kind, method, ms, 0f, "-\t0\t-", "error: " + err[0]));
                        continue;
                    }
                    float clear = StickerMaker.transparentFraction(fg[0]);
                    png(fg[0], new File(out, base + "." + method + ".png"));
                    String sticker;
                    String note = "";
                    if (animated) {
                        long g0 = System.nanoTime();
                        GifAi.Masks masks = StickerMaker.gifAiMasks(c, s, (a, b) -> {});
                        note = "gif masks " + (System.nanoTime() - g0) / 1_000_000 + " ms";
                        sticker = sticker(c, s, fit, w, h, false, false, masks, out, base + "." + method);
                    } else {
                        sticker = sticker(c, s, fg[0], fit, w, h, out, base + "." + method);
                    }
                    tsv.append(row(f, kind, method, ms, clear, sticker, note));
                    fg[0].recycle();
                }
                if (animated) {
                    tsv.append(row(f, kind, "keep", 0, 0f, sticker(c, s, fit, w, h, false, false, null, out, base + ".keep"), ""));
                }
                s.release();
            }
        } finally {
            Segmenters.select(c, picked);
        }
        try (FileWriter fw = new FileWriter(new File(out, "results.tsv"))) {
            fw.write(tsv.toString());
        }
        new File(out, "DONE").createNewFile();
        Log.i(TAG, "done");
        return "bench done: " + out;
    }

    /** Static sticker from an already processed still, like the editor's save. */
    private static String sticker(Context c, StickerMaker.Source s, Bitmap still, Matrix fit, int w, int h, File out, String name) throws Exception {
        StickerMaker.Options o = new StickerMaker.Options();
        o.animated = false;
        byte[] webp = StickerMaker.make(s, still, fit, w, h, o, (a, b, d) -> {});
        return save(webp, new File(out, name + ".webp"));
    }

    /** Animated sticker of a GIF, like the editor's save. */
    private static String sticker(Context c, StickerMaker.Source s, Matrix fit, int w, int h, boolean solid, boolean all, GifAi.Masks ai, File out, String name) throws Exception {
        StickerMaker.Options o = new StickerMaker.Options();
        o.animated = true;
        o.solidBackground = solid;
        o.everywhere = all;
        o.aiMasks = ai;
        o.lengthMs = s.gifTotalMs;
        byte[] webp = StickerMaker.make(s, s.first, fit, w, h, o, (a, b, d) -> {});
        return save(webp, new File(out, name + ".webp"));
    }

    private static String save(byte[] webp, File f) throws Exception {
        try (FileOutputStream fo = new FileOutputStream(f)) {
            fo.write(webp);
        }
        boolean alpha = webp.length > 20 && webp[12] == 'V' && webp[15] == 'X' && (webp[20] & 0x10) != 0;
        if (webp.length > 15 && webp[12] == 'V' && webp[15] == 'L') alpha = true;
        byte[] d = MessageDigest.getInstance("MD5").digest(webp);
        StringBuilder hex = new StringBuilder();
        for (int i = 0; i < 4; i++) hex.append(String.format(Locale.ROOT, "%02x", d[i]));
        return alpha + "\t" + webp.length + "\t" + hex;
    }

    private static void png(Bitmap b, File f) throws Exception {
        try (FileOutputStream fo = new FileOutputStream(f)) {
            b.compress(Bitmap.CompressFormat.PNG, 100, fo);
        }
    }

    private static String row(File f, String kind, String method, long ms, float clear, String sticker, String note) {
        String r = f.getName() + "\t" + kind + "\t" + method + "\t" + ms + "\t" + String.format(Locale.ROOT, "%.1f", clear * 100) + "\t" + sticker + "\t" + note + "\n";
        Log.i(TAG, r.trim());
        return r;
    }
}

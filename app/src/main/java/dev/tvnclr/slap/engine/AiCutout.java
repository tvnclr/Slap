package dev.tvnclr.slap.engine;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import com.google.android.gms.common.moduleinstall.InstallStatusListener;
import com.google.android.gms.common.moduleinstall.ModuleInstall;
import com.google.android.gms.common.moduleinstall.ModuleInstallClient;
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest;
import com.google.android.gms.common.moduleinstall.ModuleInstallStatusUpdate;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation;
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenter;
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions;

/**
 * Recorte com IA (ML Kit Subject Segmentation): separa pessoa/animal/objeto de qualquer fundo.

 * O modelo vem do Google Play na primeira vez. Callbacks chegam na thread principal.
 * The model runs on a copy of at most MODEL_SIDE px; its confidence mask goes through the same
 * cleanup as GIF frames (specks dropped, pinholes closed) and is scaled back onto the photo.
 */
final class AiCutout {

    private static final int MODEL_SIDE = 1024;
    private static final Executor WORK = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    interface Callback {
        /** fg com fundo transparente, ou null e a mensagem de erro. */
        void done(Bitmap fg, String error);
    }

    private AiCutout() {}

    static void cut(Context c, Bitmap src, StickerEncoder.Progress progress, Callback cb) {
        SubjectSegmenter seg = SubjectSegmentation.getClient(
                new SubjectSegmenterOptions.Builder().enableForegroundConfidenceMask().build());
        ModuleInstallClient mic = ModuleInstall.getClient(c);
        mic.areModulesAvailable(seg)
                .addOnSuccessListener(r -> {
                    if (r.areModulesAvailable()) {
                        run(c, seg, src, cb);
                    } else {
                        install(c, mic, seg, src, progress, cb);
                    }
                })
                .addOnFailureListener(e -> fail(seg, cb, c.getString(dev.tvnclr.slap.R.string.ai_play_unavailable, e.getMessage())));
    }

    private static void install(Context c, ModuleInstallClient mic, SubjectSegmenter seg, Bitmap src,
                                StickerEncoder.Progress progress, Callback cb) {
        progress.on(c.getString(dev.tvnclr.slap.R.string.ai_downloading));
        InstallStatusListener l = new InstallStatusListener() {
            @Override
            public void onInstallStatusUpdated(ModuleInstallStatusUpdate u) {
                int s = u.getInstallState();
                if (s == ModuleInstallStatusUpdate.InstallState.STATE_COMPLETED) {
                    mic.unregisterListener(this);
                    run(c, seg, src, cb);
                } else if (s == ModuleInstallStatusUpdate.InstallState.STATE_FAILED
                        || s == ModuleInstallStatusUpdate.InstallState.STATE_CANCELED) {
                    mic.unregisterListener(this);
                    fail(seg, cb, c.getString(dev.tvnclr.slap.R.string.ai_download_failed_code, u.getErrorCode()));
                } else if (u.getProgressInfo() != null && u.getProgressInfo().getTotalBytesToDownload() > 0) {
                    long pct = 100 * u.getProgressInfo().getBytesDownloaded() / u.getProgressInfo().getTotalBytesToDownload();
                    progress.on(c.getString(dev.tvnclr.slap.R.string.ai_downloading_pct, (int) pct));
                }
            }
        };
        ModuleInstallRequest req = ModuleInstallRequest.newBuilder().addApi(seg).setListener(l).build();
        mic.installModules(req)
                .addOnSuccessListener(resp -> {
                    if (resp.areModulesAlreadyInstalled()) {
                        mic.unregisterListener(l);
                        run(c, seg, src, cb);
                    }
                })
                .addOnFailureListener(e -> {
                    mic.unregisterListener(l);
                    fail(seg, cb, c.getString(dev.tvnclr.slap.R.string.ai_download_failed, e.getMessage()));
                });
    }

    private static void run(Context c, SubjectSegmenter seg, Bitmap src, Callback cb) {
        Bitmap in = src.getConfig() == Bitmap.Config.ARGB_8888 ? src : src.copy(Bitmap.Config.ARGB_8888, false);
        float k = Math.min(1f, MODEL_SIDE / (float) Math.max(in.getWidth(), in.getHeight()));
        int mw = Math.max(1, Math.round(in.getWidth() * k)), mh = Math.max(1, Math.round(in.getHeight() * k));
        Bitmap small = k < 1f ? Bitmap.createScaledBitmap(in, mw, mh, true) : in;
        seg.process(InputImage.fromBitmap(small, 0))
                .addOnSuccessListener(WORK, res -> {
                    seg.close();
                    byte[] alpha = GifAi.cleanAlpha(GifAi.toBytes(res.getForegroundConfidenceMask(), mw * mh), mw, mh);
                    boolean any = false;
                    for (byte b : alpha) if (b != 0) { any = true; break; }
                    // a drawing on a plain background can come back as one big "subject": nothing removed
                    boolean whole = MaskOps.opaqueFraction(alpha) > 0.97f;
                    Bitmap fg = any && !whole ? GifAi.apply(in.copy(Bitmap.Config.ARGB_8888, true), new GifAi.Masks(mw, mh, new byte[][] {alpha}), 0) : null;
                    if (small != in) small.recycle();
                    MAIN.post(() -> {
                        if (fg == null) cb.done(null, c.getString(whole ? dev.tvnclr.slap.R.string.ai_whole_image : dev.tvnclr.slap.R.string.ai_nothing_found));
                        else cb.done(fg, null);
                    });
                })
                .addOnFailureListener(e -> fail(seg, cb, c.getString(dev.tvnclr.slap.R.string.ai_failed, e.getMessage())));
    }

    private static void fail(SubjectSegmenter seg, Callback cb, String msg) {
        try {
            seg.close();
        } catch (Exception ignored) {
        }
        cb.done(null, msg);
    }
}

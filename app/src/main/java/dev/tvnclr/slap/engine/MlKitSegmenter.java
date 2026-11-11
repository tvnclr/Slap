package dev.tvnclr.slap.engine;

import android.content.Context;
import android.graphics.Bitmap;

import com.google.android.gms.common.moduleinstall.InstallStatusListener;
import com.google.android.gms.common.moduleinstall.ModuleInstall;
import com.google.android.gms.common.moduleinstall.ModuleInstallClient;
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest;
import com.google.android.gms.common.moduleinstall.ModuleInstallStatusUpdate;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation;
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentationResult;
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenter;
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions;

import java.util.concurrent.TimeUnit;

import dev.tvnclr.slap.R;

/**
 * Google ML Kit Subject Segmentation. Needs Google Play services, which downloads the model on
 * first use. This is the only class that touches Google libraries: the F-Droid branch deletes this
 * file and the play-services dependency, and {@link Segmenters} simply no longer offers it.
 * Instantiated by name, so keep its no-argument constructor (proguard-rules.pro).
 */
public final class MlKitSegmenter implements Segmenter {

    static final String ID = "mlkit";
    /** A frame the model has not answered in this long fails instead of hanging. */
    private static final long TIMEOUT_S = 15;

    private SubjectSegmenter seg;

    public MlKitSegmenter() {}

    @Override
    public String id() {
        return ID;
    }

    private synchronized SubjectSegmenter client() {
        if (seg == null) {
            seg = SubjectSegmentation.getClient(new SubjectSegmenterOptions.Builder().enableForegroundConfidenceMask().build());
        }
        return seg;
    }

    @Override
    public void prepare(Context c, StickerEncoder.Progress progress, Ready done) {
        SubjectSegmenter s = client();
        ModuleInstallClient mic = ModuleInstall.getClient(c);
        mic.areModulesAvailable(s)
                .addOnSuccessListener(r -> {
                    if (r.areModulesAvailable()) done.ready(null);
                    else install(c, mic, s, progress, done);
                })
                .addOnFailureListener(e -> done.ready(c.getString(R.string.ai_play_unavailable, e.getMessage())));
    }

    private static void install(Context c, ModuleInstallClient mic, SubjectSegmenter s, StickerEncoder.Progress progress, Ready done) {
        progress.on(c.getString(R.string.ai_downloading));
        InstallStatusListener l = new InstallStatusListener() {
            @Override
            public void onInstallStatusUpdated(ModuleInstallStatusUpdate u) {
                int st = u.getInstallState();
                if (st == ModuleInstallStatusUpdate.InstallState.STATE_COMPLETED) {
                    mic.unregisterListener(this);
                    done.ready(null);
                } else if (st == ModuleInstallStatusUpdate.InstallState.STATE_FAILED
                        || st == ModuleInstallStatusUpdate.InstallState.STATE_CANCELED) {
                    mic.unregisterListener(this);
                    done.ready(c.getString(R.string.ai_download_failed_code, u.getErrorCode()));
                } else if (u.getProgressInfo() != null && u.getProgressInfo().getTotalBytesToDownload() > 0) {
                    long pct = 100 * u.getProgressInfo().getBytesDownloaded() / u.getProgressInfo().getTotalBytesToDownload();
                    progress.on(c.getString(R.string.ai_downloading_pct, (int) pct));
                }
            }
        };
        ModuleInstallRequest req = ModuleInstallRequest.newBuilder().addApi(s).setListener(l).build();
        mic.installModules(req)
                .addOnSuccessListener(resp -> {
                    if (resp.areModulesAlreadyInstalled()) {
                        mic.unregisterListener(l);
                        done.ready(null);
                    }
                })
                .addOnFailureListener(e -> {
                    mic.unregisterListener(l);
                    done.ready(c.getString(R.string.ai_download_failed, e.getMessage()));
                });
    }

    @Override
    public Mask segment(Bitmap src, int maxSide) throws Exception {
        Bitmap in = src.getConfig() == Bitmap.Config.ARGB_8888 ? src : src.copy(Bitmap.Config.ARGB_8888, false);
        float k = Math.min(1f, maxSide / (float) Math.max(in.getWidth(), in.getHeight()));
        int w = Math.max(1, Math.round(in.getWidth() * k)), h = Math.max(1, Math.round(in.getHeight() * k));
        Bitmap small = k < 1f ? Bitmap.createScaledBitmap(in, w, h, true) : in;
        try {
            SubjectSegmentationResult r = Tasks.await(client().process(InputImage.fromBitmap(small, 0)), TIMEOUT_S, TimeUnit.SECONDS);
            return new Mask(w, h, GifAi.toBytes(r.getForegroundConfidenceMask(), w * h));
        } finally {
            if (small != in) small.recycle();
            if (in != src) in.recycle();
        }
    }

    @Override
    public synchronized void close() {
        if (seg != null) {
            try {
                seg.close();
            } catch (Exception ignored) {
            }
        }
        seg = null;
    }
}

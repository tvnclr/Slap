package dev.tvnclr.slap.engine;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ImageDecoder;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.media.MediaMetadataRetriever;
import android.net.Uri;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Public entry point of the sticker engine for the Kotlin UI: open a file, preview helpers and the
 * final render. Everything heavy here runs on the caller's worker thread.
 */
public final class StickerMaker {

    public static final int SIZE = StickerEncoder.SIZE;
    public static final int MAX_TOTAL_MS = StickerEncoder.MAX_TOTAL_MS;
    public static final int MIN_FRAME_MS = StickerEncoder.MIN_FRAME_MS;
    public static final int DEFAULT_TOLERANCE = BgRemover.DEFAULT_TOL;
    public static final int VIDEO_BOX = 720;
    public static final int MAX_FILE_BYTES = 40 * 1024 * 1024;
    public static final long MAX_PREVIEW_BYTES = 96L * 1024 * 1024;

    public static final int STAGE_STATIC = 1, STAGE_READ_GIF = 2, STAGE_EXTRACT_VIDEO = 3, STAGE_ENCODE = 4;

    public enum Kind {IMAGE, GIF, VIDEO}

    /** Problems the UI explains with its own translated text; the message is technical detail. */
    public static final class Problem extends IOException {
        public enum Code {FILE_TOO_LARGE, GIF_EMPTY, VIDEO_DURATION, VIDEO_FRAMES, NOT_ENOUGH_FRAMES}

        public final Code code;

        public Problem(Code code, String detail) {
            super(detail);
            this.code = code;
        }
    }

    public interface Progress {
        /** One of the STAGE_* values, with frame counters (0 when unknown). Called on the worker thread. */
        void on(int stage, int current, int total);
    }

    public interface Message {
        void on(String msg);
    }

    public interface CutCallback {
        /** Foreground with a transparent background, or null and an error. Called on the main thread. */
        void done(Bitmap fg, String error);
    }

    /** An opened file: its first frame plus what is needed to read the rest later. */
    public static final class Source {
        public final Uri uri;
        public Kind kind;
        public Bitmap first;
        public byte[] gifData;
        public int gifFrames, gifTotalMs;
        public long videoMs;
        private MediaMetadataRetriever mmr;

        Source(Uri uri) {
            this.uri = uri;
        }

        public Bitmap videoFrame(long ms) {
            if (mmr == null) return null;
            Bitmap f = mmr.getScaledFrameAtTime(ms * 1000L, MediaMetadataRetriever.OPTION_CLOSEST, VIDEO_BOX, VIDEO_BOX);
            if (f == null) f = mmr.getScaledFrameAtTime(ms * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, VIDEO_BOX, VIDEO_BOX);
            return f;
        }

        public void release() {
            if (mmr != null) {
                try {
                    mmr.release();
                } catch (Exception ignored) {
                }
                mmr = null;
            }
        }
    }

    /** Settings of one render. */
    public static final class Options {
        public boolean animated;
        public boolean solidBackground;
        public boolean everywhere;
        public int tolerance = DEFAULT_TOLERANCE;
        public float speed = 1f;
        public long startMs;
        public long lengthMs;
        /** AI cutout masks of a GIF (from {@link #gifAiMasks}), or null. */
        public GifAi.Masks aiMasks;
        /** Text boxes drawn on every frame, bottom first. */
        public List<TextLayer> texts = new ArrayList<>();
    }

    public static final class Frames {
        public final List<Bitmap> frames;
        public final int[] delays;

        Frames(List<Bitmap> frames, int[] delays) {
            this.frames = frames;
            this.delays = delays;
        }
    }

    private StickerMaker() {}

    public static Kind detectKind(Context c, Uri uri) {
        String mime = c.getContentResolver().getType(uri);
        if (mime == null) {
            String path = uri.getPath() == null ? "" : uri.getPath().toLowerCase(Locale.ROOT);
            if (path.endsWith(".gif")) mime = "image/gif";
            else if (path.matches(".*\\.(mp4|webm|mkv|3gp|mov|m4v)$")) mime = "video/*";
            else mime = "image/*";
        }
        if (mime.equals("image/gif")) return Kind.GIF;
        if (mime.startsWith("video/")) return Kind.VIDEO;
        try (InputStream in = c.getContentResolver().openInputStream(uri)) {
            byte[] h = new byte[6];
            if (in != null && in.read(h) == 6 && h[0] == 'G' && h[1] == 'I' && h[2] == 'F') return Kind.GIF;
        } catch (IOException ignored) {
        }
        return Kind.IMAGE;
    }

    private static byte[] readAll(Context c, Uri uri) throws IOException {
        try (InputStream in = c.getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IOException("cannot open the file");
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                bo.write(buf, 0, n);
                if (bo.size() > MAX_FILE_BYTES) throw new Problem(Problem.Code.FILE_TOO_LARGE, "over 40 MB");
            }
            return bo.toByteArray();
        }
    }

    public static Source open(Context c, Uri uri) throws IOException {
        return open(c, uri, null);
    }

    /** Opens a file whose kind is already known (the saved source copy of a sticker), or detects it. */
    public static Source open(Context c, Uri uri, Kind known) throws IOException {
        Source s = new Source(uri);
        s.kind = known != null ? known : detectKind(c, uri);
        if (s.kind == Kind.GIF) {
            s.gifData = readAll(c, uri);
            final Bitmap[] first = new Bitmap[1];
            final int[] total = new int[1];
            s.gifFrames = GifDecoder.decode(s.gifData, (argb, w, h, delay, idx) -> {
                if (idx == 0) first[0] = Bitmap.createBitmap(argb, w, h, Bitmap.Config.ARGB_8888);
                total[0] += delay;
                return true;
            });
            if (first[0] == null) throw new Problem(Problem.Code.GIF_EMPTY, "GIF without images");
            s.gifTotalMs = total[0];
            s.first = first[0];
        } else if (s.kind == Kind.VIDEO) {
            s.mmr = new MediaMetadataRetriever();
            s.mmr.setDataSource(c, uri);
            String dur = s.mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            s.videoMs = dur == null ? 0 : Long.parseLong(dur);
            if (s.videoMs <= 0) throw new Problem(Problem.Code.VIDEO_DURATION, "no duration");
            s.first = s.videoFrame(0);
            if (s.first == null) throw new Problem(Problem.Code.VIDEO_FRAMES, "no frames");
        } else {
            ImageDecoder.Source src = ImageDecoder.createSource(c.getContentResolver(), uri);
            s.first = ImageDecoder.decodeBitmap(src, (decoder, ii, x) -> {
                decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                int w = ii.getSize().getWidth(), h = ii.getSize().getHeight();
                int max = Math.max(w, h);
                if (max > 2048) {
                    float k = 2048f / max;
                    decoder.setTargetSize(Math.max(1, Math.round(w * k)), Math.max(1, Math.round(h * k)));
                }
            });
        }
        return s;
    }

    // ---------------------------------------------------------------- background

    public static Bitmap removeSolid(Bitmap src, int tolerance, boolean everywhere) {
        return BgRemover.solid(src, tolerance, everywhere);
    }

    /** Share of [b]'s pixels that are transparent, to tell when a background removal removed nothing. */
    public static float transparentFraction(Bitmap b) {
        int w = b.getWidth(), h = b.getHeight();
        int[] px = new int[w * h];
        b.getPixels(px, 0, w, 0, 0, w, h);
        return MaskOps.transparentFraction(px);
    }

    public static void cutWithAi(Context c, Bitmap src, Message progress, CutCallback cb) {
        AiCutout.cut(c, src, progress::on, cb::done);
    }

    private static Bitmap solidFrame(Bitmap f, int tol, boolean all, int ref) {
        Bitmap out = BgRemover.solid(f, tol, all, ref);
        f.recycle();
        return out;
    }

    /** AI cutout of every GIF frame; blocking, call off the main thread after {@link #cutWithAi} worked once. */
    public static GifAi.Masks gifAiMasks(Context c, Source s, GifAi.Progress pr) throws Exception {
        return GifAi.compute(c, s.gifData, s.gifFrames, pr);
    }

    /** First frame of a GIF with its AI mask, for the still preview. */
    public static Bitmap applyAiMask(Bitmap frame, GifAi.Masks m) {
        return GifAi.apply(frame.copy(Bitmap.Config.ARGB_8888, true), m, 0);
    }

    /**
     * All GIF frames for the looping preview, with the flat-color removal (one background color for
     * the whole GIF, taken from the first frame) or the AI masks applied if asked.
     */
    public static Frames gifPreview(byte[] data, boolean solid, int tol, boolean all, GifAi.Masks ai) {
        List<Bitmap> frames = new ArrayList<>();
        List<Integer> delays = new ArrayList<>();
        final long[] bytes = new long[1];
        final int[] ref = new int[1];
        try {
            GifDecoder.decode(data, (argb, w, h, delay, idx) -> {
                Bitmap f = Bitmap.createBitmap(argb, w, h, Bitmap.Config.ARGB_8888);
                if (solid) {
                    if (idx == 0) ref[0] = BgRemover.referenceColor(f);
                    f = solidFrame(f, tol, all, ref[0]);
                } else if (ai != null) {
                    f = GifAi.apply(f, ai, idx);
                }
                frames.add(f);
                delays.add(delay);
                bytes[0] += (long) w * h * 4;
                return bytes[0] < MAX_PREVIEW_BYTES && frames.size() < 300;
            });
        } catch (Exception e) {
            // keep what could be read
        }
        int[] d = new int[delays.size()];
        for (int i = 0; i < d.length; i++) d[i] = delays.get(i);
        return new Frames(frames, d);
    }

    // ---------------------------------------------------------------- render

    private static Bitmap render(Bitmap src, Matrix base, int prevW, int prevH, List<TextLayer> texts) {
        Bitmap out = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Matrix mm = new Matrix();
        if (src.getWidth() != prevW || src.getHeight() != prevH) {
            mm.setScale((float) prevW / src.getWidth(), (float) prevH / src.getHeight());
        }
        mm.postConcat(base);
        c.drawBitmap(src, mm, new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG));
        if (texts != null) for (TextLayer t : texts) t.draw(c, SIZE);
        return out;
    }

    /**
     * Builds the sticker WebP within WhatsApp's limits.
     *
     * @param still the frame on screen, with the background choice already applied (static stickers)
     * @param base  crop matrix from {@link CropView#outputMatrix(int)} with {@link #SIZE}
     * @param prevW width of the frame the crop matrix refers to
     * @param prevH height of that frame
     */
    public static byte[] make(Source s, Bitmap still, Matrix base, int prevW, int prevH, Options o, Progress pr) throws IOException {
        if (!o.animated) {
            pr.on(STAGE_STATIC, 0, 0);
            Bitmap out = render(still, base, prevW, prevH, o.texts);
            byte[] webp = StickerEncoder.encodeStatic(out);
            out.recycle();
            return webp;
        }
        List<Bitmap> frames = new ArrayList<>();
        List<Integer> durs = new ArrayList<>();
        if (s.kind == Kind.GIF) {
            final int[] total = new int[1];
            final int ref = o.solidBackground ? BgRemover.referenceColor(s.first) : 0;
            GifDecoder.decode(s.gifData, (argb, w, h, delay, idx) -> {
                pr.on(STAGE_READ_GIF, idx + 1, s.gifFrames);
                Bitmap f = Bitmap.createBitmap(argb, w, h, Bitmap.Config.ARGB_8888);
                if (o.solidBackground) f = solidFrame(f, o.tolerance, o.everywhere, ref);
                else if (o.aiMasks != null) f = GifAi.apply(f, o.aiMasks, idx);
                frames.add(render(f, base, prevW, prevH, o.texts));
                f.recycle();
                int d = Math.max(MIN_FRAME_MS, Math.round(delay / o.speed));
                durs.add(d);
                total[0] += d;
                if (frames.size() >= 2 * StickerEncoder.MAX_FRAMES) StickerEncoder.decimate(frames, durs);
                return total[0] < MAX_TOTAL_MS;
            });
        } else {
            long outTotal = Math.round(o.lengthMs / o.speed);
            long srcLen = o.lengthMs;
            if (outTotal > MAX_TOTAL_MS) {
                outTotal = MAX_TOTAL_MS;
                srcLen = Math.round(outTotal * o.speed);
            }
            int n = (int) Math.max(2, Math.min(StickerEncoder.MAX_FRAMES, Math.round(outTotal / 80.0)));
            int frameMs = (int) Math.max(MIN_FRAME_MS, outTotal / n);
            for (int i = 0; i < n; i++) {
                pr.on(STAGE_EXTRACT_VIDEO, i + 1, n);
                long t = o.startMs + Math.round(i * (double) srcLen / n);
                Bitmap f = s.videoFrame(t);
                if (f == null) continue;
                frames.add(render(f, base, prevW, prevH, o.texts));
                f.recycle();
                durs.add(frameMs);
            }
            if (frames.size() < 2) throw new Problem(Problem.Code.NOT_ENOUGH_FRAMES, frames.size() + " frames");
        }
        final int count = frames.size();
        final int[] done = new int[1];
        return StickerEncoder.encodeAnimated(frames, durs, msg -> pr.on(STAGE_ENCODE, Math.min(++done[0], count), count));
    }

    /** Splits typed emojis (space or comma); up to 3; a default when empty. */
    public static String parseEmoji(String in) {
        StringBuilder sb = new StringBuilder();
        int n = 0;
        if (in != null) {
            for (String tok : in.trim().split("[\\s,]+")) {
                if (tok.isEmpty()) continue;
                if (n > 0) sb.append(',');
                sb.append(tok);
                if (++n == 3) break;
            }
        }
        return n == 0 ? "😀" : sb.toString();
    }
}

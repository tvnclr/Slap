package dev.tvnclr.slap.engine;

import android.graphics.Bitmap;
import android.os.Build;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Codifica figurinhas nos limites do WhatsApp (512x512; 100 KB estatica; 500 KB animada; ate 10 s). */
final class StickerEncoder {

    static final int SIZE = 512;
    static final int STATIC_LIMIT = 100 * 1024 - 512;
    static final int ANIMATED_LIMIT = 500 * 1024 - 4 * 1024;
    static final int MAX_TOTAL_MS = 10_000;
    static final int MIN_FRAME_MS = 8;
    static final int MAX_FRAMES = 50;

    interface Progress {
        void on(String msg);
    }

    private StickerEncoder() {}

    @SuppressWarnings("deprecation")
    static byte[] still(Bitmap b, int quality) {
        ByteArrayOutputStream bo = new ByteArrayOutputStream(64 * 1024);
        Bitmap.CompressFormat f = Build.VERSION.SDK_INT >= 30 ? Bitmap.CompressFormat.WEBP_LOSSY : Bitmap.CompressFormat.WEBP;
        b.compress(f, Math.min(quality, 99), bo);
        return bo.toByteArray();
    }

    static byte[] encodeStatic(Bitmap frame) throws IOException {
        int[] qs = {92, 85, 75, 65, 55, 45, 35, 25, 15};
        byte[] best = null;
        for (int q : qs) {
            best = still(frame, q);
            if (best.length <= STATIC_LIMIT) return best;
        }
        throw new IOException("does not fit in 100 KB even at the lowest quality (" + (best.length / 1024) + " KB)");
    }

    /** Junta frames em pares (descarta um, soma a duracao no anterior). */
    static void decimate(List<Bitmap> frames, List<Integer> durs) {
        List<Bitmap> nf = new ArrayList<>();
        List<Integer> nd = new ArrayList<>();
        for (int i = 0; i < frames.size(); i++) {
            if (i % 2 == 0) {
                nf.add(frames.get(i));
                nd.add(durs.get(i));
            } else {
                nd.set(nd.size() - 1, nd.get(nd.size() - 1) + durs.get(i));
                frames.get(i).recycle();
            }
        }
        frames.clear();
        frames.addAll(nf);
        durs.clear();
        durs.addAll(nd);
    }

    /** Corta no limite de 10 s e reduz para no maximo MAX_FRAMES frames. */
    static void fitLimits(List<Bitmap> frames, List<Integer> durs) {
        for (int i = 0; i < durs.size(); i++) durs.set(i, Math.max(MIN_FRAME_MS, durs.get(i)));
        int total = 0;
        int keep = frames.size();
        for (int i = 0; i < frames.size(); i++) {
            int d = durs.get(i);
            if (total + d > MAX_TOTAL_MS) {
                int rem = MAX_TOTAL_MS - total;
                if (rem >= MIN_FRAME_MS) {
                    durs.set(i, rem);
                    keep = i + 1;
                } else {
                    keep = i;
                }
                break;
            }
            total += d;
        }
        keep = Math.max(keep, Math.min(2, frames.size()));
        while (frames.size() > keep) {
            frames.remove(frames.size() - 1).recycle();
            durs.remove(durs.size() - 1);
        }
        // caso extremo: os 2 primeiros frames sozinhos passam de 10 s
        int sum = 0;
        for (int d : durs) sum += d;
        if (sum > MAX_TOTAL_MS) {
            for (int i = 0; i < durs.size(); i++) durs.set(i, Math.max(MIN_FRAME_MS, MAX_TOTAL_MS / durs.size()));
        }
        while (frames.size() > MAX_FRAMES) decimate(frames, durs);
    }

    /** Codifica e monta; reduz qualidade e depois frames ate caber em 500 KB. Recicla os bitmaps. */
    static byte[] encodeAnimated(List<Bitmap> frames, List<Integer> durs, Progress pr) throws IOException {
        if (frames.size() < 2) throw new IOException("an animation needs at least 2 frames");
        fitLimits(frames, durs);
        int[] qs = {80, 68, 56, 45, 35, 26};
        try {
            while (true) {
                int lastSize = 0;
                for (int q : qs) {
                    List<byte[]> stills = new ArrayList<>(frames.size());
                    int sum = 0;
                    for (int i = 0; i < frames.size(); i++) {
                        if (pr != null) pr.on("encode " + (i + 1) + "/" + frames.size());
                        byte[] s = still(frames.get(i), q);
                        stills.add(s);
                        sum += s.length;
                        if (sum > ANIMATED_LIMIT * 1.15) break; // ja estourou, nao adianta continuar
                    }
                    if (stills.size() < frames.size()) {
                        lastSize = sum;
                        continue;
                    }
                    int[] d = new int[durs.size()];
                    for (int i = 0; i < d.length; i++) d[i] = Math.max(MIN_FRAME_MS, durs.get(i));
                    byte[] out = AnimatedWebp.assemble(stills, d, SIZE, SIZE, 0);
                    if (out.length <= ANIMATED_LIMIT) return out;
                    lastSize = out.length;
                }
                if (frames.size() <= 2) {
                    throw new IOException("does not fit in 500 KB (" + (lastSize / 1024) + " KB) even with 2 frames");
                }
                if (pr != null) pr.on("reduce");
                decimate(frames, durs);
            }
        } finally {
            for (Bitmap b : frames) if (!b.isRecycled()) b.recycle();
        }
    }
}

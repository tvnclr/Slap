package dev.tvnclr.slap.engine;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Monta um WebP animado (VP8X + ANIM + ANMF) a partir de frames WebP estaticos,
 * seguindo a especificacao RIFF do WebP: cada ANMF carrega o bitstream de uma
 * imagem estatica (ALPH + VP8, ou VP8L). Java puro, sem APIs do Android.
 */
public final class AnimatedWebp {

    private AnimatedWebp() {}

    /** Partes de um frame extraidas de um WebP estatico. */
    static final class FrameData {
        final List<byte[]> chunks = new ArrayList<>(); // chunks completos (cabecalho + payload + padding)
        boolean hasAlpha;
        int width, height;
    }

    static int le32(byte[] b, int o) {
        return (b[o] & 0xFF) | ((b[o + 1] & 0xFF) << 8) | ((b[o + 2] & 0xFF) << 16) | ((b[o + 3] & 0xFF) << 24);
    }

    static int le24(byte[] b, int o) {
        return (b[o] & 0xFF) | ((b[o + 1] & 0xFF) << 8) | ((b[o + 2] & 0xFF) << 16);
    }

    private static boolean fourcc(byte[] b, int o, String s) {
        return b[o] == s.charAt(0) && b[o + 1] == s.charAt(1) && b[o + 2] == s.charAt(2) && b[o + 3] == s.charAt(3);
    }

    /** Extrai ALPH/VP8/VP8L de um WebP estatico. */
    static FrameData parseStill(byte[] riff) throws IOException {
        if (riff.length < 20 || !fourcc(riff, 0, "RIFF") || !fourcc(riff, 8, "WEBP")) {
            throw new IOException("frame is not WebP");
        }
        FrameData fd = new FrameData();
        int end = Math.min(riff.length, 8 + le32(riff, 4));
        int p = 12;
        boolean gotImage = false;
        while (p + 8 <= end) {
            int size = le32(riff, p + 4);
            if (size < 0 || p + 8 + size > end) throw new IOException("invalid WebP chunk");
            int padded = size + (size & 1);
            if (fourcc(riff, p, "ALPH")) {
                fd.hasAlpha = true;
                fd.chunks.add(copyChunk(riff, p, size));
            } else if (fourcc(riff, p, "VP8 ")) {
                fd.chunks.add(copyChunk(riff, p, size));
                // dimensoes no frame header VP8 (apos 3 bytes de tag + 3 de start code)
                int o = p + 8 + 6;
                if (o + 4 <= end) {
                    fd.width = ((riff[o] & 0xFF) | ((riff[o + 1] & 0xFF) << 8)) & 0x3FFF;
                    fd.height = ((riff[o + 2] & 0xFF) | ((riff[o + 3] & 0xFF) << 8)) & 0x3FFF;
                }
                gotImage = true;
            } else if (fourcc(riff, p, "VP8L")) {
                fd.chunks.add(copyChunk(riff, p, size));
                int o = p + 8 + 1; // assinatura 0x2f
                if (o + 4 <= end) {
                    int bits = le32(riff, o);
                    fd.width = (bits & 0x3FFF) + 1;
                    fd.height = ((bits >>> 14) & 0x3FFF) + 1;
                    if (((bits >>> 28) & 1) != 0) fd.hasAlpha = true;
                }
                gotImage = true;
            } else if (fourcc(riff, p, "ANIM") || fourcc(riff, p, "ANMF")) {
                throw new IOException("frame is already animated");
            }
            // VP8X, ICCP, EXIF, XMP e desconhecidos sao ignorados
            p += 8 + padded;
        }
        if (!gotImage) throw new IOException("frame without VP8/VP8L bitstream");
        return fd;
    }

    private static byte[] copyChunk(byte[] b, int p, int size) {
        int padded = size + (size & 1);
        byte[] c = new byte[8 + padded];
        System.arraycopy(b, p, c, 0, Math.min(8 + padded, b.length - p));
        return c;
    }

    private static void w32(ByteArrayOutputStream o, int v) {
        o.write(v & 0xFF);
        o.write((v >>> 8) & 0xFF);
        o.write((v >>> 16) & 0xFF);
        o.write((v >>> 24) & 0xFF);
    }

    private static void w24(ByteArrayOutputStream o, int v) {
        o.write(v & 0xFF);
        o.write((v >>> 8) & 0xFF);
        o.write((v >>> 16) & 0xFF);
    }

    private static void w4cc(ByteArrayOutputStream o, String s) {
        for (int i = 0; i < 4; i++) o.write(s.charAt(i));
    }

    /**
     * @param stills     frames como arquivos WebP estaticos, todos do tamanho width x height
     * @param durations  duracao de cada frame em ms (minimo 1)
     * @param loopCount  0 = infinito
     */
    public static byte[] assemble(List<byte[]> stills, int[] durations, int width, int height, int loopCount) throws IOException {
        if (stills.isEmpty()) throw new IOException("no frames");
        if (durations.length != stills.size()) throw new IOException("durations do not match frames");
        List<FrameData> frames = new ArrayList<>();
        boolean anyAlpha = false;
        for (byte[] s : stills) {
            FrameData fd = parseStill(s);
            if (fd.width != width || fd.height != height) {
                throw new IOException("frame size " + fd.width + "x" + fd.height + ", expected " + width + "x" + height);
            }
            anyAlpha |= fd.hasAlpha;
            frames.add(fd);
        }

        ByteArrayOutputStream body = new ByteArrayOutputStream();
        // VP8X
        w4cc(body, "VP8X");
        w32(body, 10);
        int flags = 0x02; // animacao
        if (anyAlpha) flags |= 0x10;
        body.write(flags);
        body.write(0);
        body.write(0);
        body.write(0);
        w24(body, width - 1);
        w24(body, height - 1);
        // ANIM
        w4cc(body, "ANIM");
        w32(body, 6);
        w32(body, 0x00000000); // cor de fundo (BGRA) transparente
        body.write(loopCount & 0xFF);
        body.write((loopCount >>> 8) & 0xFF);
        // ANMF
        for (int i = 0; i < frames.size(); i++) {
            FrameData fd = frames.get(i);
            int dataLen = 0;
            for (byte[] c : fd.chunks) dataLen += c.length;
            int size = 16 + dataLen;
            w4cc(body, "ANMF");
            w32(body, size);
            w24(body, 0); // X/2
            w24(body, 0); // Y/2
            w24(body, width - 1);
            w24(body, height - 1);
            int dur = Math.max(1, Math.min(0xFFFFFF, durations[i]));
            w24(body, dur);
            body.write(0x02); // nao mistura com o frame anterior; sem disposal
            for (byte[] c : fd.chunks) body.write(c, 0, c.length);
            if ((size & 1) != 0) body.write(0);
        }

        byte[] b = body.toByteArray();
        ByteArrayOutputStream out = new ByteArrayOutputStream(b.length + 12);
        w4cc(out, "RIFF");
        w32(out, 4 + b.length);
        w4cc(out, "WEBP");
        out.write(b, 0, b.length);
        return out.toByteArray();
    }

    /** Duracao total (ms) e numero de frames de um WebP animado; null se nao for animado. */
    public static int[] info(byte[] riff) {
        if (riff.length < 20 || !fourcc(riff, 0, "RIFF") || !fourcc(riff, 8, "WEBP")) return null;
        int end = Math.min(riff.length, 8 + le32(riff, 4));
        int p = 12, total = 0, n = 0;
        boolean anim = false;
        while (p + 8 <= end) {
            int size = le32(riff, p + 4);
            if (size < 0) return null;
            if (fourcc(riff, p, "ANIM")) anim = true;
            if (fourcc(riff, p, "ANMF") && p + 8 + 15 <= end) {
                total += le24(riff, p + 8 + 12);
                n++;
            }
            p += 8 + size + (size & 1);
        }
        return anim ? new int[]{total, n} : null;
    }
}

package dev.tvnclr.slap.engine;

import java.io.IOException;
import java.util.Arrays;

/**
 * Decodificador de GIF em Java puro (sem APIs do Android), para obter os frames
 * exatos e o tempo de cada um. Entrega cada frame ja composto no tamanho do GIF,
 * em ARGB, por meio de um callback (o buffer e reutilizado entre chamadas).
 */
public final class GifDecoder {

    public interface FrameSink {
        /** @return false para parar a decodificacao */
        boolean onFrame(int[] argb, int width, int height, int delayMs, int index);
    }

    private static final int MAX_STACK = 4096;

    private final byte[] d;
    private int p;

    private int width, height;
    private int[] gct;
    private int bgIndex;

    // estado do Graphic Control Extension pendente
    private int gceDisposal;
    private boolean gceTransparent;
    private int gceTransIndex;
    private int gceDelayCs;

    private GifDecoder(byte[] data) {
        this.d = data;
    }

    /** Le so as dimensoes do GIF. */
    public static int[] readSize(byte[] data) throws IOException {
        if (data.length < 10 || data[0] != 'G' || data[1] != 'I' || data[2] != 'F') {
            throw new IOException("not a GIF");
        }
        int w = (data[6] & 0xFF) | ((data[7] & 0xFF) << 8);
        int h = (data[8] & 0xFF) | ((data[9] & 0xFF) << 8);
        return new int[]{w, h};
    }

    /** Decodifica todos os frames. Retorna quantos frames foram entregues. */
    public static int decode(byte[] data, FrameSink sink) throws IOException {
        return new GifDecoder(data).run(sink);
    }

    private int u8() throws IOException {
        if (p >= d.length) throw new IOException("truncated GIF");
        return d[p++] & 0xFF;
    }

    private int u16() throws IOException {
        int a = u8();
        int b = u8();
        return a | (b << 8);
    }

    private int[] readColorTable(int n) throws IOException {
        int[] t = new int[256];
        for (int i = 0; i < n; i++) {
            int r = u8(), g = u8(), b = u8();
            t[i] = 0xFF000000 | (r << 16) | (g << 8) | b;
        }
        return t;
    }

    private void skipSubBlocks() throws IOException {
        while (true) {
            int n = u8();
            if (n == 0) return;
            p += n;
            if (p > d.length) throw new IOException("truncated GIF");
        }
    }

    private int run(FrameSink sink) throws IOException {
        if (d.length < 13 || d[0] != 'G' || d[1] != 'I' || d[2] != 'F') {
            throw new IOException("not a GIF");
        }
        p = 6;
        width = u16();
        height = u16();
        int packed = u8();
        bgIndex = u8();
        u8(); // aspect
        if (width <= 0 || height <= 0) throw new IOException("GIF with invalid size");
        if ((long) width * height > 16_000_000L) throw new IOException("GIF too large");
        if ((packed & 0x80) != 0) {
            gct = readColorTable(2 << (packed & 7));
        }

        int[] canvas = new int[width * height];      // transparente no inicio
        int[] backup = null;                          // para disposal 3
        int[] pixels = new int[width * height];       // indices do frame atual (reaproveitado)
        int count = 0;

        // disposal pendente do frame anterior
        int prevDisposal = 0;
        int pX = 0, pY = 0, pW = 0, pH = 0;
        boolean resetGce = true;

        while (p < d.length) {
            int block = u8();
            if (block == 0x3B) break; // trailer
            if (block == 0x21) {
                int label = u8();
                if (label == 0xF9) {
                    int size = u8();
                    int start = p;
                    int pk = u8();
                    gceDisposal = (pk >> 2) & 7;
                    gceTransparent = (pk & 1) != 0;
                    gceDelayCs = u16();
                    gceTransIndex = u8();
                    p = start + size;
                    skipSubBlocks();
                    resetGce = false;
                } else {
                    skipSubBlocks();
                }
                continue;
            }
            if (block != 0x2C) {
                // byte inesperado: tenta seguir em frente
                continue;
            }
            if (resetGce) {
                gceDisposal = 0;
                gceTransparent = false;
                gceDelayCs = 0;
            }
            resetGce = true;

            int ix = u16(), iy = u16(), iw = u16(), ih = u16();
            int ipk = u8();
            boolean interlace = (ipk & 0x40) != 0;
            int[] ct = gct;
            if ((ipk & 0x80) != 0) {
                ct = readColorTable(2 << (ipk & 7));
            }
            if (ct == null) ct = new int[256];

            // aplica a disposal do frame anterior
            if (count > 0) {
                if (prevDisposal == 2) {
                    clearRect(canvas, pX, pY, pW, pH);
                } else if (prevDisposal == 3 && backup != null) {
                    System.arraycopy(backup, 0, canvas, 0, canvas.length);
                }
            }
            if (gceDisposal == 3) {
                if (backup == null) backup = new int[canvas.length];
                System.arraycopy(canvas, 0, backup, 0, canvas.length);
            }

            int n = iw * ih;
            if (pixels.length < n) pixels = new int[n];
            decodeLzw(pixels, n);

            drawFrame(canvas, pixels, ct, ix, iy, iw, ih, interlace);

            int delay = gceDelayCs * 10;
            if (delay < 20) delay = 100; // comportamento usual dos navegadores
            if (!sink.onFrame(canvas, width, height, delay, count)) {
                return count + 1;
            }
            count++;

            prevDisposal = gceDisposal;
            pX = ix; pY = iy; pW = iw; pH = ih;
        }
        return count;
    }

    private void clearRect(int[] canvas, int x, int y, int w, int h) {
        int x1 = Math.min(width, x + w), y1 = Math.min(height, y + h);
        for (int yy = Math.max(0, y); yy < y1; yy++) {
            int row = yy * width;
            for (int xx = Math.max(0, x); xx < x1; xx++) canvas[row + xx] = 0;
        }
    }

    private void drawFrame(int[] canvas, int[] px, int[] ct, int ix, int iy, int iw, int ih, boolean interlace) {
        int pass = 1, inc = 8, line = 0;
        for (int i = 0; i < ih; i++) {
            int row = i;
            if (interlace) {
                while (line >= ih && pass < 4) {
                    pass++;
                    switch (pass) {
                        case 2: line = 4; break;
                        case 3: line = 2; inc = 4; break;
                        case 4: line = 1; inc = 2; break;
                        default: break;
                    }
                }
                row = line;
                line += inc;
            }
            int y = iy + row;
            if (y < 0 || y >= height) continue;
            int src = i * iw;
            int dstRow = y * width;
            for (int x = 0; x < iw; x++) {
                int xx = ix + x;
                if (xx < 0 || xx >= width) continue;
                int idx = px[src + x];
                if (gceTransparent && idx == gceTransIndex) continue;
                canvas[dstRow + xx] = ct[idx & 0xFF];
            }
        }
    }

    /** LZW do GIF, no estilo classico (prefix/suffix/pixelStack). */
    private void decodeLzw(int[] out, int npix) throws IOException {
        int dataSize = u8();
        if (dataSize < 1 || dataSize > 11) dataSize = 8;
        int clear = 1 << dataSize;
        int eoi = clear + 1;
        int available = clear + 2;
        int oldCode = -1;
        int codeSize = dataSize + 1;
        int codeMask = (1 << codeSize) - 1;
        short[] prefix = new short[MAX_STACK];
        byte[] suffix = new byte[MAX_STACK];
        byte[] stack = new byte[MAX_STACK + 1];
        for (int c = 0; c < clear; c++) {
            prefix[c] = 0;
            suffix[c] = (byte) c;
        }

        int datum = 0, bits = 0, first = 0, top = 0, pi = 0;
        int blockLeft = 0;
        boolean ended = false;

        while (pi < npix) {
            if (top == 0) {
                // precisa de mais um codigo
                while (bits < codeSize) {
                    if (blockLeft == 0) {
                        if (p >= d.length) { ended = true; break; }
                        blockLeft = d[p++] & 0xFF;
                        if (blockLeft == 0) { ended = true; break; }
                    }
                    if (p >= d.length) { ended = true; break; }
                    datum |= (d[p++] & 0xFF) << bits;
                    bits += 8;
                    blockLeft--;
                }
                if (ended) break;
                int code = datum & codeMask;
                datum >>= codeSize;
                bits -= codeSize;

                if (code > available || code == eoi) break;
                if (code == clear) {
                    codeSize = dataSize + 1;
                    codeMask = (1 << codeSize) - 1;
                    available = clear + 2;
                    oldCode = -1;
                    continue;
                }
                if (oldCode == -1) {
                    stack[top++] = suffix[code];
                    oldCode = code;
                    first = code;
                    continue;
                }
                int inCode = code;
                if (code == available) {
                    stack[top++] = (byte) first;
                    code = oldCode;
                }
                while (code > clear) {
                    if (top >= MAX_STACK) throw new IOException("corrupt LZW data");
                    stack[top++] = suffix[code];
                    code = prefix[code];
                }
                first = suffix[code] & 0xFF;
                if (available >= MAX_STACK) {
                    // tabela cheia: segue sem adicionar (encoders podem nao mandar clear)
                    stack[top++] = (byte) first;
                    oldCode = inCode;
                    continue;
                }
                stack[top++] = (byte) first;
                prefix[available] = (short) oldCode;
                suffix[available] = (byte) first;
                available++;
                if ((available & codeMask) == 0 && available < MAX_STACK) {
                    codeSize++;
                    codeMask += available;
                }
                oldCode = inCode;
            }
            top--;
            out[pi++] = stack[top] & 0xFF;
        }
        // completa com 0 se faltou dado
        if (pi < npix) Arrays.fill(out, pi, npix, gceTransparent ? gceTransIndex : 0);
        // pula o resto dos sub-blocos deste frame
        if (!ended) {
            p += blockLeft;
            if (p > d.length) p = d.length;
            while (p < d.length) {
                int n = d[p++] & 0xFF;
                if (n == 0) break;
                p += n;
            }
        }
    }
}

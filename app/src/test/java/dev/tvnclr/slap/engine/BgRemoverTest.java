package dev.tvnclr.slap.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Flat color removal and mask application on plain pixel arrays (same code the app runs). */
public class BgRemoverTest {

    private static final int RED = 0xFFFF0000, BLUE = 0xFF0000FF, CREAM = 0xFFFFEC99;

    /** [size]x[size] image of [bg] with a [fg] square in the middle half. */
    private static int[] squareOn(int size, int bg, int fg) {
        int[] px = new int[size * size];
        int lo = size / 4, hi = size * 3 / 4;
        for (int i = 0; i < px.length; i++) {
            int x = i % size, y = i / size;
            px[i] = (x >= lo && x < hi && y >= lo && y < hi) ? fg : bg;
        }
        return px;
    }

    private static float clear(int[] px) {
        return MaskOps.transparentFraction(px);
    }

    @Test
    public void floodRemovesTheBorderColor() {
        int[] px = squareOn(200, RED, BLUE);
        BgRemover.solidPixels(px, 200, 200, 40, false, 0);
        assertEquals(0.75f, clear(px), 0.01f);
        assertEquals(BLUE, px[100 * 200 + 100]);
    }

    @Test
    public void everywhereRemovesTheSameOnAFlatBackground() {
        int[] px = squareOn(200, RED, BLUE);
        BgRemover.solidPixels(px, 200, 200, 40, true, 0);
        assertEquals(0.75f, clear(px), 0.01f);
    }

    /** Repeated runs must give the same result (a compiled-code failure only showed after a few runs). */
    @Test
    public void manyRunsStayTheSame() {
        for (int round = 0; round < 30; round++) {
            for (boolean all : new boolean[] {false, true}) {
                int[] px = squareOn(320, RED, BLUE);
                BgRemover.solidPixels(px, 320, 320, 40, all, 0);
                assertEquals("round " + round + " everywhere=" + all, 0.75f, clear(px), 0.01f);
            }
        }
    }

    /** Noisy flat background (JPEG-like): within the tolerance it still goes. */
    @Test
    public void noisyBackgroundWithinTolerance() {
        int size = 160;
        int[] px = squareOn(size, CREAM, BLUE);
        java.util.Random rnd = new java.util.Random(7);
        for (int i = 0; i < px.length; i++) {
            if (px[i] != CREAM) continue;
            int r = 0xFF, g = 0xEC + rnd.nextInt(11) - 5, b = 0x99 + rnd.nextInt(11) - 5;
            px[i] = 0xFF000000 | r << 16 | g << 8 | b;
        }
        BgRemover.solidPixels(px, size, size, 40, false, 0);
        assertEquals(0.75f, clear(px), 0.02f);
    }

    /** A border shared by a photo and a flat area: the most common border color wins and only it goes. */
    @Test
    public void mixedBorderRemovesTheMainColor() {
        int w = 300, h = 200;
        int[] px = new int[w * h];
        for (int i = 0; i < px.length; i++) px[i] = (i % w) < 100 ? 0xFF406080 : CREAM; // "photo" on the left
        BgRemover.solidPixels(px, w, h, 40, false, 0);
        assertEquals(2f / 3f, clear(px), 0.02f);
        assertEquals(0xFF406080, px[100 * w + 50]);
    }

    @Test
    public void transparentBorderIsLeftAlone() {
        int[] px = squareOn(100, 0x00000000, BLUE);
        int[] before = px.clone();
        BgRemover.solidPixels(px, 100, 100, 40, false, 0);
        assertTrue(java.util.Arrays.equals(before, px));
    }

    @Test
    public void clearOutsideZeroesOnlyTheRemovedPixels() {
        int[] px = {BLUE, BLUE, BLUE, BLUE};
        MaskOps.clearOutside(px, new boolean[] {true, false, true, false});
        assertEquals(BLUE, px[0]);
        assertEquals(0, px[1]);
        assertEquals(BLUE, px[2]);
        assertEquals(0, px[3]);
    }

    /** A 64x64 mask (left half kept) scaled onto a 256x256 frame removes the right half. */
    @Test
    public void applyAlphaScalesTheMask() {
        int mw = 64, mh = 64, w = 256, h = 256;
        byte[] mask = new byte[mw * mh];
        for (int i = 0; i < mask.length; i++) mask[i] = (byte) ((i % mw) < 32 ? 255 : 0);
        int[] px = new int[w * h];
        java.util.Arrays.fill(px, 0xFF00FF00);
        MaskOps.applyAlpha(px, w, h, mask, mw, mh);
        assertEquals(0.5f, clear(px), 0.02f);
        assertEquals(0xFF00FF00, px[10 * w + 10]);
        assertEquals(0, px[10 * w + 250]);
    }

    @Test
    public void applyAlphaKeepsColorAndScalesAlpha() {
        byte[] mask = {(byte) 128};
        int[] px = {0xFF336699};
        MaskOps.applyAlpha(px, 1, 1, mask, 1, 1);
        assertEquals(128, px[0] >>> 24);
        assertEquals(0x336699, px[0] & 0xFFFFFF);
    }

    /** Transparent PNG whose drawing touches the bottom edge: nothing is removed. */
    @Test
    public void mostlyTransparentBorderMeansNothingToRemove() {
        int size = 100;
        int[] px = new int[size * size];
        for (int y = 30; y < size; y++) for (int x = 45; x < 55; x++) px[y * size + x] = 0xFF000000; // leg reaching the edge
        int[] before = px.clone();
        BgRemover.solidPixels(px, size, size, 40, false, 0);
        assertTrue(java.util.Arrays.equals(before, px));
    }
}
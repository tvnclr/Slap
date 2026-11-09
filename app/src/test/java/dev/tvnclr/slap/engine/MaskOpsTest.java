package dev.tvnclr.slap.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MaskOpsTest {

    private static final int W = 40, H = 40;

    private static boolean[] blank() {
        return new boolean[W * H];
    }

    private static void rect(boolean[] m, int x0, int y0, int x1, int y1, boolean v) {
        for (int y = y0; y < y1; y++) for (int x = x0; x < x1; x++) m[y * W + x] = v;
    }

    private static int count(boolean[] m) {
        int n = 0;
        for (boolean b : m) if (b) n++;
        return n;
    }

    @Test
    public void specksGoBigShapeStays() {
        boolean[] m = blank();
        rect(m, 10, 10, 30, 30, true); // subject, 400 px
        rect(m, 2, 2, 4, 4, true);     // speck, 4 px
        rect(m, 35, 35, 36, 36, true); // speck, 1 px
        MaskOps.dropSpecks(m, W, H, 16, 0f);
        assertEquals(400, count(m));
        assertTrue(m[20 * W + 20]);
        assertFalse(m[3 * W + 3]);
    }

    @Test
    public void fractionOfLargestDropsMediumBlobs() {
        boolean[] m = blank();
        rect(m, 5, 5, 30, 30, true);   // 625 px
        rect(m, 33, 2, 38, 8, true);   // 30 px, under 12% of 625
        MaskOps.dropSpecks(m, W, H, 4, 0.12f);
        assertEquals(625, count(m));
    }

    @Test
    public void smallHolesCloseBorderGapsStay() {
        boolean[] m = blank();
        rect(m, 10, 10, 30, 30, true);
        rect(m, 18, 18, 20, 20, false); // pinhole inside, 4 px
        rect(m, 0, 15, 12, 17, false);  // already outside: touches the border
        MaskOps.fillHoles(m, W, H, 16);
        assertTrue(m[19 * W + 19]);
        assertFalse(m[16 * W + 5]);
    }

    @Test
    public void bigHolesStay() {
        boolean[] m = blank();
        rect(m, 5, 5, 35, 35, true);
        rect(m, 12, 12, 28, 28, false); // 256 px hole, e.g. between arms
        MaskOps.fillHoles(m, W, H, 50);
        assertFalse(m[20 * W + 20]);
    }

    @Test
    public void timeSmoothingAveragesNeighboursAndLoops() {
        byte[][] conf = {{(byte) 0}, {(byte) 200}, {(byte) 0}};
        byte[][] out = MaskOps.smoothInTime(conf, true);
        assertEquals(100, out[1][0] & 0xFF); // (0 + 400 + 0) / 4
        assertEquals(50, out[0][0] & 0xFF);  // wraps: (0 + 0 + 200) / 4
        assertEquals(50, out[2][0] & 0xFF);
    }

    @Test
    public void confidenceToAlphaCleansAndKeepsSoftEdge() {
        byte[] conf = new byte[W * H];
        for (int y = 10; y < 30; y++) for (int x = 10; x < 30; x++) conf[y * W + x] = (byte) 230;
        conf[20 * W + 20] = (byte) 40;  // dark pinhole inside the subject
        conf[2 * W + 2] = (byte) 250;   // lone false positive
        byte[] a = MaskOps.confidenceToAlpha(conf, W, H, 90, 170, 16, 0.12f, 50);
        assertEquals(255, a[20 * W + 20] & 0xFF);
        assertEquals(0, a[2 * W + 2] & 0xFF);
        assertEquals(255, a[15 * W + 15] & 0xFF);
        assertEquals(0, a[0] & 0xFF);
    }

    @Test
    public void transparentFractionCountsClearPixels() {
        int[] px = {0x00000000, 0x0F112233, 0xFF112233, 0x80112233};
        assertEquals(0.5f, MaskOps.transparentFraction(px), 1e-6f);
        assertEquals(0f, MaskOps.transparentFraction(new int[0]), 0f);
    }

    @Test
    public void opaqueFractionSpotsWholeImageCutouts() {
        byte[] whole = new byte[100];
        java.util.Arrays.fill(whole, (byte) 255);
        whole[0] = 0;
        assertTrue(MaskOps.opaqueFraction(whole) > 0.97f);
        byte[] half = new byte[100];
        for (int i = 0; i < 50; i++) half[i] = (byte) 250;
        assertEquals(0.5f, MaskOps.opaqueFraction(half), 1e-6f);
    }
}
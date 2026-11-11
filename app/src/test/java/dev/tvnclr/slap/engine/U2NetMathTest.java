package dev.tvnclr.slap.engine;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class U2NetMathTest {

    @Test
    public void normalizeDividesByBrightestChannelThenImageNetMeanStd() {
        // the brightest channel value is 200, so it becomes 1.0 before mean/std
        int[] px = {0xFFC86432, 0xFF000000};
        float[] x = U2NetMath.normalize(px);
        assertEquals(6, x.length);
        assertEquals((200 / 200f - 0.485f) / 0.229f, x[0], 1e-5f);
        assertEquals((100 / 200f - 0.456f) / 0.224f, x[1], 1e-5f);
        assertEquals((50 / 200f - 0.406f) / 0.225f, x[2], 1e-5f);
        assertEquals((0f - 0.485f) / 0.229f, x[3], 1e-5f);
    }

    @Test
    public void outputIsStretchedToTheFullRange() {
        byte[] b = U2NetMath.minMaxToBytes(new float[] {0.2f, 0.6f, 0.4f});
        assertEquals(0, b[0] & 0xFF);
        assertEquals(255, b[1] & 0xFF);
        assertEquals(128, b[2] & 0xFF, 1); // middle value, float rounding may give 127
    }

    @Test
    public void flatOutputDoesNotDivideByZero() {
        byte[] b = U2NetMath.minMaxToBytes(new float[] {0.5f, 0.5f});
        assertEquals(0, b[0] & 0xFF);
    }
}

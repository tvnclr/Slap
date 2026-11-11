package dev.tvnclr.slap.engine;

/** U^2-Net pre and post processing on plain arrays (unit tested on the JVM). */
final class U2NetMath {

    private static final float[] MEAN = {0.485f, 0.456f, 0.406f};
    private static final float[] STD = {0.229f, 0.224f, 0.225f};

    private U2NetMath() {}

    /**
     * ARGB pixels to the network input, NHWC float RGB: divided by the brightest channel value of
     * the image, then ImageNet mean/std (the same steps as the reference rembg pipeline).
     */
    static float[] normalize(int[] argb) {
        int max = 1;
        for (int c : argb) {
            max = Math.max(max, Math.max((c >> 16) & 0xFF, Math.max((c >> 8) & 0xFF, c & 0xFF)));
        }
        float[] out = new float[argb.length * 3];
        for (int i = 0; i < argb.length; i++) {
            int c = argb[i];
            out[i * 3] = (((c >> 16) & 0xFF) / (float) max - MEAN[0]) / STD[0];
            out[i * 3 + 1] = (((c >> 8) & 0xFF) / (float) max - MEAN[1]) / STD[1];
            out[i * 3 + 2] = ((c & 0xFF) / (float) max - MEAN[2]) / STD[2];
        }
        return out;
    }

    /** Network output to 0..255, stretched between its own minimum and maximum. */
    static byte[] minMaxToBytes(float[] y) {
        float lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE;
        for (float v : y) {
            lo = Math.min(lo, v);
            hi = Math.max(hi, v);
        }
        float span = Math.max(hi - lo, 1e-6f);
        byte[] out = new byte[y.length];
        for (int i = 0; i < y.length; i++) out[i] = (byte) Math.round((y[i] - lo) / span * 255f);
        return out;
    }
}

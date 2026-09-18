package com.studysmart.embedding;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class Vectors {

    private Vectors() {
    }

    public static float dot(float[] a, float[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException("Vector dimensions differ: " + a.length + " vs " + b.length);
        }
        float sum = 0f;
        for (int i = 0; i < a.length; i++) {
            sum += a[i] * b[i];
        }
        return sum;
    }

    public static void normalizeInPlace(float[] v) {
        double sumSquares = 0;
        for (float x : v) {
            sumSquares += x * x;
        }
        if (sumSquares == 0) {
            return;
        }
        float inv = (float) (1.0 / Math.sqrt(sumSquares));
        for (int i = 0; i < v.length; i++) {
            v[i] *= inv;
        }
    }

    public static float[] mean(java.util.List<float[]> vectors) {
        if (vectors.isEmpty()) {
            throw new IllegalArgumentException("Cannot take the mean of zero vectors");
        }
        float[] out = new float[vectors.get(0).length];
        for (float[] v : vectors) {
            for (int i = 0; i < out.length; i++) {
                out[i] += v[i];
            }
        }
        for (int i = 0; i < out.length; i++) {
            out[i] /= vectors.size();
        }
        normalizeInPlace(out);
        return out;
    }

    public static byte[] toBytes(float[] v) {
        ByteBuffer buf = ByteBuffer.allocate(v.length * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        for (float x : v) {
            buf.putFloat(x);
        }
        return buf.array();
    }

    public static float[] fromBytes(byte[] bytes) {
        ByteBuffer buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        float[] v = new float[bytes.length / Float.BYTES];
        for (int i = 0; i < v.length; i++) {
            v[i] = buf.getFloat();
        }
        return v;
    }
}

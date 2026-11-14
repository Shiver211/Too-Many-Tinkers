package com.smd.toomanytinkers.client.render;

/** 只改第 0 套 UV，保留位置、颜色、法线和其它顶点属性。 */
public final class QuadUvRemapper {
    private QuadUvRemapper() { }

    public static int[] remap(int[] data, int stride, int uvOffset,
                              float u0, float v0, float uScale, float vScale) {
        int[] result = data.clone();
        for (int vertex = 0; vertex < 4; vertex++) {
            int offset = vertex * stride + uvOffset;
            result[offset] = Float.floatToRawIntBits(u0 + Float.intBitsToFloat(data[offset]) * uScale);
            result[offset + 1] = Float.floatToRawIntBits(v0 + Float.intBitsToFloat(data[offset + 1]) * vScale);
        }
        return result;
    }
}

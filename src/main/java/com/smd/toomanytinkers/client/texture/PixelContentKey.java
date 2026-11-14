package com.smd.toomanytinkers.client.texture;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** 页仅保留摘要；内容去重不额外常驻一份像素。动画由各自原生时序管理。 */
public record PixelContentKey(int width, int height, String digest) {
    public static PixelContentKey of(int width, int height, int[][] mipmaps) {
        try {
            MessageDigest hash = MessageDigest.getInstance("SHA-256");
            integer(hash, mipmaps.length);
            for (int[] mipmap : mipmaps) {
                integer(hash, mipmap == null ? -1 : mipmap.length);
                if (mipmap != null) for (int pixel : mipmap) integer(hash, pixel);
            }
            return new PixelContentKey(width, height, HexFormat.of().formatHex(hash.digest()));
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private static void integer(MessageDigest hash, int value) {
        hash.update((byte) (value >>> 24));
        hash.update((byte) (value >>> 16));
        hash.update((byte) (value >>> 8));
        hash.update((byte) value);
    }
}

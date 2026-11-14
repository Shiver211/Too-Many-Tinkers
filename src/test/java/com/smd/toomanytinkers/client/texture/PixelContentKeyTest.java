package com.smd.toomanytinkers.client.texture;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PixelContentKeyTest {
    @Test void sharesEqualContentWithoutConfusingDimensionsOrMips() {
        int[][] pixels = {{0xff000000, 0xffffffff}, {0xff808080}};
        PixelContentKey original = PixelContentKey.of(2, 1, pixels);
        assertEquals(original, PixelContentKey.of(2, 1, new int[][]{{0xff000000, 0xffffffff}, {0xff808080}}));
        assertNotEquals(original, PixelContentKey.of(1, 2, pixels));
        assertNotEquals(original, PixelContentKey.of(2, 1, new int[][]{{0xff000000, 0xffffffff}, {0xff818181}}));
        assertNotEquals(original, PixelContentKey.of(2, 1, new int[][]{{0xff000000, 0x00ffffff}, {0xff808080}}));
    }
}

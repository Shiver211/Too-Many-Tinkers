package com.smd.toomanytinkers.client.render;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class QuadUvRemapperTest {
    @Test
    void mapsCornersIntoNonSquareRegionWithoutChangingGeometryOrSource() {
        // 每顶点 9 个整数；UV0 位于第 4、5 项，最后两项模拟另一套 UV。
        int[] original = new int[36];
        Arrays.fill(original, 0x12345678);
        float[][] corners = {{0f, 0f}, {1f, 0f}, {1f, 1f}, {0f, 1f}};
        for (int vertex = 0; vertex < 4; vertex++) {
            original[vertex * 9 + 4] = Float.floatToRawIntBits(corners[vertex][0]);
            original[vertex * 9 + 5] = Float.floatToRawIntBits(corners[vertex][1]);
        }
        int[] snapshot = original.clone();
        int[] mapped = QuadUvRemapper.remap(original, 9, 4, 0.25f, 0.5f, 0.125f, 0.25f);
        float[][] expected = {{0.25f, 0.5f}, {0.375f, 0.5f}, {0.375f, 0.75f}, {0.25f, 0.75f}};
        for (int vertex = 0; vertex < 4; vertex++) {
            assertEquals(expected[vertex][0], Float.intBitsToFloat(mapped[vertex * 9 + 4]));
            assertEquals(expected[vertex][1], Float.intBitsToFloat(mapped[vertex * 9 + 5]));
        }
        for (int index = 0; index < original.length; index++) {
            if (index % 9 != 4 && index % 9 != 5) {
                assertEquals(original[index], mapped[index]);
            }
        }
        assertArrayEquals(snapshot, original);
    }

    @Test
    void sameLocalQuadCanMoveToReusedPageSlot() {
        int[] data = new int[8];
        Arrays.fill(data, Float.floatToRawIntBits(0.5f));
        int[] oldSlot = QuadUvRemapper.remap(data, 2, 0, 0f, 0f, 0.25f, 0.25f);
        int[] newSlot = QuadUvRemapper.remap(data, 2, 0, 0.5f, 0.25f, 0.25f, 0.25f);
        assertEquals(0.125f, Float.intBitsToFloat(oldSlot[0]));
        assertEquals(0.625f, Float.intBitsToFloat(newSlot[0]));
        assertEquals(0.375f, Float.intBitsToFloat(newSlot[1]));
    }
}

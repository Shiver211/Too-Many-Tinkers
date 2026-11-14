package com.smd.toomanytinkers.client.texture;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.junit.jupiter.api.Test;
import java.util.Collections;
import static org.junit.jupiter.api.Assertions.*;

class SpritePlacementTest {
    @Test void preservesNativeSubtypeFramesAndAtlasCoordinatesForMaskSampling() {
        MaskSprite atlasMask = new MaskSprite();
        atlasMask.initSprite(8192, 4096, 1024, 2048, false);
        MaskSprite generated = new MaskSprite();
        int[] maskPixels = generated.getFrameTextureData(0)[0];

        assertTrue(SpritePlacement.place(generated, atlasMask));
        assertSame(maskPixels, generated.getFrameTextureData(0)[0]);
        assertTrue(generated.nonEmpty);
        assertEquals(1024, generated.getOriginX());
        assertEquals(2048, generated.getOriginY());
        // 与同一主图集中的星空 UV 使用相同坐标空间，不能变回整张纹理的 0..1。
        assertEquals(1024f / 8192, generated.getMinU(), .000001f);
        assertEquals(1040f / 8192, generated.getMaxU(), .000001f);
        assertEquals(2048f / 4096, generated.getMinV(), .000001f);
        assertEquals(2064f / 4096, generated.getMaxV(), .000001f);
    }

    @Test void retainsNormalizedCoordinatesForPagedMaterials() {
        MaskSprite generated = new MaskSprite();
        assertFalse(SpritePlacement.place(generated, null));
        assertEquals(0f, generated.getMinU(), .000001f);
        assertEquals(1f, generated.getMaxU(), .000001f);
        assertEquals(1, generated.getFrameCount());
    }

    private static final class MaskSprite extends TextureAtlasSprite {
        final boolean nonEmpty = true;
        MaskSprite() {
            super("test:infinity_cosmic");
            setIconWidth(16);
            setIconHeight(16);
            setFramesTextureData(Collections.singletonList(new int[][]{new int[256]}));
        }
    }
}

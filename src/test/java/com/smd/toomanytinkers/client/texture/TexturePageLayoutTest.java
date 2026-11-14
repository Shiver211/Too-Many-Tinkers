package com.smd.toomanytinkers.client.texture;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TexturePageLayoutTest {
    @Test void preservesResolutionAndMipmapAlignmentAcrossRows() {
        TexturePageLayout page = new TexturePageLayout(16, 16, 2);
        assertEquals(new TexturePageLayout.Region(0, 0, 7, 3), page.allocate(7, 3));
        assertEquals(new TexturePageLayout.Region(8, 0, 8, 5), page.allocate(8, 5));
        assertEquals(new TexturePageLayout.Region(0, 6, 16, 10), page.allocate(16, 10));
        assertNull(page.allocate(1, 1));
    }

    @Test void failedAllocationDoesNotConsumeTheRemainingSpace() {
        TexturePageLayout page = new TexturePageLayout(32, 32, 1);
        assertNull(page.allocate(64, 32));
        assertEquals(new TexturePageLayout.Region(0, 0, 32, 16), page.allocate(32, 16));
        assertNull(page.allocate(32, 17));
        assertEquals(new TexturePageLayout.Region(0, 16, 32, 16), page.allocate(32, 16));
    }

    @Test void independentPagesCanExceedASingleColumnAtlasHeight() {
        int textures = 4096;
        int placed = 0;
        for (int pageIndex = 0; pageIndex < 4; pageIndex++) {
            TexturePageLayout page = new TexturePageLayout(512, 512, 2);
            while (page.allocate(16, 16) != null) placed++;
        }
        assertEquals(textures, placed);
    }
}

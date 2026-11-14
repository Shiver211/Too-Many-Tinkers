package com.smd.toomanytinkers.client.texture;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;
import org.junit.jupiter.api.Test;
import slimeknights.tconstruct.library.client.texture.AbstractColoredTexture;
import slimeknights.tconstruct.library.client.texture.SimpleColoredTexture;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class NativeSpriteLoaderTest {
    private static final ResourceLocation BASE = new ResourceLocation("test", "base");
    private static final ResourceLocation EXTRA_A = new ResourceLocation("test", "extra_a");
    private static final ResourceLocation EXTRA_B = new ResourceLocation("test", "extra_b");

    @Test void nativeMultiColorUsesTheActualPartBrightnessRange() {
        NativeSpriteLoader loader = new NativeSpriteLoader(null, ignored -> null, 0);
        int[] input = {0xff505050, 0xff787878, 0xffa0a0a0, 0x00112233};
        loader.register(new Fixture(BASE, 2, 2, input));
        TextureAtlasSprite result = new SimpleColoredTexture(0xffff0000, 0xff00ff00, 0xff0000ff, BASE, "test:result");
        loader.register(result);
        loader.load(result);

        // 原图亮度范围80..160：三个不透明像素分别属于低、中、高区间。
        // 固定50/217阈值会错误地把三者都分到中间颜色。
        assertArrayEquals(new int[]{0xff500000, 0xff007800, 0xff0000a0, 0x00112233}, result.getFrameTextureData(0)[0]);
        assertArrayEquals(new int[]{0xff505050, 0xff787878, 0xffa0a0a0, 0x00112233}, input);
    }

    @Test void executesMultipleDependenciesAndBothProcessingHooksAtOriginalResolution() {
        NativeSpriteLoader loader = new NativeSpriteLoader(null, ignored -> null, 0);
        int[] base = new int[64 * 32];
        int[] extraA = new int[base.length];
        int[] extraB = new int[base.length];
        java.util.Arrays.fill(base, 0xff102030);
        java.util.Arrays.fill(extraA, 0x00010000);
        java.util.Arrays.fill(extraB, 0x00000200);
        loader.register(new Fixture(BASE, 64, 32, base));
        loader.register(new Fixture(EXTRA_A, 64, 32, extraA));
        loader.register(new Fixture(EXTRA_B, 64, 32, extraB));
        ProbeTexture result = new ProbeTexture();
        loader.register(result);
        loader.load(result);

        assertEquals(64, result.getIconWidth());
        assertEquals(32, result.getIconHeight());
        assertEquals(List.of("load", "pre", "post"), result.calls);
        assertEquals(0xff112230, result.getFrameTextureData(0)[0][1]);
        assertEquals(0xff000060, result.getFrameTextureData(0)[0][0]);
        assertEquals(0xff102030, base[0]);
    }

    private static final class Fixture extends TextureAtlasSprite {
        final int fixtureWidth;
        final int fixtureHeight;
        final int[] pixels;
        Fixture(ResourceLocation location, int width, int height, int[] pixels) {
            super(location.toString());
            this.fixtureWidth = width; this.fixtureHeight = height; this.pixels = pixels;
        }
        @Override public boolean hasCustomLoader(IResourceManager manager, ResourceLocation location) { return true; }
        @Override public boolean load(IResourceManager manager, ResourceLocation location,
                                      Function<ResourceLocation, TextureAtlasSprite> dependencies) {
            setIconWidth(fixtureWidth);
            setIconHeight(fixtureHeight);
            setFramesTextureData(Collections.singletonList(new int[][]{pixels}));
            return false;
        }
    }

    private static final class ProbeTexture extends AbstractColoredTexture {
        final List<String> calls = new ArrayList<>();
        int[] a;
        int[] b;
        int neighbourSum;
        ProbeTexture() { super(BASE, "test:probe"); }
        @Override public Collection<ResourceLocation> getDependencies() { return List.of(BASE, EXTRA_A, EXTRA_B); }
        @Override public boolean load(IResourceManager manager, ResourceLocation location,
                                      Function<ResourceLocation, TextureAtlasSprite> dependencies) {
            calls.add("load");
            a = dependencies.apply(EXTRA_A).getFrameTextureData(0)[0];
            b = dependencies.apply(EXTRA_B).getFrameTextureData(0)[0];
            return super.load(manager, location, dependencies);
        }
        @Override protected void preProcess(int[] data) {
            calls.add("pre");
            neighbourSum = (data[0] & 255) + (data[1] & 255);
        }
        @Override protected int colorPixel(int pixel, int index) { return pixel ^ a[index] ^ b[index]; }
        @Override protected void postProcess(int[] data) {
            calls.add("post");
            data[0] = 0xff000000 | neighbourSum;
            a = null; b = null;
        }
    }
}

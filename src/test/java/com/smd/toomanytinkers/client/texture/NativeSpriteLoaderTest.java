package com.smd.toomanytinkers.client.texture;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;
import org.junit.jupiter.api.Test;
import slimeknights.tconstruct.library.client.texture.AbstractColoredTexture;
import slimeknights.tconstruct.library.client.texture.SimpleColoredTexture;
import slimeknights.tconstruct.library.client.texture.TextureColoredTexture;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class NativeSpriteLoaderTest {
    private static final ResourceLocation BASE = new ResourceLocation("test", "base");
    private static final ResourceLocation EXTRA_A = new ResourceLocation("test", "extra_a");
    private static final ResourceLocation EXTRA_B = new ResourceLocation("test", "extra_b");
    private static final ResourceLocation MISSING = new ResourceLocation("missingno");
    private static final IResourceManager NO_RESOURCES = new IResourceManager() {
        @Override public Set<String> getResourceDomains() { return Set.of(); }
        @Override public IResource getResource(ResourceLocation location) throws FileNotFoundException {
            throw new FileNotFoundException(location.toString());
        }
        @Override public List<IResource> getAllResources(ResourceLocation location) throws FileNotFoundException {
            throw new FileNotFoundException(location.toString());
        }
    };

    @Test void sharedCustomDependencyKeepsRestoredFramesUntilLastLoaderCloses() {
        AtomicInteger loads = new AtomicInteger();
        TextureAtlasSprite shared = new RestoredDependency(() -> loads.incrementAndGet());
        shared.setIconWidth(2);
        shared.setIconHeight(2);
        shared.initSprite(16, 16, 4, 8, false);
        float minU = shared.getMinU();
        float minV = shared.getMinV();
        NativeSpriteLoader first = new NativeSpriteLoader(NO_RESOURCES, ignored -> shared, 0);
        NativeSpriteLoader second = new NativeSpriteLoader(NO_RESOURCES, ignored -> shared, 0);
        assertSame(shared, first.source(BASE));
        assertSame(shared, second.source(BASE));
        assertEquals(1, loads.get());
        assertEquals(minU, shared.getMinU());
        assertEquals(minV, shared.getMinV());
        first.close();
        assertEquals(1, shared.getFrameCount());
        second.close();
        assertEquals(0, shared.getFrameCount());
        try (NativeSpriteLoader third = new NativeSpriteLoader(NO_RESOURCES, ignored -> shared, 0)) {
            assertSame(shared, third.source(BASE));
            assertEquals(2, loads.get());
        }
        assertEquals(0, shared.getFrameCount());
    }

    @Test void failedSharedRestoreCanBeRetriedWithoutLeakingPartialFrames() {
        AtomicInteger attempts = new AtomicInteger();
        TextureAtlasSprite shared = new RestoredDependency(() -> {
            if (attempts.getAndIncrement() == 0) throw new IllegalStateException("模拟共享依赖加载失败");
        });
        try (NativeSpriteLoader first = new NativeSpriteLoader(NO_RESOURCES, ignored -> shared, 0)) {
            assertThrows(IllegalStateException.class, () -> first.source(BASE));
        }
        assertEquals(0, shared.getFrameCount());
        try (NativeSpriteLoader second = new NativeSpriteLoader(NO_RESOURCES, ignored -> shared, 0)) {
            assertSame(shared, second.source(BASE));
            assertEquals(1, shared.getFrameCount());
        }
        assertEquals(0, shared.getFrameCount());
    }

    @Test void concurrentSharedRestoreExecutesCustomCallbackOnce() throws Exception {
        AtomicInteger loads = new AtomicInteger();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch resume = new CountDownLatch(1);
        TextureAtlasSprite shared = new RestoredDependency(() -> {
            loads.incrementAndGet();
            entered.countDown();
            try { assertTrue(resume.await(5, TimeUnit.SECONDS)); }
            catch (InterruptedException interrupted) { throw new AssertionError(interrupted); }
        });
        try (var workers = Executors.newFixedThreadPool(2);
             NativeSpriteLoader first = new NativeSpriteLoader(NO_RESOURCES, ignored -> shared, 0);
             NativeSpriteLoader second = new NativeSpriteLoader(NO_RESOURCES, ignored -> shared, 0)) {
            var a = workers.submit(() -> first.source(BASE));
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            var b = workers.submit(() -> second.source(BASE));
            resume.countDown();
            assertSame(shared, a.get(2, TimeUnit.SECONDS));
            assertSame(shared, b.get(2, TimeUnit.SECONDS));
            assertEquals(1, loads.get());
        } finally { resume.countDown(); }
        assertEquals(0, shared.getFrameCount());
    }

    private static final class RestoredDependency extends TextureAtlasSprite {
        private final Runnable loaded;
        RestoredDependency(Runnable loaded) { super(BASE.toString()); this.loaded = loaded; }
        @Override public boolean hasCustomLoader(IResourceManager manager, ResourceLocation location) { return true; }
        @Override public boolean load(IResourceManager manager, ResourceLocation location,
                                      Function<ResourceLocation, TextureAtlasSprite> dependencies) {
            setIconWidth(2);
            setIconHeight(2);
            setFramesTextureData(new ArrayList<>(Collections.singletonList(new int[][]{new int[]{-1, -1, -1, -1}})));
            loaded.run();
            return false;
        }
    }

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

    @Test void missingBlockDependencyDoesNotAbortNativeFortifiedTexture() {
        int[] missingPixels = {0xffff00ff, 0xff000000, 0xff000000, 0xffff00ff};
        TextureAtlasSprite missing = NativeSpriteLoader.raw(MISSING);
        missing.setIconWidth(2);
        missing.setIconHeight(2);
        missing.initSprite(32, 32, 4, 8, false);
        NativeSpriteLoader loader = new NativeSpriteLoader(NO_RESOURCES,
                location -> MISSING.equals(location)
                        ? NativeSpriteLoader.recoverMissingSprite(missing, missingPixels, 0) : null, 0);
        loader.register(new Fixture(BASE, 2, 2, new int[]{-1, -1, -1, 0}));
        TextureColoredTexture fortified = new TextureColoredTexture(
                new ResourceLocation("mekanism:blocks/refinedglowstone"), BASE, "test:mod_fortified");
        fortified.stencil = true;
        loader.register(fortified);

        assertDoesNotThrow(() -> loader.load(fortified));
        assertEquals(2, fortified.getIconWidth());
        assertEquals(2, fortified.getIconHeight());
        assertArrayEquals(new int[]{0xffff00ff, 0xff000000, 0xff000000, 0},
                fortified.getFrameTextureData(0)[0]);
        TextureAtlasSprite recovered = loader.source(MISSING);
        assertEquals(missing.getMinU(), recovered.getMinU());
        assertEquals(missing.getMaxV(), recovered.getMaxV());
        assertArrayEquals(missingPixels, recovered.getFrameTextureData(0)[0]);
        recovered.getFrameTextureData(0)[0][0] = 0;
        assertEquals(0xffff00ff, missingPixels[0]);
        assertEquals(0, missing.getFrameCount());
    }

    @Test void vanillaUnqualifiedMissingSpriteNameRecoversEvenWhenNamespacedEntryIsEmpty() {
        int[] missingPixels = new int[16 * 16];
        java.util.Arrays.fill(missingPixels, 0xffff00ff);
        TextureAtlasSprite vanillaMissing = new TextureAtlasSprite("missingno") {};
        vanillaMissing.setIconWidth(16);
        vanillaMissing.setIconHeight(16);
        vanillaMissing.initSprite(32, 32, 0, 0, false);
        TextureAtlasSprite emptyRegisteredMissing = NativeSpriteLoader.raw(MISSING);

        assertTrue(NativeSpriteLoader.isMissingTexture(MISSING, vanillaMissing));
        assertFalse(NativeSpriteLoader.isMissingTexture(BASE, vanillaMissing));

        NativeSpriteLoader loader = new NativeSpriteLoader(NO_RESOURCES,
                location -> NativeSpriteLoader.isMissingTexture(location, vanillaMissing)
                        ? NativeSpriteLoader.recoverMissingSprite(vanillaMissing, missingPixels, 0)
                        : (MISSING.equals(location) ? emptyRegisteredMissing : null), 0);
        int[] basePixels = new int[16 * 16];
        java.util.Arrays.fill(basePixels, 0xffffffff);
        loader.register(new Fixture(BASE, 16, 16, basePixels));
        TextureColoredTexture treatedWood = new TextureColoredTexture(
                new ResourceLocation("immersiveengineering:blocks/treated_wood"), BASE,
                "tconstruct:items/parts/large_plate_treatedwood");
        treatedWood.stencil = true;
        loader.register(treatedWood);

        assertDoesNotThrow(() -> loader.load(treatedWood));
        assertEquals(1, treatedWood.getFrameCount());
        assertArrayEquals(missingPixels, treatedWood.getFrameTextureData(0)[0]);
        assertEquals(0, vanillaMissing.getFrameCount());
    }

    @Test void releasedAtlasDependencyWithoutPngUsesMissingTexture() {
        Fixture missing = new Fixture(MISSING, 2, 2, new int[]{-1, -1, -1, -1});
        missing.load(NO_RESOURCES, MISSING, ignored -> null);
        TextureAtlasSprite released = NativeSpriteLoader.raw(BASE);
        NativeSpriteLoader loader = new NativeSpriteLoader(NO_RESOURCES,
                location -> MISSING.equals(location) ? missing : released, 0);

        assertSame(missing, loader.source(BASE));
        assertSame(missing, loader.source(BASE));
    }

    @Test void emptyNativeTextureKeepsIndependentMissingFrames() {
        Fixture missing = new Fixture(MISSING, 2, 2, new int[]{0xffff00ff, 0xff000000, 0xff000000, 0xffff00ff});
        missing.load(NO_RESOURCES, MISSING, ignored -> null);
        NativeSpriteLoader loader = new NativeSpriteLoader(NO_RESOURCES,
                location -> MISSING.equals(location) ? missing : null, 4);
        TextureAtlasSprite empty = new TextureAtlasSprite("test:empty") {
            @Override public boolean hasCustomLoader(IResourceManager manager, ResourceLocation location) { return true; }
            @Override public boolean load(IResourceManager manager, ResourceLocation location,
                                          Function<ResourceLocation, TextureAtlasSprite> dependencies) {
                setIconWidth(1);
                setIconHeight(1);
                return false;
            }
        };
        loader.register(empty);

        assertDoesNotThrow(() -> loader.load(empty));
        assertEquals(2, empty.getIconWidth());
        assertEquals(2, empty.getIconHeight());
        assertArrayEquals(missing.getFrameTextureData(0)[0], empty.getFrameTextureData(0)[0]);
        empty.clearFramesTextureData();
        assertEquals(1, missing.getFrameCount());
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

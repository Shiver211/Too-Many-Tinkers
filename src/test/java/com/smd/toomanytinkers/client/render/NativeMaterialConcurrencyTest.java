package com.smd.toomanytinkers.client.render;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import slimeknights.tconstruct.library.client.CustomTextureCreator;
import slimeknights.tconstruct.library.client.MaterialRenderInfo;
import slimeknights.tconstruct.library.materials.Material;

import java.io.FileNotFoundException;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class NativeMaterialConcurrencyTest {
    private static final ResourceLocation BASE = new ResourceLocation("test:part");
    private static final IResourceManager NO_RESOURCES = new IResourceManager() {
        @Override public Set<String> getResourceDomains() { return Set.of(); }
        @Override public IResource getResource(ResourceLocation location) throws FileNotFoundException {
            assertFalse(Thread.holdsLock(NativeMaterialTextures.class), "资源查询仍在类锁内");
            throw new FileNotFoundException(location.toString());
        }
        @Override public List<IResource> getAllResources(ResourceLocation location) throws FileNotFoundException {
            throw new FileNotFoundException(location.toString());
        }
    };
    private Material material;
    private MaterialRenderInfo oldInfo;
    private Object oldContext;
    private int oldGeneration;
    private boolean oldReady;
    private FixtureAtlas atlas;

    @BeforeAll static void initializeMaterials() throws Exception {
        MaterialSpriteLookupTest.initializeTextureCreatorWithoutGame();
    }

    @BeforeEach void isolateTextures() throws Exception {
        material = CustomTextureCreator.guiMaterial;
        oldInfo = material.renderInfo;
        oldContext = field("textureContext").get(null);
        oldGeneration = field("generation").getInt(null);
        oldReady = field("atlasReady").getBoolean(null);
        NativeMaterialTextures.reload();
        atlas = new FixtureAtlas();
        installContext();
    }

    @AfterEach void restoreTextures() throws Exception {
        NativeMaterialTextures.reload();
        material.renderInfo = oldInfo;
        field("generation").setInt(null, oldGeneration);
        field("atlasReady").setBoolean(null, oldReady);
        field("textureContext").set(null, oldContext);
    }

    @Test void sharedModelUsesAtlasGeometryAndVertexColorWithoutGeneratingFrames() {
        material.renderInfo = new MaterialRenderInfo.Default(0xff336699);
        TextureAtlasSprite base = new Fixture(BASE.toString(), 0xffffffff, () -> {});
        atlas.sprites.put(BASE.toString(), base);
        long generated = NativeMaterialTextures.stats().generated();
        try (var texture = NativeMaterialTextures.resolveForModel(BASE, material.identifier)) {
            assertSame(base, texture.sprite());
            assertTrue(texture.vertexColored());
            assertEquals(0xff336699, texture.vertexColor());
            assertEquals(generated, NativeMaterialTextures.stats().generated());
        }
    }

    @Test void parallelAcquisitionAndFrameRestorationKeepReadersAlive() throws Exception {
        AtomicInteger factories = new AtomicInteger();
        CountDownLatch loading = new CountDownLatch(1);
        CountDownLatch resume = new CountDownLatch(1);
        material.renderInfo = info(factories, 0xff123456, () -> { loading.countDown(); await(resume); });
        try (var workers = Executors.newFixedThreadPool(2)) {
            var first = workers.submit(() -> NativeMaterialTextures.acquire(BASE, material));
            assertTrue(loading.await(2, TimeUnit.SECONDS));
            var second = workers.submit(() -> NativeMaterialTextures.acquire(BASE, material));
            synchronized (NativeMaterialTextures.class) { assertNotNull(NativeMaterialTextures.stats()); }
            resume.countDown();
            var a = first.get(2, TimeUnit.SECONDS);
            var b = second.get(2, TimeUnit.SECONDS);
            TextureAtlasSprite original = a.sprite();
            assertSame(original, b.sprite());
            assertEquals(1, factories.get());
            assertTrue(NativeMaterialTextures.hasRecipe(original));
            var pendingBake = NativeMaterialTextures.prepareFrames(original);
            a.close();
            assertEquals(1, b.sprite().getFrameCount());
            b.close();
            NativeMaterialTextures.clearGpu();
            assertEquals(1, original.getFrameCount());
            pendingBake.close();
            assertEquals(0, original.getFrameCount());

            var firstBake = NativeMaterialTextures.prepareFrames(original);
            var secondBake = NativeMaterialTextures.prepareFrames(original);
            assertEquals(2, factories.get());
            firstBake.close();
            assertEquals(1, original.getFrameCount());
            secondBake.close();
            assertEquals(0, original.getFrameCount());
        } finally { resume.countDown(); }
    }

    @Test void reloadDuringGenerationRejectsOldFramesAndRetriesWithCurrentMaterial() throws Exception {
        CountDownLatch loading = new CountDownLatch(1);
        CountDownLatch resume = new CountDownLatch(1);
        Map<String, TextureAtlasSprite> obsolete = new HashMap<>();
        material.renderInfo = new MaterialRenderInfo.Default(-1) {
            @Override public TextureAtlasSprite getTexture(ResourceLocation base, String name) {
                TextureAtlasSprite sprite = new Fixture(name, 0xff111111, () -> { loading.countDown(); await(resume); });
                obsolete.put(name, sprite);
                return sprite;
            }
        };
        long generated = NativeMaterialTextures.stats().generated();
        try (var worker = Executors.newSingleThreadExecutor()) {
            var result = worker.submit(() -> NativeMaterialTextures.acquire(BASE, material));
            assertTrue(loading.await(2, TimeUnit.SECONDS));
            NativeMaterialTextures.reload();
            installContext();
            material.renderInfo = info(new AtomicInteger(), 0xffabcdef, () -> {});
            resume.countDown();
            try (var current = result.get(2, TimeUnit.SECONDS)) {
                assertEquals(0xffabcdef, current.sprite().getFrameTextureData(0)[0][0]);
                assertEquals(generated + 1, NativeMaterialTextures.stats().generated());
                for (TextureAtlasSprite old : obsolete.values()) {
                    assertFalse(NativeMaterialTextures.hasRecipe(old));
                    assertEquals(0, old.getFrameCount());
                }
            }
        } finally { resume.countDown(); }
    }

    @Test void loadFailureReleasesPartialFramesAndAllowsRetry() {
        AtomicInteger attempts = new AtomicInteger();
        material.renderInfo = info(new AtomicInteger(), 0xff987654, () -> {
            if (attempts.getAndIncrement() == 0) throw new IllegalStateException("模拟原生加载失败");
        });
        assertThrows(IllegalStateException.class, () -> NativeMaterialTextures.acquire(BASE, material));
        try (var recovered = NativeMaterialTextures.acquire(BASE, material)) {
            assertEquals(0xff987654, recovered.sprite().getFrameTextureData(0)[0][0]);
            assertTrue(NativeMaterialTextures.hasRecipe(recovered.sprite()));
        }
    }

    private void installContext() throws Exception {
        field("textureContext").set(null, new NativeMaterialTextures.TextureContext(
                field("generation").getInt(null), NO_RESOURCES, atlas, 0));
    }

    private static MaterialRenderInfo info(AtomicInteger factories, int pixel, Runnable load) {
        return new MaterialRenderInfo.Default(-1) {
            @Override public TextureAtlasSprite getTexture(ResourceLocation base, String name) {
                assertFalse(Thread.holdsLock(NativeMaterialTextures.class));
                factories.incrementAndGet();
                return new Fixture(name, pixel, load);
            }
        };
    }

    private static final class Fixture extends TextureAtlasSprite {
        final int pixel;
        final Runnable onLoad;
        Fixture(String name, int pixel, Runnable onLoad) { super(name); this.pixel = pixel; this.onLoad = onLoad; }
        @Override public boolean hasCustomLoader(IResourceManager manager, ResourceLocation location) { return true; }
        @Override public boolean load(IResourceManager manager, ResourceLocation location,
                                      Function<ResourceLocation, TextureAtlasSprite> dependencies) {
            assertFalse(Thread.holdsLock(NativeMaterialTextures.class), "自定义 loader 仍在类锁内");
            setIconWidth(2);
            setIconHeight(2);
            setFramesTextureData(new java.util.ArrayList<>(java.util.Collections.singletonList(
                    new int[][]{new int[]{pixel, pixel, pixel, pixel}})));
            onLoad.run();
            return false;
        }
    }

    private static final class FixtureAtlas extends TextureMap {
        final Map<String, TextureAtlasSprite> sprites = new HashMap<>();
        final TextureAtlasSprite missing = new Fixture("minecraft:missingno", -1, () -> {});
        FixtureAtlas() { super("textures"); }
        @Override public TextureAtlasSprite getAtlasSprite(String name) { return sprites.getOrDefault(name, missing); }
        @Override public TextureAtlasSprite getTextureExtry(String name) { return sprites.get(name); }
        @Override public TextureAtlasSprite getMissingSprite() { return missing; }
    }

    private static Field field(String name) throws Exception {
        Field field = NativeMaterialTextures.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static void await(CountDownLatch latch) {
        try { assertTrue(latch.await(5, TimeUnit.SECONDS)); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new AssertionError(interrupted); }
    }
}

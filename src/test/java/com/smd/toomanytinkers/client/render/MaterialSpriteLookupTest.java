package com.smd.toomanytinkers.client.render;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.relauncher.Side;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import slimeknights.tconstruct.library.client.CustomTextureCreator;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

class MaterialSpriteLookupTest {
    private Set<String> knownBases;
    private Set<String> oldKnownBases;
    private Map<String, Map<String, TextureAtlasSprite>> oldSprites;
    private Object oldIndex;
    private boolean oldReady;
    private int oldGeneration;

    @BeforeAll
    static void initializeTextureCreatorWithoutGame() throws Exception {
        // Material 的静态初始化只需运行侧；不启动 Forge、Minecraft 或 GL。
        FMLCommonHandler handler = FMLCommonHandler.instance();
        Field delegate = field(FMLCommonHandler.class, "sidedDelegate");
        Object previous = delegate.get(handler);
        Class<?> sidedHandler = delegate.getType();
        delegate.set(handler, Proxy.newProxyInstance(sidedHandler.getClassLoader(), new Class<?>[]{sidedHandler},
                (proxy, method, args) -> {
                    if (method.getName().equals("getSide")) return Side.SERVER;
                    throw new UnsupportedOperationException(method.getName());
                }));
        try {
            Class.forName(CustomTextureCreator.class.getName());
        } finally {
            delegate.set(handler, previous);
        }
    }

    @BeforeEach @SuppressWarnings("unchecked")
    void isolateAtlasState() throws Exception {
        knownBases = (Set<String>) field(TmtCustomTextureBridge.class, "KNOWN_BASES").get(null);
        oldKnownBases = new HashSet<>(knownBases);
        oldSprites = new HashMap<>(CustomTextureCreator.sprites);
        oldIndex = index();
        oldReady = readyField().getBoolean(null);
        oldGeneration = field(NativeMaterialTextures.class, "generation").getInt(null);
        knownBases.clear();
        CustomTextureCreator.sprites.clear();
        TmtCustomTextureBridge.clearBaseIndex();
        readyField().setBoolean(null, true);
    }

    @AfterEach
    void restoreAtlasState() throws Exception {
        knownBases.clear();
        knownBases.addAll(oldKnownBases);
        CustomTextureCreator.sprites.clear();
        CustomTextureCreator.sprites.putAll(oldSprites);
        field(TmtCustomTextureBridge.class, "publishedBases").set(null, oldIndex);
        readyField().setBoolean(null, oldReady);
        field(NativeMaterialTextures.class, "generation").setInt(null, oldGeneration);
    }

    @Test
    void unrelatedMissingNamesDoNotWaitForTextureLock() throws Exception {
        register("test_mod:tool_part", Map.of("iron_alloy", sprite("test_mod:tool_part_iron_alloy")));
        TmtCustomTextureBridge.publishBaseIndex();

        whileTextureLockIsHeld(() -> {
            assertNull(NativeMaterialTextures.lookupMaterialSprite("other_mod:missing_texture"));
            assertNull(NativeMaterialTextures.lookupMaterialSprite("test_mod:tool_part"));
            assertNull(NativeMaterialTextures.lookupMaterialSprite("test_mod:tool_parts_iron"));
            return null;
        });
        assertNotNull(NativeMaterialTextures.lookupMaterialSprite("test_mod:tool_part_iron_alloy"));
    }

    @Test @SuppressWarnings("unchecked")
    void recursiveCandidateDoesNotWaitForTextureLock() throws Exception {
        register("test:part", Map.of("iron", sprite("test:part_iron")));
        TmtCustomTextureBridge.publishBaseIndex();
        ThreadLocal<Boolean> guard = (ThreadLocal<Boolean>) field(NativeMaterialTextures.class, "LOOKUP_GUARD").get(null);

        assertNull(whileTextureLockIsHeld(() -> {
            guard.set(true);
            try {
                return NativeMaterialTextures.lookupMaterialSprite("test:part_iron");
            } finally {
                guard.remove();
            }
        }));
    }

    @Test
    void candidateWaitingForTextureLockRechecksAtlasReadiness() throws Exception {
        register("test:part", Map.of("iron", sprite("test:part_iron")));
        TmtCustomTextureBridge.publishBaseIndex();
        CountDownLatch started = new CountDownLatch(1);
        try (var worker = Executors.newSingleThreadExecutor()) {
            Future<TextureAtlasSprite> result;
            synchronized (NativeMaterialTextures.class) {
                result = worker.submit(() -> {
                    started.countDown();
                    return NativeMaterialTextures.lookupMaterialSprite("test:part_iron");
                });
                assertTrue(started.await(2, TimeUnit.SECONDS));
                assertThrows(TimeoutException.class, () -> result.get(100, TimeUnit.MILLISECONDS));
                NativeMaterialTextures.beginStitch();
            }
            assertNull(result.get(2, TimeUnit.SECONDS));
        }
    }

    @Test
    void selectsLongestValidBaseAndFallsBackWhenItsMaterialIsUnavailable() {
        TextureAtlasSprite shorter = sprite("test:part_dark_iron");
        TextureAtlasSprite longer = sprite("test:part_dark_iron");
        Map<String, TextureAtlasSprite> longerMaterials = new HashMap<>(Map.of("iron", longer));
        register("test:part", Map.of("dark_iron", shorter));
        register("test:part_dark", longerMaterials);
        TmtCustomTextureBridge.publishBaseIndex();

        assertSame(longer, NativeMaterialTextures.lookupMaterialSprite("test:part_dark_iron"));
        longerMaterials.clear();
        assertSame(shorter, NativeMaterialTextures.lookupMaterialSprite("test:part_dark_iron"));
    }

    @Test
    void failedGenerationDoesNotLeaveRecursionGuardEnabled() {
        TextureAtlasSprite generated = sprite("test:part_iron");
        register("test:part", new AbstractMap<>() {
            private boolean fail = true;
            @Override public boolean containsKey(Object key) { return "iron".equals(key); }
            @Override public TextureAtlasSprite get(Object key) {
                if (fail) {
                    fail = false;
                    throw new IllegalStateException("模拟材质生成失败");
                }
                return generated;
            }
            @Override public Set<Entry<String, TextureAtlasSprite>> entrySet() {
                throw new AssertionError("查询不应遍历材质表");
            }
        });
        TmtCustomTextureBridge.publishBaseIndex();

        assertThrows(IllegalStateException.class, () -> NativeMaterialTextures.lookupMaterialSprite("test:part_iron"));
        assertSame(generated, NativeMaterialTextures.lookupMaterialSprite("test:part_iron"));
    }

    @Test
    void publishedIndexIsAnImmutableSnapshotAndIsReplacedAfterRebuild() throws Exception {
        knownBases.add("test:old_part");
        TmtCustomTextureBridge.publishBaseIndex();
        Set<String> published = index();
        knownBases.clear();
        knownBases.add("test:new_part");

        assertThrows(UnsupportedOperationException.class, () -> published.add("test:extra"));
        assertTrue(TmtCustomTextureBridge.hasMaterialSpriteCandidate("test:old_part_iron"));
        assertFalse(TmtCustomTextureBridge.hasMaterialSpriteCandidate("test:new_part_iron"));
        TmtCustomTextureBridge.publishBaseIndex();
        assertFalse(TmtCustomTextureBridge.hasMaterialSpriteCandidate("test:old_part_iron"));
        assertTrue(TmtCustomTextureBridge.hasMaterialSpriteCandidate("test:new_part_iron"));
    }

    @Test
    void stitchAndReloadInvalidatePublishedIndex() throws Exception {
        knownBases.add("test:part");
        TmtCustomTextureBridge.publishBaseIndex();
        NativeMaterialTextures.beginStitch();
        assertFalse(NativeMaterialTextures.stats().atlasReady());
        assertTrue(index().isEmpty());

        readyField().setBoolean(null, true);
        TmtCustomTextureBridge.publishBaseIndex();
        NativeMaterialTextures.reload();
        assertFalse(NativeMaterialTextures.stats().atlasReady());
        assertTrue(index().isEmpty());
        assertNull(whileTextureLockIsHeld(() -> NativeMaterialTextures.lookupMaterialSprite("test:part_iron")));
    }

    private void register(String base, Map<String, TextureAtlasSprite> materials) {
        knownBases.add(base);
        CustomTextureCreator.sprites.put(base, materials);
    }

    private static TextureAtlasSprite sprite(String name) { return new TextureAtlasSprite(name) {}; }

    private static <T> T whileTextureLockIsHeld(Callable<T> query) throws Exception {
        // 主线程保持类锁，工作线程必须在释放锁之前完成快速返回。
        try (var worker = Executors.newSingleThreadExecutor()) {
            synchronized (NativeMaterialTextures.class) {
                return worker.submit(query).get(2, TimeUnit.SECONDS);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Set<String> index() throws Exception {
        return (Set<String>) field(TmtCustomTextureBridge.class, "publishedBases").get(null);
    }

    private static Field readyField() throws Exception { return field(NativeMaterialTextures.class, "atlasReady"); }

    private static Field field(Class<?> owner, String name) throws Exception {
        Field result = owner.getDeclaredField(name);
        result.setAccessible(true);
        return result;
    }
}

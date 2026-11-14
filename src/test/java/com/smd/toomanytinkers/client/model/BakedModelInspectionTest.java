package com.smd.toomanytinkers.client.model;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.smd.toomanytinkers.client.render.TableItemRenderer;
import com.smd.toomanytinkers.client.render.TmtNativeQuadRenderer;
import cubex2.mods.chesttransporter.client.BakedModelCH;
import dev.beecube31.crazyae2.client.rendering.models.baked.PuredaisyEncodedPatternBakedModel;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.PerspectiveMapWrapper;
import org.junit.jupiter.api.Test;
import pl.asie.foamfix.client.FoamyItemLayerModel.Dynamic3DItemModel;
import pl.asie.foamfix.client.FoamyItemLayerModel.Static3DItemModel;
import slimeknights.mantle.client.model.BakedWrapper;
import slimeknights.tconstruct.shared.block.PropertyTableItem.TableItem;
import slimeknights.tconstruct.shared.client.BakedColoredItemModel;
import thebetweenlands.client.render.model.baked.BakedModelItemWrapper;
import thebetweenlands.client.render.model.baked.ModelItemMultiPart.BakedItemMultiPartModel;
import tschipp.hardcoreitemstages.client.HardcoreItemStageModel;

import java.lang.ref.SoftReference;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BakedModelInspectionTest {
    private final TextureAtlasSprite managed = new TextureAtlasSprite("test:managed") { };
    private final TextureAtlasSprite ordinary = new TextureAtlasSprite("test:ordinary") { };

    @Test void coldFoamModelsInspectEveryTextureWithoutQueryingQuads() {
        Dynamic3DItemModel dynamic = new Dynamic3DItemModel(List.of(ordinary, managed));
        Static3DItemModel statik = new Static3DItemModel(List.of(ordinary, managed));
        assertTrue(contains(dynamic));
        assertTrue(contains(statik));
        assertFalse(contains(new Dynamic3DItemModel(List.of(ordinary))));
        assertFalse(contains(new Static3DItemModel(List.of(ordinary))));
        assertNull(dynamic.cache());
        assertNull(statik.cache());
    }

    @Test void liveFoamCacheTakesPrecedenceAndClearedCacheFallsBackToTextures() {
        Dynamic3DItemModel dynamic = new Dynamic3DItemModel(List.of(ordinary));
        Static3DItemModel statik = new Static3DItemModel(List.of(ordinary));
        List<BakedQuad> quads = List.of(quad(managed));
        SoftReference<List<BakedQuad>> cache = new SoftReference<>(quads);
        dynamic.cache(cache);
        statik.cache(cache);
        assertTrue(contains(dynamic));
        assertTrue(contains(statik));
        cache.clear();
        assertFalse(contains(dynamic));
        assertFalse(contains(statik));
        assertSame(cache, dynamic.cache());
        assertSame(cache, statik.cache());
        assertNull(cache.get());
    }

    @Test void clearedFoamCacheStillDetectsManagedTextures() {
        Dynamic3DItemModel dynamic = new Dynamic3DItemModel(List.of(managed));
        Static3DItemModel statik = new Static3DItemModel(List.of(managed));
        dynamic.cache(new SoftReference<>(List.of()));
        statik.cache(new SoftReference<>(List.of()));
        // 存活的空缓存就是空几何；清除后才按纹理判断未来要生成的几何。
        assertFalse(contains(dynamic));
        assertFalse(contains(statik));
        dynamic.cache().clear();
        statik.cache().clear();
        assertTrue(contains(dynamic));
        assertTrue(contains(statik));
    }

    @Test void nestedKnownWrappersReachFoamTexturesWithoutCallingAnyWrapperQuads() {
        for (TextureAtlasSprite sprite : List.of(ordinary, managed)) {
            Static3DItemModel foam = new Static3DItemModel(List.of(sprite));
            IBakedModel model = new HardcoreItemStageModel(foam);
            model = new PuredaisyEncodedPatternBakedModel(model);
            model = new BakedModelItemWrapper(new Static3DItemModel(List.of(managed)), model);
            model = new BakedItemMultiPartModel(List.of(model), Map.of());
            model = new BakedWrapper(model);
            model = new PerspectiveMapWrapper(model, ImmutableMap.of());
            model = new BakedModelWrapper<>(model) { };
            assertEquals(sprite == managed, contains(model));
            assertNull(foam.cache());
        }
    }

    @Test void chestInspectsOnlyHandleAndCurrentlySelectedVariant() {
        IBakedModel plain = new Static3DItemModel(List.of(ordinary));
        IBakedModel paged = new Dynamic3DItemModel(List.of(managed));
        BakedModelCH chest = new BakedModelCH(plain, null, Map.of("unused", paged));
        assertFalse(contains(chest));
        chest.select(paged);
        assertTrue(contains(chest));
        chest.select(plain);
        assertFalse(contains(chest));
        assertTrue(contains(new BakedModelCH(paged, null, Map.of())));
    }

    @Test void multipartIgnoresInactiveMappingAndHandlesCyclesByIdentity() {
        IBakedModel paged = new Dynamic3DItemModel(List.of(managed));
        assertFalse(contains(new BakedItemMultiPartModel(null, Map.of("unused", paged))));
        assertFalse(contains(new BakedItemMultiPartModel(List.of(), Map.of("unused", paged))));
        List<IBakedModel> children = new ArrayList<>();
        BakedItemMultiPartModel cycle = new BakedItemMultiPartModel(children, Map.of());
        children.add(cycle);
        assertFalse(contains(cycle));
        children.add(paged);
        assertTrue(contains(cycle));
    }

    @Test void coloredModelUsesBakedFacesAndOnlyDelegatesWhenItsFaceMapIsEmpty() throws Exception {
        IBakedModel plain = new Static3DItemModel(List.of(ordinary));
        IBakedModel paged = new Dynamic3DItemModel(List.of(managed));
        assertTrue(contains(colored(paged, ImmutableMap.of(), ImmutableList.of())));
        assertFalse(contains(colored(plain, ImmutableMap.of(), ImmutableList.of(quad(managed)))));
        assertTrue(contains(colored(plain, ImmutableMap.of(EnumFacing.EAST, ImmutableList.of(quad(managed))),
                ImmutableList.of())));
        assertTrue(contains(colored(plain, ImmutableMap.of(EnumFacing.EAST, ImmutableList.of()),
                ImmutableList.of(quad(managed)))));
        assertFalse(contains(colored(paged, ImmutableMap.of(EnumFacing.EAST, ImmutableList.of()),
                ImmutableList.of(quad(ordinary)))));
    }

    @Test void overridingWrapperSubclassKeepsItsOwnGeometry() {
        IBakedModel plain = new Static3DItemModel(List.of(ordinary));
        IBakedModel paged = new Dynamic3DItemModel(List.of(managed));
        IBakedModel replacesParent = new BakedModelWrapper<>(paged) {
            @Override public List<BakedQuad> getQuads(IBlockState state, EnumFacing side, long rand) {
                return List.of(quad(ordinary));
            }
        };
        IBakedModel addsFace = new BakedWrapper(plain) {
            @Override public List<BakedQuad> getQuads(IBlockState state, EnumFacing side, long rand) {
                return side == EnumFacing.EAST ? List.of(quad(managed)) : null;
            }
        };
        assertFalse(contains(replacesParent));
        assertTrue(contains(addsFace));
    }

    @Test void lruWeightNeverBuildsOrRepopulatesFoamCache() {
        Dynamic3DItemModel dynamic = new Dynamic3DItemModel(List.of(managed));
        Static3DItemModel statik = new Static3DItemModel(List.of(ordinary));
        WeightedLruCache<String, IBakedModel> cache = new WeightedLruCache<>(10, 10000,
                BakedModelInspection::estimatedWeight);
        cache.put("dynamic", dynamic);
        cache.put("static", statik);
        assertEquals(1024L, cache.weight());
        assertNull(dynamic.cache());
        assertNull(statik.cache());
        List<BakedQuad> quads = List.of(quad(managed));
        SoftReference<List<BakedQuad>> live = new SoftReference<>(quads);
        dynamic.cache(live);
        statik.cache(live);
        long expected = 256L + 192L + 28L * Integer.BYTES;
        assertEquals(expected, BakedModelInspection.estimatedWeight(dynamic));
        assertEquals(expected, BakedModelInspection.estimatedWeight(statik));
        live.clear();
        assertEquals(512L, BakedModelInspection.estimatedWeight(dynamic));
        assertEquals(512L, BakedModelInspection.estimatedWeight(statik));
        assertNull(live.get());
    }

    @Test void publicHeiAndTableDetectionDoNotBuildOrdinaryFoamGeometry() {
        IBakedModel model = new HardcoreItemStageModel(new Dynamic3DItemModel(List.of(ordinary)));
        assertFalse(TmtNativeQuadRenderer.modelContainsManaged(model));
        assertFalse(TableItemRenderer.isDynamic(new TableItem(null, model)));
    }

    private boolean contains(IBakedModel model) {
        return BakedModelInspection.containsTexture(model, sprite -> sprite == managed);
    }

    private BakedQuad quad(TextureAtlasSprite sprite) {
        return new BakedQuad(new int[28], -1, EnumFacing.SOUTH, sprite, false, DefaultVertexFormats.ITEM);
    }

    private static BakedColoredItemModel colored(IBakedModel parent,
            ImmutableMap<EnumFacing, ImmutableList<BakedQuad>> faces, ImmutableList<BakedQuad> general) throws Exception {
        // 实际依赖的构造器需要已启动的 Minecraft；这里只还原它构造完成后的字段状态。
        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        Field singleton = unsafeClass.getDeclaredField("theUnsafe");
        singleton.setAccessible(true);
        BakedColoredItemModel model = (BakedColoredItemModel) unsafeClass.getMethod("allocateInstance", Class.class)
                .invoke(singleton.get(null), BakedColoredItemModel.class);
        setField(BakedWrapper.class, model, "parent", parent);
        setField(BakedColoredItemModel.class, model, "coloredQuads", faces);
        setField(BakedColoredItemModel.class, model, "coloredDefaultQuads", general);
        return model;
    }

    private static void setField(Class<?> owner, Object instance, String name, Object value) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(instance, value);
    }
}

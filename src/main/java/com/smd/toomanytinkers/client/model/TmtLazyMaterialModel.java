package com.smd.toomanytinkers.client.model;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.smd.toomanytinkers.TooManyTinkers;
import com.smd.toomanytinkers.client.render.NativeMaterialTextures;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.ItemLayerModel;
import net.minecraftforge.client.model.ModelStateComposition;
import net.minecraftforge.client.model.PerspectiveMapWrapper;
import net.minecraftforge.common.model.IModelState;
import net.minecraftforge.common.model.TRSRTransformation;
import slimeknights.tconstruct.library.client.model.BakedMaterialModel;
import slimeknights.tconstruct.library.client.model.BakedSimpleItem;
import slimeknights.tconstruct.library.client.model.MaterialModel;
import slimeknights.tconstruct.library.client.model.ModelHelper;

import javax.vecmath.Vector3f;
import java.util.HashMap;
import java.util.function.Function;

/**
 * 保留 BakedMaterialModel 契约，只把原版的逐材质预烘焙改为按需烘焙。
 * 工具组合、损坏、强化、模型覆盖、弹药与附属包装器继续使用原实现。
 */
public final class TmtLazyMaterialModel extends BakedMaterialModel {
    private static final WeightedLruCache<Key, IBakedModel> MODELS =
            new WeightedLruCache<>(4096, 16L * 1024 * 1024, TmtLazyMaterialModel::modelWeight);
    private static final WeightedLruCache<Key, Boolean> REPORTED_FAILURES =
            new WeightedLruCache<>(256, 256, ignored -> 1L);

    private final ResourceLocation baseTexture;
    private final IBakedModel sharedMaterialBase;
    private final IModelState state;
    private final VertexFormat format;
    private final ImmutableMap<ItemCameraTransforms.TransformType, TRSRTransformation> transforms;
    private static long hits;
    private static long misses;

    private TmtLazyMaterialModel(IBakedModel base, IBakedModel sharedMaterialBase,
                                 ResourceLocation baseTexture, IModelState state, VertexFormat format) {
        super(base, PerspectiveMapWrapper.getTransforms(state));
        // 父类会按所有材质数量预分配表；本实现只保留附属显式添加的模型。
        this.parts = new HashMap<>();
        this.baseTexture = baseTexture;
        this.sharedMaterialBase = sharedMaterialBase;
        this.state = state;
        this.format = format;
        this.transforms = PerspectiveMapWrapper.getTransforms(state);
    }

    public static BakedMaterialModel bake(MaterialModel source, IModelState state, VertexFormat format,
                                           Function<ResourceLocation, TextureAtlasSprite> textureGetter) {
        // 与 MaterialModel.bakeIt 相同：外部层旋转在前，部件偏移只合成一次。
        if (source.getXOffset() != 0 || source.getYOffset() != 0) {
            state = new ModelStateComposition(state, TRSRTransformation.blockCenterToCorner(
                    new TRSRTransformation(new Vector3f(source.getXOffset() / 16f,
                            -source.getYOffset() / 16f, 0f), null, null, null)));
        }
        ImmutableList<ResourceLocation> textures = ImmutableList.copyOf(source.getTextures());
        IBakedModel base = new ItemLayerModel(textures).bake(state, format, textureGetter);
        ResourceLocation particle = new ResourceLocation(base.getParticleTexture().getIconName());
        // 原版材质模型用 particle 的单层纹理；空材质才回到完整 base。
        IBakedModel sharedBase = textures.size() == 1 ? base
                : new ItemLayerModel(ImmutableList.of(particle)).bake(state, format, textureGetter);
        return new TmtLazyMaterialModel(base, sharedBase, particle, state, format);
    }

    @Override
    public IBakedModel getModelByIdentifier(String identifier) {
        IBakedModel explicitlyAdded = parts.get(identifier);
        if (explicitlyAdded != null) {
            return explicitlyAdded;
        }
        if (identifier == null || identifier.isEmpty()) {
            return this;
        }
        Key key = new Key(this, identifier);
        IBakedModel cached = MODELS.get(key);
        if (cached != null) {
            hits++;
            return cached;
        }
        misses++;
        try {
            IBakedModel model = bakeMaterial(identifier);
            MODELS.put(key, model);
            return model;
        } catch (RuntimeException exception) {
            // TiC 的组合缓存会吞掉 ExecutionException，须在这里保留定位信息。
            if (REPORTED_FAILURES.get(key) == null) {
                REPORTED_FAILURES.put(key, true);
                TooManyTinkers.LOGGER.error("TMT 原生部件烘焙失败：texture={}, material={}",
                        baseTexture, identifier, exception);
            }
            throw exception;
        }
    }

    private IBakedModel bakeMaterial(String identifier) {
        try (NativeMaterialTextures.MaterialTexture texture = NativeMaterialTextures.resolveForModel(baseTexture, identifier)) {
            TextureAtlasSprite sprite = texture.sprite();
            IBakedModel model;
            if (!NativeMaterialTextures.isManaged(sprite)
                    && sprite.getIconName().equals(sharedMaterialBase.getParticleTexture().getIconName())) {
                model = sharedMaterialBase;
            } else {
                ResourceLocation name = new ResourceLocation(sprite.getIconName());
                model = new ItemLayerModel(ImmutableList.of(name)).bake(state, format, ignored -> sprite);
            }
            if (!texture.vertexColored()) {
                return model;
            }
            ImmutableList.Builder<BakedQuad> quads = ImmutableList.builder();
            for (BakedQuad quad : model.getQuads(null, null, 0L)) {
                quads.add(ModelHelper.colorQuad(texture.vertexColor(), quad));
            }
            return new BakedSimpleItem(quads.build(), transforms, model);
        }
    }

    public static void clearCache() {
        MODELS.clear();
        REPORTED_FAILURES.clear();
    }

    public static String stats() {
        return "lazyModels=" + MODELS.size() + ", estimatedModelBytes=" + MODELS.weight()
                + ", lazyModelHits=" + hits + ", lazyModelMisses=" + misses;
    }

    private static long modelWeight(IBakedModel model) {
        long bytes = 256L;
        for (BakedQuad quad : model.getQuads(null, null, 0L)) {
            bytes += 192L + (long) quad.getVertexData().length * Integer.BYTES;
        }
        return bytes;
    }

    private record Key(TmtLazyMaterialModel owner, String material) { }
}

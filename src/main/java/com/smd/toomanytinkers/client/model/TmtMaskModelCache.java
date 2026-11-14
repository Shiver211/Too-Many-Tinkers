package com.smd.toomanytinkers.client.model;

import com.smd.toomanytinkers.client.render.NativeMaterialTextures;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.EnumFacing;

import java.util.List;
import java.util.function.Supplier;

/** TConEvo 的 Cosmic mask 模型缓存；生成函数仍是它原来的 CCL 烘焙函数。 */
public final class TmtMaskModelCache {
    private static final WeightedLruCache<TextureAtlasSprite, IBakedModel> MODELS =
            new WeightedLruCache<>(512, 8L * 1024 * 1024, TmtMaskModelCache::modelWeight);
    private static long hits;
    private static long misses;

    private TmtMaskModelCache() { }

    public static IBakedModel get(TextureAtlasSprite sprite, Supplier<IBakedModel> nativeBake) {
        IBakedModel cached = MODELS.get(sprite);
        if (cached != null) {
            hits++;
            return cached;
        }
        misses++;
        IBakedModel model;
        if (NativeMaterialTextures.hasRecipe(sprite)) {
            // 工具本体可能已经上传并释放了 mask 的 CPU 帧。
            // 只在 CCL 原算法读取轮廓期间恢复，异常也必须释放。
            try (NativeMaterialTextures.FrameLease ignored = NativeMaterialTextures.prepareFrames(sprite)) {
                model = nativeBake.get();
            }
        } else {
            model = nativeBake.get();
        }
        MODELS.put(sprite, model);
        return model;
    }

    public static void clearCache() {
        MODELS.clear();
    }

    public static String stats() {
        return "maskModels=" + MODELS.size() + ", estimatedMaskModelBytes=" + MODELS.weight()
                + ", maskModelHits=" + hits + ", maskModelMisses=" + misses;
    }

    private static long modelWeight(IBakedModel model) {
        long bytes = 256L + quadWeight(model.getQuads(null, null, 0L));
        for (EnumFacing side : EnumFacing.VALUES) {
            bytes += quadWeight(model.getQuads(null, side, 0L));
        }
        return bytes;
    }

    private static long quadWeight(List<BakedQuad> quads) {
        long bytes = 0;
        for (BakedQuad quad : quads) {
            bytes += 192L + (long) quad.getVertexData().length * Integer.BYTES;
        }
        return bytes;
    }
}

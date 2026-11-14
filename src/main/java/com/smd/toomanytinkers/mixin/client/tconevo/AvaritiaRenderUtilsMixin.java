package com.smd.toomanytinkers.mixin.client.tconevo;

import com.smd.toomanytinkers.client.model.TmtMaskModelCache;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.HashMap;
import java.util.function.Function;

@Pseudo
@Mixin(targets = "xyz.phanta.tconevo.integration.avaritia.client.AvaritiaRenderUtils", remap = false)
public abstract class AvaritiaRenderUtilsMixin {
    @Redirect(method = "getModelForMaskSprite",
            at = @At(value = "INVOKE", target = "Ljava/util/HashMap;computeIfAbsent(Ljava/lang/Object;Ljava/util/function/Function;)Ljava/lang/Object;"))
    private static Object tmt$boundedMaskModels(HashMap<?, ?> originalCache, Object key,
                                                Function<Object, Object> nativeBake) {
        // 不调用原 HashMap.computeIfAbsent，避免在有界缓存之外再留下永久强引用。
        // nativeBake 是 TConEvo 原 lambda，内部仍调用 ItemQuadBakery.bakeItem。
        return TmtMaskModelCache.get((TextureAtlasSprite) key, () -> (IBakedModel) nativeBake.apply(key));
    }
}

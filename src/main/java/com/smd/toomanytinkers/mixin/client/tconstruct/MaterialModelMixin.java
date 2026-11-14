package com.smd.toomanytinkers.mixin.client.tconstruct;

import com.smd.toomanytinkers.client.model.TmtLazyMaterialModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.model.IModelState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import slimeknights.tconstruct.library.client.model.BakedMaterialModel;
import slimeknights.tconstruct.library.client.model.MaterialModel;

import java.util.function.Function;

@Mixin(value = MaterialModel.class, remap = false)
public abstract class MaterialModelMixin {
    @Inject(method = "bakeIt", at = @At("HEAD"), cancellable = true)
    private void tmt$lazyMaterials(IModelState state, VertexFormat format,
                                  Function<ResourceLocation, TextureAtlasSprite> textures,
                                  CallbackInfoReturnable<BakedMaterialModel> cir) {
        cir.setReturnValue(TmtLazyMaterialModel.bake((MaterialModel) (Object) this, state, format, textures));
    }
}

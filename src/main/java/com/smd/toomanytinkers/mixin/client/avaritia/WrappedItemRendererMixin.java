package com.smd.toomanytinkers.mixin.client.avaritia;

import com.smd.toomanytinkers.client.render.TmtNativeQuadRenderer;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "morph.avaritia.client.render.item.WrappedItemRenderer", remap = false)
public abstract class WrappedItemRendererMixin {
    @Inject(method = "renderModel(Lnet/minecraft/client/renderer/block/model/IBakedModel;Lnet/minecraft/item/ItemStack;F)V",
            at = @At("HEAD"), cancellable = true)
    private static void tmt$materialPages(IBakedModel model, ItemStack stack, float alpha, CallbackInfo ci) {
        if (TmtNativeQuadRenderer.renderAvaritiaModel(model, stack, alpha)) {
            ci.cancel();
        }
    }
}

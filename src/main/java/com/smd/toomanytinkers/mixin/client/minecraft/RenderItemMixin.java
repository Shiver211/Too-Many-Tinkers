package com.smd.toomanytinkers.mixin.client.minecraft;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.smd.toomanytinkers.client.render.GlintRenderScope;
import com.smd.toomanytinkers.client.render.TmtNativeQuadRenderer;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(RenderItem.class)
public abstract class RenderItemMixin {
    @WrapMethod(method = "renderEffect")
    private void tmt$glintScope(IBakedModel model, Operation<Void> original) {
        GlintRenderScope.enter();
        try {
            original.call(model);
        } finally {
            GlintRenderScope.exit();
        }
    }

    @Inject(method = "renderQuads", at = @At("HEAD"), cancellable = true)
    private void tmt$materialPages(BufferBuilder buffer, List<BakedQuad> quads,
                                  int color, ItemStack stack, CallbackInfo ci) {
        if (TmtNativeQuadRenderer.renderItemQuads(buffer, quads, color, stack)) {
            ci.cancel();
        }
    }
}

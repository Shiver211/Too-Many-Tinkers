package com.smd.toomanytinkers.mixin.client.tconevo;

import com.smd.toomanytinkers.client.render.NativeMaterialTextures;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "xyz.phanta.tconevo.client.handler.TextureMapHandler", remap = false)
public abstract class TextureMapHandlerMixin {
    @Inject(method = "registerSprite", at = @At("HEAD"), cancellable = true)
    private void tmt$captureMask(TextureAtlasSprite sprite, CallbackInfo ci) {
        if (NativeMaterialTextures.captureAuxiliary(sprite)) {
            ci.cancel();
        }
    }
}

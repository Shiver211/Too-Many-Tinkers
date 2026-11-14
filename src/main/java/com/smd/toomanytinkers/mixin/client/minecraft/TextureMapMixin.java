package com.smd.toomanytinkers.mixin.client.minecraft;

import com.smd.toomanytinkers.client.render.NativeMaterialTextures;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 主图集缺少指定组合时才查询匠魂已登记的按需材质。 */
@Mixin(TextureMap.class)
public abstract class TextureMapMixin {
    @Shadow public abstract TextureAtlasSprite getMissingSprite();

    @Inject(method = "getAtlasSprite", at = @At("RETURN"), cancellable = true)
    private void tmt$lookupMaterialSprite(String name, CallbackInfoReturnable<TextureAtlasSprite> cir) {
        if ((Object) this != Minecraft.getMinecraft().getTextureMapBlocks()
                || cir.getReturnValue() != getMissingSprite()) return;
        TextureAtlasSprite sprite = NativeMaterialTextures.lookupMaterialSprite(name);
        if (sprite != null) cir.setReturnValue(sprite);
    }

    @Inject(method = "getTextureExtry", at = @At("RETURN"), cancellable = true, remap = false)
    private void tmt$lookupMaterialEntry(String name, CallbackInfoReturnable<TextureAtlasSprite> cir) {
        if ((Object) this != Minecraft.getMinecraft().getTextureMapBlocks() || cir.getReturnValue() != null) return;
        TextureAtlasSprite sprite = NativeMaterialTextures.lookupMaterialSprite(name);
        if (sprite != null) cir.setReturnValue(sprite);
    }
}

package com.smd.toomanytinkers.mixin.client.tt2;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 共享底图还需要染色，不能被 Scout 当作已合成的材质覆盖图。 */
@Pseudo
@Mixin(targets = "xy177.tt2.client.texture.ScoutTmtArmorTextures", remap = false)
public abstract class ScoutArmorTextureMixin {
    @Inject(method = "getUploadedSprite", at = @At("RETURN"), cancellable = true)
    private static void tmt$requireExactTexture(TextureMap atlas, ResourceLocation location,
                                               CallbackInfoReturnable<TextureAtlasSprite> cir) {
        TextureAtlasSprite sprite = cir.getReturnValue();
        // 查询 base_material 可能由 TMT 返回 base；交回 Scout 原生染色和原色遮罩流程。
        // 真正同名的覆盖图、生成图及底图保留原行为，不新增像素副本或缓存。
        if (sprite != null && !location.toString().equals(sprite.getIconName())) cir.setReturnValue(null);
    }
}

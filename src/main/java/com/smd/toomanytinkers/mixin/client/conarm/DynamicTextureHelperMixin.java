package com.smd.toomanytinkers.mixin.client.conarm;

import c4.conarm.common.armor.modifiers.ModPolished;
import c4.conarm.lib.client.DynamicTextureHelper;
import c4.conarm.lib.tinkering.TinkersArmor;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.smd.toomanytinkers.client.armor.TmtArmorTextures;
import com.smd.toomanytinkers.client.render.NativeMaterialTextures;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import slimeknights.tconstruct.library.materials.Material;
import slimeknights.tconstruct.library.modifiers.IModifier;

/** 保留 ConArm 原生合成、帧选择、顶点着色与强化顺序，仅接入按需素材及纹理所有权。 */
@Mixin(value = DynamicTextureHelper.class, remap = false)
public abstract class DynamicTextureHelperMixin {
    @Shadow
    private static ResourceLocation getCombinedTexture(ItemStack stack, TinkersArmor armor) {
        throw new AssertionError();
    }

    @Inject(method = "getCachedTexture", at = @At("HEAD"), cancellable = true)
    private static void tmt$getOwnedTexture(ItemStack stack, CallbackInfoReturnable<ResourceLocation> cir) {
        if (stack.getItem() instanceof TinkersArmor armor) {
            cir.setReturnValue(TmtArmorTextures.get(stack, () -> getCombinedTexture(stack, armor)));
        }
    }

    @Inject(method = "refreshCache", at = @At("HEAD"))
    private static void tmt$releaseTextures(CallbackInfo ci) {
        TmtArmorTextures.clear();
    }

    @WrapOperation(method = "getCombinedTexture", at = @At(value = "INVOKE", ordinal = 0,
            target = "Lnet/minecraft/client/renderer/texture/TextureMap;getTextureExtry(Ljava/lang/String;)Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;"),
            remap = false)
    private static TextureAtlasSprite tmt$material(TextureMap atlas, String name,
                                                   Operation<TextureAtlasSprite> original,
                                                   @Local(index = 8) Material material,
                                                   @Local(index = 12) String base) {
        // 槽位来自 AntiqueArmory 1.3.0 getCombinedTexture；不从含下划线的材质 ID 猜路径。
        return TmtArmorTextures.retain(NativeMaterialTextures.acquire(new ResourceLocation(base), material));
    }

    @WrapOperation(method = "getCombinedTexture", at = @At(value = "INVOKE", remap = true,
            target = "Lnet/minecraft/client/renderer/texture/TextureMap;getAtlasSprite(Ljava/lang/String;)Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;"),
            remap = false)
    private static TextureAtlasSprite tmt$modifier(TextureMap atlas, String name,
                                                   Operation<TextureAtlasSprite> original,
                                                   @Local(index = 9) IModifier modifier) {
        TextureAtlasSprite current = original.call(atlas, name);
        if (modifier instanceof ModPolished polished) {
            ResourceLocation base = new ResourceLocation("conarm", "models/modifiers/mod_polished_armor");
            boolean override = NativeMaterialTextures.exists(new ResourceLocation(name));
            if (override || polished.material.renderInfo != null && polished.material.renderInfo.isStitched()) {
                return TmtArmorTextures.retain(NativeMaterialTextures.acquire(base, polished.material));
            }
            // 原生非 stitched 的 Default 抛光没有材质图层，保持原有 missing 分支。
            return current == atlas.getMissingSprite() ? current
                    : TmtArmorTextures.retain(NativeMaterialTextures.acquireRaw(new ResourceLocation(name)));
        }
        if (current == atlas.getMissingSprite()) return current;
        return TmtArmorTextures.retain(NativeMaterialTextures.acquireRaw(new ResourceLocation(name)));
    }

    @WrapOperation(method = "getCombinedTexture", at = @At(value = "INVOKE", remap = true,
            target = "Lnet/minecraft/client/renderer/texture/TextureManager;getDynamicTextureLocation(Ljava/lang/String;Lnet/minecraft/client/renderer/texture/DynamicTexture;)Lnet/minecraft/util/ResourceLocation;"),
            remap = false)
    private static ResourceLocation tmt$ownUpload(TextureManager manager, String name, DynamicTexture texture,
                                                  Operation<ResourceLocation> original) {
        return TmtArmorTextures.upload(manager, texture);
    }
}

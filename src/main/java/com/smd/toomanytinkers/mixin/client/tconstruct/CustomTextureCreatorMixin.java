package com.smd.toomanytinkers.mixin.client.tconstruct;

import com.smd.toomanytinkers.client.render.TmtCustomTextureBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import slimeknights.tconstruct.library.client.CustomTextureCreator;
import slimeknights.tconstruct.library.tools.IToolPart;

import java.util.Map;
import java.util.Set;

@Mixin(value = CustomTextureCreator.class, remap = false)
public class CustomTextureCreatorMixin {

    @Shadow @Final private static Set<ResourceLocation> baseTextures;
    @Shadow @Final private static Map<ResourceLocation, Set<IToolPart>> texturePartMapping;

    @Inject(method = "createCustomTextures", at = @At("HEAD"), cancellable = true)
    private void tmt$onlyMainAtlas(TextureStitchEvent.Pre event, CallbackInfo ci) {
        // HEI 也有独立图集；不能让它重新加载材质定义或清空主图集的运行时索引。
        if (event.getMap() != Minecraft.getMinecraft().getTextureMapBlocks()) ci.cancel();
    }

    @Inject(method = "createMaterialTextures", at = @At("HEAD"), cancellable = true)
    private void tmt$registerLazyMaterials(TextureMap map, CallbackInfo ci) {
        TmtCustomTextureBridge.rebuild(baseTextures, texturePartMapping);
        ci.cancel();
    }

    @Inject(method = "onResourceManagerReload", at = @At("HEAD"), cancellable = true, remap = true)
    private void tmt$clearSourceTextures(CallbackInfo ci) {
        // 这个原生监听器可能在 ModelManager 完成烘焙后执行。
        // baseTextures 是下次解析的输入；sprite 索引/recipe 属于当前图集，要保留到下次主图集 rebuild。
        baseTextures.clear();
        ci.cancel();
    }
}

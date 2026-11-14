package com.smd.toomanytinkers.mixin.client.tconstruct;

import com.google.common.cache.Cache;
import com.smd.toomanytinkers.client.model.NativeToolModelCaches;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "slimeknights.tconstruct.library.client.model.BakedToolModel$ToolItemOverrideList", remap = false)
public abstract class ToolItemOverrideListMixin {
    @Shadow private Cache<?, ?> bakedModelCache;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void tmt$registerCache(CallbackInfo ci) {
        NativeToolModelCaches.register(bakedModelCache);
    }
}

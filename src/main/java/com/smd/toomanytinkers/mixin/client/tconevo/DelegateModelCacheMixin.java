package com.smd.toomanytinkers.mixin.client.tconevo;

import com.google.common.cache.Cache;
import com.smd.toomanytinkers.client.model.NativeToolModelCaches;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "xyz.phanta.tconevo.client.util.DelegateModelCache", remap = false)
public abstract class DelegateModelCacheMixin implements NativeToolModelCaches.RenderContext {
    @Shadow @Final private Cache<?, ?> delegateCache;
    @Shadow private World cachedRenderWorld;
    @Shadow private EntityLivingBase cachedRenderEntity;
    @Shadow private BlockPos cachedRenderEntityPos;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void tmt$registerDelegateCache(CallbackInfo ci) {
        NativeToolModelCaches.register(delegateCache);
        NativeToolModelCaches.registerContext(this);
    }

    @Override
    public void tmt$clearContext() {
        // 模型会跨世界继续使用，缓存的渲染上下文必须随旧世界释放。
        cachedRenderWorld = null;
        cachedRenderEntity = null;
        cachedRenderEntityPos = null;
    }
}

package com.smd.toomanytinkers.client.texture;

import com.smd.toomanytinkers.client.render.NativeMaterialTextures;
import com.smd.toomanytinkers.mixin.client.tconstruct.CustomTextureCreatorMixin;
import net.minecraft.util.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PublishedAtlasLifecycleTest {
    @Test @SuppressWarnings("unchecked")
    void lateSourceReloadDoesNotClearPublishedAtlasState() throws Exception {
        // 直接调用事件处理函数，模拟 ModelManager 烘焙后才通知该监听器的顺序；不启动 MC/GL。
        Field sourcesField = field(CustomTextureCreatorMixin.class, "baseTextures");
        Field namesField = field(NativeMaterialTextures.class, "ATLAS_MATERIAL_SPRITES");
        Field readyField = field(NativeMaterialTextures.class, "atlasReady");
        Object oldSources = sourcesField.get(null);
        boolean oldReady = readyField.getBoolean(null);
        Set<String> names = (Set<String>) namesField.get(null);
        Set<String> oldNames = new HashSet<>(names);
        Set<ResourceLocation> sources = new HashSet<>(Set.of(new ResourceLocation("test:part")));
        try {
            sourcesField.set(null, sources);
            readyField.setBoolean(null, true);
            names.add("test:part_infinity_metal");
            var handler = CustomTextureCreatorMixin.class.getDeclaredMethod("tmt$clearSourceTextures", CallbackInfo.class);
            handler.setAccessible(true);
            CallbackInfo callback = new CallbackInfo("onResourceManagerReload", true);
            handler.invoke(new CustomTextureCreatorMixin(), callback);
            assertTrue(callback.isCancelled());
            assertTrue(sources.isEmpty());
            assertTrue(NativeMaterialTextures.stats().atlasReady());
            assertTrue(names.contains("test:part_infinity_metal"));
        } finally {
            sourcesField.set(null, oldSources);
            readyField.setBoolean(null, oldReady);
            names.clear();
            names.addAll(oldNames);
        }
    }

    private static Field field(Class<?> owner, String name) throws Exception {
        Field result = owner.getDeclaredField(name);
        result.setAccessible(true);
        return result;
    }
}

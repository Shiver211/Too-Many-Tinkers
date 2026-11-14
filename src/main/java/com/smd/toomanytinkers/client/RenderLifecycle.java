package com.smd.toomanytinkers.client;

import com.smd.toomanytinkers.client.diagnostics.TmtDiagnostics;
import com.smd.toomanytinkers.client.armor.TmtArmorTextures;
import com.smd.toomanytinkers.client.model.NativeToolModelCaches;
import com.smd.toomanytinkers.client.model.TmtLazyMaterialModel;
import com.smd.toomanytinkers.client.model.TmtMaskModelCache;
import com.smd.toomanytinkers.client.model.TableItemModels;
import com.smd.toomanytinkers.client.render.NativeMaterialTextures;
import com.smd.toomanytinkers.client.render.TmtNativeQuadRenderer;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/** 所有缓存清理和 GL 删除都在客户端线程执行。 */
public final class RenderLifecycle {
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void beforeStitch(TextureStitchEvent.Pre event) {
        if (event.getMap() == Minecraft.getMinecraft().getTextureMapBlocks()) {
            NativeMaterialTextures.beginStitch();
            clearModels();
            // 随后的 CustomTextureCreator.rebuild 负责清纹理页并重建材质索引。
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void afterStitch(TextureStitchEvent.Post event) {
        if (event.getMap() == Minecraft.getMinecraft().getTextureMapBlocks()) NativeMaterialTextures.finishStitch();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void afterBake(ModelBakeEvent event) {
        TmtDiagnostics.captureModels(event.getModelRegistry());
    }

    @SubscribeEvent
    public void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END && !Minecraft.getMinecraft().isGamePaused()) {
            NativeMaterialTextures.tick();
        }
    }

    @SubscribeEvent
    public void renderTick(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.START) TmtDiagnostics.frame();
    }

    @SubscribeEvent
    public void unload(WorldEvent.Unload event) {
        if (!event.getWorld().isRemote) return;
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.isCallingFromMinecraftThread()) clearWorld();
        else minecraft.addScheduledTask(RenderLifecycle::clearWorld);
    }

    private static void clearWorld() {
        clearModels();
        // 已烘焙的主模型仍可能被再次使用，保留弱 recipe 登记以便重新驻留。
        NativeMaterialTextures.clearGpu();
    }

    private static void clearModels() {
        NativeToolModelCaches.clear();
        TmtLazyMaterialModel.clearCache();
        TmtMaskModelCache.clearCache();
        TableItemModels.clear();
        TmtNativeQuadRenderer.clearCache();
        TmtArmorTextures.clear();
    }
}

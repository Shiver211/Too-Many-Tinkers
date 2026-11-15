package com.smd.toomanytinkers.mixin.client.tconstruct;

import com.smd.toomanytinkers.client.render.TableItemRenderer;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import slimeknights.tconstruct.shared.tileentity.TileTable;

@Mixin(TileEntityRendererDispatcher.class)
public abstract class TableRendererDispatcherMixin {
    @Inject(method = "getRenderer(Ljava/lang/Class;)Lnet/minecraft/client/renderer/tileentity/TileEntitySpecialRenderer;",
            at = @At("RETURN"), cancellable = true)
    private void tmt$tableItems(Class<? extends TileEntity> type, CallbackInfoReturnable<TileEntitySpecialRenderer<?>> cir) {
        // RenderLib 首次查询后会缓存 renderer；空台面也必须取得同一个包装器。
        // 是否有动态物品在 render 时判断，浇筑完成后不依赖重新查询 renderer。
        if (TileTable.class.isAssignableFrom(type)) {
            cir.setReturnValue(TableItemRenderer.wrap(cir.getReturnValue(), (TileEntityRendererDispatcher) (Object) this));
        }
    }
}

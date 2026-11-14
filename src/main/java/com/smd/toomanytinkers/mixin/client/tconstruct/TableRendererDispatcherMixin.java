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
    @Inject(method = "getRenderer(Lnet/minecraft/tileentity/TileEntity;)Lnet/minecraft/client/renderer/tileentity/TileEntitySpecialRenderer;",
            at = @At("RETURN"), cancellable = true)
    private void tmt$tableItems(TileEntity tile, CallbackInfoReturnable<TileEntitySpecialRenderer<?>> cir) {
        if (tile instanceof TileTable table && TableItemRenderer.hasDynamicItems(table)) {
            cir.setReturnValue(TableItemRenderer.wrap(cir.getReturnValue(), (TileEntityRendererDispatcher) (Object) this));
        }
    }
}

package com.smd.toomanytinkers.mixin.client.tconstruct;

import com.smd.toomanytinkers.client.model.TableItemModels;
import com.smd.toomanytinkers.client.render.TableItemRenderer;
import net.minecraft.client.renderer.block.model.IBakedModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import slimeknights.tconstruct.shared.block.PropertyTableItem.TableItem;
import slimeknights.tconstruct.shared.client.BakedTableModel;

@Mixin(value = BakedTableModel.class, remap = false)
public abstract class BakedTableModelMixin {
    @Inject(method = "getModelForTableItem", at = @At("HEAD"), cancellable = true)
    private void tmt$deferDynamicItem(TableItem item, CallbackInfoReturnable<IBakedModel> cir) {
        // 区块网格只绑定主图集；动态 UV 必须由 TableItemRenderer 在绘制时解析。
        if (TableItemRenderer.isDynamic(item)) cir.setReturnValue(TableItemModels.EMPTY);
    }
}

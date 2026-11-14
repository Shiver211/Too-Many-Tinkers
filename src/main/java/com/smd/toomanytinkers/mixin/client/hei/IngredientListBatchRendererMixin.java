package com.smd.toomanytinkers.mixin.client.hei;

import com.llamalad7.mixinextras.sugar.Local;
import com.smd.toomanytinkers.client.render.TmtNativeQuadRenderer;
import mezz.jei.gui.ingredients.IIngredientListElement;
import mezz.jei.render.IngredientListBatchRenderer;
import mezz.jei.render.IngredientListSlot;
import mezz.jei.render.IngredientRenderer;
import net.minecraft.client.renderer.block.model.IBakedModel;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(value = IngredientListBatchRenderer.class, remap = false)
public class IngredientListBatchRendererMixin {

    @Shadow @Final private List<IngredientRenderer> renderOther;

    @Inject(method = "set(Lmezz/jei/render/IngredientListSlot;Lmezz/jei/gui/ingredients/IIngredientListElement;)V",
            at = @At(value = "NEW", target = "mezz/jei/render/ItemStackFastRenderer"), cancellable = true)
    private <V> void tmt$renderManagedTextures(IngredientListSlot slot,
                                               IIngredientListElement<V> element,
                                               CallbackInfo ci, @Local IBakedModel model) {
        // 只检查 HEI 已选中的普通批量模型：保留模型获取异常处理、内置渲染和 ISlowRenderItem 分流。
        // 复用 HEI 已解析的模型，避免在 set 的 HEAD 再次求值附属模型覆盖。
        if (!TmtNativeQuadRenderer.modelContainsManaged(model)) {
            return;
        }

        IngredientRenderer<V> renderer = new IngredientRenderer<>(element);
        slot.setIngredientRenderer(renderer);
        renderOther.add(renderer);
        ci.cancel();
    }
}

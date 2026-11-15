package com.smd.toomanytinkers.client.render;

import com.smd.toomanytinkers.client.model.TableItemModels;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.common.property.IUnlistedProperty;
import slimeknights.tconstruct.common.config.Config;
import slimeknights.tconstruct.shared.block.BlockTable;
import slimeknights.tconstruct.shared.block.PropertyTableItem.TableItem;
import slimeknights.tconstruct.shared.block.PropertyTableItem.TableItems;
import slimeknights.tconstruct.shared.tileentity.TileTable;

import javax.annotation.Nullable;
import java.util.IdentityHashMap;
import java.util.Map;

/** 保留原 TESR（如浇铸液体），只补交不能烘焙进主图集区块网格的动态页物品。 */
public final class TableItemRenderer extends TileEntitySpecialRenderer<TileTable> {
    // key 是原 renderer（包括 null），数量由已注册 renderer 类型决定，不持有世界或方块实体。
    private static final Map<TileEntitySpecialRenderer<?>, TableItemRenderer> WRAPPERS = new IdentityHashMap<>();
    private final TileEntitySpecialRenderer<TileTable> original;
    private static long draws;

    private TableItemRenderer(@Nullable TileEntitySpecialRenderer<TileTable> original,
                              TileEntityRendererDispatcher dispatcher) {
        this.original = original;
        setRendererDispatcher(dispatcher);
    }

    @SuppressWarnings("unchecked")
    public static synchronized TileEntitySpecialRenderer<?> wrap(@Nullable TileEntitySpecialRenderer<?> original,
                                                                  TileEntityRendererDispatcher dispatcher) {
        if (original instanceof TableItemRenderer) return original;
        return WRAPPERS.computeIfAbsent(original,
                renderer -> new TableItemRenderer((TileEntitySpecialRenderer<TileTable>) renderer, dispatcher));
    }

    public static boolean isDynamic(TableItem item) {
        return item.model != null && TmtNativeQuadRenderer.modelContainsManaged(item.model);
    }

    @Nullable
    private static IExtendedBlockState displayState(TileTable table) {
        if (!Config.renderTableItems || table.getWorld() == null) return null;
        IBlockState base = table.getWorld().getBlockState(table.getPos());
        IBlockState state = base.getBlock().getExtendedState(base, table.getWorld(), table.getPos());
        return state instanceof IExtendedBlockState extended && extended.getUnlistedNames().contains(BlockTable.INVENTORY)
                ? extended : null;
    }

    @Override public void render(TileTable table, double x, double y, double z,
                                 float partialTicks, int destroyStage, float alpha) {
        if (original != null) original.render(table, x, y, z, partialTicks, destroyStage, alpha);
        IExtendedBlockState state = displayState(table);
        if (state == null) return;
        TableItems items = state.getValue(BlockTable.INVENTORY);
        if (items == null) return;
        EnumFacing facing = state.getUnlistedNames().contains(BlockTable.FACING)
                ? state.getValue((IUnlistedProperty<EnumFacing>) BlockTable.FACING) : null;
        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(x, y, z);
            for (TableItem item : items.items) {
                if (isDynamic(item) && TmtNativeQuadRenderer.renderTableItem(TableItemModels.placed(item, facing))) draws++;
            }
        } finally {
            GlStateManager.popMatrix();
        }
    }

    @Override public boolean isGlobalRenderer(TileTable table) {
        return original != null && original.isGlobalRenderer(table);
    }

    public static String stats() { return "tableItemDraws=" + draws; }
}

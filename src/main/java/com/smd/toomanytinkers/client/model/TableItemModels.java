package com.smd.toomanytinkers.client.model;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.block.model.ItemOverrideList;
import net.minecraft.client.renderer.block.model.SimpleBakedModel;
import net.minecraft.util.EnumFacing;
import slimeknights.mantle.client.model.TRSRBakedModel;
import slimeknights.tconstruct.shared.block.PropertyTableItem.TableItem;

import java.util.EnumMap;
import java.util.List;

/** 与 BakedTableModel 使用相同的摆放变换；独立提交仅改变纹理绑定的所有者。 */
public final class TableItemModels {
    public static final IBakedModel EMPTY = emptyModel();
    private static final WeightedLruCache<Placement, IBakedModel> MODELS =
            new WeightedLruCache<>(256, 4L * 1024 * 1024, TableItemModels::weight);

    private TableItemModels() { }

    public static IBakedModel placed(TableItem item, EnumFacing facing) {
        // TileTable 每次查询都会创建新的颜色包装器；按原 TableItem 的物品/NBT/摆放值复用。
        // 复制 stack，避免库存中的可变 NBT 改坏缓存键；没有 stack 的自定义模型按对象区分。
        TableItem snapshot = new TableItem(item.stack == null ? null : item.stack.copy(), null,
                item.x, item.y, item.z, item.s, item.r);
        Placement key = new Placement(snapshot, item.stack == null ? item.model : null, facing);
        IBakedModel cached = MODELS.get(key);
        if (cached != null) return cached;
        IBakedModel model = new TRSRBakedModel(item.model, item.x, item.y + 1, item.z,
                item.r, (float) Math.PI, 0, item.s);
        if (facing != null) model = new TRSRBakedModel(model, facing);
        // TRSRBakedModel 每次 getQuads 都会变换；这里保存已变换顶点，避免逐帧重做。
        EnumMap<EnumFacing, List<BakedQuad>> faces = new EnumMap<>(EnumFacing.class);
        for (EnumFacing side : EnumFacing.VALUES) faces.put(side, List.copyOf(model.getQuads(null, side, 0)));
        model = new SimpleBakedModel(List.copyOf(model.getQuads(null, null, 0)), faces,
                model.isAmbientOcclusion(), model.isGui3d(), model.getParticleTexture(),
                ItemCameraTransforms.DEFAULT, ItemOverrideList.NONE);
        MODELS.put(key, model);
        return model;
    }

    private static IBakedModel emptyModel() {
        EnumMap<EnumFacing, List<BakedQuad>> faces = new EnumMap<>(EnumFacing.class);
        for (EnumFacing side : EnumFacing.VALUES) faces.put(side, List.of());
        return new SimpleBakedModel(List.of(), faces, false, false, null, ItemCameraTransforms.DEFAULT, ItemOverrideList.NONE);
    }

    private static long weight(IBakedModel model) {
        long bytes = 256;
        for (BakedQuad quad : model.getQuads(null, null, 0)) bytes += 192L + 4L * quad.getVertexData().length;
        for (EnumFacing side : EnumFacing.VALUES) {
            for (BakedQuad quad : model.getQuads(null, side, 0)) bytes += 192L + 4L * quad.getVertexData().length;
        }
        return bytes;
    }

    public static void clear() { MODELS.clear(); }
    public static String stats() { return "tableItemModels=" + MODELS.size() + ", estimatedTableItemBytes=" + MODELS.weight(); }

    private record Placement(TableItem snapshot, IBakedModel modelWithoutStack, EnumFacing facing) { }
}

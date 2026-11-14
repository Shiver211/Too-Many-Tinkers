package com.smd.toomanytinkers.client.model;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.block.model.ItemOverrideList;
import net.minecraft.client.renderer.block.model.SimpleBakedModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.EnumFacing;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import slimeknights.mantle.client.model.TRSRBakedModel;
import slimeknights.tconstruct.shared.block.PropertyTableItem.TableItem;
import slimeknights.tconstruct.shared.client.BakedTableModel;

import java.util.EnumMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TableItemModelsTest {
    @AfterEach void clear() { TableItemModels.clear(); }

    @Test void matchesOriginalTableBakeForAllTableOrientations() throws Exception {
        IBakedModel source = fixture();
        TableItem item = new TableItem(null, source, .125f, -.46875f, -.25f, .8f, (float) Math.PI / 2);
        BakedTableModel nativeTable = new BakedTableModel(TableItemModels.EMPTY, null, DefaultVertexFormats.ITEM);
        var bake = BakedTableModel.class.getDeclaredMethod("getModelForTableItem", TableItem.class);
        bake.setAccessible(true);
        IBakedModel nativePlacement = (IBakedModel) bake.invoke(nativeTable, item);
        // 对照实际依赖的私有烘焙入口，而不是在测试里重写同一套位移/旋转公式。
        for (EnumFacing facing : EnumFacing.HORIZONTALS) {
            IBakedModel expected = new TRSRBakedModel(nativePlacement, facing);
            IBakedModel actual = TableItemModels.placed(item, facing);
            compare(expected.getQuads(null, null, 0), actual.getQuads(null, null, 0));
            for (EnumFacing side : EnumFacing.VALUES) compare(expected.getQuads(null, side, 0), actual.getQuads(null, side, 0));
        }
        compare(nativePlacement.getQuads(null, null, 0), TableItemModels.placed(item, null).getQuads(null, null, 0));
    }

    @Test void placementSnapshotTracksMutableOffsetsAndClearsOnReload() {
        TableItem item = new TableItem(null, fixture());
        IBakedModel original = TableItemModels.placed(item, EnumFacing.NORTH);
        List<BakedQuad> quads = original.getQuads(null, null, 0);
        int[] vertices = quads.get(0).getVertexData().clone();
        assertSame(original, TableItemModels.placed(item, EnumFacing.NORTH));
        assertSame(quads, original.getQuads(null, null, 0));
        item.y += .25f;
        assertNotSame(original, TableItemModels.placed(item, EnumFacing.NORTH));
        assertArrayEquals(vertices, original.getQuads(null, null, 0).get(0).getVertexData());
        item.y -= .25f;
        TableItemModels.clear();
        assertNotSame(original, TableItemModels.placed(item, EnumFacing.NORTH));
    }

    @Test void deferredChunkModelHasEmptyListsForEveryFace() {
        assertTrue(TableItemModels.EMPTY.getQuads(null, null, 0).isEmpty());
        for (EnumFacing side : EnumFacing.VALUES) assertTrue(TableItemModels.EMPTY.getQuads(null, side, 0).isEmpty());
    }

    private static void compare(List<BakedQuad> expected, List<BakedQuad> actual) {
        assertEquals(expected.size(), actual.size());
        for (int i = 0; i < expected.size(); i++) {
            assertArrayEquals(expected.get(i).getVertexData(), actual.get(i).getVertexData());
            assertSame(expected.get(i).getSprite(), actual.get(i).getSprite());
            assertEquals(expected.get(i).getFace(), actual.get(i).getFace());
        }
    }

    private static IBakedModel fixture() {
        TextureAtlasSprite sprite = new TextureAtlasSprite("test:table_item") { };
        int[] data = new int[28];
        float[][] positions = {{0, 0, 0}, {1, 0, 0}, {1, 1, 0}, {0, 1, 0}};
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 3; j++) data[i * 7 + j] = Float.floatToRawIntBits(positions[i][j]);
            data[i * 7 + 3] = -1;
            data[i * 7 + 4] = Float.floatToRawIntBits(positions[i][0]);
            data[i * 7 + 5] = Float.floatToRawIntBits(positions[i][1]);
            data[i * 7 + 6] = 0x7f0000;
        }
        BakedQuad quad = new BakedQuad(data, -1, EnumFacing.SOUTH, sprite, false, DefaultVertexFormats.ITEM);
        EnumMap<EnumFacing, List<BakedQuad>> faces = new EnumMap<>(EnumFacing.class);
        for (EnumFacing side : EnumFacing.VALUES) faces.put(side, List.of());
        return new SimpleBakedModel(List.of(quad), faces, false, false, sprite, ItemCameraTransforms.DEFAULT, ItemOverrideList.NONE);
    }
}

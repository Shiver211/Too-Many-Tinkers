package com.smd.toomanytinkers.client.render;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.block.model.ItemOverrideList;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.EnumFacing;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class TmtNativeQuadRendererTest {
    @Test void modelWithoutQuadListsStaysOnItsOriginalRenderPath() {
        // 外部模型未提供普通几何时，检测应交回原渲染路径。
        IBakedModel model = new Fixture(side -> null);
        assertFalse(TmtNativeQuadRenderer.modelContainsManaged(model));
        assertFalse(TmtNativeQuadRenderer.renderAvaritiaModel(model, null, 1f));
    }

    @Test void absentFaceListsDoNotAbortInspectionOfRemainingFaces() {
        List<EnumFacing> queried = new ArrayList<>();
        IBakedModel model = new Fixture(side -> {
            queried.add(side);
            // 对应崩溃栈：通用面可用，但某个方向的面列表为空引用。
            return side == null || side == EnumFacing.EAST ? List.of() : null;
        });
        assertFalse(TmtNativeQuadRenderer.modelContainsManaged(model));
        List<EnumFacing> expected = new ArrayList<>();
        expected.add(null);
        expected.addAll(Arrays.asList(EnumFacing.VALUES));
        assertEquals(expected, queried);
    }

    private record Fixture(Function<EnumFacing, List<BakedQuad>> quads) implements IBakedModel {
        @Override public List<BakedQuad> getQuads(IBlockState state, EnumFacing side, long rand) {
            return quads.apply(side);
        }
        @Override public boolean isAmbientOcclusion() { return false; }
        @Override public boolean isGui3d() { return false; }
        @Override public boolean isBuiltInRenderer() { return false; }
        @Override public TextureAtlasSprite getParticleTexture() { return null; }
        @Override public ItemCameraTransforms getItemCameraTransforms() { return ItemCameraTransforms.DEFAULT; }
        @Override public ItemOverrideList getOverrides() { return ItemOverrideList.NONE; }
    }
}

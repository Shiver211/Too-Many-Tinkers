package com.smd.toomanytinkers.client.model;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.block.model.ItemOverrideList;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.EnumFacing;

import java.util.List;

/** 可选模组的最小测试模型：字段契约取自报告对应版本，任何 quad 查询均使测试失败。 */
public abstract class InspectionModelFixture implements IBakedModel {
    @Override public abstract List<BakedQuad> getQuads(IBlockState state, EnumFacing side, long rand);
    @Override public boolean isAmbientOcclusion() { return false; }
    @Override public boolean isGui3d() { return false; }
    @Override public boolean isBuiltInRenderer() { return false; }
    @Override public TextureAtlasSprite getParticleTexture() { return null; }
    @Override public ItemCameraTransforms getItemCameraTransforms() { return ItemCameraTransforms.DEFAULT; }
    @Override public ItemOverrideList getOverrides() { return ItemOverrideList.NONE; }

    protected List<BakedQuad> unexpectedQuery() {
        throw new AssertionError("检测或计重不应调用此模型的 getQuads：" + getClass().getName());
    }
}

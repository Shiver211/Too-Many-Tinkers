package dev.beecube31.crazyae2.client.rendering.models.baked;

import com.smd.toomanytinkers.client.model.InspectionModelFixture;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.util.EnumFacing;

import java.util.List;

public class PuredaisyEncodedPatternBakedModel extends InspectionModelFixture {
    private final IBakedModel baseModel;
    public PuredaisyEncodedPatternBakedModel(IBakedModel baseModel) { this.baseModel = baseModel; }
    @Override public List<BakedQuad> getQuads(IBlockState state, EnumFacing side, long rand) {
        return unexpectedQuery();
    }
}

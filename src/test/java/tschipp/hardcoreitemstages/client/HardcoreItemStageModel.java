package tschipp.hardcoreitemstages.client;

import com.smd.toomanytinkers.client.model.InspectionModelFixture;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.util.EnumFacing;

import java.util.List;

public class HardcoreItemStageModel extends InspectionModelFixture {
    private final IBakedModel originalModel;
    public HardcoreItemStageModel(IBakedModel originalModel) { this.originalModel = originalModel; }
    @Override public List<BakedQuad> getQuads(IBlockState state, EnumFacing side, long rand) {
        return unexpectedQuery();
    }
}

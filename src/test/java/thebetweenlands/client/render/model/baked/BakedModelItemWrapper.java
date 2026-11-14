package thebetweenlands.client.render.model.baked;

import com.smd.toomanytinkers.client.model.InspectionModelFixture;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.util.EnumFacing;

import java.util.List;

public class BakedModelItemWrapper extends InspectionModelFixture {
    private final IBakedModel transformsModel;
    private final IBakedModel bakedQuadModel;
    public BakedModelItemWrapper(IBakedModel transformsModel, IBakedModel bakedQuadModel) {
        this.transformsModel = transformsModel;
        this.bakedQuadModel = bakedQuadModel;
    }
    @Override public List<BakedQuad> getQuads(IBlockState state, EnumFacing side, long rand) {
        return unexpectedQuery();
    }
}

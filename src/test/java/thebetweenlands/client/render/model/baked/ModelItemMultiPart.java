package thebetweenlands.client.render.model.baked;

import com.smd.toomanytinkers.client.model.InspectionModelFixture;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.util.EnumFacing;

import java.util.List;
import java.util.Map;

public final class ModelItemMultiPart {
    public static class BakedItemMultiPartModel extends InspectionModelFixture {
        private final List<IBakedModel> models;
        private final Map<?, IBakedModel> bakedModelsMapping;
        public BakedItemMultiPartModel(List<IBakedModel> models, Map<?, IBakedModel> bakedModelsMapping) {
            this.models = models;
            this.bakedModelsMapping = bakedModelsMapping;
        }
        @Override public List<BakedQuad> getQuads(IBlockState state, EnumFacing side, long rand) {
            return unexpectedQuery();
        }
    }
}

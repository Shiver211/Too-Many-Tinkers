package cubex2.mods.chesttransporter.client;

import com.smd.toomanytinkers.client.model.InspectionModelFixture;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.util.EnumFacing;

import java.util.List;
import java.util.Map;

public class BakedModelCH extends InspectionModelFixture {
    private final IBakedModel handle;
    private IBakedModel toUse;
    private final Map<?, IBakedModel> chestModels;
    public BakedModelCH(IBakedModel handle, IBakedModel toUse, Map<?, IBakedModel> chestModels) {
        this.handle = handle;
        this.toUse = toUse;
        this.chestModels = chestModels;
    }
    public void select(IBakedModel model) { toUse = model; }
    @Override public List<BakedQuad> getQuads(IBlockState state, EnumFacing side, long rand) {
        return unexpectedQuery();
    }
}

package pl.asie.foamfix.client;

import com.smd.toomanytinkers.client.model.InspectionModelFixture;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.EnumFacing;

import java.lang.ref.SoftReference;
import java.util.List;

/** 仅供测试的 FoamFix 0.10.15 字段契约，不打入发布 JAR。 */
public final class FoamyItemLayerModel {
    public static class DynamicItemModel extends InspectionModelFixture {
        private final List<TextureAtlasSprite> textures;
        public DynamicItemModel(List<TextureAtlasSprite> textures) { this.textures = textures; }
        @Override public List<BakedQuad> getQuads(IBlockState state, EnumFacing side, long rand) {
            return unexpectedQuery();
        }
    }

    public static class Dynamic3DItemModel extends InspectionModelFixture {
        private final DynamicItemModel parent;
        private SoftReference<List<BakedQuad>> quadsSoft;
        public Dynamic3DItemModel(List<TextureAtlasSprite> textures) { parent = new DynamicItemModel(textures); }
        public SoftReference<List<BakedQuad>> cache() { return quadsSoft; }
        public void cache(SoftReference<List<BakedQuad>> value) { quadsSoft = value; }
        @Override public List<BakedQuad> getQuads(IBlockState state, EnumFacing side, long rand) {
            return unexpectedQuery();
        }
    }

    public static class Static3DItemModel extends InspectionModelFixture {
        private final List<TextureAtlasSprite> textures;
        private SoftReference<List<BakedQuad>> quadsSoft;
        public Static3DItemModel(List<TextureAtlasSprite> textures) { this.textures = textures; }
        public SoftReference<List<BakedQuad>> cache() { return quadsSoft; }
        public void cache(SoftReference<List<BakedQuad>> value) { quadsSoft = value; }
        @Override public List<BakedQuad> getQuads(IBlockState state, EnumFacing side, long rand) {
            return unexpectedQuery();
        }
    }
}

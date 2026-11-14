package com.smd.toomanytinkers.client.render;

import com.smd.toomanytinkers.client.model.WeightedLruCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldVertexBufferUploader;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import org.lwjgl.opengl.GL11;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

import net.minecraftforge.client.model.pipeline.LightUtil;

/**
 * 只替换原生 quad 的纹理绑定与 UV。保留调用方的矩阵、光照、透明状态和 shader。
 * 按原顺序合并相邻同页图层，不跨透明图层排序。
 */
public final class TmtNativeQuadRenderer {
    private static final WorldVertexBufferUploader EXTERNAL_BUFFER_UPLOADER = new WorldVertexBufferUploader();
    private static final WeightedLruCache<RemapKey, BakedQuad> REMAPPED =
            new WeightedLruCache<>(8192, 8L * 1024 * 1024,
                    quad -> 192L + (long) quad.getVertexData().length * Integer.BYTES);
    private static long draws;

    private TmtNativeQuadRenderer() { }

    public static boolean renderItemQuads(BufferBuilder buffer, List<BakedQuad> quads, int color, ItemStack stack) {
        // 闪光 pass 使用独立纹理；普通空 stack 绘制仍可使用材质页。
        if (GlintRenderScope.isActive() || !containsManaged(quads)) {
            return false;
        }
        // Forge 已在进入 renderQuads 前设置本段的发光/漫反射状态。
        // 先提交调用方先前写入的主图集顶点，返回时恢复一个仍在写入的空 buffer。
        VertexFormat format = buffer.getVertexFormat();
        drawBuffer(buffer);
        try {
            drawLists(List.of(quads), color, stack, 1f, false, buffer, format);
        } finally {
            buffer.begin(GL11.GL_QUADS, format);
        }
        return true;
    }

    public static boolean renderAvaritiaModel(IBakedModel model, ItemStack stack, float alpha) {
        return render(model, -1, stack, alpha, true);
    }

    /** 台面物品已由原生 BakedColoredItemModel 染色、TRSRBakedModel 摆放，不再次应用物品变换。 */
    public static boolean renderTableItem(IBakedModel model) {
        return render(model, -1, ItemStack.EMPTY, 1f, false);
    }

    public static boolean modelContainsManaged(IBakedModel model) {
        if (containsManaged(model.getQuads(null, null, 0L))) {
            return true;
        }
        for (EnumFacing side : EnumFacing.VALUES) {
            if (containsManaged(model.getQuads(null, side, 0L))) {
                return true;
            }
        }
        return false;
    }

    private static boolean render(IBakedModel model, int color, ItemStack stack,
                                  float alpha, boolean avaritia) {
        List<List<BakedQuad>> lists = new ArrayList<>(7);
        boolean managed = false;
        for (EnumFacing side : EnumFacing.VALUES) {
            List<BakedQuad> quads = model.getQuads(null, side, 0L);
            if (quads != null) lists.add(quads);
            managed |= containsManaged(quads);
        }
        List<BakedQuad> general = model.getQuads(null, null, 0L);
        if (general != null) lists.add(general);
        managed |= containsManaged(general);
        if (!managed) {
            return false;
        }
        drawLists(lists, color, stack, alpha, avaritia,
                Tessellator.getInstance().getBuffer(), DefaultVertexFormats.ITEM);
        return true;
    }

    private static void drawLists(List<List<BakedQuad>> lists, int color, ItemStack stack,
                                   float alpha, boolean avaritia, BufferBuilder buffer, VertexFormat format) {
        // 仅查询本函数确实改变的绑定，不查询或重置整套 OpenGL 状态。
        int originalTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int atlasTexture = Minecraft.getMinecraft().getTextureMapBlocks().getGlTextureId();
        int boundTexture = -1;
        boolean begun = false;
        TextureAtlasSprite previousSprite = null;
        NativeMaterialTextures.TextureBinding previousBinding = null;
        NativeMaterialTextures.beginDraw();
        try {
            for (List<BakedQuad> quads : lists) {
                for (BakedQuad quad : quads) {
                    TextureAtlasSprite sprite = quad.getSprite();
                    if (sprite != previousSprite) {
                        previousBinding = NativeMaterialTextures.binding(sprite);
                        previousSprite = sprite;
                    }
                    NativeMaterialTextures.TextureBinding binding = previousBinding;
                    int texture = binding == null ? atlasTexture : binding.texId();
                    if (texture != boundTexture) {
                        if (begun) {
                            // binding() 可能刚上传另一页；先恢复待提交批次的页。
                            GlStateManager.bindTexture(boundTexture);
                            drawBuffer(buffer);
                            draws++;
                        }
                        GlStateManager.bindTexture(texture);
                        boundTexture = texture;
                        buffer.begin(GL11.GL_QUADS, format);
                        begun = true;
                    }
                    BakedQuad drawQuad = binding == null ? quad : remap(quad, binding);
                    LightUtil.renderQuadColor(buffer, drawQuad, quadColor(quad, color, stack, alpha, avaritia));
                }
            }
            if (begun) {
                GlStateManager.bindTexture(boundTexture);
                drawBuffer(buffer);
                draws++;
            }
        } finally {
            try {
                NativeMaterialTextures.endDraw();
            } finally {
                GlStateManager.bindTexture(originalTexture);
            }
        }
    }

    private static void drawBuffer(BufferBuilder buffer) {
        Tessellator tessellator = Tessellator.getInstance();
        if (buffer == tessellator.getBuffer()) {
            tessellator.draw();
        } else {
            // RenderItem.renderQuads 是公共入口，附属可传入自己的 BufferBuilder。
            buffer.finishDrawing();
            EXTERNAL_BUFFER_UPLOADER.draw(buffer);
        }
    }

    private static int quadColor(BakedQuad quad, int color, ItemStack stack, float alpha, boolean avaritia) {
        int result = color;
        if (color == -1 && quad.hasTintIndex() && !stack.isEmpty()) {
            result = Minecraft.getMinecraft().getItemColors().colorMultiplier(stack, quad.getTintIndex());
            if (EntityRenderer.anaglyphEnable) {
                result = TextureUtil.anaglyphColor(result);
            }
            if (!avaritia) {
                result |= 0xFF000000;
            }
        }
        // Avaritia WrappedItemRenderer 的原始 alpha 合并规则，包括未染色 quad。
        return avaritia ? result | (((int) (alpha * 255f) & 255) << 24) : result;
    }

    private static boolean containsManaged(@Nullable List<BakedQuad> quads) {
        // 附属模型可能用 null 表示某个方向没有普通几何；不改变其原渲染入口。
        if (quads == null) return false;
        for (BakedQuad quad : quads) {
            if (NativeMaterialTextures.isManaged(quad.getSprite())) {
                return true;
            }
        }
        return false;
    }

    private static BakedQuad remap(BakedQuad source, NativeMaterialTextures.TextureBinding binding) {
        RemapKey key = new RemapKey(source, binding.u0(), binding.v0(), binding.uScale(), binding.vScale());
        BakedQuad cached = REMAPPED.get(key);
        if (cached != null) {
            return cached;
        }
        VertexFormat format = source.getFormat();
        int[] data = QuadUvRemapper.remap(source.getVertexData(), format.getIntegerSize(),
                format.getUvOffsetById(0) / Integer.BYTES,
                binding.u0(), binding.v0(), binding.uScale(), binding.vScale());
        BakedQuad result = new BakedQuad(data, source.getTintIndex(), source.getFace(), source.getSprite(),
                source.shouldApplyDiffuseLighting(), format);
        REMAPPED.put(key, result);
        return result;
    }

    public static void clearCache() {
        REMAPPED.clear();
    }

    public static String stats() {
        return "remappedQuads=" + REMAPPED.size() + ", estimatedRemapBytes=" + REMAPPED.weight()
                + ", nativePageDraws=" + draws;
    }

    private record RemapKey(BakedQuad source, float u0, float v0, float uScale, float vScale) { }
}

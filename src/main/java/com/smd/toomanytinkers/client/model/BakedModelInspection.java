package com.smd.toomanytinkers.client.model;

import com.smd.toomanytinkers.TooManyTinkers;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.EnumFacing;

import java.lang.ref.Reference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/** 检测已有纹理，不为检测唤醒 FoamFix 的像素挤出缓存；实际绘制仍使用原模型。 */
public final class BakedModelInspection {
    // 从接口签名取名，同时适用于开发环境 MCP 名与游戏运行时 SRG 名。
    private static final String QUADS_METHOD = quadsMethodName();
    private static final Access QUADS = new Access(Kind.QUADS);
    // 只缓存类的读取方式，不持有模型、sprite 或检测结果。
    private static final ClassValue<Access> ACCESS = new ClassValue<>() {
        @Override protected Access computeValue(Class<?> type) {
            try {
                // 子类若改写 getQuads，不能仅凭父类类型跳过它新增/替换的几何。
                Class<?> owner = type.getMethod(QUADS_METHOD, IBlockState.class, EnumFacing.class, long.class)
                        .getDeclaringClass();
                return switch (owner.getName()) {
                    case "pl.asie.foamfix.client.FoamyItemLayerModel$Dynamic3DItemModel" -> {
                        Field parent = field(owner, "parent");
                        yield new Access(Kind.FOAM_DYNAMIC, field(owner, "quadsSoft"), parent,
                                field(parent.getType(), "textures"));
                    }
                    case "pl.asie.foamfix.client.FoamyItemLayerModel$Static3DItemModel" ->
                            new Access(Kind.FOAM_STATIC, field(owner, "quadsSoft"), field(owner, "textures"));
                    case "tschipp.hardcoreitemstages.client.HardcoreItemStageModel",
                         "net.minecraftforge.client.model.BakedModelWrapper" ->
                            new Access(Kind.DELEGATE, field(owner, "originalModel"));
                    case "net.minecraftforge.client.model.PerspectiveMapWrapper",
                         "slimeknights.mantle.client.model.BakedWrapper" ->
                            new Access(Kind.DELEGATE, field(owner, "parent"));
                    case "slimeknights.tconstruct.shared.client.BakedColoredItemModel" ->
                            new Access(Kind.COLORED, field(owner, "coloredQuads"),
                                    field(owner, "coloredDefaultQuads"), field(owner.getSuperclass(), "parent"));
                    case "dev.beecube31.crazyae2.client.rendering.models.baked.PuredaisyEncodedPatternBakedModel" ->
                            new Access(Kind.DELEGATE, field(owner, "baseModel"));
                    case "cubex2.mods.chesttransporter.client.BakedModelCH" ->
                            new Access(Kind.CHEST, field(owner, "handle"), field(owner, "toUse"));
                    case "thebetweenlands.client.render.model.baked.BakedModelItemWrapper" ->
                            new Access(Kind.DELEGATE, field(owner, "bakedQuadModel"));
                    case "thebetweenlands.client.render.model.baked.ModelItemMultiPart$BakedItemMultiPartModel" ->
                            new Access(Kind.MULTIPART, field(owner, "models"));
                    default -> QUADS;
                };
            } catch (ReflectiveOperationException | SecurityException exception) {
                // 可选模组版本变动时保留原检测语义，每个类仅在解析失败时报告一次。
                TooManyTinkers.LOGGER.warn("TMT 无法直接检查模型纹理，使用原 quad 检测：{}", type.getName(), exception);
                return QUADS;
            }
        }
    };

    private BakedModelInspection() { }

    public static boolean containsTexture(IBakedModel model, Predicate<TextureAtlasSprite> matches) {
        return containsTexture(model, matches, Collections.newSetFromMap(new IdentityHashMap<>()));
    }

    private static boolean containsTexture(IBakedModel model, Predicate<TextureAtlasSprite> matches,
                                           Set<IBakedModel> visited) {
        if (model == null || !visited.add(model)) return false;
        Access access = ACCESS.get(model.getClass());
        try {
            switch (access.kind) {
                case FOAM_DYNAMIC, FOAM_STATIC -> {
                    List<BakedQuad> cached = access.cachedQuads(model);
                    if (cached != null) return containsQuads(cached, matches);
                    // FoamFix 连 side != null 的查询也会先挤出网格，不能调用任何 getQuads。
                    for (TextureAtlasSprite sprite : access.textures(model)) {
                        if (matches.test(sprite)) return true;
                    }
                    return false;
                }
                case DELEGATE -> {
                    return containsTexture((IBakedModel) access.fields[0].get(model), matches, visited);
                }
                case CHEST -> {
                    // 只看当前使用的箱子；chestModels 保存的其他变体不参与本次绘制。
                    return containsTexture((IBakedModel) access.fields[0].get(model), matches, visited)
                            || containsTexture((IBakedModel) access.fields[1].get(model), matches, visited);
                }
                case MULTIPART -> {
                    List<?> models = (List<?>) access.fields[0].get(model);
                    if (models != null) {
                        for (Object child : models) {
                            if (containsTexture((IBakedModel) child, matches, visited)) return true;
                        }
                    }
                    return false;
                }
                case COLORED -> {
                    Map<?, ?> faces = (Map<?, ?>) access.fields[0].get(model);
                    if (faces.isEmpty()) {
                        return containsTexture((IBakedModel) access.fields[2].get(model), matches, visited);
                    }
                    if (containsQuads(asQuads(access.fields[1].get(model)), matches)) return true;
                    for (Object quads : faces.values()) {
                        if (containsQuads(asQuads(quads), matches)) return true;
                    }
                    return false;
                }
                case QUADS -> { }
            }
        } catch (IllegalAccessException exception) {
            // 字段已在解析时 setAccessible；若运行环境后来限制访问，仍保留原行为。
        }
        if (containsQuads(model.getQuads(null, null, 0L), matches)) return true;
        for (EnumFacing side : EnumFacing.VALUES) {
            if (containsQuads(model.getQuads(null, side, 0L), matches)) return true;
        }
        return false;
    }

    /** 部件 LRU 只估计已有通用面；未挤出的 FoamFix 模型采用小额估值，不提前创建网格。 */
    public static long estimatedWeight(IBakedModel model) {
        Access access = ACCESS.get(model.getClass());
        List<BakedQuad> quads;
        if (access.kind == Kind.FOAM_DYNAMIC || access.kind == Kind.FOAM_STATIC) {
            try {
                quads = access.cachedQuads(model);
            } catch (IllegalAccessException exception) {
                return 512L;
            }
            if (quads == null) return 512L;
        } else {
            quads = model.getQuads(null, null, 0L);
        }
        long bytes = 256L;
        if (quads != null) {
            for (BakedQuad quad : quads) {
                bytes += 192L + (long) quad.getVertexData().length * Integer.BYTES;
            }
        }
        return bytes;
    }

    private static boolean containsQuads(List<BakedQuad> quads, Predicate<TextureAtlasSprite> matches) {
        if (quads == null) return false;
        for (BakedQuad quad : quads) {
            if (matches.test(quad.getSprite())) return true;
        }
        return false;
    }

    private static Field field(Class<?> owner, String name) throws NoSuchFieldException {
        Field result = owner.getDeclaredField(name);
        result.setAccessible(true);
        return result;
    }

    private static String quadsMethodName() {
        for (Method method : IBakedModel.class.getMethods()) {
            if (Arrays.equals(method.getParameterTypes(),
                    new Class<?>[]{IBlockState.class, EnumFacing.class, long.class})) return method.getName();
        }
        throw new IllegalStateException("IBakedModel 缺少 quad 查询方法");
    }

    @SuppressWarnings("unchecked")
    private static List<BakedQuad> asQuads(Object value) { return (List<BakedQuad>) value; }

    private enum Kind { QUADS, FOAM_DYNAMIC, FOAM_STATIC, DELEGATE, CHEST, MULTIPART, COLORED }

    private record Access(Kind kind, Field... fields) {
        List<BakedQuad> cachedQuads(IBakedModel model) throws IllegalAccessException {
            Reference<?> reference = (Reference<?>) fields[0].get(model);
            return reference == null ? null : asQuads(reference.get());
        }

        @SuppressWarnings("unchecked")
        List<TextureAtlasSprite> textures(IBakedModel model) throws IllegalAccessException {
            return kind == Kind.FOAM_DYNAMIC
                    ? (List<TextureAtlasSprite>) fields[2].get(fields[1].get(model))
                    : (List<TextureAtlasSprite>) fields[1].get(model);
        }
    }
}

package com.smd.toomanytinkers.client.render;

import com.smd.toomanytinkers.TooManyTinkers;
import com.smd.toomanytinkers.client.texture.AnimationTimeline;
import com.smd.toomanytinkers.client.texture.NativeSpriteAnimationAccess;
import com.smd.toomanytinkers.client.texture.NativeSpriteLoader;
import com.smd.toomanytinkers.client.texture.NativeAtlasMaterial;
import com.smd.toomanytinkers.client.texture.SpritePlacement;
import com.smd.toomanytinkers.client.texture.PixelContentKey;
import com.smd.toomanytinkers.client.texture.TexturePageLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.data.AnimationMetadataSection;
import net.minecraft.util.ResourceLocation;
import slimeknights.tconstruct.library.TinkerRegistry;
import slimeknights.tconstruct.library.client.MaterialRenderInfo;
import slimeknights.tconstruct.library.materials.Material;
import org.lwjgl.opengl.GL11;

import javax.annotation.Nullable;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Function;

/**
 * 原生材质输出的按需所有者。动态页模型保留规范化 UV 和弱登记的生成配方；
 * 依赖主图集采样的材质保留原生图集 UV。页淘汰后重新驻留只上传新矩形。
 */
public final class NativeMaterialTextures {
    private static final int PAGE_SIZE = 1024;
    private static final long GPU_BUDGET = 64L * 1024 * 1024;
    private static final long PREPARED_BUDGET = 16L * 1024 * 1024;
    private static final long ANIMATION_BUDGET = 32L * 1024 * 1024;
    private static final int ALIAS_LIMIT = 16_384;
    private static final Map<TextureAtlasSprite, Recipe> RECIPES = new WeakHashMap<>();
    private static final Map<Recipe, Resident> RESIDENT = boundedAliases();
    private static final Map<PixelContentKey, Resident> CONTENT = boundedAliases();
    private static final Set<String> ATLAS_MATERIAL_SPRITES = new HashSet<>();
    private static final LinkedHashMap<MaterialKey, Bundle> PREPARED = new LinkedHashMap<>(16, .75f, true);
    private static final List<Page> PAGES = new ArrayList<>();
    private static final ThreadLocal<List<TextureAtlasSprite>> AUXILIARY = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> LOOKUP_GUARD = ThreadLocal.withInitial(() -> false);
    private static final LinkedHashMap<ResourceLocation, Boolean> EXISTS = new LinkedHashMap<>(16, .75f, true);
    private static long preparedBytes;
    private static long animationBytes;
    private static long gpuBytes;
    private static long peakGpuBytes;
    private static long tick;
    private static long useCounter;
    private static long generated;
    private static long uploads;
    private static long evictions;
    private static long deduplicated;
    private static int nextPageId;
    private static int drawDepth;
    private static int generation;
    private static boolean atlasReady;
    private static long nativeAtlasPixelBytes;

    private NativeMaterialTextures() {}

    private static <K> Map<K, Resident> boundedAliases() {
        return new LinkedHashMap<>(16, .75f, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<K, Resident> eldest) {
                // 只忘记别名，不移动/释放页；以后可重新生成并通过内容摘要复用。
                return size() > ALIAS_LIMIT;
            }
        };
    }

    public static boolean isShared(Material material) {
        return material.renderInfo != null && material.renderInfo.getClass() == MaterialRenderInfo.Default.class;
    }

    public static boolean requiresNativeAtlas(Material material) {
        return material.renderInfo instanceof NativeAtlasMaterial;
    }

    /** 只注册有主图集采样契约的材质，加载/动画/上传仍由原生 TextureMap 执行。 */
    public static synchronized void registerAtlasMaterial(ResourceLocation base, Material material, TextureMap atlas) {
        List<TextureAtlasSprite> captured = new ArrayList<>();
        List<TextureAtlasSprite> previous = AUXILIARY.get();
        AUXILIARY.set(captured);
        try {
            TextureAtlasSprite primary = createPrimary(base, material);
            atlas.setTextureEntry(primary);
            ATLAS_MATERIAL_SPRITES.add(primary.getIconName());
            for (TextureAtlasSprite auxiliary : captured) {
                atlas.setTextureEntry(auxiliary);
                ATLAS_MATERIAL_SPRITES.add(auxiliary.getIconName());
            }
        } finally {
            if (previous == null) AUXILIARY.remove();
            else AUXILIARY.set(previous);
        }
    }

    public static synchronized void beginStitch() { atlasReady = false; }
    public static synchronized void finishStitch() {
        nativeAtlasPixelBytes = 0;
        TextureMap atlas = Minecraft.getMinecraft().getTextureMapBlocks();
        for (String name : ATLAS_MATERIAL_SPRITES) {
            TextureAtlasSprite sprite = atlasSprite(new ResourceLocation(name));
            if (name.equals(sprite.getIconName())) {
                nativeAtlasPixelBytes += mipBytes(sprite.getIconWidth(), sprite.getIconHeight(), atlas.getMipmapLevels());
            }
        }
        atlasReady = true;
        TooManyTinkers.LOGGER.info("TMT 主图集已就绪：generation={}, nativeAtlasSprites={}, nativeAtlasPixelBytes={}",
                generation, ATLAS_MATERIAL_SPRITES.size(), nativeAtlasPixelBytes);
    }

    public static synchronized MaterialTexture resolve(ResourceLocation base, String materialId) {
        Material gui = slimeknights.tconstruct.library.client.CustomTextureCreator.guiMaterial;
        return acquire(base, gui.identifier.equals(materialId) ? gui : TinkerRegistry.getMaterial(materialId));
    }

    /** 模型在首次 bake 时已保存未染色几何；共享材质不再次读取 PNG。 */
    public static synchronized MaterialTexture resolveForModel(ResourceLocation base, String materialId) {
        Material gui = slimeknights.tconstruct.library.client.CustomTextureCreator.guiMaterial;
        Material material = gui.identifier.equals(materialId) ? gui : TinkerRegistry.getMaterial(materialId);
        if (isShared(material) && !exists(new ResourceLocation(base + "_" + material.identifier))
                && selectBase(base, material.renderInfo).equals(base)) {
            TextureAtlasSprite sprite = atlasSprite(base);
            if (sprite.getIconName().equals(base.toString())) {
                return new MaterialTexture(sprite, true, material.renderInfo.getVertexColor(), () -> {});
            }
        }
        return acquire(base, material);
    }

    public static synchronized MaterialTexture acquire(ResourceLocation base, Material material) {
        ResourceLocation direct = new ResourceLocation(base + "_" + material.identifier);
        boolean override = exists(direct);
        MaterialRenderInfo info = material.renderInfo;
        ResourceLocation selected = override ? direct : selectBase(base, info);
        if (!requiresNativeAtlas(material) && (override || isShared(material))) {
            TextureAtlasSprite atlas = atlasSprite(selected);
            if (selected.toString().equals(atlas.getIconName())) {
                // 独立读取几何所需帧，不让模型烘焙依赖主图集的 CPU 保留策略。
                NativeSpriteLoader loader = loader();
                TextureAtlasSprite scratch = loader.source(selected);
                scratch.copyFrom(atlas);
                return new MaterialTexture(scratch, !override, info == null ? -1 : info.getVertexColor(),
                        () -> clearFrames(loader.ownedSprites()));
            }
        }
        MaterialKey key = new MaterialKey(base, material, generation);
        Bundle bundle = PREPARED.remove(key);
        if (bundle != null) preparedBytes -= bundle.bytes;
        else bundle = generate(key);
        Bundle acquired = bundle;
        acquired.leases++;
        return new MaterialTexture(acquired.primary, !override && info != null && info.useVertexColoring(),
                info == null ? -1 : info.getVertexColor(), () -> release(acquired));
    }

    /** 原始强化/盔甲贴图的临时帧，不要求主图集保留 CPU 数据。 */
    public static synchronized MaterialTexture acquireRaw(ResourceLocation texture) {
        NativeSpriteLoader loader = loader();
        TextureAtlasSprite sprite = loader.source(texture);
        TextureAtlasSprite atlas = atlasSprite(texture);
        if (atlas.getIconName().equals(texture.toString())) sprite.copyFrom(atlas);
        return new MaterialTexture(sprite, false, -1, () -> clearFrames(loader.ownedSprites()));
    }

    public static synchronized boolean isManaged(TextureAtlasSprite sprite) {
        Recipe recipe = RECIPES.get(sprite);
        return recipe != null && !recipe.inAtlas;
    }

    public static synchronized boolean hasRecipe(TextureAtlasSprite sprite) { return RECIPES.containsKey(sprite); }

    /** 仅作为已注册匠魂纹理的缺省查找，不将生成对象塞回主图集的强引用表。 */
    @Nullable
    public static synchronized TextureAtlasSprite lookupMaterialSprite(String fullName) {
        // StitcherCache 会在 atlas 尺寸和依赖帧未就绪时查询，不能返回虚拟 sprite。
        if (!atlasReady || LOOKUP_GUARD.get()) return null;
        LOOKUP_GUARD.set(true);
        try {
            return TmtCustomTextureBridge.lookupMaterialSprite(fullName);
        } finally {
            LOOKUP_GUARD.remove();
        }
    }

    private static TextureAtlasSprite atlasSprite(ResourceLocation location) {
        return directAtlasLookup(location, Minecraft.getMinecraft().getTextureMapBlocks()::getAtlasSprite);
    }

    /** 查源贴图时禁用按需回退，否则生成的材质可能被误认为自己的底图。 */
    @Nullable
    private static TextureAtlasSprite directAtlasLookup(ResourceLocation location, Function<String, TextureAtlasSprite> lookup) {
        boolean previous = LOOKUP_GUARD.get();
        LOOKUP_GUARD.set(true);
        try {
            return lookup.apply(location.toString());
        } finally {
            if (previous) LOOKUP_GUARD.set(true);
            else LOOKUP_GUARD.remove();
        }
    }

    /** 附属稍后才从原 sprite 烘焙轮廓时，临时恢复其帧；不改变对象类型和 UV。 */
    public static synchronized FrameLease prepareFrames(TextureAtlasSprite sprite) {
        Recipe recipe = RECIPES.get(sprite);
        if (sprite.getFrameCount() > 0) {
            Bundle prepared = recipe == null ? null : PREPARED.get(recipe.material);
            if (prepared != null && prepared.outputs.get(recipe.spriteName) == sprite) {
                PREPARED.remove(recipe.material);
                preparedBytes -= prepared.bytes;
                prepared.leases++;
                return new FrameLease(() -> release(prepared));
            }
            return new FrameLease(() -> {});
        }
        if (recipe == null) throw new IllegalArgumentException("非 TMT 管理的空帧 sprite：" + sprite.getIconName());
        Bundle bundle = generate(recipe.material);
        TextureAtlasSprite generatedSprite = bundle.outputs.get(recipe.spriteName);
        if (generatedSprite == null) {
            clearFrames(bundle.owned);
            throw new IllegalStateException("重建时未生成子纹理：" + recipe.spriteName);
        }
        List<int[][]> frames = new ArrayList<>(generatedSprite.getFrameCount());
        for (int i = 0; i < generatedSprite.getFrameCount(); i++) frames.add(generatedSprite.getFrameTextureData(i));
        sprite.setFramesTextureData(frames);
        return new FrameLease(() -> {
            sprite.clearFramesTextureData();
            clearFrames(bundle.owned);
        });
    }

    /** 由 TConEvo TextureMapHandler 的窄入口调用；无生成上下文时保持原行为。 */
    public static boolean captureAuxiliary(TextureAtlasSprite sprite) {
        List<TextureAtlasSprite> captured = AUXILIARY.get();
        if (captured == null) return false;
        captured.add(sprite);
        return true;
    }

    @Nullable
    public static synchronized TextureBinding binding(TextureAtlasSprite sprite) {
        Recipe recipe = RECIPES.get(sprite);
        if (recipe == null || recipe.inAtlas) return null;
        Resident resident = RESIDENT.get(recipe);
        if (resident == null) resident = makeResident(recipe);
        resident.page.lastUse = ++useCounter;
        if (drawDepth > 0) resident.page.pinned = true;
        TexturePageLayout.Region r = resident.region;
        return new TextureBinding(resident.page.location, resident.page.texture.getGlTextureId(),
                r.x() / (float) resident.page.width, r.y() / (float) resident.page.height,
                r.width() / (float) resident.page.width, r.height() / (float) resident.page.height);
    }

    public static synchronized void beginDraw() { drawDepth++; }

    public static synchronized void endDraw() {
        if (--drawDepth != 0) return;
        for (Page page : PAGES) page.pinned = false;
        while (gpuBytes > GPU_BUDGET || animationBytes > ANIMATION_BUDGET) {
            Page oldest = null;
            for (Page page : PAGES) if (oldest == null || page.lastUse < oldest.lastUse) oldest = page;
            if (oldest == null) break;
            evict(oldest);
        }
    }

    public static synchronized BufferedImage materialImage(ResourceLocation base, Material material) {
        try (MaterialTexture texture = acquire(base, material)) {
            TextureAtlasSprite sprite = texture.sprite();
            int[] pixels = firstFrame(sprite)[0];
            BufferedImage image = new BufferedImage(sprite.getIconWidth(), sprite.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
            image.setRGB(0, 0, image.getWidth(), image.getHeight(), pixels, 0, image.getWidth());
            return image;
        }
    }

    public static synchronized void tick() {
        tick++;
        if (animationBytes == 0) return;
        int previous = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try {
            for (Page page : PAGES) {
                if (page.animations.isEmpty()) continue;
                GlStateManager.bindTexture(page.texture.getGlTextureId());
                for (TextureAtlasSprite sprite : page.animations) sprite.updateAnimation();
            }
        } finally {
            GlStateManager.bindTexture(previous);
        }
    }

    public static synchronized void clearGpu() {
        for (Page page : PAGES) {
            clearFrames(page.animations);
            Minecraft.getMinecraft().getTextureManager().deleteTexture(page.location);
        }
        PAGES.clear();
        RESIDENT.clear();
        CONTENT.clear();
        gpuBytes = 0;
        animationBytes = 0;
        for (Bundle bundle : PREPARED.values()) clearFrames(bundle.owned);
        PREPARED.clear();
        preparedBytes = 0;
    }

    public static synchronized void reload() {
        atlasReady = false;
        clearGpu();
        RECIPES.clear();
        ATLAS_MATERIAL_SPRITES.clear();
        nativeAtlasPixelBytes = 0;
        EXISTS.clear();
        tick = 0;
        drawDepth = 0;
        generation++;
    }

    public static synchronized Stats stats() {
        return new Stats(PAGES.size(), RESIDENT.size(), gpuBytes, peakGpuBytes, preparedBytes, animationBytes,
                generated, uploads, evictions, deduplicated, ATLAS_MATERIAL_SPRITES.size(), nativeAtlasPixelBytes,
                generation, atlasReady);
    }

    private static TextureAtlasSprite createPrimary(ResourceLocation base, Material material) {
        ResourceLocation direct = new ResourceLocation(base + "_" + material.identifier);
        if (exists(direct)) return NativeSpriteLoader.raw(direct);
        MaterialRenderInfo info = material.renderInfo;
        if (info == null) throw new IllegalStateException("材质没有原生 renderInfo 或覆盖纹理：" + material.identifier);
        TextureAtlasSprite sprite = info.getTexture(selectBase(base, info), direct.toString());
        if (sprite == null) throw new IllegalStateException("材质原生 getTexture 返回 null：" + material.identifier);
        return sprite;
    }

    private static Bundle generate(MaterialKey key) {
        NativeSpriteLoader loader = loader();
        List<TextureAtlasSprite> captured = new ArrayList<>();
        TextureAtlasSprite primary;
        List<TextureAtlasSprite> previousCapture = AUXILIARY.get();
        AUXILIARY.set(captured);
        try {
            primary = createPrimary(key.base, key.material);
            loader.register(primary);
            for (TextureAtlasSprite sprite : captured) loader.register(sprite);
            loader.load(primary);
            for (int i = 0; i < captured.size(); i++) loader.load(captured.get(i));
        } catch (RuntimeException failure) {
            clearFrames(loader.ownedSprites());
            throw failure;
        } finally {
            if (previousCapture == null) AUXILIARY.remove();
            else AUXILIARY.set(previousCapture);
        }
        Map<String, TextureAtlasSprite> outputs = new LinkedHashMap<>();
        outputs.put(primary.getIconName(), primary);
        for (TextureAtlasSprite sprite : captured) outputs.put(sprite.getIconName(), sprite);
        for (TextureAtlasSprite sprite : outputs.values()) {
            TextureAtlasSprite registered = null;
            if (ATLAS_MATERIAL_SPRITES.contains(sprite.getIconName())) {
                registered = atlasSprite(new ResourceLocation(sprite.getIconName()));
                if (!registered.getIconName().equals(sprite.getIconName())) {
                    throw new IllegalStateException("主图集材质未成功加载：" + sprite.getIconName());
                }
            }
            boolean inAtlas = SpritePlacement.place(sprite, registered);
            RECIPES.put(sprite, new Recipe(key, sprite.getIconName(), inAtlas));
        }
        generated++;
        long bytes = 0;
        for (TextureAtlasSprite sprite : loader.ownedSprites()) bytes += frameBytes(sprite);
        return new Bundle(key, primary, outputs, new ArrayList<>(loader.ownedSprites()), bytes);
    }

    private static NativeSpriteLoader loader() {
        Minecraft mc = Minecraft.getMinecraft();
        TextureMap atlas = mc.getTextureMapBlocks();
        return new NativeSpriteLoader(mc.getResourceManager(), location -> {
            if (location.toString().equals(atlas.getMissingSprite().getIconName())) {
                return NativeSpriteLoader.recoverMissingSprite(atlas.getMissingSprite(),
                        TextureUtil.MISSING_TEXTURE_DATA, atlas.getMipmapLevels());
            }
            return directAtlasLookup(location, atlas::getTextureExtry);
        }, atlas.getMipmapLevels());
    }

    private static ResourceLocation selectBase(ResourceLocation base, @Nullable MaterialRenderInfo info) {
        if (info == null || info.getTextureSuffix() == null) return base;
        ResourceLocation suffixed = new ResourceLocation(base + "_" + info.getTextureSuffix());
        if (exists(suffixed)) return suffixed;
        TextureMap atlas = Minecraft.getMinecraft().getTextureMapBlocks();
        return directAtlasLookup(suffixed, atlas::getTextureExtry) != null ? suffixed : base;
    }

    public static synchronized boolean exists(ResourceLocation texture) {
        Boolean cached = EXISTS.get(texture);
        if (cached != null) return cached;
        boolean found;
        try (IResource ignored = Minecraft.getMinecraft().getResourceManager().getResource(NativeSpriteLoader.textureFile(texture))) {
            found = true;
        } catch (IOException exception) {
            found = false;
        }
        EXISTS.put(texture, found);
        if (EXISTS.size() > 4096) EXISTS.remove(EXISTS.keySet().iterator().next());
        return found;
    }

    private static synchronized void release(Bundle bundle) {
        if (--bundle.leases != 0) return;
        if (bundle.key.generation != generation) {
            clearFrames(bundle.owned);
            return;
        }
        Bundle replaced = PREPARED.put(bundle.key, bundle);
        if (replaced != null) {
            preparedBytes -= replaced.bytes;
            if (replaced != bundle) clearFrames(replaced.owned);
        }
        preparedBytes += bundle.bytes;
        Iterator<Bundle> iterator = PREPARED.values().iterator();
        while (preparedBytes > PREPARED_BUDGET && iterator.hasNext()) {
            Bundle expired = iterator.next();
            preparedBytes -= expired.bytes;
            clearFrames(expired.owned);
            iterator.remove();
        }
    }

    private static Resident makeResident(Recipe recipe) {
        Bundle bundle = PREPARED.remove(recipe.material);
        if (bundle != null) preparedBytes -= bundle.bytes;
        else bundle = generate(recipe.material);
        try {
            // 一次生成的 root/mask 一同上传；其原生对象间引用不会因子纹理懒加载而断裂。
            for (Map.Entry<String, TextureAtlasSprite> entry : bundle.outputs.entrySet()) {
                Recipe outputRecipe = new Recipe(recipe.material, entry.getKey(), false);
                if (RESIDENT.containsKey(outputRecipe)) continue;
                upload(outputRecipe, entry.getValue());
            }
            Resident result = RESIDENT.get(recipe);
            if (result == null) throw new IllegalStateException("原生材质重建时缺少子纹理：" + recipe.spriteName);
            return result;
        } finally {
            for (TextureAtlasSprite owned : bundle.owned) {
                boolean retained = false;
                for (Page page : PAGES) if (page.animations.contains(owned)) { retained = true; break; }
                if (!retained) owned.clearFramesTextureData();
            }
        }
    }

    private static void upload(Recipe recipe, TextureAtlasSprite sprite) {
        PixelContentKey content = sprite.hasAnimationMetadata() ? null
                : PixelContentKey.of(sprite.getIconWidth(), sprite.getIconHeight(), firstFrame(sprite));
        Resident shared = content == null ? null : CONTENT.get(content);
        if (shared != null) {
            RESIDENT.put(recipe, shared);
            shared.page.lastUse = ++useCounter;
            if (drawDepth > 0) shared.page.pinned = true;
            deduplicated++;
            return;
        }
        int mipLevels = Minecraft.getMinecraft().getTextureMapBlocks().getMipmapLevels();
        Page page = null;
        TexturePageLayout.Region region = null;
        for (Page candidate : PAGES) {
            if (candidate.mipLevels != mipLevels) continue;
            region = candidate.layout.allocate(sprite.getIconWidth(), sprite.getIconHeight());
            if (region != null) { page = candidate; break; }
        }
        if (page == null) {
            int width = Math.max(PAGE_SIZE, powerOfTwo(sprite.getIconWidth()));
            int height = Math.max(PAGE_SIZE, powerOfTwo(sprite.getIconHeight()));
            int maximum = Minecraft.getGLMaximumTextureSize();
            if (width > maximum || height > maximum) {
                throw new IllegalStateException("纹理尺寸超过 GPU 上限：" + sprite.getIconName() + " "
                        + width + "×" + height + "，GL_MAX_TEXTURE_SIZE=" + maximum);
            }
            long bytes = mipBytes(width, height, mipLevels);
            while (gpuBytes + bytes > GPU_BUDGET) {
                Page oldest = null;
                for (Page candidate : PAGES) {
                    if (!candidate.pinned && (oldest == null || candidate.lastUse < oldest.lastUse)) oldest = candidate;
                }
                if (oldest == null) {
                    // 单张高清资源或单次绘制可以超过软预算；不降采样，也不逐出仍在提交的页。
                    TooManyTinkers.LOGGER.warn("TMT 当前绘制需要超过纹理软预算：{} 字节", gpuBytes + bytes);
                    break;
                }
                evict(oldest);
            }
            page = new Page(width, height, mipLevels, bytes);
            PAGES.add(page);
            gpuBytes += bytes;
            peakGpuBytes = Math.max(peakGpuBytes, gpuBytes);
            region = page.layout.allocate(sprite.getIconWidth(), sprite.getIconHeight());
        }
        page.lastUse = ++useCounter;
        if (drawDepth > 0) page.pinned = true;
        GlStateManager.bindTexture(page.texture.getGlTextureId());
        TextureUtil.uploadTextureMipmap(firstFrame(sprite), sprite.getIconWidth(), sprite.getIconHeight(), region.x(), region.y(), false, false);
        if (sprite.hasAnimationMetadata()) {
            NativeSpriteAnimationAccess access = (NativeSpriteAnimationAccess) sprite;
            access.tmt$setOriginX(region.x());
            access.tmt$setOriginY(region.y());
            restoreAnimation(sprite, access);
            page.animations.add(sprite);
            long bytes = frameBytes(sprite);
            if (access.tmt$getAnimationMetadata().isInterpolate()) {
                // 原生更新函数会另外保留一帧插值工作区，预算同时预留它。
                for (int[] mip : firstFrame(sprite)) if (mip != null) bytes += 4L * mip.length;
            }
            page.animationBytes += bytes;
            animationBytes += bytes;
        }
        Resident resident = new Resident(page, region);
        RESIDENT.put(recipe, resident);
        if (content != null) CONTENT.put(content, resident);
        uploads++;
    }

    private static void restoreAnimation(TextureAtlasSprite sprite, NativeSpriteAnimationAccess access) {
        AnimationMetadataSection metadata = access.tmt$getAnimationMetadata();
        int count = metadata.getFrameCount() == 0 ? sprite.getFrameCount() : metadata.getFrameCount();
        int[] durations = new int[count];
        for (int i = 0; i < count; i++) durations[i] = metadata.getFrameTimeSingle(i);
        AnimationTimeline.Position position = AnimationTimeline.at(Math.max(0, tick - 1), durations);
        access.tmt$setFrameCounter(position.frame());
        access.tmt$setTickCounter(position.tick());
        int frame = metadata.getFrameIndex(position.frame());
        TextureUtil.uploadTextureMipmap(sprite.getFrameTextureData(frame), sprite.getIconWidth(), sprite.getIconHeight(),
                sprite.getOriginX(), sprite.getOriginY(), false, false);
        if (tick > 0) sprite.updateAnimation();
    }

    private static void evict(Page page) {
        RESIDENT.values().removeIf(resident -> resident.page == page);
        CONTENT.values().removeIf(resident -> resident.page == page);
        for (TextureAtlasSprite animation : page.animations) animation.clearFramesTextureData();
        Minecraft.getMinecraft().getTextureManager().deleteTexture(page.location);
        PAGES.remove(page);
        gpuBytes -= page.bytes;
        animationBytes -= page.animationBytes;
        evictions++;
    }

    private static int[][] firstFrame(TextureAtlasSprite sprite) {
        for (int i = 0; i < sprite.getFrameCount(); i++) {
            int[][] frame = sprite.getFrameTextureData(i);
            if (frame != null) return frame;
        }
        throw new IllegalStateException("纹理没有可读取帧：" + sprite.getIconName());
    }

    private static long frameBytes(TextureAtlasSprite sprite) {
        long size = 0;
        for (int i = 0; i < sprite.getFrameCount(); i++) {
            int[][] frame = sprite.getFrameTextureData(i);
            if (frame != null) for (int[] mip : frame) if (mip != null) size += 4L * mip.length;
        }
        return size;
    }

    private static void clearFrames(List<TextureAtlasSprite> sprites) {
        for (TextureAtlasSprite sprite : sprites) sprite.clearFramesTextureData();
    }

    private static int powerOfTwo(int size) {
        return Integer.highestOneBit(Math.max(1, size - 1)) << 1;
    }

    private static long mipBytes(int width, int height, int levels) {
        long bytes = 0;
        for (int i = 0; i <= levels; i++) bytes += 4L * Math.max(1, width >> i) * Math.max(1, height >> i);
        return bytes;
    }

    public record TextureBinding(ResourceLocation texture, int texId, float u0, float v0, float uScale, float vScale) {}
    public record Stats(int pages, int residentTextures, long gpuBytes, long peakGpuBytes, long preparedBytes, long animationBytes,
                        long generated, long uploads, long evictions, long deduplicated, int nativeAtlasSprites,
                        long nativeAtlasPixelBytes, int generation, boolean atlasReady) {}
    private record MaterialKey(ResourceLocation base, Material material, int generation) {}
    private record Recipe(MaterialKey material, String spriteName, boolean inAtlas) {}
    private record Resident(Page page, TexturePageLayout.Region region) {}

    public static final class MaterialTexture implements AutoCloseable {
        private final TextureAtlasSprite sprite;
        private final boolean vertexColored;
        private final int vertexColor;
        private Runnable release;
        private MaterialTexture(TextureAtlasSprite sprite, boolean vertexColored, int vertexColor, Runnable release) {
            this.sprite = sprite;
            this.vertexColored = vertexColored;
            this.vertexColor = vertexColor;
            this.release = release;
        }
        public TextureAtlasSprite sprite() { return sprite; }
        public boolean vertexColored() { return vertexColored; }
        public int vertexColor() { return vertexColor; }
        @Override public void close() {
            if (release != null) { release.run(); release = null; }
        }
    }

    public static final class FrameLease implements AutoCloseable {
        private Runnable release;
        private FrameLease(Runnable release) { this.release = release; }
        @Override public void close() {
            if (release != null) { release.run(); release = null; }
        }
    }

    private static final class Bundle {
        final MaterialKey key;
        final TextureAtlasSprite primary;
        final Map<String, TextureAtlasSprite> outputs;
        final List<TextureAtlasSprite> owned;
        final long bytes;
        int leases;
        Bundle(MaterialKey key, TextureAtlasSprite primary, Map<String, TextureAtlasSprite> outputs,
               List<TextureAtlasSprite> owned, long bytes) {
            this.key = key; this.primary = primary; this.outputs = outputs; this.owned = owned; this.bytes = bytes;
        }
    }

    private static final class Page {
        final int width;
        final int height;
        final int mipLevels;
        final long bytes;
        final TexturePageLayout layout;
        final ResourceLocation location;
        final PageTexture texture;
        final List<TextureAtlasSprite> animations = new ArrayList<>();
        long lastUse;
        long animationBytes;
        boolean pinned;
        Page(int width, int height, int mipLevels, long bytes) {
            this.width = width; this.height = height; this.mipLevels = mipLevels; this.bytes = bytes;
            this.layout = new TexturePageLayout(width, height, 1 << mipLevels);
            this.location = new ResourceLocation("toomanytinkers", "dynamic/material_page_" + nextPageId++);
            this.texture = new PageTexture(width, height, mipLevels);
            Minecraft.getMinecraft().getTextureManager().loadTexture(location, texture);
        }
    }

    private static final class PageTexture extends AbstractTexture {
        final int width;
        final int height;
        final int mipLevels;
        PageTexture(int width, int height, int mipLevels) { this.width = width; this.height = height; this.mipLevels = mipLevels; }
        @Override public void loadTexture(IResourceManager resources) {
            TextureUtil.allocateTextureImpl(getGlTextureId(), mipLevels, width, height);
        }
    }
}

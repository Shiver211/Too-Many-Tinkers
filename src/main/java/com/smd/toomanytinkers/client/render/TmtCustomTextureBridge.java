package com.smd.toomanytinkers.client.render;

import com.smd.toomanytinkers.client.texture.NativeSpriteLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.IResource;
import net.minecraft.util.ResourceLocation;
import slimeknights.tconstruct.library.TinkerRegistry;
import slimeknights.tconstruct.library.client.CustomTextureCreator;
import slimeknights.tconstruct.library.materials.Material;
import slimeknights.tconstruct.library.materials.MaterialGUI;
import slimeknights.tconstruct.library.tools.IToolPart;

import javax.annotation.Nullable;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 保留附属使用的原生索引；普通材质按需生成，依赖主图集采样的材质提前注册。 */
public final class TmtCustomTextureBridge {
    private static final Set<String> KNOWN_BASES = new LinkedHashSet<>();
    private static volatile Set<String> publishedBases = Set.of();
    private TmtCustomTextureBridge() {}

    public static void rebuild(Set<ResourceLocation> baseTextures,
                               Map<ResourceLocation, Set<IToolPart>> texturePartMapping) {
        NativeMaterialTextures.reload();
        CustomTextureCreator.sprites.clear();
        KNOWN_BASES.clear();
        TextureMap atlas = Minecraft.getMinecraft().getTextureMapBlocks();
        Set<String> suffixes = new LinkedHashSet<>();
        List<Material> atlasMaterials = new ArrayList<>();
        for (Material material : TinkerRegistry.getAllMaterials()) {
            if (NativeMaterialTextures.requiresNativeAtlas(material)) atlasMaterials.add(material);
            if (material.renderInfo != null && material.renderInfo.getTextureSuffix() != null) {
                suffixes.add(material.renderInfo.getTextureSuffix());
            }
        }
        for (ResourceLocation base : baseTextures) {
            if ("minecraft:missingno".equals(base.toString())) continue;
            atlas.registerSprite(base);
            for (String suffix : suffixes) {
                ResourceLocation suffixed = new ResourceLocation(base + "_" + suffix);
                if (NativeMaterialTextures.exists(suffixed)) atlas.registerSprite(suffixed);
            }
            Set<IToolPart> parts = texturePartMapping.get(base);
            LazyMaterialMap materials = new LazyMaterialMap(base, parts);
            CustomTextureCreator.sprites.put(base.toString(), materials);
            for (Material material : atlasMaterials) {
                if (materials.usable(material)) NativeMaterialTextures.registerAtlasMaterial(base, material, atlas);
            }
            KNOWN_BASES.add(base.toString());
        }
    }

    /** 仅由主图集生命周期在 NativeMaterialTextures 的锁内清除/发布。 */
    static void clearBaseIndex() { publishedBases = Set.of(); }

    static void publishBaseIndex() { publishedBases = Set.copyOf(KNOWN_BASES); }

    /** 只检查名称，不读取材质或资源；成本取决于查询名称，而非已登记底图数。 */
    static boolean hasMaterialSpriteCandidate(String fullName) {
        Set<String> bases = publishedBases;
        if (bases.isEmpty()) return false;
        for (int separator = fullName.lastIndexOf('_'); separator >= 0;
             separator = fullName.lastIndexOf('_', separator - 1)) {
            if (bases.contains(fullName.substring(0, separator))) return true;
        }
        return false;
    }

    @Nullable
    static TextureAtlasSprite lookupMaterialSprite(String fullName) {
        String selectedBase = null;
        String selectedMaterial = null;
        for (String base : KNOWN_BASES) {
            if (!fullName.startsWith(base + "_")) continue;
            String identifier = fullName.substring(base.length() + 1);
            Map<String, TextureAtlasSprite> materials = CustomTextureCreator.sprites.get(base);
            if (materials != null && materials.containsKey(identifier)
                    && (selectedBase == null || base.length() > selectedBase.length())) {
                selectedBase = base;
                selectedMaterial = identifier;
            }
        }
        return selectedBase == null ? null : CustomTextureCreator.sprites.get(selectedBase).get(selectedMaterial);
    }

    public static BufferedImage createMaterialImage(String baseTextureName, Material material) {
        return NativeMaterialTextures.materialImage(new ResourceLocation(baseTextureName), material);
    }

    @Nullable
    public static BufferedImage createTextureImage(String textureName) {
        try (IResource resource = Minecraft.getMinecraft().getResourceManager()
                .getResource(NativeSpriteLoader.textureFile(new ResourceLocation(textureName)))) {
            return ImageIO.read(resource.getInputStream());
        } catch (IOException missing) {
            return null;
        }
    }

    public static boolean textureExists(String textureName) {
        return NativeMaterialTextures.exists(new ResourceLocation(textureName));
    }

    private static final class LazyMaterialMap extends AbstractMap<String, TextureAtlasSprite> {
        private final ResourceLocation base;
        private final Set<IToolPart> parts;
        private final boolean gui;
        private boolean cleared;

        private LazyMaterialMap(ResourceLocation base, @Nullable Set<IToolPart> parts) {
            this.base = base;
            this.parts = parts == null ? null : new LinkedHashSet<>(parts);
            this.gui = CustomTextureCreator.belongsToToolPart(base);
        }

        @Override public TextureAtlasSprite get(Object key) {
            Material material = material(key);
            return material == null ? null : create(material);
        }

        private Material material(Object key) {
            if (cleared) return null;
            if (!(key instanceof String identifier)) return null;
            Material material = CustomTextureCreator.guiMaterial.identifier.equals(identifier)
                    ? CustomTextureCreator.guiMaterial : TinkerRegistry.getMaterial(identifier);
            if (!material.identifier.equals(identifier) || !usable(material)) return null;
            return material;
        }

        private boolean usable(Material material) {
            if (material.renderInfo == null) return NativeMaterialTextures.exists(new ResourceLocation(base + "_" + material.identifier));
            if (material == CustomTextureCreator.guiMaterial) return gui;
            if (parts == null || material instanceof MaterialGUI) return true;
            for (IToolPart part : parts) if (part.canUseMaterialForRendering(material)) return true;
            return false;
        }

        private List<Material> materials() {
            List<Material> result = new ArrayList<>();
            if (cleared) return result;
            for (Material material : TinkerRegistry.getAllMaterials()) if (usable(material)) result.add(material);
            if (gui && !result.contains(CustomTextureCreator.guiMaterial)) result.add(CustomTextureCreator.guiMaterial);
            return result;
        }

        private TextureAtlasSprite create(Material material) {
            try (NativeMaterialTextures.MaterialTexture texture = NativeMaterialTextures.acquire(base, material)) {
                TextureAtlasSprite sprite = texture.sprite();
                // Default 的查询仍返回主图集对象；临时几何帧只归烘焙 lease 所有。
                return NativeMaterialTextures.hasRecipe(sprite) ? sprite
                        : Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(sprite.getIconName());
            }
        }

        @Override public boolean containsKey(Object key) {
            return material(key) != null;
        }

        @Override public int size() { return materials().size(); }

        /** 原生资源重载会对每个内层 Map 调用 clear，不触发懒生成或迭代删除。 */
        @Override public void clear() { cleared = true; }

        @Override public Set<Entry<String, TextureAtlasSprite>> entrySet() {
            return new AbstractSet<>() {
                @Override public int size() { return LazyMaterialMap.this.size(); }
                @Override public Iterator<Entry<String, TextureAtlasSprite>> iterator() {
                    Iterator<Material> iterator = materials().iterator();
                    return new Iterator<>() {
                        @Override public boolean hasNext() { return iterator.hasNext(); }
                        @Override public Entry<String, TextureAtlasSprite> next() {
                            Material material = iterator.next();
                            return new SimpleImmutableEntry<>(material.identifier, create(material));
                        }
                    };
                }
            };
        }
    }
}

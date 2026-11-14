package com.smd.toomanytinkers.client.texture;

import net.minecraft.client.renderer.texture.PngSizeInfo;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * 使用 Forge 的公开 sprite 加载协议。附属的 load/preProcess/postProcess
 * 及其依赖顺序均由原对象执行，不从类名猜测颜色算法。
 */
public final class NativeSpriteLoader {
    private final IResourceManager resources;
    private final Function<ResourceLocation, TextureAtlasSprite> registered;
    private final int mipLevels;
    private final Map<ResourceLocation, TextureAtlasSprite> sprites = new HashMap<>();
    private final Set<TextureAtlasSprite> loaded = new HashSet<>();
    private final Set<TextureAtlasSprite> loading = new HashSet<>();
    private final List<TextureAtlasSprite> owned = new ArrayList<>();

    public NativeSpriteLoader(IResourceManager resources,
                              Function<ResourceLocation, TextureAtlasSprite> registered,
                              int mipLevels) {
        this.resources = resources;
        this.registered = registered;
        this.mipLevels = mipLevels;
    }

    public void register(TextureAtlasSprite sprite) {
        sprites.put(new ResourceLocation(sprite.getIconName()), sprite);
        owned.add(sprite);
    }

    public TextureAtlasSprite source(ResourceLocation location) {
        TextureAtlasSprite present = sprites.get(location);
        if (present != null) {
            load(present);
            return present;
        }
        // 普通 PNG 使用独立帧：不依赖 FoamFix 等是否已释放主图集的 CPU 数据。
        TextureAtlasSprite sprite;
        TextureAtlasSprite previousCoordinates = null;
        try (IResource ignored = resources.getResource(textureFile(location))) {
            sprite = new SourceSprite(location.toString());
            register(sprite);
        } catch (IOException missingFile) {
            sprite = registered.apply(location);
            if (sprite == null) throw new IllegalStateException("纹理依赖不存在：" + location, missingFile);
            sprites.put(location, sprite);
            if (sprite.getFrameCount() > 0) {
                loaded.add(sprite);
                return sprite;
            }
            // 自定义依赖只有原对象能够重新加载；保留它的主图集坐标。
            if (!sprite.hasCustomLoader(resources, textureFile(location))) {
                throw new IllegalStateException("纹理依赖既无 PNG 也无自定义加载器：" + location, missingFile);
            }
            if (sprite.getIconWidth() > 0 && sprite.getMaxU() > sprite.getMinU()) {
                previousCoordinates = raw(location);
                previousCoordinates.copyFrom(sprite);
            }
        }
        try {
            load(sprite);
        } finally {
            if (previousCoordinates != null) sprite.copyFrom(previousCoordinates);
        }
        if (!owned.contains(sprite) && !sprite.hasAnimationMetadata()) owned.add(sprite);
        return sprite;
    }

    public void load(TextureAtlasSprite sprite) {
        if (loaded.contains(sprite)) return;
        if (!loading.add(sprite)) throw new IllegalStateException("循环纹理依赖：" + sprite.getIconName());
        ResourceLocation file = textureFile(new ResourceLocation(sprite.getIconName()));
        try {
            for (ResourceLocation dependency : sprite.getDependencies()) source(dependency);
            if (sprite.hasCustomLoader(resources, file)) {
                sprite.load(resources, file, this::source);
            } else {
                PngSizeInfo size;
                try (IResource header = resources.getResource(file)) {
                    size = PngSizeInfo.makeFromResource(header);
                }
                try (IResource data = resources.getResource(file)) {
                    boolean animated = data.getMetadata("animation") != null;
                    if (!animated && size.pngWidth != size.pngHeight) {
                        // 穿戴盔甲使用矩形画布，不经 Minecraft 的方形 item atlas 限制。
                        sprite.setIconWidth(size.pngWidth);
                        sprite.setIconHeight(size.pngHeight);
                    } else {
                        sprite.loadSprite(size, animated);
                    }
                    sprite.loadSpriteFrames(data, mipLevels + 1);
                }
            }
            if (sprite.getFrameCount() == 0) {
                throw new IllegalStateException("原生纹理加载器未生成帧：" + sprite.getIconName());
            }
            // load 已经提供原始第 0 级；关闭 mipmap 时不再复制帧容器或进入 GL 工具类。
            if (mipLevels > 0) sprite.generateMipmaps(mipLevels);
            loaded.add(sprite);
        } catch (IOException exception) {
            throw new IllegalStateException("加载原生纹理失败：" + sprite.getIconName(), exception);
        } finally {
            loading.remove(sprite);
        }
    }

    public List<TextureAtlasSprite> ownedSprites() {
        return owned;
    }

    public static ResourceLocation textureFile(ResourceLocation texture) {
        String path = texture.getPath();
        if (!path.startsWith("textures/")) path = "textures/" + path;
        if (!path.endsWith(".png")) path += ".png";
        return new ResourceLocation(texture.getNamespace(), path);
    }

    public static TextureAtlasSprite raw(ResourceLocation location) {
        return new SourceSprite(location.toString());
    }

    private static final class SourceSprite extends TextureAtlasSprite {
        private SourceSprite(String name) { super(name); }
    }
}

package com.smd.toomanytinkers.client.armor;

import com.smd.toomanytinkers.client.render.NativeMaterialTextures;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.List;
import java.util.function.Supplier;

/** 仅拥有 ConArm getCombinedTexture 新建的纹理，不接管 TTT Scout 的独立管线。 */
public final class TmtArmorTextures {
    private static final Logger LOGGER = LogManager.getLogger("toomanytinkers/armor");
    private static final long BUDGET_BYTES = 16L * 1024 * 1024;
    private static final ArrayDeque<ResourceLocation> FREE_LOCATIONS = new ArrayDeque<>();
    private static final OwnedTextureCache<Key, Texture> CACHE =
            new OwnedTextureCache<>(256, BUDGET_BYTES, texture -> 2 * texture.pixelBytes,
                    TmtArmorTextures::release);
    private static final ThreadLocal<Composition> CURRENT = new ThreadLocal<>();
    private static long hits;
    private static long generated;
    private static long failures;
    private static int nextLocation;

    private TmtArmorTextures() {}

    public static ResourceLocation get(ItemStack stack, Supplier<ResourceLocation> compose) {
        Key key = new Key(stack.getItem(), stack.getMetadata(), stack.getTagCompound());
        Texture cached = CACHE.get(key);
        if (cached != null) {
            hits++;
            return cached.location;
        }
        // 命中时只查询；存入缓存前才复制，避免每次穿戴绘制复制完整 NBT。
        key = new Key(key.item, key.metadata, key.tag == null ? null : key.tag.copy());
        Composition previous = CURRENT.get();
        Composition current = new Composition();
        CURRENT.set(current);
        try {
            ResourceLocation location = compose.get();
            Texture result = current.uploaded;
            if (result == null || !result.location.equals(location)) {
                throw new IllegalStateException("ConArm 合成没有登记动态纹理：" + location);
            }
            CACHE.put(key, result);
            current.uploaded = null; // 所有权移交缓存。
            generated++;
            return location;
        } catch (RuntimeException failure) {
            failures++;
            LOGGER.error("ConArm 原生纹理合成失败，物品：{}", stack.getItem().getRegistryName(), failure);
            return null;
        } finally {
            for (int i = current.leases.size() - 1; i >= 0; i--) current.leases.get(i).close();
            if (current.uploaded != null) {
                release(current.uploaded);
            }
            if (previous == null) CURRENT.remove();
            else CURRENT.set(previous);
        }
    }

    public static TextureAtlasSprite retain(NativeMaterialTextures.MaterialTexture texture) {
        CURRENT.get().leases.add(texture);
        return texture.sprite();
    }

    public static ResourceLocation upload(TextureManager manager, DynamicTexture texture) {
        ResourceLocation location = FREE_LOCATIONS.pollFirst();
        if (location == null) {
            location = new ResourceLocation("toomanytinkers", "dynamic/conarm_" + nextLocation++);
        }
        // 复用有限位置，避免 LayerArmorBase 的路径缓存随每次重新生成无限增长。
        CURRENT.get().uploaded = new Texture(location, 4L * texture.getTextureData().length);
        manager.loadTexture(location, texture);
        return location;
    }

    private static void release(Texture texture) {
        Minecraft.getMinecraft().getTextureManager().deleteTexture(texture.location);
        FREE_LOCATIONS.addLast(texture.location);
    }

    /** 在客户端线程的重载/退出世界边界调用；重复调用不会二次删除。 */
    public static void clear() { CACHE.clear(); }

    public static Stats stats() {
        // 原生 DynamicTexture 同时保留 RGBA int[] 与同分辨率 GPU 纹理。
        long pixelBytes = CACHE.bytes() / 2;
        return new Stats(CACHE.size(), pixelBytes, pixelBytes, hits, generated, CACHE.evictions(), failures,
                CACHE.bytes() > BUDGET_BYTES);
    }

    public record Stats(int textures, long gpuBytes, long cpuPixelBytes, long hits, long generated,
                        long evictions, long failures, boolean singleTextureOverBudget) {}

    private record Key(Item item, int metadata, NBTTagCompound tag) {}
    private record Texture(ResourceLocation location, long pixelBytes) {}
    private static final class Composition {
        private final List<NativeMaterialTextures.MaterialTexture> leases = new ArrayList<>();
        private Texture uploaded;
    }
}

package com.smd.toomanytinkers.client.render;

import net.minecraft.util.ResourceLocation;
import javax.annotation.Nullable;

public record MaterialDescriptor(
        String materialId,
        int materialType,
        int materialIndex,
        int sourceIndex,
        int animationId,
        int flags,
        MaterialRenderMode renderMode,
        @Nullable String textureSuffix,
        @Nullable ResourceLocation sourceTexture
) {
    public static final int TYPE_DIRECT = 0;
    public static final int TYPE_RAMP = 1;
    public static final int TYPE_TEXTURE = 2;

    public static final int FLAG_VERTEX_COLOR = 1;
    public static final int FLAG_TEXTURE_SUFFIX = 1 << 1;
    public static final int FLAG_ANIMATED = 1 << 2;
    public static final int FLAG_SOURCE_TEXTURE = 1 << 3;

    public int getRampRow() {
        return materialIndex;
    }

    public int getMapSlot() {
        return materialIndex;
    }

    public boolean hasTextureSuffix() {
        return textureSuffix != null && !textureSuffix.isEmpty();
    }
}
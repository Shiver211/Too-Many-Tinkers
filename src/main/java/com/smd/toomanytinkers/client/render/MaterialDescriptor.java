package com.smd.toomanytinkers.client.render;

import net.minecraft.util.ResourceLocation;

import javax.annotation.Nullable;

public record MaterialDescriptor(String materialId, int materialType, int materialIndex, int sourceIndex,
                                 int animationId, int flags, MaterialRenderMode renderMode, String textureSuffix,
                                 ResourceLocation sourceTexture) {

    public static final int TYPE_DIRECT = 0;
    public static final int TYPE_RAMP = 1;
    public static final int TYPE_TEXTURE = 2;

    public static final int FLAG_VERTEX_COLOR = 1;
    public static final int FLAG_TEXTURE_SUFFIX = 1 << 1;
    public static final int FLAG_ANIMATED = 1 << 2;
    public static final int FLAG_SOURCE_TEXTURE = 1 << 3;

    public MaterialDescriptor(String materialId,
                              int materialType,
                              int materialIndex,
                              int sourceIndex,
                              int animationId,
                              int flags,
                              MaterialRenderMode renderMode,
                              @Nullable String textureSuffix,
                              @Nullable ResourceLocation sourceTexture) {
        this.materialId = materialId;
        this.materialType = materialType;
        this.materialIndex = materialIndex;
        this.sourceIndex = sourceIndex;
        this.animationId = animationId;
        this.flags = flags;
        this.renderMode = renderMode;
        this.textureSuffix = textureSuffix;
        this.sourceTexture = sourceTexture;
    }

    public int getRampRow() {
        return materialIndex;
    }

    public int getMapSlot() {
        return materialIndex;
    }

    @Override
    @Nullable
    public String textureSuffix() {
        return textureSuffix;
    }

    @Override
    @Nullable
    public ResourceLocation sourceTexture() {
        return sourceTexture;
    }

    public boolean hasTextureSuffix() {
        return textureSuffix != null && !textureSuffix.isEmpty();
    }
}

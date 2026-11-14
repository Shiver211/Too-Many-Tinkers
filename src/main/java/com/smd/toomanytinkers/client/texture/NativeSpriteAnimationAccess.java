package com.smd.toomanytinkers.client.texture;

import net.minecraft.client.resources.data.AnimationMetadataSection;

/** 由 Minecraft 的具名 Mixin accessor 实现，不探测附属私有字段。 */
public interface NativeSpriteAnimationAccess {
    AnimationMetadataSection tmt$getAnimationMetadata();
    void tmt$setFrameCounter(int frame);
    void tmt$setTickCounter(int tick);
    void tmt$setOriginX(int x);
    void tmt$setOriginY(int y);
}

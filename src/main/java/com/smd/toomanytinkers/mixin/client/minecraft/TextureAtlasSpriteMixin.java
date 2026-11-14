package com.smd.toomanytinkers.mixin.client.minecraft;

import com.smd.toomanytinkers.client.texture.NativeSpriteAnimationAccess;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.data.AnimationMetadataSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(TextureAtlasSprite.class)
public interface TextureAtlasSpriteMixin extends NativeSpriteAnimationAccess {
    @Override @Accessor("animationMetadata")
    AnimationMetadataSection tmt$getAnimationMetadata();
    @Override @Accessor("frameCounter")
    void tmt$setFrameCounter(int value);
    @Override @Accessor("tickCounter")
    void tmt$setTickCounter(int value);
    @Override @Accessor("originX")
    void tmt$setOriginX(int value);
    @Override @Accessor("originY")
    void tmt$setOriginY(int value);
}

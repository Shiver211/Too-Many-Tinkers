package com.smd.toomanytinkers.client.texture;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import javax.annotation.Nullable;

/** 保留原生 sprite 类型和像素帧，只决定它的 UV 属于主图集还是动态页。 */
public final class SpritePlacement {
    private SpritePlacement() { }

    public static boolean place(TextureAtlasSprite generated, @Nullable TextureAtlasSprite atlasSprite) {
        if (atlasSprite != null) {
            generated.copyFrom(atlasSprite);
            return true;
        }
        generated.initSprite(generated.getIconWidth(), generated.getIconHeight(), 0, 0, false);
        return false;
    }
}

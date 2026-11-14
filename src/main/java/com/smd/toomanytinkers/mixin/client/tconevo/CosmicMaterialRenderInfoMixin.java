package com.smd.toomanytinkers.mixin.client.tconevo;

import com.smd.toomanytinkers.client.texture.NativeAtlasMaterial;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
@Mixin(targets = "xyz.phanta.tconevo.client.render.material.CosmicMaterialRenderInfo", remap = false)
public abstract class CosmicMaterialRenderInfoMixin implements NativeAtlasMaterial { }

package com.smd.toomanytinkers.client.render;

/** 只有进入原生 renderEffect 才是闪光 pass；空 ItemStack 本身不是判断条件。 */
public final class GlintRenderScope {
    private static final ThreadLocal<int[]> DEPTH = ThreadLocal.withInitial(() -> new int[1]);

    private GlintRenderScope() { }

    public static void enter() {
        DEPTH.get()[0]++;
    }

    public static void exit() {
        DEPTH.get()[0]--;
    }

    public static boolean isActive() {
        return DEPTH.get()[0] > 0;
    }
}

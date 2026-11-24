package com.smd.toomanytinkers.client.texture;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Supplier;

/** 共享原生依赖保留对象类型；恢复的帧在最后一个加载器退出使用后才清除。 */
final class SharedSpriteFrames {
    private static final Object CUSTOM_LOAD = new Object();
    private static final Map<TextureAtlasSprite, State> STATES = new WeakHashMap<>();

    static <T> T custom(Supplier<T> callback) {
        // 第三方 loader 可递归加载其他共享依赖，用同一个可重入锁避免对象锁顺序反转。
        synchronized (CUSTOM_LOAD) { return callback.get(); }
    }

    static Lease acquire(TextureAtlasSprite sprite, Runnable restore) {
        synchronized (CUSTOM_LOAD) {
            State state = STATES.computeIfAbsent(sprite, ignored -> new State());
            state.users++;
            try {
                if (sprite.getFrameCount() == 0) {
                    state.owned = true;
                    restore.run();
                    state.owned = !sprite.hasAnimationMetadata();
                }
                return new Lease(sprite, state);
            } catch (RuntimeException | Error failure) {
                if (--state.users == 0 && state.owned) {
                    sprite.clearFramesTextureData();
                    state.owned = false;
                }
                throw failure;
            }
        }
    }

    static final class Lease implements AutoCloseable {
        private final TextureAtlasSprite sprite;
        private final State state;
        private boolean closed;
        private Lease(TextureAtlasSprite sprite, State state) { this.sprite = sprite; this.state = state; }
        boolean ownsFrames() { return state.owned; }
        TextureAtlasSprite sprite() { return sprite; }
        @Override public void close() {
            synchronized (CUSTOM_LOAD) {
                if (closed) return;
                closed = true;
                if (--state.users == 0 && state.owned) {
                    sprite.clearFramesTextureData();
                    state.owned = false;
                }
            }
        }
    }

    private static final class State { int users; boolean owned; }
}

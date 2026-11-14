package com.smd.toomanytinkers.client.model;

import com.google.common.cache.Cache;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/** 保留原生组合缓存策略，仅补足资源重载和世界卸载的失效通知。 */
public final class NativeToolModelCaches {
    private static final Set<Cache<?, ?>> CACHES = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Set<RenderContext> CONTEXTS = Collections.newSetFromMap(new WeakHashMap<>());
    private static long invalidations;

    private NativeToolModelCaches() { }

    public static synchronized void register(Cache<?, ?> cache) { CACHES.add(cache); }

    public static synchronized void registerContext(RenderContext context) { CONTEXTS.add(context); }

    public static synchronized void clear() {
        for (Cache<?, ?> cache : CACHES) {
            cache.invalidateAll();
            cache.cleanUp();
        }
        for (RenderContext context : CONTEXTS) context.tmt$clearContext();
        invalidations++;
    }

    public static synchronized String stats() {
        long models = 0;
        for (Cache<?, ?> cache : CACHES) models += cache.size();
        return "nativeToolCaches=" + CACHES.size() + ", nativeToolModels=" + models
                + ", nativeRenderContexts=" + CONTEXTS.size()
                + ", nativeToolInvalidations=" + invalidations;
    }

    /** 注册实现者本身的弱引用，不保存捕获模型/世界的清理 lambda。 */
    public interface RenderContext {
        void tmt$clearContext();
    }
}

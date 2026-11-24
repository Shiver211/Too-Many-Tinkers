package com.smd.toomanytinkers.client.render;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToLongFunction;

/** 状态共用所有者的短锁；生成、等待和已退出使用的纹理清理都在锁外执行。 */
final class LeasedTextureCache<K, V> {
    private final Object lock;
    private final long budget;
    private final ToLongFunction<V> weight;
    private final Predicate<K> current;
    private final Consumer<V> publish;
    private final Consumer<V> dispose;
    private final Map<K, Entry> active = new HashMap<>();
    private final LinkedHashMap<K, Entry> prepared = new LinkedHashMap<>(16, .75f, true);
    private final Map<K, Pending> building = new HashMap<>();
    private long bytes;
    private int version;

    LeasedTextureCache(Object lock, long budget, ToLongFunction<V> weight, Predicate<K> current,
                       Consumer<V> publish, Consumer<V> dispose) {
        this.lock = lock;
        this.budget = budget;
        this.weight = weight;
        this.current = current;
        this.publish = publish;
        this.dispose = dispose;
    }

    Lease acquire(K key, Supplier<V> generate) {
        Pending pending;
        boolean owner;
        synchronized (lock) {
            if (!current.test(key)) return null;
            Lease present = retainIfPresent(key);
            if (present != null) return present;
            pending = building.get(key);
            owner = pending == null;
            if (owner) {
                pending = new Pending();
                building.put(key, pending);
            } else {
                if (pending.owner == Thread.currentThread()) throw new IllegalStateException("递归生成同一纹理：" + key);
                pending.users++;
            }
        }
        if (owner) {
            V result = null;
            try {
                result = generate.get();
                synchronized (lock) {
                    if (pending.version == version && current.test(key)) {
                        publish.accept(result);
                        Entry entry = new Entry(key, result, pending.users);
                        active.put(key, entry);
                        building.remove(key);
                        pending.result.complete(entry);
                        result = null; // 生成结果的所有权交给已预约的使用者。
                    } else {
                        pending.result.complete(null);
                    }
                }
            } catch (RuntimeException | Error failure) {
                synchronized (lock) {
                    building.remove(key, pending);
                    if (pending.version != version || !current.test(key)) pending.result.complete(null);
                    else pending.result.completeExceptionally(failure);
                }
            } finally {
                if (result != null) dispose.accept(result);
            }
        }
        try {
            Entry entry = pending.result.join();
            return entry == null ? null : new Lease(entry);
        } catch (CompletionException failure) {
            if (failure.getCause() instanceof RuntimeException cause) throw cause;
            if (failure.getCause() instanceof Error cause) throw cause;
            throw failure;
        }
    }

    Lease retainIfPresent(K key) {
        synchronized (lock) {
            Entry entry = active.get(key);
            if (entry == null) {
                entry = prepared.remove(key);
                if (entry != null) {
                    bytes -= weight.applyAsLong(entry.value);
                    active.put(key, entry);
                }
            }
            if (entry == null) return null;
            entry.users++;
            return new Lease(entry);
        }
    }

    /** 调用方可在同一状态锁内使代次失效；返回的闲置对象须在退出锁后清理。 */
    List<V> invalidate() {
        synchronized (lock) {
            version++;
            List<V> expired = new ArrayList<>();
            for (Entry entry : prepared.values()) expired.add(entry.value);
            prepared.clear();
            active.clear(); // 正在使用的旧对象由最后一个 lease 归还，不提前清帧。
            for (Pending pending : building.values()) pending.result.complete(null);
            building.clear();
            bytes = 0;
            return expired;
        }
    }

    long bytes() { synchronized (lock) { return bytes; } }

    final class Lease implements AutoCloseable {
        private final Entry entry;
        private boolean closed;
        private Lease(Entry entry) { this.entry = entry; }
        V value() { return entry.value; }
        /** GPU 已接管输出时不再把 CPU 帧放回准备缓存。 */
        void discard() { finish(false); }
        @Override public void close() { finish(true); }
        private void finish(boolean cacheable) {
            List<V> expired = new ArrayList<>();
            synchronized (lock) {
                if (closed) return;
                closed = true;
                entry.cacheable &= cacheable;
                if (--entry.users != 0) return;
                active.remove(entry.key, entry);
                if (entry.version == version && current.test(entry.key) && entry.cacheable) {
                    prepared.put(entry.key, entry);
                    bytes += weight.applyAsLong(entry.value);
                    Iterator<Entry> iterator = prepared.values().iterator();
                    while (bytes > budget && iterator.hasNext()) {
                        Entry evicted = iterator.next();
                        bytes -= weight.applyAsLong(evicted.value);
                        expired.add(evicted.value);
                        iterator.remove();
                    }
                } else expired.add(entry.value);
            }
            for (V value : expired) dispose.accept(value);
        }
    }

    private final class Entry {
        final K key;
        final V value;
        final int version = LeasedTextureCache.this.version;
        int users;
        boolean cacheable = true;
        Entry(K key, V value, int users) { this.key = key; this.value = value; this.users = users; }
    }

    private final class Pending {
        final int version = LeasedTextureCache.this.version;
        final Thread owner = Thread.currentThread();
        final CompletableFuture<Entry> result = new CompletableFuture<>();
        int users = 1;
    }
}

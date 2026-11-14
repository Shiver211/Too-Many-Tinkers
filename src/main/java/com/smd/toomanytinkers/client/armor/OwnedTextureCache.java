package com.smd.toomanytinkers.client.armor;

import java.util.LinkedHashMap;
import java.util.function.Consumer;
import java.util.function.ToLongFunction;

/** 淘汰同时释放资源。最新一个超预算纹理允许完成本次绘制，下一项或 clear 会释放它。 */
final class OwnedTextureCache<K, V> {
    private final LinkedHashMap<K, Entry<V>> entries = new LinkedHashMap<>(16, .75f, true);
    private final int maxEntries;
    private final long maxBytes;
    private final ToLongFunction<V> size;
    private final Consumer<V> release;
    private long bytes;
    private long evictions;

    OwnedTextureCache(int maxEntries, long maxBytes, ToLongFunction<V> size, Consumer<V> release) {
        this.maxEntries = maxEntries;
        this.maxBytes = maxBytes;
        this.size = size;
        this.release = release;
    }

    V get(K key) {
        Entry<V> entry = entries.get(key);
        return entry == null ? null : entry.value;
    }

    void put(K key, V value) {
        Entry<V> previous = entries.remove(key);
        if (previous != null) dispose(previous);
        Entry<V> added = new Entry<>(value, size.applyAsLong(value));
        entries.put(key, added);
        bytes += added.bytes;
        // 不在调用者尚未绑定新纹理时删除它，也不降低单张大纹理的分辨率。
        while (entries.size() > 1 && (entries.size() > maxEntries || bytes > maxBytes)) {
            var iterator = entries.entrySet().iterator();
            Entry<V> oldest = iterator.next().getValue();
            iterator.remove();
            dispose(oldest);
            evictions++;
        }
    }

    void clear() {
        for (Entry<V> entry : entries.values()) dispose(entry);
        entries.clear();
    }

    private void dispose(Entry<V> entry) {
        bytes -= entry.bytes;
        release.accept(entry.value);
    }

    int size() { return entries.size(); }
    long bytes() { return bytes; }
    long evictions() { return evictions; }

    private record Entry<V>(V value, long bytes) {}
}

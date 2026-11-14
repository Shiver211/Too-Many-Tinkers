package com.smd.toomanytinkers.client.model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.ToLongFunction;

/** 按访问顺序淘汰；重量只计算本缓存拥有的数据，不重复计算外部纹理。 */
public final class WeightedLruCache<K, V> {
    private final int maxEntries;
    private final long maxWeight;
    private final ToLongFunction<V> weigh;
    private final LinkedHashMap<K, Entry<V>> entries = new LinkedHashMap<>(16, 0.75f, true);
    private long weight;

    public WeightedLruCache(int maxEntries, long maxWeight, ToLongFunction<V> weigh) {
        if (maxEntries < 1 || maxWeight < 1) {
            throw new IllegalArgumentException("缓存预算必须为正数");
        }
        this.maxEntries = maxEntries;
        this.maxWeight = maxWeight;
        this.weigh = weigh;
    }

    public synchronized V get(K key) {
        Entry<V> entry = entries.get(key);
        return entry == null ? null : entry.value;
    }

    public void put(K key, V value) {
        // 权重计算可能读取原生模型；不把它放进保护 LRU 表的短锁。
        long entryWeight = Math.max(1L, weigh.applyAsLong(value));
        synchronized (this) {
            Entry<V> previous = entries.remove(key);
            if (previous != null) {
                weight -= previous.weight;
            }
            // 单个超预算模型仍可用于本次绘制，但不挤掉整个工作集。
            if (entryWeight > maxWeight) {
                return;
            }
            entries.put(key, new Entry<>(value, entryWeight));
            weight += entryWeight;
            while (entries.size() > maxEntries || weight > maxWeight) {
                Map.Entry<K, Entry<V>> eldest = entries.entrySet().iterator().next();
                weight -= eldest.getValue().weight;
                entries.remove(eldest.getKey());
            }
        }
    }

    public synchronized void clear() {
        entries.clear();
        weight = 0L;
    }

    public synchronized int size() {
        return entries.size();
    }

    public synchronized long weight() {
        return weight;
    }

    private record Entry<V>(V value, long weight) { }
}

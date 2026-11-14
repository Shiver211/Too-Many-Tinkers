package com.smd.toomanytinkers.client.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class WeightedLruCacheTest {
    @Test
    void recentEntrySurvivesEntryLimit() {
        WeightedLruCache<String, String> cache = new WeightedLruCache<>(2, 100, String::length);
        cache.put("a", "甲");
        cache.put("b", "乙");
        assertEquals("甲", cache.get("a"));
        cache.put("c", "丙");
        assertNull(cache.get("b"));
        assertEquals("甲", cache.get("a"));
        assertEquals("丙", cache.get("c"));
    }

    @Test
    void weightLimitEvictsEnoughEntriesAndReplacementUpdatesWeight() {
        WeightedLruCache<String, String> cache = new WeightedLruCache<>(10, 7, String::length);
        cache.put("a", "aaa");
        cache.put("b", "bbb");
        cache.put("a", "a");
        assertEquals(4, cache.weight());
        cache.put("c", "ccccc");
        assertNull(cache.get("b"));
        assertEquals("a", cache.get("a"));
        assertEquals(6, cache.weight());
    }

    @Test
    void oversizedEntryDoesNotEvictWorkingSetOrRemainAfterClear() {
        WeightedLruCache<String, String> cache = new WeightedLruCache<>(10, 3, String::length);
        cache.put("a", "abc");
        cache.put("large", "1234");
        assertEquals("abc", cache.get("a"));
        assertNull(cache.get("large"));
        cache.clear();
        assertEquals(0, cache.weight());
        assertEquals(0, cache.size());
        assertNull(cache.get("a"));
    }

    @Test
    void parallelReadersAndWritersKeepEntryAndWeightBudgetsConsistent() throws Exception {
        WeightedLruCache<Integer, Integer> cache = new WeightedLruCache<>(32, 32, ignored -> 1L);
        CyclicBarrier start = new CyclicBarrier(4);
        List<Callable<Void>> tasks = new ArrayList<>();
        for (int worker = 0; worker < 4; worker++) {
            int lane = worker;
            tasks.add(() -> {
                start.await();
                for (int index = 0; index < 5000; index++) {
                    int key = (index + lane * 7) % 64;
                    cache.put(key, key);
                    cache.get((key + 1) % 64);
                }
                return null;
            });
        }
        try (var executor = Executors.newFixedThreadPool(4)) {
            for (var result : executor.invokeAll(tasks)) result.get();
        }
        assertEquals(32, cache.size());
        assertEquals(cache.size(), cache.weight());
    }
}

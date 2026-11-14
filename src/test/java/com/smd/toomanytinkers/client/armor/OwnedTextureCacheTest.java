package com.smd.toomanytinkers.client.armor;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OwnedTextureCacheTest {
    @Test
    void evictsLeastRecentlyUsedAndReleasesExactlyOnce() {
        List<String> released = new ArrayList<>();
        OwnedTextureCache<String, String> cache = new OwnedTextureCache<>(2, 100, String::length, released::add);
        cache.put("a", "first");
        cache.put("b", "second");
        assertEquals("first", cache.get("a"));
        cache.put("c", "third");
        assertNull(cache.get("b"));
        assertEquals(List.of("second"), released);
        cache.clear();
        cache.clear();
        assertEquals(List.of("second", "first", "third"), released);
        assertEquals(0, cache.bytes());
    }

    @Test
    void byteBudgetEvictsEvenWhenEntryLimitIsNotReached() {
        List<String> released = new ArrayList<>();
        OwnedTextureCache<String, String> cache = new OwnedTextureCache<>(100, 5, String::length, released::add);
        cache.put("a", "123");
        cache.put("b", "456");
        assertEquals(List.of("123"), released);
        assertEquals(1, cache.size());
        assertEquals(3, cache.bytes());
    }

    @Test
    void oversizedTextureSurvivesCurrentDrawAndIsReleasedOnReplacement() {
        List<String> released = new ArrayList<>();
        OwnedTextureCache<String, String> cache = new OwnedTextureCache<>(2, 3, String::length, released::add);
        cache.put("large", "123456");
        assertEquals("123456", cache.get("large"));
        assertTrue(released.isEmpty());
        cache.put("next", "12");
        assertEquals(List.of("123456"), released);
        assertEquals(2, cache.bytes());
        cache.put("next", "3");
        assertEquals(List.of("123456", "12"), released);
        assertEquals(1, cache.bytes());
    }
}

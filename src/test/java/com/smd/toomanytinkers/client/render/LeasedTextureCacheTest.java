package com.smd.toomanytinkers.client.render;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class LeasedTextureCacheTest {
    @Test
    void concurrentRequestsShareGenerationAndKeepFramesUntilLastRelease() throws Exception {
        Object lock = new Object();
        AtomicInteger builds = new AtomicInteger();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch resume = new CountDownLatch(1);
        var cache = cache(lock, 0, new ArrayList<>());
        try (var workers = Executors.newFixedThreadPool(2)) {
            var first = workers.submit(() -> cache.acquire("part", () -> {
                assertFalse(Thread.holdsLock(lock));
                builds.incrementAndGet();
                entered.countDown();
                await(resume);
                return new Frames();
            }));
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            var second = workers.submit(() -> cache.acquire("part", () -> {
                builds.incrementAndGet();
                return new Frames();
            }));
            // 正在生成时，其他线程仍可进入所有者的状态锁。
            synchronized (lock) { assertEquals(0, cache.bytes()); }
            resume.countDown();
            var a = first.get(2, TimeUnit.SECONDS);
            var b = second.get(2, TimeUnit.SECONDS);
            assertSame(a.value(), b.value());
            assertEquals(1, builds.get());
            a.close();
            assertTrue(b.value().alive);
            b.close();
            assertFalse(b.value().alive);
        } finally { resume.countDown(); }
    }

    @Test
    void distinctKeysGenerateConcurrently() throws Exception {
        var cache = cache(new Object(), 16, new ArrayList<>());
        CountDownLatch entered = new CountDownLatch(2);
        CountDownLatch resume = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var first = workers.submit(() -> cache.acquire("a", () -> { entered.countDown(); await(resume); return new Frames(); }));
            var second = workers.submit(() -> cache.acquire("b", () -> { entered.countDown(); await(resume); return new Frames(); }));
            boolean concurrent = entered.await(2, TimeUnit.SECONDS);
            resume.countDown();
            first.get(2, TimeUnit.SECONDS).close();
            second.get(2, TimeUnit.SECONDS).close();
            assertTrue(concurrent);
        } finally { resume.countDown(); }
    }

    @Test
    void reloadDiscardsUnpublishedResultAndAllowsFreshGeneration() throws Exception {
        List<Frames> published = new ArrayList<>();
        var cache = cache(new Object(), 16, published);
        Frames obsolete = new Frames();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch resume = new CountDownLatch(1);
        try (var worker = Executors.newSingleThreadExecutor()) {
            var old = worker.submit(() -> cache.acquire("part", () -> {
                entered.countDown(); await(resume); return obsolete;
            }));
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            assertTrue(cache.invalidate().isEmpty());
            var current = cache.acquire("part", Frames::new);
            resume.countDown();
            assertNull(old.get(2, TimeUnit.SECONDS));
            assertFalse(obsolete.alive);
            assertEquals(List.of(current.value()), published);
            current.close();
        } finally { resume.countDown(); }
    }

    @Test
    void reloadDoesNotClearFramesStillInUseOrRecacheTheirLateRelease() {
        var cache = cache(new Object(), 16, new ArrayList<>());
        var held = cache.acquire("part", Frames::new);
        assertTrue(cache.invalidate().isEmpty());
        assertTrue(held.value().alive);
        held.close();
        assertFalse(held.value().alive);
        assertEquals(0, cache.bytes());
    }

    @Test
    void failedGenerationCanBeRetriedAndRecursiveRequestDoesNotDeadlock() {
        var cache = cache(new Object(), 16, new ArrayList<>());
        assertThrows(IllegalStateException.class, () -> cache.acquire("part", () -> {
            throw new IllegalStateException("模拟加载失败");
        }));
        assertThrows(IllegalStateException.class, () -> cache.acquire("part", () -> {
            cache.acquire("part", Frames::new);
            return new Frames();
        }));
        var recovered = cache.acquire("part", Frames::new);
        assertTrue(recovered.value().alive);
        recovered.close();
    }

    @Test
    void budgetEvictionAndGpuTransferRespectActiveReaders() {
        var cache = cache(new Object(), 4, new ArrayList<>());
        var active = cache.acquire("active", Frames::new);
        var older = cache.acquire("older", Frames::new);
        older.close();
        var newer = cache.acquire("newer", Frames::new);
        newer.close();
        assertFalse(older.value().alive);
        assertTrue(active.value().alive);
        var secondReader = cache.retainIfPresent("active");
        active.discard();
        assertTrue(secondReader.value().alive);
        secondReader.close();
        assertFalse(active.value().alive);
        assertEquals(4, cache.bytes());
    }

    private static LeasedTextureCache<String, Frames> cache(Object lock, long budget, List<Frames> published) {
        return new LeasedTextureCache<>(lock, budget, ignored -> 4, ignored -> true,
                value -> { assertTrue(Thread.holdsLock(lock)); published.add(value); },
                value -> { assertFalse(Thread.holdsLock(lock)); value.alive = false; });
    }

    private static void await(CountDownLatch latch) {
        try { assertTrue(latch.await(5, TimeUnit.SECONDS)); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new AssertionError(interrupted); }
    }

    private static final class Frames { boolean alive = true; }
}

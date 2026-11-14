package com.smd.toomanytinkers.client.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GlintRenderScopeTest {
    @Test
    void nestedPassAndExceptionRestoreTheOuterScope() {
        assertFalse(GlintRenderScope.isActive());
        GlintRenderScope.enter();
        try {
            assertTrue(GlintRenderScope.isActive());
            assertThrows(IllegalStateException.class, () -> {
                GlintRenderScope.enter();
                try {
                    assertTrue(GlintRenderScope.isActive());
                    throw new IllegalStateException("模拟原生闪光绘制失败");
                } finally {
                    GlintRenderScope.exit();
                }
            });
            assertTrue(GlintRenderScope.isActive());
        } finally {
            GlintRenderScope.exit();
        }
        assertFalse(GlintRenderScope.isActive());
    }

    @Test
    void anotherThreadDoesNotInheritGlintPass() throws InterruptedException {
        boolean[] active = {true};
        GlintRenderScope.enter();
        try {
            Thread worker = Thread.startVirtualThread(() -> active[0] = GlintRenderScope.isActive());
            worker.join();
            assertFalse(active[0]);
            assertTrue(GlintRenderScope.isActive());
        } finally {
            GlintRenderScope.exit();
        }
    }
}

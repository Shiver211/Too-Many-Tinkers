package com.smd.toomanytinkers.client.diagnostics;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class FrameSamplesTest {
    @Test void retainsOnlyRecentFramesAndReportsNearestRank() {
        FrameSamples samples = new FrameSamples(100);
        for (int i = 1; i <= 200; i++) samples.add(i * 1_000_000L);
        FrameSamples.Summary result = samples.summary();
        assertEquals(100, result.retainedFrames());
        assertEquals(200, result.totalFrames());
        assertEquals(150.0, result.p50Ms());
        assertEquals(195.0, result.p95Ms());
        assertEquals(199.0, result.p99Ms());
    }
}

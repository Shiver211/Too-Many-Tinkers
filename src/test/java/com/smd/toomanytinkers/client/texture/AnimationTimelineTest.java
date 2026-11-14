package com.smd.toomanytinkers.client.texture;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AnimationTimelineTest {
    @Test void restoresNonUniformFrameDurationsAfterEviction() {
        int[] durations = {2, 5, 1};
        assertEquals(new AnimationTimeline.Position(0, 0), AnimationTimeline.at(0, durations));
        assertEquals(new AnimationTimeline.Position(0, 1), AnimationTimeline.at(1, durations));
        assertEquals(new AnimationTimeline.Position(1, 0), AnimationTimeline.at(2, durations));
        assertEquals(new AnimationTimeline.Position(1, 4), AnimationTimeline.at(6, durations));
        assertEquals(new AnimationTimeline.Position(2, 0), AnimationTimeline.at(7, durations));
        assertEquals(new AnimationTimeline.Position(0, 0), AnimationTimeline.at(8, durations));
        assertEquals(new AnimationTimeline.Position(1, 3), AnimationTimeline.at(8_000_005, durations));
    }
}

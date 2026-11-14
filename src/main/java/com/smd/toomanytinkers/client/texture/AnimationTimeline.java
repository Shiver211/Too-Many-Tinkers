package com.smd.toomanytinkers.client.texture;

/** 逐帧时长来自原生 metadata，页被淘汰不改变动画的时间原点。 */
public final class AnimationTimeline {
    private AnimationTimeline() {}

    public static Position at(long elapsedTicks, int[] frameDurations) {
        long cycle = 0;
        for (int duration : frameDurations) cycle += duration;
        long remaining = Math.floorMod(elapsedTicks, cycle);
        for (int frame = 0; frame < frameDurations.length; frame++) {
            if (remaining < frameDurations[frame]) return new Position(frame, (int) remaining);
            remaining -= frameDurations[frame];
        }
        throw new AssertionError("动画周期计算失败");
    }

    public record Position(int frame, int tick) {}
}

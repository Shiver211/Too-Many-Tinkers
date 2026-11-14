package com.smd.toomanytinkers.client.diagnostics;

import java.util.Arrays;

/** 有界记录相邻帧开始时间；包括等待、帧率限制和交换缓冲，不能当作纯 GPU 耗时。 */
public final class FrameSamples {
    private final long[] samples;
    private int size;
    private int next;
    private long total;

    public FrameSamples(int capacity) { samples = new long[capacity]; }

    public void add(long nanos) {
        samples[next] = nanos;
        next = (next + 1) % samples.length;
        size = Math.min(size + 1, samples.length);
        total++;
    }

    public Summary summary() {
        long[] sorted = Arrays.copyOf(samples, size);
        Arrays.sort(sorted);
        return new Summary(size, total, percentile(sorted, .50), percentile(sorted, .95), percentile(sorted, .99));
    }

    private static double percentile(long[] sorted, double quantile) {
        return sorted.length == 0 ? 0 : sorted[(int) Math.ceil(sorted.length * quantile) - 1] / 1_000_000.0;
    }

    public record Summary(int retainedFrames, long totalFrames, double p50Ms, double p95Ms, double p99Ms) { }
}

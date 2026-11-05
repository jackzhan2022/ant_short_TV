package com.antshorttv.operations.telemetry;

import java.util.Arrays;

/** Non-cumulative buckets. Percentiles are approximate upper bounds, never averaged. */
public final class LatencyHistogram {
    public static final long[] BOUNDS = {10,25,50,100,250,500,1000,2000,3000,5000,10000,30000,60000,120000,300000,600000,1800000};
    private final long[] counts = new long[BOUNDS.length + 1];
    public void record(long millis) {
        int index = Arrays.binarySearch(BOUNDS, Math.max(0, millis));
        counts[index < 0 ? -index - 1 : index]++;
    }
    public void merge(LatencyHistogram other) { merge(other.counts); }
    public void merge(long[] values) {
        if (values.length != counts.length) throw new IllegalArgumentException("Histogram boundary mismatch");
        for (int i=0;i<counts.length;i++) counts[i] += Math.max(0, values[i]);
    }
    public long[] counts() { return counts.clone(); }
    public Double percentile(double quantile) {
        int index = rankBucket(quantile);
        return index < 0 || index == BOUNDS.length ? null : (double) BOUNDS[index];
    }
    public boolean exceedsBound(double quantile) { return rankBucket(quantile) == BOUNDS.length; }
    private int rankBucket(double quantile) {
        if (!(quantile > 0 && quantile <= 1)) throw new IllegalArgumentException("Quantile must be in (0,1]");
        long total = Arrays.stream(counts).sum();
        if (total == 0) return -1;
        long rank = (long)Math.ceil(total * quantile), seen=0;
        for (int i=0;i<counts.length;i++) { seen+=counts[i]; if (seen>=rank) return i; }
        return -1;
    }
}

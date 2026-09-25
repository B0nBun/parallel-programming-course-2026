package ru.itmo.parallel;

import java.util.Arrays;

public record Snapshot(
    long[] buckets,
    long count,
    long sum,
    long min,
    long max,
    long p50,
    long p99
) {
    public static final int BUCKETS_N = 256;
    public static final long BUCKET_INTERVAL = 4; // ms

    public static int bucket(long value) {
        return (int)Math.min(value / BUCKET_INTERVAL, BUCKETS_N - 1);
    }

    public static long[] percentiles(long[] buckets, long count) {
        double[] sortedPercentage = new double[]{0.5, 0.99};
        long accumulated = 0;
        int bucket = 0;
        long[] result = new long[sortedPercentage.length];
        for (int i = 0; i < sortedPercentage.length; i ++) {
            assert i > 1 ? sortedPercentage[i - 1] < sortedPercentage[i] : true;
            
            double percentage = sortedPercentage[i]; 
            assert 0 < percentage && percentage <= 1;
            long threshold = (long)(count * percentage);

            while (bucket < buckets.length && accumulated < threshold) {
                accumulated += buckets[bucket];
                bucket ++;
            }

            result[i] = (bucket - 1) * BUCKET_INTERVAL;
        }
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (!(obj instanceof Snapshot other)) {
            return false;
        }

        return count == other.count
            && sum == other.sum
            && min == other.min
            && max == other.max
            && p50 == other.p50
            && p99 == other.p99
            && Arrays.equals(buckets, other.buckets);
    }
}

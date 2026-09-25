package ru.itmo.parallel;

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

    public static long[] percentiles(double[] sortedPercentage, long[] buckets, long count) {
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
}

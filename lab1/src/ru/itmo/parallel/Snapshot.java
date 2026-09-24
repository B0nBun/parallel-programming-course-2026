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
}

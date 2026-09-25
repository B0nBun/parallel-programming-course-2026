package ru.itmo.parallel.collectors;

import ru.itmo.parallel.MetricsCollector;
import ru.itmo.parallel.Snapshot;

public class SingleThreadedCollector implements MetricsCollector {
    private long[] buckets = new long[Snapshot.BUCKETS_N];
    private long min = Long.MAX_VALUE;
    private long max = Long.MIN_VALUE;
    private long sum = 0;
    private long count = 0;
    
    @Override
    public void record(long value) {
        int bucket = Snapshot.bucket(value);
        this.buckets[bucket] += 1;
        this.min = Math.min(this.min, value);
        this.max = Math.max(this.max, value);
        this.sum += value;
        this.count += 1;
    }

    @Override
    public Snapshot snapshot() {
        long[] buckets = this.buckets.clone();
        long[] percentiles = Snapshot.percentiles(buckets, count);
        assert percentiles.length == 2 && percentiles[0] <= percentiles[1];

        return new Snapshot(
            buckets,
            this.count,
            this.sum,
            this.min,
            this.max,
            percentiles[0],
            percentiles[1]
        );
    }
}

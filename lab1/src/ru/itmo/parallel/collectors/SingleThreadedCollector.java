package ru.itmo.parallel.collectors;

import ru.itmo.parallel.MetricsCollector;
import ru.itmo.parallel.Snapshot;

public class SingleThreadedCollector implements MetricsCollector {
    long[] buckets = new long[Snapshot.BUCKETS_N];
    long min = Long.MAX_VALUE;
    long max = Long.MIN_VALUE;
    long sum = 0;
    long count = 0;
    
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

        double threshold50 = this.count * 0.5;
        long p50 = 0;
        long accumulated = 0;
        int i = 0;
        for (;i < this.buckets.length; i ++) {
            accumulated += buckets[i];
            if (accumulated >= threshold50) {
                p50 = accumulated;
                break;
            }
        }
        double threshold99 = this.count * 0.99;
        long p99 = 0;
        for (;i < this.buckets.length; i ++) {
            accumulated += buckets[i];
            if (accumulated >= threshold99) {
                p99 = accumulated;
                break;
            }
        }

        return new Snapshot(
            buckets,
            this.count,
            this.sum,
            this.min,
            this.max,
            p50,
            p99
        );
    }
}

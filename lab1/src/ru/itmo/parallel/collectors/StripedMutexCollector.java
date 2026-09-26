package ru.itmo.parallel.collectors;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

import ru.itmo.parallel.MetricsCollector;
import ru.itmo.parallel.Snapshot;

public class StripedMutexCollector implements MetricsCollector {
    private static final int BUCKET_GROUPS = 16;
    
    private final Object[] bucketLocks = new Object[BUCKET_GROUPS];
    private final long[] buckets = new long[Snapshot.BUCKETS_N];
    private final AtomicLong min = new AtomicLong(Long.MAX_VALUE);
    private final AtomicLong max = new AtomicLong(Long.MIN_VALUE);
    private final AtomicLong sum = new AtomicLong(0);
    private final AtomicLong count = new AtomicLong(0);

    public StripedMutexCollector() {
        for (int i = 0; i < this.bucketLocks.length; i ++) {
            this.bucketLocks[i] = new ReentrantLock();
        }
    }
    
    @Override
    public void record(long value) {
        int bucket = Snapshot.bucket(value);
        synchronized (this.lockForBucket(bucket)) {
            this.buckets[bucket] += 1;
        }

        this.sum.addAndGet(value);
        this.count.addAndGet(1);

        while (true) {
            long min = this.min.get();
            if (value >= min) { break; }
            if (this.min.compareAndSet(min, value)) { break; }
        }
        while (true) {
            long max = this.max.get();
            if (value <= max) { break; }
            if (this.max.compareAndSet(max, value)) { break; }
        }
    }

    @Override
    public Snapshot snapshot() {
        long[] bucketsCopy = new long[this.buckets.length];
        for (int i = 0; i < this.bucketLocks.length; i ++) {
            synchronized (this.bucketLocks[i]) {
                for (int j = i; j < this.buckets.length; j += this.bucketLocks.length) {
                    assert this.lockForBucket(j) == this.bucketLocks[i];
                    bucketsCopy[j] = this.buckets[j];
                }
            }
        }

        long count = this.count.get();

        long[] percentiles = Snapshot.percentiles(buckets, count);
        assert percentiles.length == 2 && percentiles[0] <= percentiles[1];

        return new Snapshot(
            bucketsCopy,
            count,
            this.sum.get(),
            this.min.get(),
            this.max.get(),
            percentiles[0],
            percentiles[1]
        );
    }

    private Object lockForBucket(int bucketIdx) {
        return this.bucketLocks[bucketIdx % BUCKET_GROUPS];
    }
}

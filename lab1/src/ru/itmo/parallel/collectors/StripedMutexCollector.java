package ru.itmo.parallel.collectors;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

import ru.itmo.parallel.MetricsCollector;
import ru.itmo.parallel.Snapshot;

public class StripedMutexCollector implements MetricsCollector {
    private static final int BUCKET_GROUPS = 16;
    
    private final ReentrantLock[] bucketLocks = new ReentrantLock[BUCKET_GROUPS];
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
        ReentrantLock lock = this.lockForBucket(bucket);
        lock.lock();
        this.buckets[bucket] += 1;
        lock.unlock();

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
            this.bucketLocks[i].lock();
            for (int j = i; j < this.buckets.length; j += this.bucketLocks.length) {
                assert this.lockForBucket(j) == this.bucketLocks[i];
                bucketsCopy[j] = this.buckets[j];
            }
            this.bucketLocks[i].unlock();
        }

        long count = this.count.get();

        double threshold50 = count * 0.5;
        long p50 = 0;
        long accumulated = 0;
        int i = 0;
        for (;i < this.buckets.length; i ++) {
            accumulated += bucketsCopy[i];
            if (accumulated >= threshold50) {
                p50 = accumulated;
                break;
            }
        }
        double threshold99 = count * 0.99;
        long p99 = 0;
        for (;i < this.buckets.length; i ++) {
            accumulated += bucketsCopy[i];
            if (accumulated >= threshold99) {
                p99 = accumulated;
                break;
            }
        }

        return new Snapshot(
            bucketsCopy,
            count,
            this.sum.get(),
            this.min.get(),
            this.max.get(),
            p50,
            p99
        );
    }

    private ReentrantLock lockForBucket(int bucketIdx) {
        return this.bucketLocks[bucketIdx % BUCKET_GROUPS];
    }
}

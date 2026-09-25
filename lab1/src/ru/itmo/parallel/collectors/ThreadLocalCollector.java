package ru.itmo.parallel.collectors;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;

import ru.itmo.parallel.MetricsCollector;
import ru.itmo.parallel.Snapshot;

public class ThreadLocalCollector implements MetricsCollector {
    private final List<ThreadState> allStates = new ArrayList<>();
    private final ThreadLocal<ThreadState> state = ThreadLocal.withInitial(() -> {
        ThreadState state = new ThreadState();
        synchronized (this.allStates) {
            this.allStates.add(state);
        }
        return state;
    });
    
    @Override
    public void record(long value) {
        ThreadState s = this.state.get();
        int bucket = Snapshot.bucket(value);
        s.buckets.setRelease(bucket, s.buckets.getPlain(bucket) + 1);
        s.count.setRelease(s.count.getPlain() + 1);
        s.sum.setRelease(s.sum.getPlain() + value);

        if (value < s.min.getPlain()) {
            s.min.setRelease(value);
        }
        if (value > s.max.getPlain()) {
            s.max.setRelease(value);
        }
    }

    @Override
    public Snapshot snapshot() {
        List<ThreadState> states = null;
        synchronized (this.allStates) {
            states = new ArrayList<>(this.allStates);
        }
        long[] buckets = new long[Snapshot.BUCKETS_N];
        long count = 0;
        long sum = 0;
        long min = Long.MAX_VALUE;
        long max = Long.MIN_VALUE;
        for (ThreadState s : states) {
            for (int i = 0; i < buckets.length; i ++) {
                buckets[i] = s.buckets.get(i);
            }
            count += s.count.get();
            sum += s.sum.get();
            min = Math.min(min, s.min.get());
            max = Math.max(max, s.max.get());
        }
        long[] percentiles = Snapshot.percentiles(buckets, count);
        assert percentiles.length == 2 && percentiles[0] <= percentiles[1];

        return new Snapshot(buckets, count, sum, min, max, percentiles[0], percentiles[1]);
    }

    private static class ThreadState {
        final AtomicLongArray buckets = new AtomicLongArray(256);
        final AtomicLong count = new AtomicLong();
        final AtomicLong sum = new AtomicLong();
        final AtomicLong min = new AtomicLong(Long.MAX_VALUE);
        final AtomicLong max = new AtomicLong(0);
    }
}



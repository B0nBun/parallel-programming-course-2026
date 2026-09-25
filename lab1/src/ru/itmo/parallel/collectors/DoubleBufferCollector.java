package ru.itmo.parallel.collectors;

import java.util.concurrent.atomic.AtomicInteger;

import ru.itmo.parallel.MetricsCollector;
import ru.itmo.parallel.Snapshot;

public class DoubleBufferCollector implements MetricsCollector {
    @Override
    public void record(long value) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public Snapshot snapshot() {
        // TODO Auto-generated method stub
        return null;
    }

    private static class ThreadBuffers {
        final long[][] buckets = new long[2][Snapshot.BUCKETS_N];
        final long[] count = new long[2];
        final long[] sum = new long[2];
        final long[] min = new long[]{Long.MAX_VALUE, Long.MAX_VALUE};
        final long[] max = new long[]{Long.MIN_VALUE, Long.MIN_VALUE};
        // -1 = not writing anywhere
        // 0  = writing to 0th buffer
        // 1  = writing to 1st buffer
        final AtomicInteger inside = new AtomicInteger(-1);
    }
}

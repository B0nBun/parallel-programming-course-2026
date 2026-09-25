package ru.itmo.parallel.collectors;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import ru.itmo.parallel.MetricsCollector;
import ru.itmo.parallel.Snapshot;

public class DoubleBufferCollector implements MetricsCollector {
    private final List<ThreadState> allStates = new ArrayList<>();
    private volatile int activeBuffer = 0;
    private final Object snapshotLock = new Object();
    private final ThreadBuffer global = new ThreadBuffer();
    
    private final ThreadLocal<ThreadState> state = ThreadLocal.withInitial(() -> {
        ThreadState statePair = new ThreadState();
        synchronized (this.snapshotLock) {
            this.allStates.add(statePair);
        }
        return statePair;
    });
    
    @Override
    public void record(long value) {
        ThreadState state = this.state.get();
        int active = -1;
        while (true) {
            active = this.activeBuffer;
            state.writingBuffer.set(active);
            if (this.activeBuffer == active) {
                break;
            }
            state.writingBuffer.set(ThreadState.NEITHER);
        }
        assert active == 0 || active == 1;

        int bucket = Snapshot.bucket(value);
        ThreadBuffer buf = state.buffers[active];
        buf.buckets[bucket] ++;
        buf.count ++;
        buf.sum += value;
        buf.min = Math.min(buf.min, value);
        buf.max = Math.max(buf.max, value);
        state.writingBuffer.set(ThreadState.NEITHER);        
    }

    @Override
    public Snapshot snapshot() {
        synchronized (snapshotLock) {
            int old = this.activeBuffer;
            this.activeBuffer = 1 - old;
            
            for (var state : allStates) {
                while (state.writingBuffer.get() == old) {
                    Thread.onSpinWait();
                }
                this.global.combineWith(state.buffers[old]);
                state.buffers[old].clear();
            }
            long[] buckets = this.global.buckets.clone();
            long[] percentiles = Snapshot.percentiles(buckets, this.global.count);
            assert percentiles.length == 2 && percentiles[0] <= percentiles[1];

            return new Snapshot(
                buckets,
                this.global.count,
                this.global.sum,
                this.global.min,
                this.global.max,
                percentiles[0],
                percentiles[1]
            );
        }
    }


    private static class ThreadState {
        final ThreadBuffer[] buffers = new ThreadBuffer[]{new ThreadBuffer(), new ThreadBuffer()};
        AtomicInteger writingBuffer = new AtomicInteger(NEITHER);
        static final int NEITHER = -1;
    }

    private static class ThreadBuffer {
        final long[] buckets = new long[Snapshot.BUCKETS_N];
        long count;
        long sum;
        long min;
        long max;

        public ThreadBuffer() {
            this.clear();
        }

        void clear() {
            this.count = 0;
            this.sum = 0;
            this.min = Long.MAX_VALUE;
            this.max = Long.MIN_VALUE;
            Arrays.fill(this.buckets, 0);
        }

        void combineWith(ThreadBuffer other) {
            this.count += other.count;
            this.sum += other.sum;
            this.min = Math.min(this.min, other.min);
            this.max = Math.max(this.max, other.max);

            assert this.buckets.length == other.buckets.length;
            for (int i = 0; i < this.buckets.length; i ++) {
                this.buckets[i] += other.buckets[i];
            }
        }
    }
}

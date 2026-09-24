package ru.itmo.parallel.collectors;

import ru.itmo.parallel.Snapshot;

public class MutexCollector extends SingleThreadedCollector {
    @Override
    public synchronized void record(long value) {
        super.record(value);
    }

    @Override
    public synchronized Snapshot snapshot() {
        return super.snapshot();
    }
}

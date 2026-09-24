package ru.itmo.parallel.collectors;

import ru.itmo.parallel.MetricsCollector;
import ru.itmo.parallel.Snapshot;

public class EmptyMutexCollector implements MetricsCollector {
    @Override
    public synchronized void record(long value) {}

    @Override
    public synchronized Snapshot snapshot() {
        return new Snapshot(null, 0, 0, 0, 0, 0, 0);
    }
}

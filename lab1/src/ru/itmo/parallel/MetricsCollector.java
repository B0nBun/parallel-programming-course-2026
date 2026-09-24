package ru.itmo.parallel;

public interface MetricsCollector {
    void record(long value);
    Snapshot snapshot();
}

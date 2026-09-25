package ru.itmo.parallel;

import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;
import java.util.function.Supplier;

import ru.itmo.parallel.collectors.DoubleBufferCollector;
import ru.itmo.parallel.collectors.EmptyMutexCollector;
import ru.itmo.parallel.collectors.MutexCollector;
import ru.itmo.parallel.collectors.StripedMutexCollector;
import ru.itmo.parallel.collectors.ThreadLocalCollector;
import ru.itmo.parallel.collectors.SingleThreadedCollector;
import ru.itmo.parallel.helper.SingleLinePrinter;
import ru.itmo.parallel.helper.ZipfDistribution;

public class Main {
    private static final int SEED = 67;
    private static final int MAX_VALUE = 1023;
    private static final long VALUES_N = 1 << 20;
    private static final Duration MEASURE_RUNTIME = Duration.ofSeconds(5);
    private static final int MEASURE_RUNS = 5;
    private static final int STRESS_SNAPSHOTS = 10_000;
    private static final Map<String, Supplier<MetricsCollector>> collectors = Map.ofEntries(
        Map.entry("single",        SingleThreadedCollector::new),
        Map.entry("mutex",         MutexCollector::new),
        Map.entry("empty-mutex",   EmptyMutexCollector::new),
        Map.entry("striped-mutex", StripedMutexCollector::new),
        Map.entry("thread-local",  ThreadLocalCollector::new),
        Map.entry("double-buffer", DoubleBufferCollector::new)
    );

    public static void main(String args[]) throws InterruptedException {
        Args arguments = Args.parse(args);
        
        Supplier<MetricsCollector> collectorSup = Main.collectors.get(arguments.collectorName());
        if (collectorSup == null) {
            throw new IllegalArgumentException("unknown collector name");
        }
        MetricsCollector collector = collectorSup.get();
        long[] values = Main.generateValues(SEED);

        if (arguments.testType() == TestType.BENCHMARK) {
            long opsPerSecond = Main.benchmark(collector, values, arguments.threads());
            System.out.printf("%s\t%d\t%d\n", arguments.collectorName(), arguments.threads(), opsPerSecond);
            return;
        }
        if (arguments.testType() == TestType.STRESS) {
            StressResult result = Main.stress(collector, values, arguments.threads());
            System.out.printf(
                "%s\t%d\tinvalid=%.01f%% (lt_count=%d gt_count=%d) total_diff=%d\n",
                arguments.collectorName(),
                arguments.threads(),
                (double)(result.lessInBuckets() + result.moreInBuckets()) / STRESS_SNAPSHOTS * 100,
                result.lessInBuckets(),
                result.moreInBuckets(),
                result.collectorCount() - result.threadsCount()
            );
            return;
        }
        if (arguments.testType() == TestType.PROPERTY) {
            MetricsCollector targetCollector = new SingleThreadedCollector();
            Main.testAgainst(collector, targetCollector, values);
            System.out.printf(
                "Collector %s matches %s\n",
                collector.getClass().getSimpleName(),
                targetCollector.getClass().getSimpleName()
            );
            return;
        }
        throw new UnsupportedOperationException("specified test type is not implemented: " + arguments.testType());
    }

    private static record StressResult(
        long moreInBuckets,
        long lessInBuckets,
        long equalToBuckets,
        long threadsCount,
        long collectorCount
    ) {}

    private static StressResult stress(
        MetricsCollector collector,
        long[] values,
        int threadsN
    ) throws InterruptedException {
        var start = new CountDownLatch(1);
        var stop = new AtomicBoolean(false);
        var threadCounts = new long[threadsN];
        var threads = new Thread[threadsN];

        for (int k_ = 0; k_ < threadsN; k_ ++) {
            int k = k_;
            threads[k] = new Thread(() -> {
                try {
                    long local_count = 0;
                    int i = k * 1000;
                    start.await();
                    while (!stop.get()) {
                        collector.record(values[i]);
                        local_count ++;
                        i = (i + 1) % values.length;
                    }
                    threadCounts[k] = local_count;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            threads[k].start();
        }

        var printer = new SingleLinePrinter(System.out);
        long moreInBuckets = 0;
        long lessInBuckets = 0;
        long equal = 0;
        start.countDown();
        for (int i = 0; i < STRESS_SNAPSHOTS; i ++) {
            printer.printf("snapshot %d", i);
            Snapshot snapshot = collector.snapshot();
            long bucketsSum = Arrays.stream(snapshot.buckets()).sum();
            long count = snapshot.count();
            if (bucketsSum < count) {
                lessInBuckets ++;
            } else if (bucketsSum > count) {
                moreInBuckets ++;
            } else {
                equal ++;
            }
        }
        stop.set(true);
        printer.print("joining threads");
        for (int k = 0; k < threadsN; k ++) {
            threads[k].join();
        }
        long threadsCount = Arrays.stream(threadCounts).sum();
        long collectorCount = collector.snapshot().count();
        printer.emptyLine();
        return new StressResult(moreInBuckets, lessInBuckets, equal, threadsCount, collectorCount);
    }

    private static long benchmark(
        MetricsCollector collector,
        long[] values,
        int threadsN
    ) throws InterruptedException {
        var printer = new SingleLinePrinter(System.out);
        printer.print("running warmup");
        Main.benchmarkRound(collector, values, threadsN, MEASURE_RUNTIME);

        var results = new long[MEASURE_RUNS];
        for (int i = 0; i < MEASURE_RUNS; i ++) {
            printer.printf("running round %d", i + 1);
            System.out.flush();
            results[i] = Main.benchmarkRound(collector, values, threadsN, MEASURE_RUNTIME);
        }
        printer.emptyLine();
        return median(results);
    }

    // returns operations per second
    private static long benchmarkRound(
        MetricsCollector collector,
        long[] values,
        int threadsN,
        Duration sleep
    ) throws InterruptedException {
        var start = new CountDownLatch(1);
        var stop = new AtomicBoolean(false);
        var operations = new long[threadsN];
        var threads = new Thread[threadsN];

        for (int k_ = 0; k_ < threadsN; k_ ++) {
            int k = k_;
            threads[k] = new Thread(() -> {
                try {
                    long local_count = 0;
                    int i = k * 1000;
                    start.await();
                    while (!stop.get()) {
                        collector.record(values[i]);
                        local_count ++;
                        i = (i + 1) % values.length;
                    }
                    operations[k] = local_count;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            threads[k].start();
        }

        long startTimeNs = System.nanoTime();
        start.countDown();
        Thread.sleep(sleep);
        stop.set(true);
        long endTimeNs = System.nanoTime();
        for (int k = 0; k < threadsN; k ++) {
            threads[k].join();
        }
        long seconds = Duration.ofNanos(endTimeNs - startTimeNs).toSeconds();
        return Arrays.stream(operations).sum() / seconds;
    }

    private static void testAgainst(
        MetricsCollector actual,
        MetricsCollector expected,
        long[] values
    ) {
        for (int i = 0; i < values.length; i ++) {
            long value = values[i];
            expected.record(value);
            actual.record(value);

            if (i % 10 == 0) {
                Main.assertSnapshotsEqual(actual, actual.snapshot(), expected.snapshot());
            }
            if (i % 100 == 0) { // occasional consequitive snapshots
                Main.assertSnapshotsEqual(actual, actual.snapshot(), expected.snapshot());
            }
        }
    }

    private static void assertSnapshotsEqual(
        MetricsCollector actualCollector,
        Snapshot actual,
        Snapshot expected
    ) {
        if (expected.equals(actual)) {
            return;
        }
        throw new AssertionError(String.format(
            "Snapshots are not equal:\n\texpected: %s\n\tactual: %s\n\tcollector:%s",
            expected.toString(),
            actual.toString(),
            actualCollector.getClass().getSimpleName()
        ));
    }
    
    private static long[] generateValues(int seed) {
        var distribution = new ZipfDistribution(MAX_VALUE + 1, 1.15, new Random(seed));
        long[] values = IntStream.generate(distribution::sample)
            .asLongStream()
            .map(v -> v - 1) // [1:1024] -> [0:1023]
            .limit(VALUES_N)
            .toArray();
        return values;
    }

    private static long median(long values[]) {
        Arrays.sort(values);
        if (values.length % 2 == 0) {
            return values[values.length / 2];
        } else {
            return (values[values.length / 2 - 1] + values[values.length / 2]) / 2;
        }
    }
}

enum TestType {
    BENCHMARK,
    STRESS,
    PROPERTY,
};

record Args(
    String collectorName,
    int threads,
    TestType testType
) {
    private static String USAGE = "USAGE: ./lab1.jar {--benchmark | --stress | --property} [--threads N] --collector <name>";

    public static Args parse(String args[]) {
        try {
            return Args.parseThrowing(args);
        } catch (IllegalArgumentException e) {
            System.err.println(USAGE);
            System.err.println(e.getMessage());
            System.exit(1);
            return null;
        }
    }
    
    public static Args parseThrowing(String args[]) {
        String collectorName = null;
        int threads = 1;
        TestType testType = null;

        for (int i = 0; i < args.length; i ++) {
            switch (args[i]) {
                case "--collector":
                    collectorName = args[++i];
                    break;
                case "--threads":
                    threads = Integer.parseInt(args[++i]);
                    break;
                case "--benchmark":
                    testType = TestType.BENCHMARK;
                    break;
                case "--stress":
                    testType = TestType.STRESS;
                    break;
                case "--property":
                    testType = TestType.PROPERTY;
                    break;
                default:
                    throw new IllegalArgumentException("Unknown command line argument: " + args[i]);
            }
        }
        if (collectorName == null) {
            throw new IllegalArgumentException("--collector argument must be specified");
        }
        if (testType == null) {
            throw new IllegalArgumentException("--stress or --benchmark test type must be specified");
        }
        return new Args(collectorName, threads, testType);
    }
};

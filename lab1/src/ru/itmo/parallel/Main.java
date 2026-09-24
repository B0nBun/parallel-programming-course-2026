package ru.itmo.parallel;

import java.time.Duration;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;

import ru.itmo.parallel.collectors.EmptyMutexCollector;
import ru.itmo.parallel.collectors.MutexCollector;
import ru.itmo.parallel.collectors.ShardedMutexCollector;
import ru.itmo.parallel.collectors.SingleThreadedCollector;
import ru.itmo.parallel.helper.SingleLinePrinter;
import ru.itmo.parallel.helper.ZipfDistribution;

public class Main {
    private static final int SEED = 67;
    private static final int MAX_VALUE = 1023;
    private static final long VALUES_N = 1 << 20;
    private static final Duration MEASURE_RUNTIME = Duration.ofSeconds(5);
    private static final int MEASURE_RUNS = 5;

    public static void main(String args[]) throws InterruptedException {
        MetricsCollector collector = null;
        int threads = 1;

        for (int i = 0; i < args.length; i ++) {
            switch (args[i]) {
                case "--collector":
                    collector = Main.collectorByName(args[++i]);
                    break;
                case "--threads":
                    threads = Integer.parseInt(args[++i]);
                    break;
                default:
                    throw new IllegalArgumentException("Unknown command line argument: " + args[i]);
            }
        }
        if (collector == null) {
            throw new IllegalArgumentException("--collector argument must be specified");
        }
        
        long[] values = Main.generateValues(SEED);
        long opsPerSecond = Main.measurePoint(collector, values, threads);
        System.out.printf("%s\t%d\t%d\n", collector.getClass().getSimpleName(), threads, opsPerSecond);
    }

    public static MetricsCollector collectorByName(String name) {
        switch (name) {
            case "single": return new SingleThreadedCollector();
            case "mutex": return new MutexCollector();
            case "empty-mutex": return new EmptyMutexCollector();
            case "sharded-mutex": return new ShardedMutexCollector();
            default:
                throw new IllegalArgumentException("Unknown collector: " + name);
        }
    }

    private static long measurePoint(
        MetricsCollector collector,
        long[] values,
        int threadsN
    ) throws InterruptedException {
        var printer = new SingleLinePrinter(System.out);
        printer.print("running warmup");
        Main.run(collector, values, threadsN, MEASURE_RUNTIME);

        var results = new long[MEASURE_RUNS];
        for (int i = 0; i < MEASURE_RUNS; i ++) {
            printer.printf("running round %d\r", i + 1);
            System.out.flush();
            results[i] = Main.run(collector, values, threadsN, MEASURE_RUNTIME);
        }
        printer.emptyLine();
        return median(results);
    }

    // returns operations per second
    private static long run(
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

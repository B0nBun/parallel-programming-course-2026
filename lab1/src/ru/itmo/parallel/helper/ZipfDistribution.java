package ru.itmo.parallel.helper;

import java.util.random.RandomGenerator;

public class ZipfDistribution {
    private final double[] cdf;
    private final RandomGenerator rng;

    public ZipfDistribution(int n, double s, RandomGenerator rng) {
        this.rng = rng;
        this.cdf = new double[n];

        double sum = 0;
        for (int k = 1; k <= n; k++) {
            sum += 1.0 / Math.pow(k, s);
            cdf[k - 1] = sum;
        }

        for (int i = 0; i < n; i++) {
            cdf[i] /= sum;
        }
    }

    public int sample() {
        double x = rng.nextDouble();

        int lo = 0, hi = cdf.length - 1;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (x <= cdf[mid]) hi = mid;
            else lo = mid + 1;
        }
        return lo + 1;
    }
}

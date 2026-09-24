#!/usr/bin/env python3

import sys
from collections import defaultdict

import matplotlib.pyplot as plt
from matplotlib.ticker import LogLocator, ScalarFormatter


def main():
    data = defaultdict(list)

    for line in sys.stdin:
        line = line.strip()

        if not line:
            continue

        parts = line.split()
        if len(parts) < 3:
            continue

        collector = parts[0]
        threads = int(parts[1])
        ops = int(parts[2])

        data[collector].append((threads, ops))

    if not data:
        print("No benchmark data received.", file=sys.stderr)
        sys.exit(1)

    for collector, values in data.items():
        values.sort()
        threads, ops = zip(*values)

        plt.plot(
            threads,
            ops,
            marker="o",
            label=collector,
        )

    plt.xlabel("Threads")
    plt.ylabel("Operations / second")
    plt.title("Collector throughput")

    # Thread counts are powers of two, so use a logarithmic X axis.
    plt.xscale("log", base=2)

    all_threads = sorted({
        threads
        for values in data.values()
        for threads, _ in values
    })
    plt.xticks(all_threads, [str(x) for x in all_threads])

    # Use logarithmic Y axis.
    plt.yscale("log")

    # More Y-axis ticks:
    # Major ticks at 1, 2, 3, ... × powers of 10
    # Minor ticks at 2, 3, ..., 9 × powers of 10.
    ax = plt.gca()

    ax.yaxis.set_major_locator(
        LogLocator(base=10, subs=(1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0))
    )

    ax.yaxis.set_minor_locator(
        LogLocator(
            base=10,
            subs=(1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0),
        )
    )

    # Show numeric labels instead of scientific notation.
    ax.yaxis.set_major_formatter(ScalarFormatter())
    ax.ticklabel_format(axis="y", style="plain")

    # Major and minor grid lines.
    ax.grid(True, which="major", alpha=0.4)
    ax.grid(True, which="minor", alpha=0.15)

    plt.legend()
    plt.tight_layout()

    plt.savefig("benchmark.png", dpi=150)
    plt.show()


if __name__ == "__main__":
    main()

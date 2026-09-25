#!/usr/bin/env python3

import math
import sys
from collections import defaultdict

import matplotlib.pyplot as plt
from matplotlib.ticker import LogLocator, FuncFormatter


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

    plt.xticks(
        all_threads,
        [str(x) for x in all_threads],
    )

    plt.yscale("log")

    ax = plt.gca()

    ax.yaxis.set_major_locator(
        LogLocator(
            base=10,
            subs=(1.0, 2.0, 5.0),
        )
    )

    ax.yaxis.set_minor_locator(
        LogLocator(
            base=10,
            subs=(3.0, 4.0, 6.0, 7.0, 8.0, 9.0),
        )
    )

    all_ops = [
        ops
        for values in data.values()
        for _, ops in values
    ]

    max_ops = max(all_ops)

    exponent = int(math.floor(math.log10(max_ops)))
    scale = 10 ** exponent

    def format_y(value, _position):
        scaled = value / scale
        return f"{scaled:g}"

    ax.yaxis.set_major_formatter(
        FuncFormatter(format_y)
    )

    ax.yaxis.set_minor_formatter(
        FuncFormatter(lambda value, position: "")
    )

    ax.text(
        0.0,
        1.02,
        rf"$\times 10^{{{exponent}}}$",
        transform=ax.transAxes,
        ha="left",
        va="bottom",
    )

    ax.grid(
        True,
        which="major",
        alpha=0.4,
    )

    ax.grid(
        True,
        which="minor",
        alpha=0.15,
    )

    plt.legend()
    plt.tight_layout()

    plt.savefig("benchmark.png", dpi=150)
    plt.show()


if __name__ == "__main__":
    main()

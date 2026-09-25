package ru.itmo.parallel.helper;

import java.io.PrintStream;

public class SingleLinePrinter {
    private final PrintStream stream;
    private int lastLen = 0;

    public SingleLinePrinter(PrintStream stream) {
        this.stream = stream;
    }

    public void print(String s) {
        emptyLine();
        this.lastLen = s.length();
        stream.print(s);
        stream.flush();
    }

    public void printf(String format, Object... args) {
        print(String.format(format, args));
    }

    public void emptyLine() {
        stream.print("\r" + " ".repeat(this.lastLen) + "\r");
        stream.flush();
    }
}

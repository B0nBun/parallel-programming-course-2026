package ru.itmo.parallel.helper;

import java.io.PrintStream;

public class SingleLinePrinter {
    PrintStream stream;
    int previousLen = 0;

    public SingleLinePrinter(PrintStream stream) {
        this.stream = stream;
    }

    public void print(String s) {
        this.emptyLine();
        this.stream.print(s);
    }

    public void printf(String s, Object... args) {
        this.emptyLine();
        this.stream.printf(s, args);
    }

    public void emptyLine() {
        this.stream.print("\r" + " ".repeat(this.previousLen) + "\r");
        this.stream.flush();
    }
}

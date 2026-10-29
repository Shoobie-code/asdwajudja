package com.skyblockminer.helpers;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * A small in-memory ring buffer of recent macro messages, so a bug report can include the last lines without digging
 * through {@code latest.log}. Oldest lines are dropped once it is full. Thread-safe: messages can arrive off the client
 * thread (webhook, scripts).
 */
public final class MacroLog {
    /** The buffer the mod records into; dumped by {@code /sm log}. */
    public static final MacroLog SHARED = new MacroLog(500);

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final int capacity;
    private final Deque<String> lines = new ArrayDeque<>();

    public MacroLog(int capacity) {
        this.capacity = Math.max(1, capacity);
    }

    /** Records {@code text} with a wall-clock timestamp, dropping the oldest line if the buffer is full. */
    public void add(String text) {
        String line = TIME.format(Instant.now().atZone(ZoneId.systemDefault())) + "  " + text;
        synchronized (this.lines) {
            while (this.lines.size() >= this.capacity) {
                this.lines.removeFirst();
            }
            this.lines.addLast(line);
        }
    }

    /** The recorded lines, oldest first. */
    public List<String> snapshot() {
        synchronized (this.lines) {
            return new ArrayList<>(this.lines);
        }
    }

    /** The recorded lines as one block of text, oldest first. */
    public String dump() {
        return String.join("\n", this.snapshot());
    }

    public int size() {
        synchronized (this.lines) {
            return this.lines.size();
        }
    }

    public void clear() {
        synchronized (this.lines) {
            this.lines.clear();
        }
    }
}

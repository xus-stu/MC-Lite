package mclite;

import java.util.concurrent.atomic.AtomicLong;

public final class TaskProgress implements ProgressSink {
    private final AtomicLong done = new AtomicLong();
    private final AtomicLong total = new AtomicLong(-1);
    private volatile String stage = "准备中…";
    private volatile boolean indeterminate = true;
    private volatile long lastSampleTime = System.currentTimeMillis();
    private volatile long lastSampleDone;
    private volatile double bytesPerSecond;

    @Override
    public void total(long totalBytes) {
        total.set(Math.max(0, totalBytes));
        indeterminate = totalBytes <= 0;
    }

    @Override
    public void addTotal(long bytes) {
        if (bytes > 0) {
            total.updateAndGet(current -> current < 0 ? bytes : current + bytes);
            indeterminate = false;
        }
    }

    @Override
    public void add(long bytes) {
        if (bytes <= 0) {
            return;
        }
        long current = done.addAndGet(bytes);
        long now = System.currentTimeMillis();
        long elapsed = now - lastSampleTime;
        if (elapsed >= 450) {
            long delta = current - lastSampleDone;
            if (delta > 0) {
                double instant = delta * 1000.0 / elapsed;
                bytesPerSecond = bytesPerSecond <= 0 ? instant
                        : bytesPerSecond * 0.65 + instant * 0.35;
            }
            lastSampleDone = current;
            lastSampleTime = now;
        }
    }

    @Override
    public void stage(String text) {
        if (text != null && !text.isBlank()) {
            stage = text;
        }
    }

    @Override
    public void indeterminate(boolean value) {
        indeterminate = value;
    }

    public long done() {
        return done.get();
    }

    public long total() {
        return total.get();
    }

    public String stage() {
        return stage;
    }

    public boolean isIndeterminate() {
        return indeterminate;
    }

    public int percent() {
        long t = total.get();
        if (t <= 0) {
            return -1;
        }
        return (int) Math.max(0, Math.min(100, done.get() * 100 / t));
    }

    public long remaining() {
        long t = total.get();
        return t <= 0 ? -1 : Math.max(0, t - done.get());
    }

    public double bytesPerSecond() {
        return bytesPerSecond;
    }

    public long etaSeconds() {
        long remaining = remaining();
        double speed = bytesPerSecond;
        if (remaining < 0 || speed <= 1) {
            return -1;
        }
        return Math.max(0, Math.round(remaining / speed));
    }
}

package mclite;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class DownloadTask {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    public enum State { RUNNING, DONE, FAILED, CANCELLED }

    private final String title;
    private final String source;
    private final String startedAt = LocalDateTime.now().format(TIME);
    private volatile String stage = "准备中…";
    private volatile long done;
    private volatile long total = -1;
    private volatile double bytesPerSecond;
    private volatile long etaSeconds = -1;
    private volatile State state = State.RUNNING;

    public DownloadTask(String title, String source) {
        this.title = title;
        this.source = source == null ? "未知来源" : source;
    }

    public DownloadTask(String title) {
        this(title, "Mojang 官方源");
    }

    public String title() {
        return title;
    }

    public String source() {
        return source;
    }

    public String startedAt() {
        return startedAt;
    }

    public String stage() {
        return stage;
    }

    public void stage(String value) {
        if (value != null && !value.isBlank()) {
            stage = value;
        }
    }

    public long done() {
        return done;
    }

    public long total() {
        return total;
    }

    public void progress(long done, long total) {
        this.done = Math.max(0, done);
        this.total = total;
    }

    public void speed(double bytesPerSecond, long etaSeconds) {
        this.bytesPerSecond = Math.max(0, bytesPerSecond);
        this.etaSeconds = etaSeconds;
    }

    public double bytesPerSecond() {
        return bytesPerSecond;
    }

    public long remaining() {
        return total <= 0 ? -1 : Math.max(0, total - done);
    }

    public long etaSeconds() {
        return etaSeconds;
    }

    public State state() {
        return state;
    }

    public void state(State value) {
        state = value;
    }

    public int percent() {
        if (total <= 0) {
            return -1;
        }
        return (int) Math.max(0, Math.min(100, done * 100 / total));
    }

    public String text() {
        int percent = percent();
        return stage + (percent >= 0 && state == State.RUNNING ? " · " + percent + "%" : "");
    }

    public String remainingText() {
        if (state != State.RUNNING || total <= 0) {
            return "";
        }
        String text = "剩余 " + Formatting.bytes(remaining());
        if (bytesPerSecond > 0) {
            text += " · " + Formatting.bytes(Math.round(bytesPerSecond)) + "/s";
        }
        if (etaSeconds >= 0) {
            text += " · 约 " + formatEta(etaSeconds);
        }
        return text;
    }

    private static String formatEta(long seconds) {
        if (seconds < 60) {
            return seconds + " 秒";
        }
        if (seconds < 3600) {
            return (seconds / 60) + " 分 " + (seconds % 60) + " 秒";
        }
        return (seconds / 3600) + " 小时 " + ((seconds % 3600) / 60) + " 分";
    }
}

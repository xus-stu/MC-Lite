package dev.mclite.island.core;

import java.util.List;

/** 一份加载好的歌词：按时间查找当前行。 */
public final class Lyrics {

    public static final Lyrics EMPTY = new Lyrics(List.of());

    private final List<LrcLine> lines;

    public Lyrics(List<LrcLine> lines) {
        this.lines = lines == null ? List.of() : List.copyOf(lines);
    }

    public boolean isEmpty() {
        return lines.isEmpty();
    }

    public List<LrcLine> lines() {
        return lines;
    }

    /** 返回 timeMs 时应当高亮的行号；还没到第一行时返回 -1。 */
    public int indexAt(long timeMs) {
        int lo = 0;
        int hi = lines.size() - 1;
        int found = -1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (lines.get(mid).timeMs() <= timeMs) {
                found = mid;
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        return found;
    }

    public LrcLine lineAt(long timeMs) {
        int index = indexAt(timeMs);
        return index < 0 ? null : lines.get(index);
    }

    public LrcLine nextLine(long timeMs) {
        int index = indexAt(timeMs);
        int next = index + 1;
        return next < lines.size() ? lines.get(next) : null;
    }

    /** 当前行到下一行的进度 0..1；最后一行返回 0。 */
    public float progress(long timeMs) {
        int index = indexAt(timeMs);
        if (index < 0) {
            return 0f;
        }
        long start = lines.get(index).timeMs();
        long end = index + 1 < lines.size() ? lines.get(index + 1).timeMs() : start + 4_000L;
        long span = Math.max(1L, end - start);
        return Math.max(0f, Math.min(1f, (timeMs - start) / (float) span));
    }
}

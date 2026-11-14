package dev.mclite.island.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * 本地播放队列。纯逻辑，不碰音频与界面，便于单测。
 *
 * <p>自动切歌（一首放完）和手动切歌（按下一首）在单曲循环下语义不同：
 * 自动结束应当重播当前曲目，手动切歌应当真的去下一首。
 */
public final class Playlist {

    private final List<Track> tracks = new ArrayList<>();
    private final Random random = new Random();
    private int index = -1;
    private PlayMode mode = PlayMode.SEQUENTIAL;

    public void replace(List<Track> newTracks) {
        tracks.clear();
        if (newTracks != null) {
            tracks.addAll(newTracks);
        }
        index = tracks.isEmpty() ? -1 : 0;
    }

    /** 追加到队尾；不改变当前索引。 */
    public void add(Track track) {
        if (track != null) {
            tracks.add(track);
        }
    }

    public List<Track> tracks() {
        return List.copyOf(tracks);
    }

    public int size() {
        return tracks.size();
    }

    public boolean isEmpty() {
        return tracks.isEmpty();
    }

    public int index() {
        return index;
    }

    public void index(int value) {
        if (tracks.isEmpty()) {
            index = -1;
        } else {
            index = Math.max(0, Math.min(value, tracks.size() - 1));
        }
    }

    public PlayMode mode() {
        return mode;
    }

    public void mode(PlayMode value) {
        mode = value == null ? PlayMode.SEQUENTIAL : value;
    }

    public PlayMode cycleMode() {
        mode = mode.next();
        return mode;
    }

    public Track current() {
        if (index < 0 || index >= tracks.size()) {
            return null;
        }
        return tracks.get(index);
    }

    public Track at(int i) {
        if (i < 0 || i >= tracks.size()) {
            return null;
        }
        return tracks.get(i);
    }

    /**
     * 前进一首。
     *
     * @param manual true 表示用户按了「下一首」；false 表示当前曲目自然结束
     * @return 下一首；返回 null 表示播放应当停止（顺序模式播完）
     */
    public Track advance(boolean manual) {
        if (tracks.isEmpty()) {
            return null;
        }
        switch (mode) {
            case LOOP_ONE -> {
                if (manual) {
                    return move(1, false);
                }
                return current();
            }
            case RANDOM -> {
                if (tracks.size() == 1) {
                    return current();
                }
                int next;
                do {
                    next = random.nextInt(tracks.size());
                } while (next == index);
                index = next;
                return current();
            }
            case LOOP_ALL -> {
                return move(1, true);
            }
            case SEQUENTIAL -> {
                return move(1, false);
            }
            default -> {
                return null;
            }
        }
    }

    /** 上一首；顺序模式在歌首回卷到最后一首。 */
    public Track previous() {
        if (tracks.isEmpty()) {
            return null;
        }
        if (mode == PlayMode.RANDOM) {
            return advance(true);
        }
        return move(-1, true);
    }

    private Track move(int delta, boolean wrap) {
        int next = index + delta;
        if (next < 0) {
            if (!wrap) {
                next = 0;
            } else {
                next = tracks.size() - 1;
            }
        } else if (next >= tracks.size()) {
            if (!wrap) {
                // 停播时保留当前曲目，下一首/重播仍有落点。
                return null;
            }
            next = 0;
        }
        index = next;
        return current();
    }

    /** 恢复到上次保存的曲目；越界时回到第一首。 */
    public void restoreIndex(int saved) {
        if (tracks.isEmpty()) {
            index = -1;
            return;
        }
        index = Math.max(0, Math.min(saved, tracks.size() - 1));
    }

    public void remove(Track track) {
        int i = tracks.indexOf(Objects.requireNonNull(track));
        if (i < 0) {
            return;
        }
        tracks.remove(i);
        if (tracks.isEmpty()) {
            index = -1;
        } else if (i < index) {
            index--;
        } else if (i == index && index >= tracks.size()) {
            index = tracks.size() - 1;
        }
    }
}

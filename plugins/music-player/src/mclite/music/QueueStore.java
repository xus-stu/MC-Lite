package mclite.music;

import mclite.Json;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** 队列持久化：歌单 + 当前曲目 + 播放位置 + 循环模式，下次打开还原。 */
final class QueueStore {

    record State(List<Player.Track> tracks, int index, long position, Player.Mode mode) {
    }

    private final Path file;

    QueueStore(Path dataDir) {
        this.file = dataDir.resolve("queue.json");
    }

    State load() {
        try {
            if (!Files.isRegularFile(file)) {
                return null;
            }
            var root = Json.obj(Json.parse(Files.readString(file, StandardCharsets.UTF_8)));
            if (root == null) {
                return null;
            }
            List<Player.Track> tracks = new ArrayList<>();
            for (Object raw : Json.arr(root.get("tracks"))) {
                var item = Json.obj(raw);
                if (item == null) {
                    continue;
                }
                String bvid = Json.str(item, "bvid", "");
                if (bvid.isBlank()) {
                    continue;
                }
                tracks.add(new Player.Track(bvid, Json.str(item, "title", bvid),
                        Json.str(item, "author", ""), Json.str(item, "cover", ""),
                        Json.lng(item, "seconds", 0), Json.str(item, "quality", "")));
            }
            Player.Mode mode;
            try {
                mode = Player.Mode.valueOf(Json.str(root, "mode", "LIST"));
            } catch (Exception ex) {
                mode = Player.Mode.LIST;
            }
            return new State(tracks, (int) Json.lng(root, "index", -1),
                    Json.lng(root, "position", 0), mode);
        } catch (Exception ex) {
            return null;
        }
    }

    void save(List<Player.Track> tracks, int index, long position, Player.Mode mode) {
        try {
            Files.createDirectories(file.getParent());
            StringBuilder out = new StringBuilder("{\"index\":").append(index)
                    .append(",\"position\":").append(position)
                    .append(",\"mode\":\"").append(mode == null ? "LIST" : mode.name())
                    .append("\",\"tracks\":[");
            for (int i = 0; i < tracks.size(); i++) {
                Player.Track track = tracks.get(i);
                if (i > 0) {
                    out.append(',');
                }
                out.append("{\"bvid\":\"").append(esc(track.bvid()))
                        .append("\",\"title\":\"").append(esc(track.title()))
                        .append("\",\"author\":\"").append(esc(track.author()))
                        .append("\",\"cover\":\"").append(esc(track.cover()))
                        .append("\",\"seconds\":").append(track.seconds())
                        .append(",\"quality\":\"").append(esc(track.quality())).append("\"}");
            }
            out.append("]}");
            Files.writeString(file, out.toString(), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            // 存不了不影响播放。
        }
    }

    void clear() {
        try {
            Files.deleteIfExists(file);
        } catch (Exception ignored) {
            // 忽略。
        }
    }

    private static String esc(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }
}

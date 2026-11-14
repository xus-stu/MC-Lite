package dev.mclite.island.mc26;

import dev.mclite.island.core.PlaybackService;
import dev.mclite.island.core.Playlist;
import dev.mclite.island.core.Track;

/** 把 B 站点歌追加到队列，而不是覆盖当前队列。 */
final class BiliQueueHelper {

    private BiliQueueHelper() {
    }

    static void enqueue(PlaybackService service, Track track) {
        Playlist playlist = service.playlist();
        playlist.add(track);
        service.playAt(playlist.size() - 1);
    }
}

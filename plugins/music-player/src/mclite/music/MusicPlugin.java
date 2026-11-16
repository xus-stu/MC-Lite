package mclite.music;

import mclite.plugin.Host;
import mclite.plugin.LauncherPlugin;

/** 启动器插件入口：注册「音乐播放器」功能。 */
public final class MusicPlugin implements LauncherPlugin {

    @Override
    public void onAttach(Host host) {
        Bili bili = new Bili(host.pluginDataDir().resolve("bilibili.json"));
        bili.syncFromInstance(host.instanceDir());
        QueueStore store = new QueueStore(host.pluginDataDir());
        Player player = new Player(new Player.Listener() {
            @Override
            public void onTrack(Player.Track track, int index) {
            }

            @Override
            public void onState(boolean playing) {
            }

            @Override
            public void onError(String message) {
            }

            @Override
            public void onQueueChanged() {
            }

            @Override
            public void onRetry(String message) {
            }
        });
        player.resolver(track -> bili.resolveByBvid(track.bvid(), qualityOf(track.quality())));
        QueueStore.State state = store.load();
        if (state != null && !state.tracks().isEmpty()) {
            player.restore(state.tracks(), state.index(), state.position(), state.mode(), false);
        }
        javax.swing.Timer saver = new javax.swing.Timer(4000, e -> {
            if (!player.queue().isEmpty()) {
                store.save(player.queue(), player.index(), player.positionSeconds(), player.mode());
            }
        });
        saver.setRepeats(true);
        saver.start();
        host.addFeature("music-player", "音乐播放器",
                "搜 B 站音乐，队列播放，支持列表/单曲/随机/顺序循环",
                "B 站 · 音乐", () -> new MusicPage(host, bili, player, store).build());
    }

    private static Bili.Quality qualityOf(String label) {
        if (label != null) {
            for (Bili.Quality quality : Bili.Quality.values()) {
                if (quality.label.equals(label)) {
                    return quality;
                }
            }
        }
        return Bili.Quality.VERY_HIGH;
    }
}

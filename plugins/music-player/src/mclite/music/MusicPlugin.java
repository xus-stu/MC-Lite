package mclite.music;

import mclite.plugin.Host;
import mclite.plugin.LauncherPlugin;

/** 启动器插件入口：注册「音乐播放器」功能。 */
public final class MusicPlugin implements LauncherPlugin {

    @Override
    public void onAttach(Host host) {
        Bili bili = new Bili(host.pluginDataDir().resolve("bilibili.json"));
        bili.syncFromInstance(host.instanceDir());
        Player player = new Player(new Player.Listener() {
            @Override
            public void onTrack(Bili.Song song, int index) {
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
        });
        host.addFeature("music-player", "音乐播放器",
                "搜 B 站音乐，队列播放，支持列表/单曲/随机/顺序循环",
                "B 站 · 音乐", () -> new MusicPage(host, bili, player).build());
    }
}

package mclite.backup;

import mclite.plugin.Host;
import mclite.plugin.LauncherPlugin;

/** 启动器插件入口：注册「存档备份」。 */
public final class BackupPlugin implements LauncherPlugin {

    @Override
    public void onAttach(Host host) {
        host.addFeature("save-backup", "存档备份",
                "一键备份存档，列表里直接覆盖恢复，不用手动拖文件",
                "存档 · 备份", () -> new BackupPage(host).build());
    }
}

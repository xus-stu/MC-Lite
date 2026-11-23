package mclite.editor;

import mclite.plugin.Host;
import mclite.plugin.LauncherPlugin;

/** 启动器插件入口：注册「修改工具」。 */
public final class EditPlugin implements LauncherPlugin {

    @Override
    public void onAttach(Host host) {
        host.addFeature("save-editor", "修改工具",
                "改存档游戏模式 / 极限 / 允许命令 / 时间（改前自动备份）",
                "存档 · 编辑", () -> new EditorPage(host).build());
    }
}

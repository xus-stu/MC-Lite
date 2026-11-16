package mclite.plugin;

import javax.swing.JComponent;
import java.nio.file.Path;
import java.util.function.Supplier;

/** 启动器提供给插件的能力。 */
public interface Host {

    /** 启动器数据目录（通常是 ~/.mclite）。 */
    Path appHome();

    /** 这个插件专属的数据目录，已经创建好。 */
    Path pluginDataDir();

    /** 当前选中的游戏实例 ID；没选时是空字符串。 */
    String instanceId();

    /** 当前实例目录（版本隔离时是 versions/&lt;id&gt;）；没有时返回 null。 */
    Path instanceDir();

    /**
     * 在「功能」列表里加一条入口。page 只在用户点开时创建。
     *
     * @param id       唯一 ID，重复注册会覆盖
     * @param title    标题
     * @param subtitle 一行说明
     * @param tag      右侧小标签，可为空
     * @param page     详情页构造器
     */
    void addFeature(String id, String title, String subtitle, String tag, Supplier<JComponent> page);

    /** 移除自己注册的入口。 */
    void removeFeature(String id);

    /** 已注册入口有变化时调用，刷新「功能」页。 */
    void refreshFeatures();

    /** 是否处于独占显示模式（隐藏侧边栏；子页面需要自己的返回键）。 */
    boolean exclusiveChrome();

    /** 是否处于「无 bar」模式（页面标题栏也应隐藏）。 */
    boolean bareChrome();

    /** 回到「功能」列表。 */
    void backToFeatures();

    /** 切到 UI 线程执行。 */
    void runOnUiThread(Runnable task);
}

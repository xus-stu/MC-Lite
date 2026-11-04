# 页面所有权约定

## 规则

- 页面类只持有本页面的控件。
- 页面不得访问 `owner.<控件>`，例如 `owner.metalToggle`。
- 共享能力通过窄上下文传入：`Config`、`TaskCenter`、回调用 `Runnable`。
- 允许保留少量 `owner` 回调（导航、错误提示、打开目录），但设置页上限为 10。

CI 的 `test/run-ci.sh` 会检查：

```text
InstancesPage owner<=20 controls=0
SettingsPage  owner<=10 controls=0
LaunchPage    owner<=6  controls=0
ConfigPage    owner<=7  controls=0
```

基线只允许下降，不允许上升。四个页面的控件访问都已归零，下一步是 `InstancesPage` 的包级字段和 `DownloadPage`。

## 已完成

- `SettingsPage`：控件字段已私有化，`owner.config` 通过构造上下文传入，`owner` 调用从 199 降到 10。
- `InstancesPage`：没有 `owner.<控件>`。
- 1.2.1：删除 `LauncherFrame` 中 15 个搬走后仍残留的控件字段，启动/启停/退出改走 `SettingsPage`；CI 增加 frame control gate，`LauncherFrame` 再引用设置页控件会直接失败。
- 1.2.2：`ConfigPage` 自己创建并持有 `gameDirField`、`javaCombo`、`javaHint`、账户控件，只通过构造参数拿 `Config` 和两个回调；`owner.` 56 → 7，控件访问 29 → 0。
- 1.2.2：`LaunchPage` 持有账户/版本/参数标签和四个按钮，暴露 `setVersion`、`setMeta`、`setBusy` 等窄方法；`owner.` 45 → 6，控件访问 17 → 0。

## 未完成

- `InstancesPage` 仍通过包级字段读 `owner.config/gamePaths/allVersions/installedCache/selectedEntry`（没有控件访问）。
- `DownloadPage` 还没拆出 `LauncherFrame`。

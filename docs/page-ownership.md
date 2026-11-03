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
LaunchPage    owner<=45 controls<=17   ← 当前基线
ConfigPage    owner<=56 controls<=29   ← 当前基线
```

基线只允许下降，不允许上升。下一步应继续把 Launch/Config 的控件搬回各自页面。

## 已完成

- `SettingsPage`：控件字段已私有化，`owner.config` 通过构造上下文传入，`owner` 调用从 199 降到 10。
- `InstancesPage`：没有 `owner.<控件>`。

## 未完成

- `LaunchPage` 仍访问 `owner.launchVersion` 等展示控件。
- `ConfigPage` 仍访问 `owner.javaCombo`、`owner.gameDirField` 等控件。

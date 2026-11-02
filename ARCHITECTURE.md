# MC Lite 架构与拆分计划

## 当前状态

`LauncherFrame` 仍然承担窗口、页面构建和事件编排，是主要 God Class。
固定约束：

- JAR 必须小于 700KB（`build.sh` 会强制检查）。
- 不引入 JavaFX、FlatLaf 等运行库，避免包体和启动成本失控。
- 继续使用 Swing + 自定义 Theme，先改善观感和交互，不做全量重写。

## 已完成的拆分

- `InstanceService`：重命名、删除、加载器/基础版本识别、继承引用更新。
- `ContentInstaller`、`ModpackInstaller`、`LoaderInstaller`、`Installer`：安装逻辑。
- `GameLauncher`、`JvmTuner`、`Net`、`ImageCache`：启动、JVM、网络和图标。

## 后续页面拆分

目标：`LauncherFrame` 降到 1000 行以内。

1. `LauncherContext`：持有 Config、GamePaths、TaskCenter、导航回调。
2. `VersionPage`：版本列表、筛选、版本详情。
3. `DownloadPage`：内容搜索、分页、项目详情、安装队列。
4. `InstancesPage`：实例列表、重命名/删除、ZIP/数据包安装。
5. `LaunchPage`：账户、版本、Java、启动按钮。
6. `SettingsPage`：性能、目录、界面、更新设置。
7. `LauncherFrame` 只保留窗口、导航、状态栏和页面装配。

每拆一页都要求：

- 行为不变，`SelfTest` 通过。
- `build.sh` 的 700KB 检查通过。
- 不引入新的运行时依赖。

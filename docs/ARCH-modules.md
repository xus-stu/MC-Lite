# 启动器模块化改造计划

## 现状问题

| 问题 | 现状 | 影响 |
|---|---|---|
| `LauncherFrame.java` 单文件过大 | ~5000 行 / 127KB class | 每次加功能都要改它，冲突和回归风险高 |
| `Ui.java` 混合多职责 | 主题色、按钮、面板、滚动条、动画、工具函数全在一起 | 想加新控件/效果必须动同一个大文件 |
| 页面与外壳强耦合 | 页面构造时注入 `owner`（LauncherFrame）回调 | 抽页面/复用需要先收窄回调面 |
| 新功能缺少统一入口 | 靠改 `featureEntries()` / `pluginsPanel` 等硬编码列表 | 新增功能容易漏改多处 |

## 目标

1. **新增一个效果/页面/工具 = 新增文件 + 一行注册**，不用改大文件。
2. 引入内部模块（internal module）机制：`mclite.module.LauncherModule` + 注册表，模块声明自己的 id、页面、设置项。
3. 插件 API（`mclite.plugin.*`）**保持不变**；`mclite.Ui` / `mclite.Theme` / `mclite.Json` / `mclite.ModernDialogs` 保留在根包作为**门面**，插件无需重编译。
4. 体积上限 1.3MB 内完成；`./test.sh` 全绿。

## 目标结构

```
src/mclite/
├── LauncherApp.java             # 入口：初始化、单例装配
├── shell/                       # 外壳（从 LauncherFrame 拆出）
│   ├─ LauncherFrame.java        # 只做窗口/装配/路由
│   ├─ HeaderBar.java
│   ├─ Sidebar.java
│   ├─ PageRouter.java           # 页面切换 + 返回栈
│   └─ ContentRenderers.java     # 列表渲染器
├── ui/                          # 界面基础设施
│   ├─ Theme.java / ThemePreset.java
│   ├─ Ui.java                   # 门面（保持旧签名，转发到子包）
│   ├─ widgets/                  # AccentButton / SegmentButton / ToggleChip / ValueSlider / DarkCombo / Pill / RoundedPanel
│   ├─ effects/                  # FadePanel / PointerGlow / HoverGlow / Shimmer（新增效果都放这里）
│   └─ layout/                   # LauncherLayout / 尺寸与间距常量
├── pages/
│   ├─ Page.java                 # 页面接口：JComponent build(PageContext)
│   ├─ PageContext.java          # 收窄后的回调（启动、打开目录、刷新任务…）
│   ├─ download/ launch/ settings/ features/ instances/ seed/
├── services/                    # Config / TaskCenter / Installer / Updater / JavaManager / Accounts / Content / PyBridge / Benchmark
├── plugin/                      # 插件 API（稳定，不移动）
└── module/                      # 内部模块 SPI（新）
    ├─ LauncherModule.java       # id() / register(Registry)
    └─ ModuleRegistry.java
```

## 分阶段（估算）

| 阶段 | 内容 | 产出 | 估算 |
|---|---|---|---|
| P0 | 建 `effects/` 包，迁移 `PointerGlow`；`build.sh` 上限调到 1.3MB | 已有：`mclite.effects.PointerGlow` | 0.5 天（完成） |
| P1 | 拆 `Ui.java`：widgets / effects / layout / 门面转发；保证插件旧调用不变 | 每个控件独立文件 | 2 天 |
| P2 | 拆 `LauncherFrame`：HeaderBar、Sidebar、PageRouter、渲染器独立 | 主类降到 <1500 行 | 2–3 天 |
| P3 | 页面接口化：`Page` + `PageContext`，迁移 5 个内置页面；去掉页面里的 `owner.` 直接调用 | 页面可独立测试/替换 | 3–4 天 |
| P4 | 内部模块注册表：页面/效果/设置项由模块注册；`featureEntries()` 变自动收集 | 新功能=新文件+注册一行 | 2 天 |
| P5 | 插件 API v2（可选）：暴露 `Ui.effects()`、`Ui.widgets()` 给插件复用；旧 API 保留 | 插件也能用新效果 | 1–2 天 |
| P6 | 回归与性能：`test.sh`、启动时间、内存、4K 下 repaint 压测 | 验收报告 | 1–2 天 |

**合计：约 11–14 人日**（不含新功能本身）。

## 验收标准

1. 新增一个页面的 diff 只包含 1 个新文件 + 注册表 1 行。
2. 新增一个动态效果只动 `ui/effects/`。
3. 现有插件 jar（music-player / terrain-query / save-editor / save-backup / island）**不重编译**直接可用。
4. `./test.sh` 全绿；主 JAR < 1.3MB。
5. 4K 分辨率下鼠标快速移动时保持 ~60fps（效果模块有开关，默认开）。

## 风险与对策

- **大文件重构回归**：先门面后迁移（P1/P2 保持公共签名），每阶段跑 `test.sh` + 截图对比。
- **插件二进制兼容**：`mclite.Ui/Theme/Json/plugin` 包名与方法签名冻结；内部移动只针对 package-private/内部类。
- **性能**：所有效果必须有独立开关；指针效果只在鼠标移动时重绘局部区域（已实现：240px 圆形区域、33ms 节流）。
- **体积**：新模块拆分本身不显著增加体积；上限 1.3MB 留出约 480KB。

## 本阶段已落地

- `src/mclite/effects/PointerGlow.java`：全局界面跟随鼠标柔光，独立模块、可配置开关（设置 → 窗口 → 界面指针柔光）。
- 各按钮控件的指针柔光统一走 `Ui.installPointerGlow/paintPointerGlow` 共享实现。
- `build.sh` 体积上限 900KB → **1.3MB**。

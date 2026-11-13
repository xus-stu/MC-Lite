# MC Lite 接手工作指南

> 给下一个 AI / 开发者的交接文档。当前版本 **1.4.25**，最新发布：
> https://github.com/xus-stu/MC-Lite/releases/tag/v1.4.25
>
> 仓库：`git@github.com:xus-stu/MC-Lite.git`
> 本地目录：`/Users/xiaoxu/PycharmProjects/Java`
> 用户环境：macOS ARM（M5），Java 21 编译，运行游戏常用 26.1.2。
>
> 启动器插件「MCLite Island」的细节见 [`HANDOFF-island.md`](HANDOFF-island.md)；本文只讲启动器本体和插件安装链路。

---

## 1. 先做什么

不要先写功能，按这个顺序确认环境：

```bash
cd /Users/xiaoxu/PycharmProjects/Java
git status --short
cat VERSION
./test.sh
```

预期：

```text
VERSION = 1.4.25
SELF-TEST PASSED (84 checks)
SELF-TEST PASSED (84 checks)
HEADLESS REGRESSION PASSED
```

`test.sh` 会重新构建，再跑自检、无网络回归测试和 Bridge 冒烟测试。

然后读：

- [page-ownership.md](page-ownership.md)
- [bridge-contract.md](bridge-contract.md)
- [../ARCHITECTURE.md](../ARCHITECTURE.md)
- [../CHANGELOG.md](../CHANGELOG.md)

当前工作区可能有未跟踪的本地缓存/素材/DSH 插件目录，不要提交：

```text
.cache/
FlowMini/
diskbench/
math_exam_8a_bnu/
dsh-*/
cheer_docx/
运动会加油稿/
```

`.cache/clang` 里的一些 `.pcm` 可能显示为已删除的 git 文件，不要 `git add -A` 误提交。

---

## 1.1 文件存放目录

### 仓库目录

```text
/Users/xiaoxu/PycharmProjects/Java/
├── src/mclite/          Java 源码
├── assets/              图标等打包资源
├── benchmark/           可选跑分模块源码
├── test/                手动测试 + 无网络回归测试
├── python/              可选 Python Bridge 模块源码
├── docs/                架构、页面所有权、Bridge 契约、交接文档
├── installer/           Windows 安装器相关文件
├── packaging/           打包资源
├── build/               构建中间产物（不提交）
├── dist/                发布产物（不提交）
├── backup/              本地源码备份（.gitignore）
└── .github/workflows/   CI 发布流程
```

关键文件：

```text
src/mclite/LauncherFrame.java       主窗口，当前 5015 行
src/mclite/InstancesPage.java       版本列表页
src/mclite/SettingsPage.java        设置页
src/mclite/LaunchPage.java          启动页
src/mclite/ConfigPage.java          启动器配置页
src/mclite/DownloadPage.java        下载中心外壳（顶部栏/子标签/卡片容器）
src/mclite/ContentPage.java         内容浏览卡片（搜索/列表/分页）
src/mclite/ContentCache.java        内容搜索缓存（LRU + 5 分钟 TTL）
src/mclite/VersionDetailPage.java    版本安装卡片（加载器/版本号/安装）
src/mclite/VersionListCard.java     版本列表卡片（筛选结果/空状态/选中）
src/mclite/InstanceMeta.java        实例分组/备注存储
src/mclite/SkinEditorDialog.java    自绘皮肤编辑器
src/mclite/LaunchDiagnostics.java   启动失败诊断
src/mclite/ProjectDetailPage.java    项目详情卡片（文件版本/前置模组）
src/mclite/BridgeServer.java        Python Bridge HTTP API
src/mclite/Version.java             启动器版本常量
CHANGELOG.md                        发布说明来源
VERSION                             当前版本号
build.sh / test.sh / test-update.sh
```

### 本地运行数据

启动器自己的数据在：

```text
~/.mclite/
├── config.properties   用户配置
├── accounts/           离线账户与皮肤
├── cache/icons/        远程图标缓存
├── java/               自动下载的 Java 运行时
│   └── java-runtime-epsilon/
├── logs/launcher.log   启动器日志
├── updates/            更新暂存区
└── bridge.json         --py-bridge 运行时生成，退出后删除
```

注意：`bridge.json` 只在 Bridge 运行期间存在，里面有临时 token，不要提交。

### 用户游戏目录

不在仓库内：

```text
/Users/xiaoxu/Desktop/MC/
├── versions/26.1.2/            当前主要实例
│   ├── mods/                   启用的模组
│   ├── mods-disabled/          已移出的冲突/不兼容模组
│   ├── datapacks/              实例级全局数据包
│   ├── saves/                  存档
│   │   └── <世界>/players/data/<uuid>.dat
│   ├── config/                 模组和 Tectonic 配置
│   ├── logs/                   latest.log / 历史日志
│   ├── crash-reports/          崩溃报告
│   ├── shaderpacks/            Iris 光影
│   └── resourcepacks/          资源包
└── versions/<其他实例>/         MITE、Fabric、NeoForge 等
```

**重要**：26.1.2 的玩家数据目录是：

```text
saves/<世界>/players/data/<uuid>.dat
```

不是旧版的 `playerdata/`。迁移/编辑玩家数据前确认路径。

### 发布产物

```text
dist/
├── MCLite_v1.4.25.jar
├── MCLite_v1.4.25-region.jar
├── MCLitePyBridge_v1.4.25.zip
├── MC-Lite_v1.4.25_region_Windows.zip
├── MCLiteBenchmark.jar
└── SHA256SUMS.txt
```

- 主 JAR 和地区版 JAR 由 `build.sh` 生成。
- macOS app 在 macOS 上生成到 `dist/MC Lite*.app`。
- Python 模块是单独的 ZIP，解压到任意目录使用，不需要放进游戏目录。
- 不要手动改 `dist/` 里的产物；重新构建。

### 备份与截图

```text
backup/
├── LauncherFrame-v1.1.77.java
└── ModernDialogs-v1.1.77.java
```

- Git 备份标签：`v1.1.77-before-ui-split`
- 游戏数据备份通常命名为 `*.bak-*`，例如：

```text
versions/26.1.2/saves/<世界>/players/data/<uuid>.dat.bak-inventory
versions/26.1.2/config/tectonic.json.bak
```

- `docs/*.png` 是仓库内的界面截图；临时截图放 `/tmp/`，不要提交。

---

## 2. 硬约束

1. **JAR 必须小于 800KB**
   - `build.sh` 会检查，1.4.25 约 732KB。
   - 不引入 JavaFX、FlatLaf、Jython、GraalPy 等运行库。
   - Python 模块不打包进 JAR。

2. **不要为了“版本号推进”连续发版**
   - 当前没有冻结期，但每次发版都要有用户能感知的修复/功能。
   - 模组可以走 `island-v*` 独立发布，不必每次都 bump 启动器；见第 4.5 节。

3. **发布必须是真实 Release**
   - 有版本 tag、JAR、Windows 包、`SHA256SUMS.txt`。
   - 当前 CI 上传：地区版 JAR、Windows 包、`MCLiteBenchmark.jar`、Python Bridge ZIP、`SHA256SUMS.txt`。
   - macOS `.app` 和 macOS ZIP 只在本地 `build.sh` 生成，CI 用的是 Linux runner，不会上传。
   - 不能只给源码或口头说明。

4. **不要提交用户游戏目录**
   - 用户游戏目录是 `/Users/xiaoxu/Desktop/MC`，不属于仓库。
   - 不要提交存档、mods、日志、账号数据。

5. **中文界面、中文回复**
   - 用户主要中文交流，回复保持简洁、直接。

6. **保留旧兼容**
   - Python Bridge 的 `/api` 路径和旧客户端函数不能删除。
   - 页面拆分必须保证旧行为。

---

## 3. 构建、测试、发布

### 构建

```bash
./build.sh
```

产物在 `dist/`：

```text
MCLite_v1.4.25.jar
MCLite_v1.4.25-region.jar
MCLitePyBridge_v1.4.25.zip
MC-Lite_v1.4.25_region_Windows.zip
MCLiteBenchmark.jar
SHA256SUMS.txt
```

`build.sh` 还会：

- 清理旧版本 dist 产物
- 打包 macOS 应用（仅 macOS）
- 强制检查主 JAR ≤ 800KB（构建时打印剩余空间）

### 测试

```bash
./test.sh
./test-update.sh
```

- `test.sh`：构建 + 两个 JAR 自检（84 checks）+ `test/run-ci.sh`
- `test-update.sh`：模拟 1.4.25 → 999.0.0 的普通 JAR 原地更新；macOS 上还测试 app 包
- `test/run-ci.sh` 包含：
  - LocalTest
  - JvmTunerTest
  - ConfigCssTest
  - CrashTest
  - 页面所有权 grep 门槛
  - Bridge `/api` + `/api/v1` 冒烟测试

CI 只跑无网络、无 GUI 测试；不会真正启动 Minecraft。

### 截图检查

```bash
java -jar dist/MCLite_v1.4.25.jar \
  --screenshot=/tmp/settings.png --page=settings
```

可用页面：

```text
download
launch
instances
config
settings
versions
mod
project
version-detail
```

离线环境出现“获取版本列表失败”是正常的。

### 版本号

`bump-version.sh` 的 `major` 不是标准 semver：

```bash
./bump-version.sh major
# 1.4.0 -> 1.5.0（major 会重置 minor）
```

需要精确版本时手动改：

- `VERSION`
- `src/mclite/Version.java`
- `run*.sh / run*.bat`
- `README.md`
- `python/README.md`

发布说明由 `CHANGELOG.md` 对应 `## vX.Y.Z` 段自动提取为 GitHub Release body。

发版：

```bash
git add ...
git commit -m "...; V1.4.25"
git push origin main
git tag -a v1.4.25 -m "..."
git push origin v1.4.25
```

CI 由 tag `v*` 触发；不要重写已经推送的 tag。

---

## 4. 当前架构

### 入口与核心

| 类 | 作用 |
|---|---|
| `LauncherApp` | main / CLI 参数 / 自检 / Bridge 启动 |
| `LauncherFrame` | 窗口、导航、下载页和剩余逻辑 |
| `Config` | 用户配置 |
| `Net` | 下载、分块、镜像、超时 |
| `Installer` / `LoaderInstaller` | 原版和加载器安装 |
| `ContentInstaller` | 模组/资源包/光影安装 |
| `ModpackInstaller` | 整合包安装 |
| `GameLauncher` / `JvmTuner` | 启动命令和 JVM 参数 |
| `InstanceService` | 实例重命名、删除、加载器识别 |
| `GlobalDatapacks` | 实例级全局数据包同步 |
| `BridgeServer` | Python Bridge HTTP API |
| `IslandCatalog` | 岛清单 URL/解析/兼容过滤；`island-latest` + 旧 latest 回退 |
| `IslandInstaller` | 岛 jar 下载/校验/安装；删旧版；装 Sound Physics 伴随模组 |
| `LauncherPluginsPanel` | 「下载中心 → 启动器插件」页面；安装任务上报 TaskCenter |
| `TaskCenter` / `DownloadTask` / `TasksDialog` | 任务中心（进度、阶段、成功/失败） |
| `GithubSources` | GitHub 直连 + 镜像候选 |

### 页面类

当前行数：

```text
LauncherFrame   4655
LauncherPluginsPanel 664
IslandCatalog   108
IslandInstaller 255
Net             1062
InstancesPage    496
SettingsPage     802
LaunchPage       241
ConfigPage       293
DownloadPage     309
ContentPage      692
VersionDetailPage 196
ProjectDetailPage 272
VersionListCard  185
```

已拆出：

- `InstancesPage`：版本列表/实例详情
- `SettingsPage`：设置页
- `LaunchPage`：启动页（1.2.2 起控件也归页面所有）
- `ConfigPage`：启动器配置页（1.2.2 起控件也归页面所有）
- `DownloadPage`：下载中心外壳（1.2.3 起顶部栏、子标签、卡片容器归页面；四个卡片内容还在搬）
- `ContentPage`：内容浏览卡片（1.2.3 起搜索栏、列表、分页、选中底栏归页面）
- `VersionDetailPage` / `ProjectDetailPage`：两张详情卡片（1.3.0 起控件归页面）
- `VersionListCard`：版本列表卡片（1.3.1 起控件归页面，下载中心四张卡片全部搬完）
- `InstancesPage`：实例复制/导出/导入/分组备注（1.3.1）

### 所有权状态

`SettingsPage` 已完成控件所有权拆分：

```text
owner.*          199 -> 10
owner.<控件>     0
```

`LaunchPage` 和 `ConfigPage` 也已完成控件所有权拆分：

```text
LaunchPage  owner.* 45 -> 5   控件 17 -> 0
ConfigPage  owner.* 56 -> 7   控件 29 -> 0
```

剩余债务：`InstancesPage` 仍有 19 处 `owner` 包级字段访问（`config/gamePaths/allVersions/installedCache/selectedEntry`），但不碰控件。

`test/run-ci.sh` 当前基线（未变）：

```text
InstancesPage <=20 / 0
SettingsPage  <=10 / 0
LaunchPage    <=5 / 0
ConfigPage    <=7 / 0
frame control gate  LauncherFrame 不得引用 SettingsPage 的控件
```

**基线只允许下降，不允许上升。**

规则详见 [page-ownership.md](page-ownership.md)。

---

## 4.5 启动器插件系统（1.4.25）

链路：

```text
LauncherPluginsPanel（下载中心 → 启动器插件）
  → IslandCatalog.fetch()
      → 固定 URL：releases/download/island-latest/island-manifest.json
      → 旧 URL：releases/latest/download/island-manifest.json（回退）
      → 8 个候选（ghfast/gh-proxy/直连/ghproxy.net/moeyy）并行竞速
      → URL 带 ?ts=<毫秒> 强制回源，绕开镜像缓存
  → 选目标实例 / build
  → 安装按钮：
      → TaskCenter.start("MCLite 岛 x.y.z", "GitHub Releases")
      → 后台线程：IslandInstaller.install()
          → jar URL 带 ?v=<sha256 前 8 位> 防镜像缓存
          → 单次 25 秒超时；最多 4 个候选
          → 校验 SHA-256 / size
          → 写 mods，cleanupOldJars()：直接删除旧 jar / .bak / .part
          → ensureSoundPhysics()：从 Modrinth 拉 Sound Physics Remastered
      → 进度写入 TaskProgress；任务页可见
  → 失败：自动刷新清单，用同实例最新 build 重试一次
```

发布：

- 只发模组：打 `island-v0.3.x` tag → `.github/workflows/island-release.yml`
  - 两个构建 job（1.20.1 / 26.1.2）→ 上传 artifact → publish job
  - 发布到固定 release tag **`island-latest`**（prerelease，不占 latest）
  - jar 资产名必须去掉 `-bundled`（`gen_manifest.py` 生成的 URL 不带）
  - 旧 jar 资产保留，避免旧清单 404
  - 额外把 manifest 镜像到当前 `releases/latest` 的 release（旧启动器回退）
- 启动器：打 `v1.4.x` tag → `.github/workflows/release.yml`
  - 构建启动器 + 两个岛的 jar + manifest，发布到该 tag
  - `releases/latest` 会指向它；插件页仍读 `island-latest`，所以启动器 release 里的岛 jar 版本可以落后

注意：

- `island-release` 的 upload 代码在 `build-1201` / `build-2612` 两个 job 里，改路径时两边都要改。
- 不要把 GPL 的 Sound Physics jar 塞进岛 jar；由启动器作为独立伴随模组安装。
- `island-latest` release 里的 manifest 是唯一真相；用户插件页版本不对时先点「刷新清单」，或确认清单 URL 没被镜像缓存（已加 `?ts=`）。

---

## 5. 下一阶段工作

按优先级：

1. **真实网络验收启动器插件安装（1.4.25）**
   - 用户环境更新到 1.4.25 后，走「下载中心 → 启动器插件」安装/更新一次。
   - 检查：任务页出现 `MCLite 岛 x.y.z`、进度/速度、旧 jar 被删（不留 `.bak`）、Sound Physics 被装上/更新。
   - 如果又 404：先点「刷新清单」，看 `latest.log`/launcher.log；确认 `island-latest` 资产名不带 `-bundled`。

2. **岛 0.3.21 的实机验收**
   - 世界悬浮歌词是否出现、是否跟镜头/距离正确；户外是否还触发室内混响；石洞/羊毛房/水下是否有区别。
   - 细节与死代码清单见 [`HANDOFF-island.md`](HANDOFF-island.md) 第 6、8 节。

3. **清理启动器插件链路死代码/配置**
   - 用户旧配置里 `soundPhysicsAudio` 仍可能是 true，但已无 UI/无效果；`lyrics3dStyle` 也无 UI。
   - 移除或重新接线时注意旧配置兼容（Gson 忽略未知字段，删除字段安全）。

4. **继续所有权拆分**
   - `InstancesPage` 仍有 19 处包级字段访问（`config/gamePaths/allVersions/installedCache/selectedEntry`）。
   - 每搬一个，降低 CI 基线，不能只改文件位置。

5. **不要为了版本号堆功能**
   - 模组走 `island-v*` 独立发布；只有启动器本体改动才发 `v1.4.x`。
   - 发布前必须 `./test.sh`（84 checks）通过。

---

## 6. Python Bridge

可选模块，**不在主 JAR 内**：

```text
dist/MCLitePyBridge_v1.4.25.zip
```

启动：

```bash
java -jar dist/MCLite_v1.4.25.jar --py-bridge
```

生成：

```text
~/.mclite/bridge.json
```

包含 `url`、`token`、`pid`。只监听 `127.0.0.1`，除 `/api/health` 外都要：

```text
X-MCLite-Token: <token>
```

### Python 客户端 / UI

```bash
python3 run_ui.py --jar /path/to/MCLite_v1.4.25.jar
```

`run_ui.py` 会找已安装 PySide6 的解释器。

本机情况：

- 默认 `python3` 是 3.13，未装 PySide6
- `python3.14` 已装 PySide6 6.11.2
- 所以 `run_ui.py` 通常会切到 3.14

Python 前端是独立 PySide6 窗口，不是 Swing 插件。修改 UI 直接改：

```text
python/mclite_bridge/ui_pyside6.py
```

### 兼容规则

- `/api` 旧路径永久保留。
- `/api/v1` 是版本化别名。
- 旧 Python 函数名/参数不删。
- 新方法可以增加；破坏性变更必须开 `/api/v2`。
- `/api/health` 返回 `apiVersion`。

详见 [bridge-contract.md](bridge-contract.md)。

### 安全

- 只绑定 loopback。
- token 随机生成。
- 不提供任意文件读取；数据包安装接口只接受本地 ZIP 路径。
- 不要把 Bridge 暴露到公网。

---

## 7. 用户游戏环境（不在仓库）

用户游戏目录：

```text
/Users/xiaoxu/Desktop/MC
```

26.1.2 实例：

```text
versions/26.1.2
```

用户自己调整过的内容：

- Tectonic 地形配置
- 有限高度模组曾因 26.1.2 注册表不兼容被移除
- 全局数据包：
  - `MCLitePlainsTrees.zip`
  - `MCLiteSlimeCraft.zip`
- CTOV、Structory、Geophilic、YUNG's Better Dungeons 等结构/装饰模组
- 已禁用：Terralith、Nature's Spirit、Towns and Towers、Cristel Lib
- 当前 mods 约 116 个；含 **Sound Physics Remastered 1.5.2+26.1.2**、JEI 29.53.0.114、Jade 26.1.11
- 当前岛模组：`mclite-island-26.1.2-fabric-0.3.21.jar`（mods 里不要再有 `.bak`）
- GUI Scale=3，岛 scale=0.9，常用全屏；用户反馈全屏 41–78 FPS，窗口化约 160 FPS
- 声学配置 `acousticsSubmersion/Enclosure/Rainfall` 当前为 true
- 游戏日志：`versions/26.1.2/logs/latest.log`；启动器日志：`~/.mclite/logs/launcher.log`

**不要在没有备份的情况下改用户游戏目录。**
涉及 `level.dat`、`playerdata`、`saves` 的操作必须备份后再做。

---

## 8. 已知风险

- 启动器插件链路 1.4.24/1.4.25 刚改过：清单竞速、jar 版本 hash、安装任务、旧 jar 删除、Sound Physics 伴随安装。**要在真实网络下再走一遍安装/更新**，特别关注 ghfast/gh-proxy 缓存与 Modrinth 可达性。
- `releases/latest` = v1.4.25，但它的资产里岛 jar 是 0.3.19；0.3.20/0.3.21 只在 `island-latest`。用户插件页版本以清单为准，不要拿 latest release 的 jar 判断。
- 启动器 JAR 已 732KB，离 800KB 上限只剩 68KB；再加 UI/依赖要小心。
- Sound Physics 是 GPL 独立模组，只能由启动器伴随安装，不能打进岛 jar。

- `LauncherFrame` 仍有 **4655 行**；下载中心四张卡片已全部搬完，实例复制/导出/分组备注已完成。
- 1.2.3 审查遗留（P2，未修）：窗口最大化状态不持久化；manifest 为空时 `updateVersionPicker` 会在 EDT 扫 versions；busy 时从实例页点「启动所选实例」会静默丢弃 `pendingLaunch`。
- `ContentPage` 仍向 `LauncherFrame` 暴露 `list()/model()/installButton()` 等访问器（所有权是名义上的），下一步改成 `appendResults/selectFirst/setStatusText` 这类窄方法。
- 1.3.0 审查遗留（P2，未修）：整合包安装只写 `modpack.properties`，内容页「仅未安装」对整合包无效；筛选首次判定会逐项读 `content.properties`（慢盘可感知）；`TasksDialog` 取消后台更新/跑分任务不生效；`cancelTask` 不清 `installQueue`；启动进度期间关窗只检查 `busy`。
- 四个页面控件访问都已归零，接下来是 `DownloadPage` 和 `InstancesPage` 的包级字段。
- `InstancesPage` 通过包级字段访问 owner 的 `config/gamePaths/allVersions/installedCache/selectedEntry`。
- 页面类仍依赖 owner 回调，不是完全独立。
- GUI 主要靠截图和手动检查，CI 不启动真实 Minecraft。
- 真机进游戏只验证到「启动进世界」；皮肤/角色预览界面在 owo 降级后尚未复测。
- Python Bridge 新方法需要 1.1.98+ 服务端；旧方法兼容。
- `bump-version.sh major` 不符合标准 semver。
- macOS app 更新测试只在 macOS 跑；Linux CI 会跳过 app 部分。

---

## 9. 接手检查清单

- [ ] `git status` 没有意外修改
- [ ] `VERSION` = 1.4.25
- [ ] `./test.sh` 通过（84 checks + headless + bridge）
- [ ] `./test-update.sh` 通过
- [ ] JAR < 800KB
- [ ] 新 JAR 能打开设置页/版本页
- [ ] 真正进一次 26.1.2 或 MITE
- [ ] 改页面所有权后同步降低 CI 基线
- [ ] 新 API 只增不改旧路径
- [ ] 发版前更新 `CHANGELOG.md`

---

## 10. 绝对不要做

- 不要引入 JavaFX / FlatLaf / 大型运行时依赖。
- 不要把 Python 运行时塞进 JAR。
- 不要为了“看起来拆完了”只移动文件、不移动控件所有权。
- 不要提高 CI 所有权基线。
- 不要提交 `.cache/`、游戏存档、日志、账号数据。
- 不要重写已发布 tag 或 force-push 发布历史。
- 不要把旧插件 jar 改名 `.bak`；安装/更新时直接删除旧版。
- 不要把 Sound Physics 或其它 GPL 模组打进岛 jar。

---

## 11. 建议的第一条命令

```bash
cd /Users/xiaoxu/PycharmProjects/Java
cat VERSION
./test.sh
```

通过后先做 dogfooding，再动架构。

---

## 12. MCLite Island 交接（2026-11-13）

启动器外置组件「MCLite Island」的完整交接见 [`docs/HANDOFF-island.md`](HANDOFF-island.md)：

- 当前发布：启动器 **v1.4.25**、模组 **0.3.21**（1.20.1 / 26.1.2 Fabric；0.3.20+ 只走 `island-latest`）
- 已实现：本地播放、B 站扫码/搜索/收藏/在线播放、LRCLIB 自动歌词、方块材质声学混响、透明播放器、22px 极简灵动岛、DangoMusic 式世界悬浮歌词、启动器插件任务/独立发布
- 已知问题：世界悬浮歌词与 6 comb 混响**未实机确认**；Sound Physics 只作为伴随模组装上，不接管音乐；1.20.1 UI 未同步；`SoundPhysicsAudioEngine`/`LyricsOverlay26` 是死代码
- 下一位接手请先读该文件第 6、8 节

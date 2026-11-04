# MC Lite 接手工作指南

> 给下一个 AI / 开发者的交接文档。当前版本 **1.2.3**，最新发布：
> https://github.com/xus-stu/MC-Lite/releases/tag/v1.2.3
>
> 仓库：`git@github.com:xus-stu/MC-Lite.git`
> 本地目录：`/Users/xiaoxu/PycharmProjects/Java`
> 用户环境：macOS ARM（M5），Java 21 编译，运行游戏常用 26.1.2。

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
VERSION = 1.2.3
SELF-TEST PASSED (66 checks)
SELF-TEST PASSED (66 checks)
HEADLESS REGRESSION PASSED
```

`test.sh` 会重新构建，再跑自检、无网络回归测试和 Bridge 冒烟测试。

然后读：

- [page-ownership.md](page-ownership.md)
- [bridge-contract.md](bridge-contract.md)
- [../ARCHITECTURE.md](../ARCHITECTURE.md)
- [../CHANGELOG.md](../CHANGELOG.md)

当前只有未跟踪的本地缓存/素材目录，不要提交：

```text
.cache/
FlowMini/
diskbench/
math_exam_8a_bnu/
usb-speed-sandisk.png
运动会加油稿/
```

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
├── MCLite_v1.2.3.jar
├── MCLite_v1.2.3-region.jar
├── MCLitePyBridge_v1.2.3.zip
├── MC-Lite_v1.2.3_region_Windows.zip
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
   - `build.sh` 会检查，当前 1.2.2 约 618KB。
   - 不引入 JavaFX、FlatLaf、Jython、GraalPy 等运行库。
   - Python 模块不打包进 JAR。

2. **不要为了“版本号推进”连续发版**
   - 1.2.x 是页面拆分阶段收尾，当前处于冻结期。
   - 冻结期只接受修复类补丁（如 1.2.1 的启动崩溃）；新功能、重构、发版都要先得到用户明确要求。

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
MCLite_v1.2.3.jar
MCLite_v1.2.3-region.jar
MCLitePyBridge_v1.2.3.zip
MC-Lite_v1.2.3_region_Windows.zip
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

- `test.sh`：构建 + 两个 JAR 自检 + `test/run-ci.sh`
- `test-update.sh`：模拟 1.2.3 → 999.0.0 的普通 JAR 原地更新；macOS 上还测试 app 包
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
java -jar dist/MCLite_v1.2.3.jar \
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
# 1.2.3 -> 1.3.8，而不是 1.3.0
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
git commit -m "...; V1.2.3"
git push origin main
git tag -a v1.2.3 -m "..."
git push origin v1.2.3
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

### 页面类

当前行数：

```text
LauncherFrame   4644
InstancesPage    371
SettingsPage     674
LaunchPage       242
ConfigPage       293
DownloadPage     302
ContentPage      633
```

已拆出：

- `InstancesPage`：版本列表/实例详情
- `SettingsPage`：设置页
- `LaunchPage`：启动页（1.2.2 起控件也归页面所有）
- `ConfigPage`：启动器配置页（1.2.2 起控件也归页面所有）
- `DownloadPage`：下载中心外壳（1.2.3 起顶部栏、子标签、卡片容器归页面；四个卡片内容还在搬）
- `ContentPage`：内容浏览卡片（1.2.3 起搜索栏、列表、分页、选中底栏归页面）

### 所有权状态

`SettingsPage` 已完成控件所有权拆分：

```text
owner.*          199 -> 10
owner.<控件>     0
```

`LaunchPage` 和 `ConfigPage` 也已完成控件所有权拆分：

```text
LaunchPage  owner.* 45 -> 6   控件 17 -> 0
ConfigPage  owner.* 56 -> 7   控件 29 -> 0
```

剩余债务：`InstancesPage` 仍有 19 处 `owner` 包级字段访问（`config/gamePaths/allVersions/installedCache/selectedEntry`），但不碰控件。

`test/run-ci.sh` 当前基线：

```text
InstancesPage <=20 / 0
SettingsPage  <=10 / 0
LaunchPage    <=6 / 0
ConfigPage    <=7 / 0
frame control gate  LauncherFrame 不得引用 SettingsPage 的控件
```

**基线只允许下降，不允许上升。**

规则详见 [page-ownership.md](page-ownership.md)。

---

## 5. 下一阶段工作

按优先级：

1. **先 dogfooding**
   - 1.2.0 的「启动游戏」NPE 已在 1.2.1 修复，并于 2026-11-04 18:50 实机启动 26.1.2 成功。
   - 26.1.2 在 2026-11-03 已能启动进世界，但 22:54 在皮肤预览界面崩溃：`show-me-your-skin 2.0.3` 是对着 owo 0.13.0 编译的（`braid/widgets/object/EntityWidget`），实例里装的 0.13.1 把类移到了 `object/entity/`。
   - 处理：`mods/owo-lib-0.13.1+26.1.jar` 已备份到 `versions/26.1.2/mods-backup-20261104-owo/`，改为 `owo-lib-0.13.0+26.1.jar`。
   - 仍需验证：进世界后打开皮肤/角色预览界面不再崩。

2. **继续所有权拆分**
   - `LaunchPage`：已完成（45/17 → 6/0），只剩导航回调。
   - `ConfigPage`：已完成（56/29 → 7/0），可当模板。
   - 下一步：`InstancesPage` 的 19 处包级字段访问（`config/gamePaths/allVersions/installedCache/selectedEntry`）。
   - 每搬一个，降低 CI 基线，不能只改文件位置。

3. **拆 DownloadPage**
   - 外壳已完成：顶部栏、搜索/过滤/刷新、子标签、卡片容器归 `DownloadPage`。
   - 内容浏览卡片已完成：`ContentPage` 接管搜索、列表、分页和选中底栏。
   - 还剩三张卡片：版本列表、版本详情、项目详情。
   - 搬完后再处理 `LauncherFrame` 的包级字段访问。

4. **收敛 `owner`**
   - 页面只拿窄上下文：`Config`、`TaskCenter`、少量回调。
   - 禁止 `owner.<控件>`；CI 已能检测。

5. **不要继续堆功能**
   - 冻结期先修债务、补 dogfooding、保持 1.2.x 补丁。
   - 不要为了版本号连续发布。

---

## 6. Python Bridge

可选模块，**不在主 JAR 内**：

```text
dist/MCLitePyBridge_v1.2.3.zip
```

启动：

```bash
java -jar dist/MCLite_v1.2.3.jar --py-bridge
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
python3 run_ui.py --jar /path/to/MCLite_v1.2.3.jar
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

**不要在没有备份的情况下改用户游戏目录。**
涉及 `level.dat`、`playerdata`、`saves` 的操作必须备份后再做。

---

## 8. 已知风险

- `LauncherFrame` 仍有 **4644 行**，下载中心还剩版本列表、版本详情、项目详情三张卡片。
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
- [ ] `VERSION` = 1.2.3
- [ ] `./test.sh` 通过（66 checks + headless + bridge）
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
- 不要在冻结期连续发功能版本。

---

## 11. 建议的第一条命令

```bash
cd /Users/xiaoxu/PycharmProjects/Java
cat VERSION
./test.sh
```

通过后先做 dogfooding，再动架构。

# MC Lite 更新记录

## v1.4.9

- 启动器插件详情页：简介改为 Markdown 渲染（标题/列表/代码/链接），不再显示原始 Markdown 源码。
- 详情页内容可滚动（简介独立滚动区 + 整个详情页滚动），不再被窗口高度截断。

## v1.4.8

- 「启动器插件」改为卡片列表：MCLite Island、Python Bridge 模块各一张卡片，点进去是详情页。
- 详情页简介从 GitHub raw 拉取（`docs/island.md`、`python/README.md`），失败回退内置文案；含返回、安装/卸载、打开音乐目录、Python 安装/启动。
- 模组版本 0.1.4（26.1.2 按 M 崩溃与灵动岛空态提示）。

## v1.4.7

- 修复 26.1.2 按 M 打开播放器崩溃：`Screen` 父类已经做过一次 `extractBackground`（含模糊），子类重复调用触发 `Can only blur once per frame`；已移除重复调用。
- 灵动岛在无歌曲、无目标时显示引导提示（音乐目录为空 / 按 M 打开），不再完全不可见。
- 模组版本 0.1.4，重新构建 1.20.1/26.1.2 Fabric。

## v1.4.6

- 启动器插件页所有控件统一圆角：目标实例下拉改用 `Ui.DarkCombo`（10px 圆角）、构建列表放进 14px 圆角容器、列表选中高亮改为圆角、外层卡片 20px。
- Python 模块入口保持不变（安装/启动按钮）。

## v1.4.5

- 「启动器插件」页补回 Python 模块入口：可直接安装 Python Bridge 模块、启动 Bridge，不必再去设置页。
- 插件页恢复圆角卡片留白（之前内嵌后卡片贴边，圆角被裁掉）。
- 模组版本仍为 0.1.3。

## v1.4.4

- 「扩展组件」从设置页移到**下载中心 → 启动器插件**，作为独立分类页；设置页不再保留入口。
- 启动器插件页内嵌实例选择、构建列表、安装/卸载、打开音乐目录，进入页面时才拉取 release 清单，启动阶段不联网。
- 模组版本沿用 0.1.3（本次只有启动器 UI 调整），重新生成 manifest。

## v1.4.3

- 修复 MCLite 岛三个实测音频 bug：16-bit FLAC 字节序错误导致噪音、OGG 每声道 PCM 起始索引错误导致错音、MP3 seek 重复计数只跳到一半；新增 FLAC 解码与 WAV 逐字节一致的单测。
- 补全 MP3/OGG 时长估算，HUD 进度条可正常显示；播放器界面会显示音频错误。
- 音频引擎：打开失败不再误停当前歌曲；切歌先回收旧设备再启动新线，消除叠音；3D 歌词改用独立 immediate buffer 并正确 flush。
- 灵动岛：关闭上下文显示时立即清空；歌词导入到 `lyrics/` 后优先于音频旁的旧 `.lrc`；歌单切换加锁避免“下一首”与自动切歌竞态。
- 安装器：升级时清理更早的 `.bak`；release 工作流校验 tag 与 VERSION 一致、断言 JDK 25 环境。
- 模组版本 0.1.3，重新构建 1.20.1/26.1.2 Fabric 构建。

## v1.4.2

- MCLite 岛：重写音频播放引擎的并发模型——打开解码器/音频设备不再持锁，停播不再 join 等待，消除切歌时最长约 5.5 秒的卡顿与旧线程误发「播完」导致的重复播放/跳歌。
- 音频：补全 WAV/FLAC 时长（HUD 进度条可用）；OGG/FLAC 不支持 seek 时如实从 0 播放并显示 0，不再伪装成已跳转；MP3 seek 不再先播首帧。
- 1.20.1 3D 歌词改用 `WorldRenderContext.consumers()`，不再 flush 全局共享 buffer，避免与 Sodium/Iris 抢批次。
- 播放器界面不再每帧全量复制歌单，改为每 20 帧刷新；LRC 缓存改为并发容器；安装器清理旧 jar 失败不再把已成功的安装报成失败，卸载会清 `.bak/.part`。
- 模组版本 0.1.2，重新构建 1.20.1/26.1.2 Fabric 构建。

## v1.4.1

- MCLite 岛：灵动岛接入 JEI 物品列表悬停（装 JEI 时优先显示悬停物品名与 mod id）与 Jade 目标提示（反射读取当前 tooltip，失败自动降级到自带射线探针）。
- MCLite 岛：模组版本 0.1.1，重新构建 1.20.1 与 26.1.2 Fabric 构建；JEI/Jade 都是可选依赖，未安装不会崩溃。
- 文档补充灵动岛上下文优先级与降级行为。

## v1.4.0

- 新增可选扩展组件「MCLite 岛」：设置页 →「扩展组件 · MCLite 岛…」，按实例、MC 版本和加载器匹配构建，支持下载、SHA-256 校验、安装与卸载；启动器本身不打包任何 mod jar。
- 新模组 `mclite-island`：本地音乐播放（mp3/ogg/wav/flac）、歌单与播放模式、LRC 歌词导入、灵动岛 HUD、1.20.1 的 3D 歌词、26.1.2 的 HUD 与播放器界面；零登录、零联网。
- 启动器新增 release 清单拉取（`island-manifest.json`）、实例加载器识别（Fabric/Forge/NeoForge）、同名旧 jar 清理与下载失败提示；`Net` 增加 GitHub release 主机白名单。
- 测试与 CI：模组核心 22 项单测（含真实 WAV/MP3/OGG/FLAC 解码冒烟）、启动器自测新增清单/兼容/防穿越/卸载用例；新增 `island` workflow，release 自动构建 1.20.1 与 26.1.2 Fabric 构建并附带清单。
- 版本矩阵现状：1.20.1 Fabric、26.1.2 Fabric 可用；1.20.1 Forge/NeoForge、1.21.1 与 26.1.2 NeoForge 在后续阶段补齐。26.1.2 的 3D 歌词因渲染 API 重写暂未移植。

## v1.3.12

- 新增模块化设置 API：`GET /api/config/schema` 返回可编辑设置的 key/label/type/min/max/value，外部界面（PySide6 等）可据此生成设置页并用 `PUT /api/config` 写回，无需修改启动器。
- PySide6 新增「扩展设置」标签页，按 schema 动态渲染整数/开关/文本控件并保存。
- Python 客户端新增 `config_schema()`；`python/README.md` 增加外部 UI 集成说明。


## v1.3.11

- 设置页新增「启动 Python Bridge」：后台拉起 `--py-bridge`，自动生成 `~/.mclite/bridge.json`，不用再手敲终端命令。
- `api_tour.py` 在 Bridge 未运行时给出明确提示（bridge.json 不存在、Bridge 需要常驻），不再只抛出 FileNotFoundError。


## v1.3.10

- 「安装 Python 模块」默认只列 `~/PycharmProjects/*/.venv|venv|env` 里的 PyCharm 环境（带项目名与 Python 版本），不再混入系统 Python；找不到时可选「选择 PyCharm 项目文件夹…」自动定位 .venv。


## v1.3.9

- 「安装 Python 模块」改为自动检测解释器：扫描 PATH、Homebrew/系统目录、pyenv/conda，以及 `~/PycharmProjects/*/.venv|venv|env`，列出带版本号的候选；仍保留「浏览可执行文件…」兜底。


## v1.3.8

- `mclite_bridge` 现在随 JAR 一起打包，设置页新增「安装 Python 模块…」：选择解释器即可离线 pip 安装，PyCharm 里可直接 `import mclite_bridge`。
- 新增 `python/pyproject.toml`，支持 `pip install -e python`；PyBridge ZIP 一并附带。
- `api_tour.py` 在模块缺失时给出安装提示，不再只报 ImportError；README 增加 PyCharm 配置说明。


## v1.3.7

- 实例页的分组下拉改用主题化的 `Ui.DarkCombo`，去掉系统默认的黑边，和搜索/排序下拉一致的圆角与配色。


## v1.3.6

- 修复实例页标题被工具栏挤压：标题与操作按钮分成两行，按钮收进带圆角和边框的工具栏卡片，和整体界面风格一致。


## v1.3.5

- PySide6 前端新增「账户」和「诊断」两个标签页：账户列表标出当前账户，诊断页可按实例分析崩溃并显示 Bridge 返回的建议。


## v1.3.4

- Java 管理新增「测试运行」：直接探测所选运行时的版本、供应商和首行输出，失败时提示路径不可运行。


## v1.3.3

- Bridge 新增 `GET /api/accounts`（账户列表与当前选中账户）和 `GET /api/diagnostics?instance=`（最近崩溃分类建议）。
- Python 客户端新增 `accounts()` / `diagnostics()`；`api_tour.py` 覆盖这两个接口。
- 能力列表同步加入 `accounts`、`diagnostics`，旧路径与旧函数不变。


## v1.3.2

- 新增自绘皮肤编辑器（`SkinEditorDialog`）：64×64 画布、画笔/橡皮/填充/吸管、20 步撤销、导入 PNG、保存即应用到当前账户。
- 账户管理新增重命名/复制账户，改 ID 时自动迁移皮肤文件。
- 实例审查遗留修复：复制/导入后主启动入口同步、官方版本 id 冲突拒绝、备注编码与保存失败提示、分组保留名校验。
- 新增 `python/examples/api_tour.py`：逐行中文注释覆盖全部 Bridge API。
- 新增 `LaunchDiagnostics`：按日志/崩溃报告识别 Java 版本、内存、缺前置、模组不兼容、资源下载五类失败。
- 自检从 70 项增加到 74 项。


## v1.3.1

- 抽出 `VersionListCard`：筛选后的版本列表、空状态、选中与滚动归页面所有；下载中心四张卡片全部搬出 `LauncherFrame`。
- `LauncherFrame` 从 4477 行降到 4555 行（含新增实例功能）。
- 实例管理新增：复制实例（重写 id 与继承引用）、导出实例 ZIP、导入实例 ZIP（zip-slip 校验与 id 冲突保护）。
- 实例支持分组与备注：保存在 `<gameDir>/.mclite/instance-meta.properties`，实例页可按分组筛选，重命名/删除会同步迁移或清理。
- 自检新增实例复制、导出导入 round-trip、分组备注读写、备注跟随重命名 4 项（70 项）。


## v1.3.0

- 抽出 `VersionDetailPage`：版本标题/元信息、加载器选择、版本号输入、安装按钮和推荐模组区域归页面所有，`LauncherFrame` 只保留安装流程。
- 抽出 `ProjectDetailPage`：项目头图/标题/摘要、文件版本列表、前置模组面板、刷新/安装按钮归页面所有。
- `LauncherFrame` 从 4644 行降到 4477 行；下载中心只剩版本列表卡片。
- 内容浏览新增「仅未安装」筛选：切换时按当前实例已安装记录过滤，结果缓存只保留原始列表。
- 批量安装确认可用：多选后按最多 3 个并发安装，整合包仍然限制单选。
- `bump-version.sh` 新增 `minor` 模式（1.2.3 → 1.3.0）；`major` 改为真正的 +1（1.3.0 → 2.0.0）。
- 修复审查发现：侧栏切换版本后版本详情页会按旧版本安装；项目详情快速切换时旧请求会把版本混进新项目。
- 修复：`pendingLaunch` 改为带版本 id，只有目标版本 meta 到达才触发启动。
- 修复：游戏运行中「应用界面样式」重建窗口会丢失进程跟踪；安装完成或切换实例后「仅未安装」筛选不刷新。
- 修复：筛选隐藏全部结果时误触发重新联网搜索；结果计数与翻页按钮改按过滤后数量。
- 修复：项目详情「安装此版本」在下载/安装中恢复为禁用；版本详情重开时加载器高亮正确复位。


## v1.2.3

- 抽出 `DownloadPage`：下载中心顶部栏（标题/搜索/过滤/刷新/详情）、子标签和卡片容器归页面所有，`LauncherFrame` 不再直接操作这些控件。
- `LauncherFrame` 从 5206 行降到 5015 行；四个卡片（版本列表、内容浏览、版本详情、项目详情）仍由 frame 构建后注入，后续继续搬。
- frame control gate 扩展到所有页面控件，`LauncherFrame` 再直接引用页面控件会直接失败。
- 抽出 `ContentPage`：搜索栏、结果列表、选中底栏和分页都归页面所有；`LauncherFrame` 从 5022 行降到 4644 行。
- 新增 `ContentCache`：内容搜索结果按来源/类型/关键词/版本/加载器/排序/页码缓存 5 分钟，最多 40 条，切标签页不再重复请求。
- JAR 上限从 700KB 提高到 800KB，构建时打印剩余空间；当前主 JAR 约 620KB。
- 修复翻页回归：内容浏览的上一页/下一页/页码跳转不再被重置回第 1 页。
- 修复内容缓存命中后丢失 MC百科中文名、翻译开关变化仍命中旧缓存的问题；「刷新」强制绕过缓存，空结果不入缓存。
- 修复选中结果后「版本 / 详情」按钮不可点；busy 状态不再影响详情按钮。
- 修复任务进行中可以「应用界面样式」重建主窗口、导致任务窗口和游戏进程失去归属的问题；busy/启动中会提示稍后再试。
- 配置写入失败不再静默：`Config.save()` 返回结果并清理临时文件，退出时会提示保存失败。
- 更新去重改用与安装流程一致的 staged 路径校验（限定 `updates/`、校验通道与版本），避免手工/跨通道 pending 文件卡住检查。
- CI 的 frame control gate 改为 Python 正则，修复 `contentPage.installButton()`、局部变量 `launchMeta` 的误报，并补上 `accountsDirLabel`。
- 启动器窗口大小会记住（默认 1180×900，超出屏幕自动收敛，最小 1100×720）。
- 启动器日志超过 1MB 自动轮转为 `launcher.log.1`，不再无限增长。
- 已下载的更新在重启生效前不再每 30 分钟重复下载同一版本。


## v1.2.2

- `ConfigPage` 完成控件所有权拆分：账户、游戏目录、Java 下拉和提示都归页面自己持有；`owner.` 调用 56 → 7，控件访问 29 → 0。
- `LaunchPage` 完成控件所有权拆分：账户、版本、启动参数和四个按钮归页面持有；`owner.` 调用 45 → 6，控件访问 17 → 0。
- `LauncherFrame` 从 5288 行降到 5206 行，`ConfigPage` 189 → 293 行，`LaunchPage` 135 → 242 行（控件都搬过来了）。
- `SHA256SUMS.txt` 现在覆盖 `dist/` 全部 7 个可分发产物，不再漏掉 Benchmark 和 PyBridge。
- 页面所有权 CI 基线同步降到 `LaunchPage <=6 / 0`、`ConfigPage <=7 / 0`。


## v1.2.1

- 修复 1.2.0 的启动崩溃：`SettingsPage` 拆分后 `LauncherFrame` 仍引用已搬走的设置控件，点「启动游戏」报 `Cannot invoke "mclite.UI$ValueSlider.getValue()" because "this.memorySlider" is null`。
- `startLaunch`、`setControlsEnabled`、`closeApplication` 改为委托 `SettingsPage`，删除 15 个失效控件字段。
- 切换版本时「自动分配内存」推荐值、CurseForge key 保存恢复生效（此前因字段恒为 null 被静默跳过）。
- CI 新增 frame control gate：`LauncherFrame` 不得再直接引用 `SettingsPage` 的控件。
- 未引入新依赖，JAR 仍小于 700KB。


## v1.2.0

- `SettingsPage` 完成所有权拆分：控件移入页面，构造时只传 Config、TaskCenter 和回调；`owner.` 调用 199→10，控件访问 0。
- 新增页面所有权 CI 门槛；Launch/Config 暂按当前基线锁定，只允许下降。
- 新增 [docs/page-ownership.md](docs/page-ownership.md) 和 [docs/bridge-contract.md](docs/bridge-contract.md)。
- Bridge API 继续使用 `/api` + `/api/v1`，旧 Python 函数保持兼容。
- 1.2.0 作为页面拆分阶段收尾，后续先冻结并修整，不继续堆功能。


## v1.1.98

- Bridge API 增加版本化前缀 `/api/v1`，旧 `/api` 路径继续保留。
- 新增能力查询、最新版本、实例、内容源、Java、任务详情/清理、数据包安装/移除等接口。
- Python 客户端新增对应函数，旧函数名和参数保持不变。
- CI 增加 Bridge 冒烟测试，同时验证旧路径和 `/api/v1`。


## v1.1.97

- 抽出 `LaunchPage` 和 `ConfigPage`，`LauncherFrame` 从 6472 行降到约 5342 行。
- 启动页、启动器配置页的布局和行为不变，截图检查通过。
- 四轮页面拆分完成：Instances、Settings、Launch、Config。


## v1.1.96

- 抽出 `SettingsPage`，`LauncherFrame` 从 6167 行降到约 5622 行。
- 设置页的布局和控件行为不变，截图检查通过。
- Python 模块继续使用 `run_ui.py` 自动寻找 PySide6 解释器。


## v1.1.95

- 抽出 `InstancesPage`，`LauncherFrame` 从 6472 行降到约 6173 行。
- 版本列表页的布局和行为不变，截图检查通过。
- Python 模块新增 `run_ui.py`，会自动寻找装了 PySide6 的 Python 版本。


## v1.1.94

- 清理 Python 模块打包时误带的 `__pycache__`，发布包只包含源码。
- Python Bridge 用法不变。


## v1.1.93

- 新增可选 Python Bridge 模块，不打包进主 JAR，按需下载。
- Java 启动器提供本机回环 JSON API：版本、内容搜索、安装任务、启动、配置、数据包、日志。
- 自带 Python 标准库客户端和可选 PySide6 前端；PySide6 是独立前端，不是注入 Swing。
- Bridge 只监听 127.0.0.1，使用随机 token，不提供任意文件读写。


## v1.1.92

- 内容安装失败时会列出项目名、来源、选择的游戏版本/加载器、项目支持版本和具体原因。
- 批量安装失败时也能看出是哪一个项目出的问题。


## v1.1.91

- CI 增加 `test/run-ci.sh` 无网络回归测试，不再只跑启动器自检。
- 覆盖本地版本解析、JVM 参数、主题 CSS、崩溃分析四个基础模块。
- 手动/网络/UI 测试仍保留在 `test/`，不放进发布流程，避免不稳定。


## v1.1.90

- 撤销启动器设置里的「平原树木」选项，改为 26.1.2 实例自己的全局数据包。
- 平原树木保持「零星几棵」（1% 概率），已同步到该实例所有现有存档。
- 新存档会在下次启动 26.1.2 时自动同步。

## v1.1.88

- 修复 MC百科按钮点击没反应：统一打开 `https://www.mcmod.cn/class/<id>.html`。
- 打开失败时会提示地址，方便手动打开。
- 没有匹配到 MC百科条目的项目会隐藏详情页按钮。


## v1.1.87

- 中文名没有英文原名时，在后面补上原版英文名，例如「钠 (Sodium)」。
- MC百科标题已经带英文时不重复追加。


## v1.1.86

- 中文名直接使用 MC百科标题，不再额外追加英文原名；MC百科标题通常已经带英文。
- MC百科简介里的 `[h1=...]`、`[b]`、`[list]` 等标记继续自动清理。


## v1.1.85

- 清理 MC百科简介里的 `[h1=...]`、`[b]`、`[list]` 等排版标记，只显示正常文字。
- 中文模组名继续保留原版英文名，例如「钠 (Sodium)」。


## v1.1.84

- 中文模组名后面会保留原版英文名，例如「钠 (Sodium)」。
- MC百科标题本身已经带英文时不重复追加。


## v1.1.83

- 下载页的模组名称会自动查询 MC百科中文名，不再只翻译当前搜索关键词刚好匹配到的项目。
- 中文名称和简介会缓存，翻页或再次搜索时不用重复请求。
- 中文名、简介和 MC百科链接仍可关闭：设置里关掉「强制显示中文模组 / 整合包名称」。


## v1.1.82

- 更新窗口的滚动条换成启动器同款细滚动条，不再是系统默认的亮色样式。
- 更新说明会直接显示这次改了什么，不再只给一个链接。
- 精简更新说明，去掉内部实现细节。

## v1.1.81

- 版本列表滑到底部时，最后一个版本图标不会再被状态栏切掉。
- 下载页面的内容不会再压到状态栏上。
- 数据包可以一次安装到所有存档：全局数据包会在启动前同步，新存档下次启动生效。
- 弹窗统一为圆角样式。
- 改善部分网络下连接下载源的等待时间，字体显示更清晰。

## v1.1.80

- 修复下载页面列表盖住底部状态栏的问题。

## v1.1.79

- 数据包支持全局安装。
- 弹窗统一为圆角样式。

## v1.1.78

- 数据包、实例、Java、账户和更新弹窗统一为圆角样式。

## v1.1.77

- 实例的重命名、删除和加载器识别拆到独立模块。
- JAR 增加 700KB 上限检查。
- 改善部分网络连接和字体显示。

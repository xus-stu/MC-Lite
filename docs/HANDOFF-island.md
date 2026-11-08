# MCLite Island 交接说明（给下一个 agent）

最后更新：2026-11-07 21:5x（Asia/Shanghai）
仓库：`/Users/xiaoxu/PycharmProjects/Java`
当前 HEAD：`e62fc8c` 之后（`V1.4.21`，插件独立发布 + B 站头像/封面）
启动器版本：`1.4.24`
模组版本：`0.3.14`

## 1. 背景与用户目标

用户要一个**本地音乐 + B 站音乐**的 Minecraft 客户端模组，作为启动器的「外置可选组件」（不塞进启动器 JAR），并希望 UI 接近 MuoniumPlayer 的观感（圆角、深色玻璃、顶部小胶囊灵动岛），但**帧率必须优先**。之前的 MuoniumPlayer 因 UI 掉到 30 帧被删除，不要再引入 Skija/每帧离屏画布那种方案，除非用户明确要求。

用户偏好：中文、直接动手、少解释；对“像素锯齿/方角/原版按钮”非常敏感；JEI/Jade 信息默认要隐藏；歌词不能乱匹配；B 站需要登录、搜索、收藏、在线播放。

## 2. 仓库结构（island 部分）

```text
island/
  common/src/main/java/dev/mclite/island/
    core/                 Track、Playlist、PlaybackService、IslandConfig、LrcParser、MediaScanner
    core/audio/           JavaSoundEngine、WAV/MP3/OGG/FLAC 解码、PcmEffect
    audio/                AcousticEnvironment、ReverbEffect（场景声学）
    bili/                 BiliApi、BiliCookies、BiliQuality、BiliAudioCache
    lyrics/               AutoLyrics（LRCLIB 匹配）
  versions/1.20.1/fabric/ 旧版外壳（仅本地播放，UI 未跟上 0.3.x）
  versions/26.1.2/fabric/ 主力外壳（用户实际玩的版本）
    mc26/
      McliteIsland26Client.java   入口/按键/上下文/声学探针/B 站装配
      IslandHud26.java            灵动岛（Java2D 烘焙胶囊）
      IslandPlayerScreen26.java   播放器（双栏卡片）
      IslandConfigScreen26.java   设置（左分类/右滚动）
      BiliBrowseScreen26.java     B 站搜索/收藏
      BiliLoginScreen26.java      扫码登录
      LrcImportScreen26.java      路径导入 LRC
      UISurface26.java            Java2D 烘焙 → DynamicTexture → 1 次 blit
      UiPaint26.java              Java2D 静态页面底图（卡片/渐变/阴影）
      UiButton26.java / UiSlider26.java / UiDraw26.java / UiText26.java
  tools/gen_manifest.py  生成启动器清单
```

启动器侧：

```text
src/mclite/IslandCatalog.java      拉 release 的 island-manifest.json（多镜像回退）
src/mclite/IslandInstaller.java    下载/SHA-256/安装/卸载，旧 jar 改名 .bak
src/mclite/LauncherPluginsPanel.java 下载中心「启动器插件」卡片列表 + 详情页
src/mclite/Net.java                白名单与下载（GitHub / raw / 镜像 host）
.github/workflows/island.yml       模组构建
.github/workflows/release.yml      启动器 release + 模组 jar + manifest
```

## 3. 已完成功能

- 本地播放：wav/ogg/mp3/flac（JLayer/JOrbis/jFLAC 已并入 jar）；歌单、模式、进度、音量、seek。
- 灵动岛：顶部居中、160–360 自适应宽度、26/34 高、标题+歌词、进度线；Java2D 烘焙圆角底图，每帧 1 blit。
- B 站：扫码登录（cookie 持久化 `config/mclite-island/bilibili.json`，600 权限）、搜索、收藏夹、在线播放；音频 DASH AAC 下载后用**系统 ffmpeg**转 WAV 缓存（`mclite-island/cache/bili`），URL 过期自动重解析。
- 自动歌词：本地按标题/艺术家/时长从 LRCLIB 匹配；B 站优先官方字幕；缓存到 `lyrics/auto_<hash>.lrc`；匹配已收紧（艺术家必须匹配、时长 ±3s、无时长且非精确标题不显示）。
- 场景声学：水下/室内/雨天自动混响；Freeverb 轻量实现，只在音频线程；关闭时逐字节直通。
- 默认隐藏灵动岛里的 JEI/Jade/自带探针信息（`showContext/useJei/useJade/useOwnProbe` 默认 false，旧配置一次性迁移）；JEI/Jade 自己的悬浮窗不归我们控制。
- 启动器「下载中心 → 启动器插件」卡片 + 详情页；从 GitHub release 清单安装/卸载；Python 模块安装/启动入口。
- 多下载源：清单/简介/jar 优先 `ghfast.top`、`gh-proxy.com`，再直连/备用镜像；jar 仍校验 SHA-256。

## 4. 构建 / 发布命令

```bash
# 模组核心单测
cd /Users/xiaoxu/PycharmProjects/Java/island
./gradlew :common:test --no-daemon

# 1.20.1 Fabric（根 Gradle 8.10 / JDK 21）
cd /Users/xiaoxu/PycharmProjects/Java/island
./gradlew :versions:1.20.1:fabric:bundledJar --no-daemon

# 26.1.2 Fabric（standalone Gradle 9.6.1 / JDK 25）
cd /Users/xiaoxu/PycharmProjects/Java/island/versions/26.1.2/fabric
JAVA_HOME=/tmp/jdk25/Contents/Home ./gradlew clean bundledJar --no-daemon \
  -Dorg.gradle.java.home=/tmp/jdk25/Contents/Home

# 启动器
cd /Users/xiaoxu/PycharmProjects/Java && ./build.sh
java -jar dist/MCLite_v<版本>.jar --self-test
./test.sh

# 发版（tag 必须与 VERSION 一致，release.yml 会校验）
./bump-version.sh patch
git add -A && git commit -m "...; V<版本>"
git tag -a v<版本> -m "..."
git push origin main && git push origin v<版本>
```

产物：`island/versions/26.1.2/fabric/build/libs/mclite-island-26.1.2-fabric-<版本>-bundled.jar`。

## 5. 已知问题 / 风险（优先看）

1. **播放器/灵动岛新版待实机复验**：0.3.4 播放器改为「队列 + 大封面 + 底部传输栏」；传输按钮是自绘圆形图标（`UiIconButton26`，圆底烘焙、每帧 blit + fill）。B 站页新增头像（圆形）/视频封面（`BiliImage26`，后台 2 线程 + ImageIO + LRU 120 张）。重点看：740x490 面板在 guiScale=3 下是否贴边、封面异步加载会不会闪、圆形按钮点击热区。
2. **模组独立发布**：`.github/workflows/island-release.yml` 监听 tag `island-v*`（也可手动 workflow_dispatch），把 jar + manifest 覆盖到固定 release `island-latest`（prerelease，不占 latest）。启动器 1.4.21 起读取 `releases/download/island-latest/island-manifest.json`，旧地址作为回退；以后改模组只需打 `island-v0.3.x` tag + 跑该 workflow，不用 bump 启动器。
3. **`island` / `release` workflow 断流**：v1.4.18 的 island tag run 与 main run 同 commit 一失败一成功，确认是腾讯 Gradle 镜像偶发断流；v1.4.19 的 release 也因此在「Build island mods」失败（tag 已推但没发出去）。两个 workflow 都已加 Gradle 缓存、HTTP 超时和 3 次重试，v1.4.20 的 release 用来验证补发。
3. **1.20.1 外壳未同步新 UI/B 站/声学**：只有本地播放的旧界面；用户主要在 26.1.2，但“1.20.1+ 所有加载器”仍未完成（Forge/NeoForge/1.21.1/26.1.2 NeoForge 都没有）。
4. **26.x 3D 歌词未实现**：`threeDLyrics` 开关会保存但不渲染，26.x 渲染提交 API 重写，需要单独移植。1.20.1 有旧 3D billboard 实现。
5. **B 站依赖系统 ffmpeg**：不是捆绑解码器。没有 ffmpeg 会明确报错；后续可考虑 JCodec（jar 里已含 `net/sourceforge/jaad` 类，但未接）。
6. **歌词匹配仍可能无结果**：本地文件名没有艺术家、未知时长时，严格匹配会宁可不显示；用户可能觉得“没歌词”。可加“文件名/内嵌标签解析”或手动搜索。
7. **Java2D/AWT 风险**：macOS 若以 `-XstartOnFirstThread` 启动，渲染线程创建 `BufferedImage/Graphics2D` 理论上可能卡死；目前未复现。若出问题，退回 sprite/预烘焙 PNG。
8. **HUD 烘焙频率**：岛宽随歌词文本变化，每次变化会重新烘焙/上传一张小纹理（约 200×34），可能造成轻微卡顿；可把宽度量化到 8px 桶并缓存。
9. **JEI/Jade 开关位置**：设置页「灵动岛」分类里有 JEI/Jade/自带探针开关；默认全关。用户如果又说“JEI 还显示”，先确认他说的是我们岛里的信息，还是 JEI 自己的悬浮窗（后者要在 JEI 配置里关）。
10. **启动器插件页 Python 入口**：Python 安装/启动按钮复用 SettingsPage 的方法，若以后重构 SettingsPage 注意保持包级可见。
11. **release 资产上传**：workflow 只上传 region jar（`dist/MCLite_v*-region.jar`），通用 jar 不在 release 里；用户更新用 region 包/本地 dist。
12. **配置迁移**：`contextDefaultsVersion` 会把旧配置的 `showContext/useJei/useJade/useOwnProbe` 一次性置 false；用户手动打开后会保存为 1 并保留，别再次强制覆盖。

## 6. 关键实现细节

- `Track` 现在是 `(Path file, title, artist, durationMs, bvid, cid, audioId, remoteUrl)`；`isRemote()` 表示 B 站源。
- `PlaybackService`：`setRemoteResolver(BiliAudioCache)`、`setOnlineLyricsProvider(bvid/cid → subtitle LRC)`；`maybeAutoLyrics` 异步、等待引擎真实时长后匹配。
- `JavaSoundEngine`：`setEffect(PcmEffect)`；环境探针每 20 tick 更新 `ReverbEffect`，`setEnvironment` 平滑参数。
- `BiliApi`：QR `passport`、`nav`（未登录 -101 也返回 wbi_img）、WBI MD5 签名、search/fav/view/playurl；CDN 下载用 `HttpURLConnection`（`java.net.http` 对部分 B 站 CDN 会连接超时）。
- `UISurface26`：Java2D → `NativeImage`（ARGB）→ `DynamicTexture` → `TextureManager.register`；`blit(GpuTextureView,GpuSampler, x0,y0,x1,y1,u0,u1,v0,v1)` 注意角点签名。
- 启动器 `Net` 白名单新增 `github.com`、`raw.githubusercontent.com`、`objects.githubusercontent.com`、`ghfast.top`、`gh-proxy.com`、`ghproxy.net`、`github.moeyy.xyz`；清单 URL 前缀限制为官方 release。

## 7. 用户最新指令与下一步建议

- 最新反馈：灵动岛仍觉得不圆/不好看，歌词有错配（已收紧匹配，未复验）。
- 下一步优先级：
  1. 让用户启动 0.3.6（已直接装进 `~/Desktop/MC/versions/26.1.2/mods`）看新播放器 + B 站头像/封面，要一张实机截图；GUI Scale=3、约 160 FPS、islandScale=0.9。如果 B 站头像仍不显示，先看 `latest.log` 里 `[mclite-island] B 站图片` 报错，再用 `curl` 验证 i2.hdslb.com 是否可达。
  2. 若玩家嫌面板太满，可把 `PLAYER_W/H` 从 740x490 降到 720x470，或做按屏幕宽度自适应缩放。
  2. 修 `island` workflow 失败（本地跑 `island.yml` 两条命令）。
  3. 让用户列一首“歌词错配”的歌（文件名/标题/艺术家），按实际 LRCLIB 返回调整匹配或加文件名优先策略。
  4. 之后再补 1.20.1 UI/B 站、26.x 3D 歌词、Forge/NeoForge/1.21.1。
- 发版流程已自动化：提交 → tag → push，`release.yml` 会构建两个 jar、生成 `island-manifest.json` 并发布；注意 tag 与 `VERSION` 必须一致。

## 8. 用户环境

- macOS Apple Silicon（M5），MC 26.1.2 Fabric，Java 25，`ffmpeg` 在 `/opt/homebrew/bin/ffmpeg`。
- 游戏目录：`/Users/xiaoxu/Desktop/MC/versions/26.1.2`；音乐目录 `mclite-island/music`（用户此前只放了少量 wav）。
- 已装 JEI 29.53.0.114、Jade 26.1.11；Sound Physics 等大量模组。
- 用户不喜欢被改画质/渲染距离；性能设置只动模组配置。

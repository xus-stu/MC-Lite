# MCLite Island 交接说明（给下一个 agent）

最后更新：2026-11-13（Asia/Shanghai）
仓库：`/Users/xiaoxu/PycharmProjects/Java`
当前 HEAD：以 `git log -1` 为准（0.3.21 功能提交是 `380d702`）
启动器版本：`1.4.25`
模组版本：`0.3.21`

> 先读第 6 节「已知问题」和第 8 节「下一步」。这份文档已经覆盖 0.3.21，但**世界悬浮歌词和方块混响都还没拿到用户的实机确认**。

## 1. 背景与用户目标

用户要一个 **本地音乐 + B 站音乐** 的 Minecraft 客户端模组，作为启动器的「下载中心 → 启动器插件」外置组件，不塞进启动器 JAR。

核心偏好：
- 中文、直接动手、少解释。
- 对「像素锯齿 / 方角 / 原版按钮 / UI 贴屏」非常敏感；要现代、圆滑、小尺寸。
- **灵动岛要像 iPhone 灵动岛 / MacBook 刘海**：小、黑色胶囊，不要大卡片。
- **3D 歌词要像 DangoMusic（团子音乐）的世界悬浮字幕**：锚定在世界坐标、朝向镜头、随距离缩放，**不是 HUD 屏幕贴字**。参考视频：`BV1FwxF6AEZM`；DangoMusic 仓库：`https://github.com/Sorihiko/DangoMusic`。
- 场景声学要「水下闷、石洞/大厅有回声、羊毛/树叶房吸声」；参考 Sound Physics 的思路。
- 歌词不能乱匹配（《海底（四国语言版）》曾被 B 站口播字幕污染）。
- 帧率优先；不要 Skija / 每帧离屏画布；不要改画质/渲染距离。
- 旧版插件 jar **直接删除**，不要堆 `.bak`。

## 2. 仓库结构（island 相关，0.3.21 状态）

```text
island/
  common/src/main/java/dev/mclite/island/
    core/                 Track、Playlist、PlaybackService、IslandConfig、LrcParser、MediaScanner
    core/audio/           AudioEngine 接口、JavaSoundEngine、解码、PcmEffect
    audio/                AcousticEnvironment、ReverbEffect（6 comb + early reflections）
    bili/                 BiliApi、BiliCookies、BiliAudioCache、BiliImageRef
    lyrics/               AutoLyrics（LRCLIB；远端只按标题+时长）
  versions/26.1.2/fabric/ 主力外壳（用户实际玩）
    mc26/
      McliteIsland26Client.java   入口 / 按键 / 声学探针 / B 站 / Mixin 初始化
      IslandHud26.java            22px 高极简灵动岛（目标：iPhone 灵动岛大小）
      IslandPlayerScreen26.java   透明播放器（队列 + 大封面 + 图标传输栏）
      IslandConfigScreen26.java   设置（分类 + 3D 歌词/声学开关）
      BiliBrowseScreen26.java     透明 B 站页（文本导航 + 搜索框 + 封面行）
      BiliLoginScreen26.java      扫码登录
      LrcImportScreen26.java      路径导入 LRC
      LyricsWorld26.java          世界空间悬浮歌词（Dango 式）
      RoomAcoustics26.java        方块材质声学采样
      SoundPhysicsAudioEngine.java  已废弃的 MC 声音引擎后端（保留但未使用）
      LyricsOverlay26.java      已废弃的 HUD 贴屏歌词（保留但未注册）
      PcmAudioStream.java       废弃后端用的 PCM AudioStream
      UiPaint26.java / UISurface26.java / UiDraw26.java / UiButton26.java …
      HudPaint26.java             HUD 底图（纯 AWT）
      UiIconButton26.java        无底色图标按钮
      UiScale26.java             GUI Scale 烘焙倍率
      mixin/GameRendererMixin.java  renderLevel 后触发世界歌词
    src/main/resources/
      mclite-island.mixins.json  JAVA_25
      fabric.mod.json            mixins 已注册
  versions/1.20.1/fabric/ 旧外壳：只有本地播放 + 旧 UI；未同步 B 站/新 UI/世界歌词
  tools/gen_manifest.py     生成 island-manifest.json
```

启动器侧：

```text
src/mclite/IslandCatalog.java       固定 island-latest 清单 + 旧 latest 回退；时间戳防镜像缓存
src/mclite/IslandInstaller.java     下载/SHA-256/安装；旧 jar 直接删除；自动装 Sound Physics 伴随模组
src/mclite/LauncherPluginsPanel.java 插件页；安装任务上报 TaskCenter
src/mclite/Net.java                 白名单；quick fetch / pickFastest / getFirstBytes
.github/workflows/island.yml        模组构建（1.20.1 + 26.1.2）
.github/workflows/island-release.yml island-v* tag → island-latest（只发模组）
.github/workflows/release.yml       启动器 release（也会带 island jar）
```

## 3. 当前功能状态

- 本地播放：wav/ogg/mp3/flac；歌单、模式、进度、音量、seek。
- 灵动岛：22px 高、118–262 宽、黑色胶囊、左侧三根电平 + 一行标题/歌词；按 GUI Scale 烘焙，圆角平滑。
- 播放器：**透明背景**，不画面板/玻璃底；大封面 + 歌词 + 细进度条 + 无底色图标传输栏；每帧按窗口重算坐标。
- B 站：扫码登录、头像/昵称、搜索、收藏夹、视频封面、在线播放；音频仍依赖系统 ffmpeg 转 WAV 缓存。
- B 站歌词：**不再使用 B 站 CC 字幕**（口播会污染歌词）；远端统一 LRCLIB 标题 + 时长 ±8 秒；`lyricsCacheVersion` 升级时删除旧 `auto_*` / `bili_*`。
- 3D 歌词：`LyricsWorld26` + `GameRendererMixin` 在 `LevelRenderer.renderLevel` 之后画；世界锚点、朝向镜头、按距离缩放。**未实机确认**。
- 场景声学：
  - JavaSound + `ReverbEffect`（6 梳状 + 2 全通 + 6 早期反射）。
  - `RoomAcoustics26` 采样 11×7×11 方块，按材质给 absorption；**封闭判断只看 `!canSeeSky`**，户外不应再触发室内混响。
  - 水下低通 + 高 wet；设置里「水下 / 室内 / 雨天」开关必须打开才生效。
- 启动器：
  - 插件清单固定 `island-latest`，请求带时间戳；jar 带版本 hash 防镜像缓存；404 自动刷新清单并重试。
  - 插件安装显示在「任务」。
  - 安装/更新时删除旧 `mclite-island-*.jar`、`.jar.bak`、`.part`。
  - 自动安装/更新 **Sound Physics Remastered** 作为伴随模组（Modrinth，Fabric，GPL 独立模组，不塞进岛 jar）。

## 4. 版本与发布

- 启动器最新 release：**v1.4.25**（GitHub latest）。注意 v1.4.25 的 release 资产里岛 jar 是 **0.3.19**；0.3.20/0.3.21 只通过 `island-latest` 发布。
- 岛最新：**0.3.21**，地址：
  `https://github.com/xus-stu/MC-Lite/releases/download/island-latest/island-manifest.json`
- 启动器 1.4.25 插件页读 `island-latest`，所以能装到 0.3.21。
- 旧版 jar 资产保留（不再清理 `island-latest` 的旧 jar），避免旧清单 404。

## 5. 构建 / 发布命令

```bash
# 模组单测
cd /Users/xiaoxu/PycharmProjects/Java/island
./gradlew :common:test --no-daemon

# 1.20.1（Gradle 8.10 / JDK 21）
./gradlew :versions:1.20.1:fabric:bundledJar --no-daemon

# 26.1.2（Gradle 9.6.1 / JDK 25）
cd /Users/xiaoxu/PycharmProjects/Java/island/versions/26.1.2/fabric
JAVA_HOME=/tmp/jdk25/Contents/Home ./gradlew clean bundledJar --no-daemon \
  -Dorg.gradle.java.home=/tmp/jdk25/Contents/Home

# 启动器
cd /Users/xiaoxu/PycharmProjects/Java
./test.sh
./build.sh

# 只发模组（不 bump 启动器）
./bump-version.sh 不要用；手动改 island/build.gradle 与 versions/26.1.2/fabric/build.gradle
git add -A && git commit -m "Island 0.3.x: ..."
git tag -a island-v0.3.x -m "Island 0.3.x"
git push origin main && git push origin island-v0.3.x

# 发启动器（tag 必须与 VERSION 一致）
./bump-version.sh patch
./test.sh && ./build.sh
git add -A && git commit -m "...; V1.4.x"
git tag -a v1.4.x -m "..."
git push origin main && git push origin v1.4.x
```

注意：`island-release` 会把 jar 名去掉 `-bundled` 再上传；不要直接上传 `*-bundled.jar`，否则 manifest URL 会 404。

## 6. 已知问题 / 风险（优先看）

1. **3D 世界悬浮歌词未实机确认**：0.3.19 加了 `LyricsWorld26` + `GameRendererMixin`，0.3.21 已把 RenderSystem modelview 压成 identity。用户还没有确认是否可见。若不可见或崩溃：
   - 看 `latest.log` 里 `Mixin` / `mclite-island.mixins.json` / 崩栈；
   - 对照 DangoMusic `modern26` 的 `GameRendererMixin` 与 `ModernDango.worldRenderEnd`；
   - 当前注入点是 `LevelRenderer.renderLevel(...)` 之后（Shift.AFTER），target 描述符在 mixin 文件里。
2. **Sound Physics 没有接管音乐**：`SoundPhysicsAudioEngine` 在 26.1.2 下能注入、`SoundEngine.play` 返回 STARTED，但拿不到音频流，最终静音。已放弃该路径，音乐走 JavaSound。启动器只负责把 Sound Physics 作为**伴随模组**装上，对游戏声音生效，对音乐无效。`SoundPhysicsAudioEngine.java` / `PcmAudioStream.java` / `LyricsOverlay26.java` / `lyrics3dStyle` 都是死代码，接手可删。
3. **混响质量/户外误判**：0.3.21 已修 `enclosed`（只看 canSeeSky）并真正编译了 6 comb 混响；用户尚未复验。`RoomAcoustics26` 是方块密度采样，不是 Sound Physics 的射线追踪；如果要更准，需要移植 DangoMusic `RoomAcoustics` 的 raycast（源码在 `/tmp/DangoMusic` 或 GitHub）。
4. **FPS**：用户全屏（2539×1651）时 41–78 FPS，窗口化约 160 FPS；先排除分辨率/光影，再 profile。HUD/播放器都是 1 blit + 文本；3D 歌词每帧少量文本。
5. **`soundPhysicsAudio` 配置字段仍在 JSON 里但已无 UI/无效果**；`lyrics3dStyle` 字段/`LyricsOverlay26` 也无 UI/未注册。清理时注意旧配置兼容。
6. **1.20.1 外壳未同步**：只有本地播放旧 UI；B 站、新 HUD、世界歌词、方块声学都没有。
7. **B 站音频依赖系统 ffmpeg**（`/opt/homebrew/bin/ffmpeg`）；没有会明确报错。JCodec 未接。
8. **插件下载/镜像**：清单/jar 已加时间戳/版本 hash；若再出现 404，先看是否 `island-latest` 资产名与 manifest 不一致（必须无 `-bundled`），再刷新清单；启动器 1.4.24+ 会自动重试。
9. **旧版清理**：启动器 1.4.25 已改成直接删旧 jar；用户 mods 目录里历史上 19 个 `.bak` 已手动清理。新代码不要再写 `.bak`。
10. **release 资产**：`release.yml` 只上传 region jar；通用 jar 不在 release。最新 release 可能不含最新 island jar（island-only 发布），以 `island-latest` 为准。

## 7. 关键实现细节

- `AcousticEnvironment` 现在是 `(submerged, enclosed, raining, roomSize, absorption)`；4 参构造保留。
- `ReverbEffect.process(byte[] buffer, int length, AudioFormat)`：原地 16-bit little-endian；不要改回 short[]。
- `LyricsWorld26`：世界锚点 = 眼睛 + 视线 × `lyricsDistance` + 垂直偏移；歌词行变化或距眼睛 >18 格时重设锚点；渲染时 `PoseStack` 先 rotateX(cameraXRot) rotateY(cameraYRot+180)，再平移相对相机、yaw/pitch 朝向镜头，scale(-s,-s,s)。
- `GameRendererMixin`：`@Inject(method="renderLevel", at=@At(value="INVOKE", target="Lnet/minecraft/client/renderer/LevelRenderer;renderLevel(...)V", shift=AFTER))`；target 里第一个参数是 `com/mojang/blaze3d/resource/GraphicsResourceAllocator`，CameraRenderState 包名是 `net/minecraft/client/renderer/state/level/`。
- `BiliBrowseScreen26`：`extractBackground` 空实现（透明），`EditBox` 无边框，搜索框底图由 `UiPaint26.searchField` 按 GUI Scale 烘焙。
- `UISurface26.get(key,w,h,painter,renderScale)`：小控件传 `UiScale26.current()`；大页面仍 1x。
- `IslandInstaller.ensureSoundPhysics`：Modrinth project `sound-physics-remastered`，按实例 MC/loader 取最新 Fabric 文件，已有同文件名跳过，旧 SP jar 删除；失败不影响主插件。
- `IslandCatalog.fetch`：8 个候选并行竞速（`Net.getFirstBytes`），清单 URL 带 `?ts=`。
- `IslandInstaller`：jar URL 带 `?v=<sha256 前 8 位>`；旧 jar 直接删；安装任务写 `TaskCenter`。

## 8. 下一步建议（按优先级）

1. **让用户实机验收 0.3.21**：世界悬浮歌词是否出现、户外是否还触发室内混响、石洞/羊毛房/水下是否有区别；要 `latest.log` 和截图。
2. 若世界歌词不可见：先看 mixin 是否 apply（日志/崩栈），再对照 DangoMusic 的 `ModernDango.worldRenderEnd`；必要时把渲染改到 `GameRendererMixin` 的同一位置并尝试自定义 projection。
3. 若混响仍差：移植 DangoMusic `RoomAcoustics` 的射线追踪 + `EnvironmentAudio` 参数（源码已拉过：`/tmp/DangoMusic`，或 GitHub `Sorihiko/DangoMusic`）。
4. 清理死代码：`SoundPhysicsAudioEngine`、`PcmAudioStream`、`LyricsOverlay26`、`lyrics3dStyle`、`soundPhysicsAudio`；或把 3D 风格配置重新接到世界歌词上。
5. 同步 1.20.1 外壳（至少新 HUD/播放器；B 站与 Mixin 另说）。
6. 若用户还要“所有 Muonium 风格”，再基于世界歌词实现 Tilt/Apple/Diorama 等，而不是 HUD 贴屏。
7. 性能：用窗口化/全屏对比 FPS，确认不是全屏分辨率/光影；再 profile 3D 歌词。

## 9. 用户环境

- macOS Apple Silicon，MC 26.1.2 Fabric，Java 25；`ffmpeg` 在 `/opt/homebrew/bin/ffmpeg`。
- 游戏目录：`/Users/xiaoxu/Desktop/MC/versions/26.1.2`；音乐目录 `mclite-island/music`。
- mods 约 116 个，含 JEI 29.53.0.114、Jade 26.1.11、Sound Physics Remastered 1.5.2+26.1.2。
- 当前 mods 里只应保留 `mclite-island-26.1.2-fabric-0.3.21.jar`，不要再加 `.bak`。
- 配置：`~/Desktop/MC/versions/26.1.2/config/mclite-island.json`；`acousticsSubmersion/Enclosure/Rainfall` 用户已打开。
- 用户 GUI Scale=3，islandScale=0.9，常用全屏；曾反馈 41–78 FPS。
- 日志：`~/Desktop/MC/versions/26.1.2/logs/latest.log`；启动器：`~/.mclite/logs/launcher.log`。

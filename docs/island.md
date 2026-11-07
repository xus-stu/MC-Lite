# MCLite Island（可选扩展组件）

本地音乐播放 + LRC 歌词 + 灵动岛，**纯客户端、零登录、零联网**。不随启动器打包，由启动器按需下载安装。

## 安装

1. 启动器 → **下载中心 → 启动器插件**
2. 选择目标实例（必须已安装对应 MC 版本与加载器）
3. 选中构建 → 安装；重启游戏生效
4. 卸载：同一页面点「卸载」

手动安装也可以：把 `mclite-island-<mc>-<loader>-<版本>.jar` 放进实例的 `mods/`。

## 目录

```text
<实例目录>/mclite-island/music/     # 音乐文件（mp3 / ogg / wav / flac，支持子目录）
<实例目录>/mclite-island/lyrics/    # 导入的 LRC
<实例目录>/config/mclite-island.json
```

歌词匹配顺序：同目录同名 `.lrc` → `lyrics/` 下同名 `.lrc` → 游戏内「导入 LRC」。

## 按键（默认，可在「选项 → 控制」重绑）

| 按键 | 功能 |
|---|---|
| `M` | 打开播放器 |
| `V` | 切换 3D 歌词 |
| `PageUp` / `PageDown` | 上一首 / 下一首 |
| 小键盘 `+` / `-` | 音量 |
| 小键盘 `*` | 播放 / 暂停 |

播放器界面里还有歌单列表、播放模式、导入 LRC、打开音乐/歌词目录、灵动岛位置与缩放。

## 灵动岛

- 播放中显示歌名 + 当前歌词；没有播放时显示上下文信息
- 上下文优先级：**JEI 物品列表悬停**（装了 JEI 时）→ **Jade 目标提示**（装了 Jade 时）→ 自带射线探针（方块/实体名、mod id、血量）
- optional：JEI/Jade 未安装或版本不匹配时自动降级，不会崩
- 位置/缩放保存在配置里；F1 隐藏 GUI 时一并隐藏
- 纯原版绘制，无模糊/后处理；空闲时整帧跳过

## LRC 格式

支持 `[mm:ss]`、`[mm:ss.xx]`、一行多个时间标签、`[offset:]`、`[ti:]`、`[ar:]`；UTF-8 / GBK 自动识别。

## 版本支持

| MC | Fabric | Forge | NeoForge | 3D 歌词 | JEI/Jade |
|---|---|---|---|---|---|
| 1.20.1 | ✅ | 待做 | 待做 | ✅ | 仅自带探针 |
| 26.1.2 | ✅ | — | 待做 | 待做（26.x 渲染 API 重写） | ✅（可选） |

## 从源码构建

```bash
cd island && ./gradlew :versions:1.20.1:fabric:bundledJar    # JDK 21，Gradle 8.10
cd island/versions/26.1.2/fabric && JAVA_HOME=/path/to/jdk25 ./gradlew bundledJar   # JDK 25，Gradle 9.6.1
```

产物：`build/libs/mclite-island-<mc>-<loader>-<版本>-bundled.jar`。

启动器清单由 `island/tools/gen_manifest.py` 生成，随启动器 release 作为 `island-manifest.json` 资产发布；启动器只读取该清单，不内置任何 mod jar。

## 第三方库

| 库 | 用途 | 许可证 |
|---|---|---|
| JLayer | MP3 解码 | LGPL-2.1 |
| JOrbis | OGG Vorbis 解码 | BSD |
| jFLAC | FLAC 解码 | Apache-2.0 |

以上库以未修改的 class 形式并入 mod jar；源代码可从各自上游获取。

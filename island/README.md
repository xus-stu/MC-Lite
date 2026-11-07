# MCLite Island（源码）

本地音乐播放器 + 3D 歌词 + 灵动岛，Minecraft 客户端模组。用户文档见 [../docs/island.md](../docs/island.md)。

## 目录

```text
common/                    纯 Java 核心（歌单、LRC、配置、解码、播放引擎）+ JUnit
versions/1.20.1/fabric/    1.20.1 Fabric 外壳（Gradle 8.10 / JDK 17 target）
versions/26.1.2/fabric/    26.1.2 Fabric 外壳（Gradle 9.6.1 / JDK 25，无映射）
tools/gen_manifest.py      生成启动器用的 island-manifest.json
NOTICE                     第三方解码库许可证说明
```

## 测试与构建

```bash
# 核心单测（含真实 WAV/MP3/OGG/FLAC 解码冒烟，本机有 ffmpeg 时自动扩展）
cd island && ./gradlew :common:test

# 1.20.1 Fabric
cd island && ./gradlew :versions:1.20.1:fabric:bundledJar

# 26.1.2 Fabric（需要 JDK 25，gradle.properties 指向 /tmp/jdk25）
cd island/versions/26.1.2/fabric && ./gradlew bundledJar
```

## 设计约束

- 不引入 Minecraft 之外的重型渲染库；只用原版字体/矩形绘制
- 音频走 Java Sound，独立于 MC 声音引擎，版本间共用
- 核心 `common` 不 import 任何 Minecraft 类；外壳只做接线和渲染
- 26.x 使用 `GuiGraphicsExtractor` / `HudElementRegistry` 新 API；3D 歌词在 26.x 上待移植

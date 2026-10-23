# MC Lite

Java 21 + Swing 写的 Minecraft 启动器，支持 Windows、macOS 和 Linux。

![下载中心](docs/screenshot.png)

![启动页](docs/launch.png)

## 能做什么

- 从 Mojang 获取完整版本列表，正式版、快照和旧版本都能选。
- 读取本地客户端、运行库和资源文件；游戏本体需自行从官方渠道获取。
- 每个版本可以单独隔离存档、模组、资源包和配置。
- 扫描本地 `versions/`，也支持导入 HMCL 的游戏目录。
- 离线账户的创建、切换、删除，以及皮肤头像。
- 自动检测 Java；缺少运行时可以从 Mojang 下载，也可以手动添加 `java` / `java.exe`。
- 调整内存、JVM 参数、窗口大小和界面 CSS。
- 下载模组、资源包和光影，支持 Modrinth、CurseForge 和 MC百科中文搜索。
- 下载任务、进度、速度、剩余时间，以及异常退出后的崩溃报告。

## 运行

当前版本：V1.0.4

需要 Java 21 或更高版本。

```bash
./run-region.sh
```

macOS 可以打开 `dist/MC Lite Region v1.0.4.app`，或者双击 `启动器-地区版.command`。

Windows：

```bat
run-region.bat
```

也可以直接运行：

```bash
java -jar dist/MCLite_v1.0.4-region.jar
```

## 地区限制

启动时会向 Cloudflare 查询网络归属地，只读取 `loc` 字段，不保存 IP。网络不可用时，退回到系统语言和时区。

出口 IP 在中国大陆时：

- 不下载游戏本体
- 没有本地游戏版本时，不下载加载器
- 不下载整合包
- 模组、资源包和光影仍然可以下载
- 游戏本体和整合包会跳转到官网，由用户自行下载

内置锁定，正常使用无法通过 `--region` 参数关闭。锁定文件随 jar 分发。

为遵守相关法律法规，我们不对中国大陆地区提供游戏本体和整合包下载服务，模组下载服务可用，请前往官网自行下载。

## 版本命名

- 小版本：`x.y.(z+1)`
- 大版本：`x.(y+1).(z+5)`

构建文件、应用包和启动器内部版本都使用同一个版本号。

升级版本：

```bash
./bump-version.sh patch
./bump-version.sh major
```

然后重新运行 `./build.sh`。

## 构建

```bash
./build.sh
```

会生成：

```text
dist/MCLite_v1.0.4-region.jar
dist/MC Lite Region v1.0.4.app
dist/MC-Lite_v1.0.4_region_macOS.zip
dist/MC-Lite_v1.0.4_region_Windows.zip
dist/SHA256SUMS.txt
```

Windows 解压 `dist/MC-Lite_v1.0.4_region_Windows.zip`，双击 `start.bat`。

## Java 和游戏目录

默认目录是 `~/.mclite`。游戏目录可以在启动器配置里修改，也可以直接导入 HMCL 的目录。

Windows 便携版内置独立的 Java 21 运行时，使用自己的 `runtime`，不会修改或覆盖系统 JDK。

macOS 设置页有一项「强制使用 Metal 渲染」，只影响启动器界面，重启后生效；如果开启后黑屏，关闭它再重启。

紧急恢复时可以加参数启动：

```bash
java -jar dist/MCLite.jar --software-render
```

启动器日志在 `~/.mclite/logs/`。设置页右上角有「打开日志目录」。Windows 下路径为：

```text
C:\Users\<用户名>\.mclite\logs
```

版本隔离默认开启。关闭后，所有版本共用同一个 `.minecraft`。

## 下载内容

- Modrinth 不需要 Key。
- CurseForge 要在设置里填写自己的 API Key。
- 模组、资源包、光影会安装到当前实例对应的目录。
- 下载的模组版本会跟随当前选择的游戏版本，整合包版本也会自动识别。

## 安全

- 只从 HTTPS 白名单域名下载。
- Mojang 和 Modrinth 文件会校验哈希。
- 下载先写临时文件，校验通过后再替换。
- 解压和路径检查限制在目标目录内。
- 不保存密码和 access token。
- 不安装系统服务，也不自动更新。

更详细的白名单和校验说明见 [SECURITY.md](SECURITY.md)。

## 账户和皮肤

离线账户默认保存在 `~/.mclite/accounts/`，可以在设置里改位置。

离线模式不会自动加载皮肤。需要皮肤的话，游戏内要装 CustomSkinLoader 一类的模组。

## 使用要求

- 使用本启动器需要自备正版 Minecraft。
- 游戏本体和整合包需要自行从官方渠道获取，再导入本地实例。
- 本项目与 Mojang、Microsoft、网易均无关联。
- Minecraft 是 Mojang Studios / Microsoft 的商标。
- Fabric、Forge、NeoForge、Quilt 是各自项目的商标。
- MC百科简介来自公开搜索结果，遵循 CC BY-NC-SA 3.0，并保留原文链接。
- 账户信息只存在本地，以明文保存，不保存密码或 access token。
- 本项目使用 PolyForm Noncommercial 1.0.0，不得主要用于商业利益或获取金钱报酬。许可证在 `META-INF/LICENSE`。
- 修改版必须保留 NOTICE，并标注为非官方修改版。

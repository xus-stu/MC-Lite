# 安全说明

## 下载来源

只从以下 HTTPS 域名下载：

- Mojang：`piston-meta.mojang.com`、`launchermeta.mojang.com`、`launcher.mojang.com`、`piston-data.mojang.com`、`libraries.minecraft.net`、`resources.download.minecraft.net`
- Modrinth：`api.modrinth.com`、`cdn.modrinth.com`、`cdn-raw.modrinth.com`
- CurseForge：`api.curseforge.com`、`edge.forgecdn.net`、`mediafilez.forgecdn.net`、`media.forgecdn.net`
- 加载器：`meta.fabricmc.net`、`maven.fabricmc.net`、`meta.quiltmc.org`、`maven.quiltmc.org`、`maven.minecraftforge.net`、`maven.neoforged.net`
- 镜像：`bmclapi2.bangbang93.com`
- MC百科：`search.mcmod.cn`、`www.mcmod.cn`
- 启动器更新：`api.github.com`、`github.com`、`objects.githubusercontent.com`，以及 `Updater` 中列出的 GitHub 加速镜像

其他地址会被拒绝。

## 文件校验

- Mojang 文件使用元数据中的 SHA-1。
- Modrinth / CurseForge 文件使用文件哈希。
- Fabric / Quilt 库缺少哈希时读取官方 `.sha1`。
- Forge / NeoForge 安装器使用官方 `.sha1`。
- 下载写入 `.part`，校验通过后再替换目标文件。
- 启动器更新会校验 GitHub Release 提供的 SHA-256（可用时）、`Main-Class`、
  `region.lock` / `unrestricted` 标记和内部版本号。
- 更新包只写入 `~/.mclite/updates/` 中转，校验通过后才替换实际安装的 jar 或 macOS 应用包。

## 路径安全

- 版本 ID、资源路径和库路径会检查。
- 禁止 `..` 和绝对路径。
- 整合包和 overrides 解压限制在实例目录内。
- 皮肤只接受 64×64 / 64×32 PNG。

## 本地数据

- 账户和皮肤只保存在本地。
- 不保存密码或 access token。
- 没有遥测或广告；自动更新只检查 GitHub Release 并替换启动器自身文件。
- 不修改系统 PATH 或注册表。

## 限制

Minecraft 客户端、模组和 Java 本身都是可执行程序。启动器只能保证下载来源和哈希，不能保证上游代码没有漏洞。

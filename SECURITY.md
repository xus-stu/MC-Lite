# 安全说明

## 下载来源

只从以下 HTTPS 域名下载：

- Mojang：`piston-meta.mojang.com`、`launchermeta.mojang.com`、`launcher.mojang.com`、`piston-data.mojang.com`、`libraries.minecraft.net`、`resources.download.minecraft.net`
- Modrinth：`api.modrinth.com`、`cdn.modrinth.com`、`cdn-raw.modrinth.com`
- CurseForge：`api.curseforge.com`、`edge.forgecdn.net`、`mediafilez.forgecdn.net`、`media.forgecdn.net`
- 加载器：`meta.fabricmc.net`、`maven.fabricmc.net`、`meta.quiltmc.org`、`maven.quiltmc.org`、`maven.minecraftforge.net`、`maven.neoforged.net`
- 镜像：`bmclapi2.bangbang93.com`
- MC百科：`search.mcmod.cn`、`www.mcmod.cn`

其他地址会被拒绝。

## 文件校验

- Mojang 文件使用元数据中的 SHA-1。
- Modrinth / CurseForge 文件使用文件哈希。
- Fabric / Quilt 库缺少哈希时读取官方 `.sha1`。
- Forge / NeoForge 安装器使用官方 `.sha1`。
- 下载写入 `.part`，校验通过后再替换目标文件。

## 路径安全

- 版本 ID、资源路径和库路径会检查。
- 禁止 `..` 和绝对路径。
- 整合包和 overrides 解压限制在实例目录内。
- 皮肤只接受 64×64 / 64×32 PNG。

## 本地数据

- 账户和皮肤只保存在本地。
- 不保存密码或 access token。
- 没有遥测、广告或后台更新。
- 不修改系统 PATH 或注册表。

## 限制

Minecraft 客户端、模组和 Java 本身都是可执行程序。启动器只能保证下载来源和哈希，不能保证上游代码没有漏洞。

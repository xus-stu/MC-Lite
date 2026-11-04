package mclite;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

public final class SelfTest {
    private SelfTest() {
    }

    public static int run(Config config) {
        int passed = 0;
        try {
            Object parsed = Json.parse("{\"a\":\"x\\n\\u4e2d\",\"n\":-12,\"f\":1.5,\"b\":true,\"z\":null,"
                    + "\"arr\":[1,2,3]}");
            Map<String, Object> object = Json.obj(parsed);
            check("x\n中".equals(Json.str(object, "a")), "JSON 字符串转义");
            check(Json.lng(object, "n", 0) == -12, "JSON 整数");
            check(Json.arr(object.get("arr")).size() == 3, "JSON 数组");
            passed += 3;

            Modrinth.Project cacheSample = new Modrinth.Project("id", "slug", "title", "desc",
                    "author", 1, null, "mod");
            ContentCache cache = new ContentCache(60_000, 2);
            ContentCache.Key cacheKey = new ContentCache.Key("modrinth", "mod", "a", "26.1.2",
                    "", "relevance", 0, false);
            ContentCache.Result cacheResult = new ContentCache.Result(List.of(cacheSample),
                    Map.of("id", "中文名"));
            cache.put(cacheKey, cacheResult);
            check(cache.get(cacheKey) != null
                    && cache.get(cacheKey).projects().size() == 1
                    && "中文名".equals(cache.get(cacheKey).chineseTitles().get("id")), "内容缓存命中");
            ContentCache expired = new ContentCache(0, 2);
            expired.put(cacheKey, cacheResult);
            check(expired.get(cacheKey) == null, "内容缓存过期");
            cache.put(new ContentCache.Key("modrinth", "mod", "b", "26.1.2", "", "relevance", 0, false),
                    cacheResult);
            cache.put(new ContentCache.Key("modrinth", "mod", "c", "26.1.2", "", "relevance", 0, false),
                    cacheResult);
            check(cache.size() == 2, "内容缓存上限");
            passed += 3;

            Rules.Env env = new Rules.Env("osx", "arm64", "15.0", Map.of("has_custom_resolution", true));
            List<Rules.Rule> allowOsx = Rules.Rule.parseList(Json.arr(Json.parse(
                    "[{\"action\":\"allow\",\"os\":{\"name\":\"osx\"}}]")));
            check(Rules.allows(allowOsx, env), "OS 规则 allow");
            List<Rules.Rule> disallowOsx = Rules.Rule.parseList(Json.arr(Json.parse(
                    "[{\"action\":\"allow\"},{\"action\":\"disallow\",\"os\":{\"name\":\"osx\"}}]")));
            check(!Rules.allows(disallowOsx, env), "OS 规则 disallow");
            List<Rules.Rule> allowArm = Rules.Rule.parseList(Json.arr(Json.parse(
                    "[{\"action\":\"allow\",\"os\":{\"name\":\"osx\",\"arch\":\"arm64\"}}]")));
            check(Rules.allows(allowArm, env), "架构规则");
            List<Rules.Rule> featureRule = Rules.Rule.parseList(Json.arr(Json.parse(
                    "[{\"action\":\"allow\",\"features\":{\"has_custom_resolution\":true}}]")));
            check(Rules.allows(featureRule, env), "feature 规则");
            passed += 4;

            check(Rules.splitLegacy("--username ${auth_player_name} --gameDir \"/tmp/a b\"")
                    .size() == 4, "旧版参数分词");
            passed++;

            check(GameLauncher.offlineUuid("Steve").toString().startsWith("5627dd98"),
                    "离线 UUID");
            passed++;

            VersionManifest spaced = VersionManifest.parse("""
                    {"latest":{"release":"1.14.2 Pre-Release 4","snapshot":""},
                     "versions":[{"id":"1.14.2 Pre-Release 4","type":"snapshot",
                       "url":"https://piston-meta.mojang.com/x.json",
                       "sha1":"0000000000000000000000000000000000000000","releaseTime":"2019-05-27T07:21:11+00:00"}]}
                    """);
            check(spaced.find("1.14.2 Pre-Release 4") != null, "带空格的版本 ID");
            passed++;
            check(GamePaths.safeId("3D Shareware v1.34").equals("3D Shareware v1.34"),
                    "带空格的版本目录");
            passed++;

            Path base = Path.of("/tmp/mclite-base");
            check(GamePaths.resolveUnder(base, "libraries/a.jar").startsWith(base),
                    "安全路径解析");
            try {
                GamePaths.resolveUnder(base, "../escape");
                check(false, "路径穿越必须被拒绝");
            } catch (IllegalArgumentException ex) {
                check(true, "路径穿越必须被拒绝");
                passed++;
            }

            check(Net.normalizeAndValidate("http://resources.download.minecraft.net/ab/abcdef")
                            .getScheme().equals("https"),
                    "旧 HTTP 地址升级为 HTTPS");
            passed++;
            String encoded = Net.normalizeAndValidate(
                    "https://piston-meta.mojang.com/v1/packages/x/1.14.2%20Pre-Release%204.json")
                    .toString();
            check(encoded.contains("%20") && !encoded.contains("%2520"),
                    "URL 百分号编码不会被二次转义");
            passed++;
            String special = Net.normalizeAndValidate(
                    "https://cdn.modrinth.com/data/a/versions/b/New-§6[Default]§r.zip")
                    .toString();
            check(special.contains("%C2%A7") && special.contains("%5B"),
                    "Modrinth 特殊字符 URL 编码");
            passed++;
            check(McMod.cleanText("[h1=简介]这是简介[/h1]").equals("这是简介"),
                    "MC百科简介 BBCode 清理");
            passed++;
            String installError = new ContentInstallException(new Modrinth.Project(
                    "abc", "sodium", "Sodium", "", "作者", 0, "",
                    "mod", null, null, "modrinth"),
                    "1.21.1", "fabric", "1.21, 1.21.1", new IOException("network down"))
                    .getMessage();
            check(installError.contains("Sodium") && installError.contains("1.21.1")
                            && installError.contains("支持版本") && installError.contains("network down"),
                    "安装错误包含项目与版本范围");
            passed++;
            try {
                Net.normalizeAndValidate("https://example.com/evil.jar");
                check(false, "非官方域名必须被拒绝");
            } catch (SecurityException ex) {
                check(true, "非官方域名必须被拒绝");
                passed++;
            }

            String json = """
                    {
                      "id":"selftest","type":"release","mainClass":"net.minecraft.client.main.Main",
                      "assets":"legacy",
                      "downloads":{"client":{"sha1":"0000000000000000000000000000000000000000",
                        "size":1,"url":"https://piston-data.mojang.com/v1/objects/x/client.jar"}},
                      "libraries":[],
                      "arguments":{
                        "jvm":["-Djava.library.path=${natives_directory}","-cp","${classpath}"],
                        "game":["--username","${auth_player_name}","--version","${version_name}",
                          "--gameDir","${game_directory}","--assetsDir","${assets_root}",
                          "--assetIndex","${assets_index_name}","--uuid","${auth_uuid}",
                          "--accessToken","${auth_access_token}","--userType","${user_type}",
                          "--width","${resolution_width}","--height","${resolution_height}"]
                      },
                      "javaVersion":{"component":"java-runtime-epsilon","majorVersion":25}
                    }
                    """;
            VersionMeta meta = VersionMeta.parse(json, "selftest");
            check(meta.javaMajor() == 25, "Java 版本解析");
            VersionManifest.VersionEntry entry = new VersionManifest.VersionEntry(
                    "selftest", "release", "https://piston-meta.mojang.com/x.json",
                    "0000000000000000000000000000000000000000", "2026-01-01T00:00:00+00:00", 1);
            config.username("TestUser");
            config.windowWidth(854);
            config.windowHeight(480);
            config.autoJvmTuning(false);
            Path java = Path.of(System.getProperty("java.home"), "bin",
                    System.getProperty("os.name", "").toLowerCase().contains("win")
                            ? "java.exe" : "java");
            List<String> command = GameLauncher.buildCommand(config, meta, entry, java, false);
            check(command.contains("TestUser"), "启动参数用户名替换");
            check(command.contains("--width") && command.contains("854"), "启动参数窗口尺寸");
            check(command.contains("-Djava.library.path=" + new GamePaths(config.gameDir())
                    .nativesDir("selftest")), "启动参数 natives 路径");
            String legacyJson = """
                    {"id":"legacy-test","mainClass":"net.minecraft.client.main.Main",
                     "assets":"legacy","downloads":{"client":{"url":"https://piston-meta.mojang.com/x.jar",
                     "sha1":"0000000000000000000000000000000000000000","size":1}},
                     "minecraftArguments":"--username ${auth_player_name} --width 1280 --height 720",
                     "javaVersion":{"component":"jre-legacy","majorVersion":8}}
                    """;
            VersionMeta legacy = VersionMeta.parse(legacyJson, "legacy-test");
            VersionManifest.VersionEntry legacyEntry = new VersionManifest.VersionEntry(
                    "legacy-test", "release", "https://piston-meta.mojang.com/x.json",
                    "0000000000000000000000000000000000000000", "2013-09-19T00:00:00+00:00", 1);
            List<String> legacyCommand = GameLauncher.buildCommand(config, legacy, legacyEntry,
                    java, false);
            int widthIndex = legacyCommand.indexOf("--width");
            check(widthIndex >= 0 && widthIndex + 1 < legacyCommand.size()
                            && "854".equals(legacyCommand.get(widthIndex + 1)),
                    "低版本窗口尺寸覆盖");
            passed += 5;

            TaskCenter center = new TaskCenter();
            DownloadTask task = center.start("测试任务");
            TaskProgress cancelledProgress = new TaskProgress();
            cancelledProgress.total(100);
            cancelledProgress.add(10);
            center.update(task, cancelledProgress);
            center.cancel(task);
            center.update(task, cancelledProgress);
            center.fail(task, "不应覆盖取消");
            check(task.state() == DownloadTask.State.CANCELLED
                            && "已取消".equals(task.stage()),
                    "取消任务后状态不再变化");
            passed += 1;

            TaskProgress progress = new TaskProgress();
            progress.total(100);
            progress.add(100);
            check(progress.percent() == 100, "任务进度 100%");
            progress.reset("新阶段");
            check(progress.done() == 0 && progress.total() < 0
                            && progress.isIndeterminate() && "新阶段".equals(progress.stage()),
                    "任务进度重置");
            progress.addTotal(80);
            progress.add(40);
            check(progress.percent() == 50, "任务进度重新累计");
            passed += 3;

            Path work = Files.createTempDirectory("mclite-selftest-");
            try {
                passed += updateTests(work, config);
            } finally {
                deleteTree(work);
            }

            System.out.println("SELF-TEST PASSED (" + passed + " checks)");
            return 0;
        } catch (Throwable error) {
            Log.error("SELF-TEST FAILED", error);
            error.printStackTrace(System.err);
            return 1;
        }
    }

    private static void check(boolean condition, String name) {
        if (!condition) {
            throw new AssertionError("检查失败: " + name);
        }
        System.out.println("  [ok] " + name);
    }

    private static int updateTests(Path work, Config config) throws Exception {
        int passed = 0;
            check(Recommendations.forLoader("fabric").stream()
                            .anyMatch(item -> item.slug().equals("immediatelyfast"))
                            && Recommendations.forLoader("fabric").stream()
                            .anyMatch(item -> item.slug().equals("badoptimizations")),
                    "推荐高性能模组");
            check(Recommendations.forLoader("forge", "1.21.1").stream()
                            .noneMatch(item -> item.slug().equals("embeddium")),
                    "Forge 1.21 推荐列表兼容");
            check(Recommendations.forLoader("fabric").stream()
                            .anyMatch(item -> item.slug().equals("fabric-api"))
                            && Recommendations.forLoader("fabric").stream()
                            .anyMatch(item -> item.slug().equals("jade"))
                            && Recommendations.forLoader("fabric").stream()
                            .anyMatch(item -> item.slug().equals("emi")),
                    "实用类模组推荐");
            String[] fabricLoader = CurseForgeModpackInstaller.parseLoader("fabric-loader-0.20.1");
            check("fabric".equals(fabricLoader[0]) && "0.20.1".equals(fabricLoader[1]),
                    "CurseForge Fabric 加载器 ID");
            String[] forgeLoader = CurseForgeModpackInstaller.parseLoader("1.21.1-forge-52.0.1");
            check("forge".equals(forgeLoader[0]) && "52.0.1".equals(forgeLoader[1]),
                    "CurseForge Forge 加载器 ID");
            passed += 5;

            Path mergeRoot = work.resolve("merge");
            Path mergeBase = mergeRoot.resolve("versions/1.21");
            Path mergeLoader = mergeRoot.resolve("versions/fabric-loader-test");
            Files.createDirectories(mergeBase);
            Files.createDirectories(mergeLoader.resolve("mods"));
            Files.writeString(mergeBase.resolve("1.21.json"), """
                    {"id":"1.21","type":"release","mainClass":"net.minecraft.client.main.Main",
                     "assets":"34","downloads":{"client":{"url":"https://piston-data.mojang.com/x.jar",
                     "sha1":"0000000000000000000000000000000000000000","size":1}},
                     "libraries":[{"name":"base:lib:1.0"}]}
                    """, StandardCharsets.UTF_8);
            Files.writeString(mergeLoader.resolve("fabric-loader-test.json"), """
                    {"id":"fabric-loader-test","inheritsFrom":"1.21","type":"release",
                     "mainClass":"net.fabricmc.loader.impl.launch.knot.KnotClient",
                     "libraries":[{"name":"fabric:loader:1.0"}]}
                    """, StandardCharsets.UTF_8);
            Files.writeString(mergeLoader.resolve("mods/example.jar"), "mod",
                    StandardCharsets.UTF_8);
            Config mergeConfig = config;
            mergeConfig.gameDir(mergeRoot);
            Map<String, Object> child = Json.obj(Json.parse(Files.readString(
                    mergeLoader.resolve("fabric-loader-test.json"), StandardCharsets.UTF_8)));
            new LoaderInstaller(mergeConfig, ProgressSink.NOOP, new CancellationToken())
                    .mergeAndWrite("1.21", "fabric-loader-test", child);
            String merged = Files.readString(mergeBase.resolve("1.21.json"), StandardCharsets.UTF_8);
            check(merged.contains("KnotClient") && !merged.contains("inheritsFrom"),
                    "加载器合并到游戏本体");
            check(merged.contains("base:lib:1.0") && merged.contains("fabric:loader:1.0"),
                    "加载器库与原版库合并");
            check(Files.isRegularFile(mergeBase.resolve("1.21.vanilla.json")),
                    "保留原版 JSON 备份");
            check(Files.isRegularFile(mergeBase.resolve("mods/example.jar")),
                    "旧实例模组迁移到本体文件夹");
            check(!Files.exists(mergeLoader), "旧加载器文件夹已清理");
            passed += 5;

            Path standaloneRoot = work.resolve("standalone");
            Path standaloneBase = standaloneRoot.resolve("versions/1.21");
            Files.createDirectories(standaloneBase);
            Files.writeString(standaloneBase.resolve("1.21.json"),
                    "{\"id\":\"1.21\",\"mainClass\":\"net.minecraft.client.main.Main\","
                            + "\"libraries\":[]}", StandardCharsets.UTF_8);
            Files.writeString(standaloneBase.resolve("1.21.jar"), "client",
                    StandardCharsets.UTF_8);
            LoaderInstaller.finalizeStandaloneVersion(standaloneRoot, "1.21", "pack");
            Map<String, Object> packed = Json.obj(Json.parse(Files.readString(
                    standaloneRoot.resolve("versions/pack/pack.json"), StandardCharsets.UTF_8)));
            check("pack".equals(Json.str(packed, "id"))
                            && Json.str(packed, "inheritsFrom") == null
                            && "pack".equals(Json.str(packed, "jar"))
                            && Files.isRegularFile(standaloneRoot.resolve("versions/pack/pack.jar")),
                    "整合包生成独立版本文件夹");
            LoaderInstaller.removeVersionIfUnused(standaloneRoot, "1.21");
            check(!Files.exists(standaloneBase), "独立整合包清理临时原版文件夹");
            Path instanceRoot = work.resolve("instance-service");
            Path instanceOld = instanceRoot.resolve("versions/old");
            Path instanceNew = instanceRoot.resolve("versions/new");
            Files.createDirectories(instanceOld);
            Files.writeString(instanceOld.resolve("old.json"),
                    "{\"id\":\"old\",\"mainClass\":\"net.minecraft.client.main.Main\","
                            + "\"downloads\":{\"client\":{\"url\":\"https://x/y.jar\","
                            + "\"sha1\":\"0000000000000000000000000000000000000000\",\"size\":1}},"
                            + "\"libraries\":[]}", StandardCharsets.UTF_8);
            Files.writeString(instanceOld.resolve("old.jar"), "jar", StandardCharsets.UTF_8);
            InstanceService.rename(instanceRoot, "old", "new", instanceOld, instanceNew);
            check(Files.isRegularFile(instanceNew.resolve("new.json"))
                            && Files.isRegularFile(instanceNew.resolve("new.jar"))
                            && Files.readString(instanceNew.resolve("new.json")).contains("\"id\": \"new\""),
                    "实例重命名服务");
            InstanceService.deleteTree(instanceNew);
            check(!Files.exists(instanceNew), "实例删除服务");
            Path globalGame = work.resolve("global-game");
            Path globalWorld = globalGame.resolve("saves/world");
            Files.createDirectories(globalWorld);
            Files.writeString(globalWorld.resolve("level.dat"), "level", StandardCharsets.UTF_8);
            Path globalZip = work.resolve("global-pack.zip");
            try (java.util.zip.ZipOutputStream out = new java.util.zip.ZipOutputStream(
                    Files.newOutputStream(globalZip))) {
                out.putNextEntry(new java.util.zip.ZipEntry("pack.mcmeta"));
                out.write("{\"pack\":{\"pack_format\":1}}".getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
            }
            Path globalTarget = GlobalDatapacks.install(globalGame, globalZip);
            check(Files.isRegularFile(globalTarget)
                            && Files.isRegularFile(globalWorld.resolve("datapacks/global-pack.zip")),
                    "全局数据包同步到存档");
            check(GlobalDatapacks.sync(globalGame) == 1, "全局数据包统计存档");
            check(JvmTuner.isLegacyGame("1.12.2") && !JvmTuner.isLegacyGame("1.21.1"),
                    "低版本 JVM 参数识别");
            check(JvmTuner.java8Args(4096).contains("-XX:+UseParallelGC")
                            && JvmTuner.java8Args(4096).contains("-XX:+DisableExplicitGC"),
                    "Java 8 使用 ParallelGC");
            check(JvmTuner.legacyTier("1.6.4") == 2
                            && JvmTuner.legacyTier("1.12.2") == 1
                            && JvmTuner.legacyTier("1.21.1") == 0
                            && JvmTuner.legacyTier("26.1.2", "2026-10-30T19:09:50+00:00") == 0
                            && JvmTuner.legacyTier("MITE-R196",
                            "2013-09-19T15:52:37+00:00") == 2,
                    "低版本内存分级");
            passed += 9;

        check(UpdateInstaller.versionedName("MCLite_v1.1.33.jar", "1.1.34", false)
                        .equals("MCLite_v1.1.34.jar"),
                "普通版更新文件名");
        check(UpdateInstaller.versionedName("MCLite_v1.1.33-region.jar", "1.1.34", true)
                        .equals("MCLite_v1.1.34-region.jar"),
                "地区版更新文件名");
        passed += 2;

        Path patch = work.resolve("patch");
        Path regionJar = makeJar(patch.resolve("region.jar"),
                "mclite.RegionLauncherApp", true, "1.1.34");
        Path normalJar = patch.resolve("normal.jar");
        RegionPatcher.makeUnrestricted(regionJar, normalJar);
        Updater.verifyJar(normalJar, "1.1.34", true);
        check(!Updater.isUnrestricted(regionJar) && Updater.isUnrestricted(normalJar),
                "普通版/地区版标记识别");
        passed++;
        check(true, "地区版转普通版后可校验");
        passed++;
        try {
            Updater.verifyJar(normalJar, "1.1.33", true);
            check(false, "更新包版本不一致必须被拒绝");
        } catch (IOException ex) {
            check(true, "更新包版本不一致必须被拒绝");
            passed++;
        }
        try {
            Updater.verifyJar(regionJar, "1.1.34", true);
            check(false, "地区版 jar 不能冒充普通版");
        } catch (IOException ex) {
            check(true, "地区版 jar 不能冒充普通版");
            passed++;
        }

        Path plain = work.resolve("plain");
        Path installDir = plain.resolve("install");
        Files.createDirectories(installDir);
        Path oldJar = writeFile(installDir.resolve("MCLite_v1.1.33.jar"), "old");
        Path run = writeFile(plain.resolve("run.sh"),
                "exec java -jar \"install/MCLite_v1.1.33.jar\"\n");
        Path staged = writeFile(plain.resolve("staged/MCLite_v1.1.34-region.jar"), "new");
        UpdateInstaller.Installed installed = UpdateInstaller.installPlain(
                oldJar, staged, "1.1.34", "normal");
        check(Files.readString(installed.launch()).equals("new"), "普通版 jar 原地写入新内容");
        check(installed.launch().getFileName().toString().equals("MCLite_v1.1.34.jar"),
                "普通版 jar 文件名跟随版本");
        check(!Files.exists(oldJar), "旧版本 jar 已删除");
        check(Files.readString(run).contains("MCLite_v1.1.34.jar"), "run.sh 指向新 jar");
        passed += 4;

        Path custom = plain.resolve("custom");
        Path customOld = writeFile(custom.resolve("MC Lite.jar"), "old-custom");
        Path customStaged = writeFile(custom.resolve("staged.jar"), "new-custom");
        UpdateInstaller.Installed customInstalled = UpdateInstaller.installPlain(
                customOld, customStaged, "1.1.34", "normal");
        check(customInstalled.launch().equals(customOld)
                        && Files.readString(customOld).equals("new-custom"),
                "非版本化 jar 保持原文件名覆盖");
        passed++;

        Path apps = work.resolve("apps");
        Path app = apps.resolve("MC Lite v1.1.33.app");
        Path resources = app.resolve("Contents/Resources");
        Path oldAppJar = writeFile(resources.resolve("MCLite_v1.1.33.jar"), "old-app");
        Path appScript = writeFile(app.resolve("Contents/MacOS/MCLite"),
                "#!/bin/bash\nJAR=\"$HERE/../Resources/MCLite_v1.1.33.jar\"\n");
        writeFile(app.resolve("Contents/Info.plist"),
                plist("MC Lite v1.1.33", "1.1.33"));
        Path stagedApp = writeFile(work.resolve("staged-app/MCLite_v1.1.34-region.jar"),
                "new-app");
        UpdateInstaller.Installed appInstalled = UpdateInstaller.installAppBundle(
                oldAppJar, app, stagedApp, "1.1.34", "normal");
        Path newApp = apps.resolve("MC Lite v1.1.34.app");
        check(appInstalled.appMode() && appInstalled.appBundle().equals(newApp),
                "macOS 应用包跟随版本改名");
        check(Files.readString(newApp.resolve("Contents/Resources/MCLite_v1.1.34.jar"))
                        .equals("new-app"),
                "macOS 应用包内 jar 已替换");
        check(!Files.exists(app) && !Files.exists(oldAppJar), "macOS 旧应用包和旧 jar 已清理");
        check(Files.readString(newApp.resolve("Contents/MacOS/MCLite"))
                        .contains("MCLite_v1.1.34.jar"),
                "macOS 启动脚本指向新 jar");
        String newPlist = Files.readString(newApp.resolve("Contents/Info.plist"));
        check(newPlist.contains("<string>1.1.34</string>")
                        && newPlist.contains("MC Lite v1.1.34"),
                "macOS Info.plist 版本已更新");
        passed += 5;

        Path regionApp = apps.resolve("MC Lite Region v1.1.33.app");
        Path regionResources = regionApp.resolve("Contents/Resources");
        Path oldRegionJar = writeFile(regionResources.resolve(
                "MCLite_v1.1.33-region.jar"), "old-region");
        writeFile(regionApp.resolve("Contents/MacOS/MCLite"),
                "#!/bin/bash\nJAR=\"$HERE/../Resources/MCLite_v1.1.33-region.jar\"\n");
        writeFile(regionApp.resolve("Contents/Info.plist"),
                plist("MC Lite Region v1.1.33", "1.1.33"));
        Path stagedRegion = writeFile(work.resolve(
                "staged-region/MCLite_v1.1.34-region.jar"), "new-region");
        UpdateInstaller.Installed regionInstalled = UpdateInstaller.installAppBundle(
                oldRegionJar, regionApp, stagedRegion, "1.1.34", "region");
        Path newRegionApp = apps.resolve("MC Lite Region v1.1.34.app");
        check(regionInstalled.appBundle().equals(newRegionApp)
                        && Files.exists(newRegionApp.resolve(
                                "Contents/Resources/MCLite_v1.1.34-region.jar")),
                "地区版应用包保留地区版命名");
        passed++;

        Path versionsRoot = work.resolve("instances");
        Path sourceDir = versionsRoot.resolve("inst-a");
        writeFile(sourceDir.resolve("inst-a.json"), "{\"id\":\"inst-a\"}");
        writeFile(sourceDir.resolve("mods/a.jar"), "jar");
        try {
            InstanceService.copy(versionsRoot, "inst-a", "inst-b");
            check(Files.isRegularFile(versionsRoot.resolve("inst-b/inst-b.json"))
                    && !Files.exists(versionsRoot.resolve("inst-b/inst-a.json")),
                    "实例复制并重写 id");
            Path instanceZip = work.resolve("inst-b.zip");
            InstanceService.exportZip(versionsRoot, "inst-b", instanceZip);
            String importedId = InstanceService.importZip(versionsRoot, instanceZip, "inst-c");
            check("inst-c".equals(importedId)
                    && Files.isRegularFile(versionsRoot.resolve("inst-c/inst-c.json")),
                    "实例导出导入 round-trip");
        } catch (Exception ex) {
            check(false, "实例复制/导出/导入: " + ex.getMessage());
        }
        InstanceMeta.put(work, "inst-a", "刷铁", "测试备注");
        InstanceMeta.Entry instanceMeta = InstanceMeta.load(work).get("inst-a");
        check(instanceMeta != null && "刷铁".equals(instanceMeta.group())
                && "测试备注".equals(instanceMeta.note()), "实例分组备注读写");
        InstanceMeta.rename(work, "inst-a", "inst-d");
        check(InstanceMeta.load(work).containsKey("inst-d"), "实例备注跟随重命名");
        passed += 4;

        return passed;
    }

    private static Path writeFile(Path path, String text) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, text, StandardCharsets.UTF_8);
        return path;
    }

    private static Path makeJar(Path path, String mainClass, boolean region, String version)
            throws IOException {
        Files.createDirectories(path.getParent());
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, mainClass);
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(path), manifest)) {
            if (region) {
                out.putNextEntry(new JarEntry("mclite/region.lock"));
                out.closeEntry();
            }
            out.putNextEntry(new JarEntry("mclite/version.properties"));
            out.write(("version=" + version + "\n").getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
            out.putNextEntry(new JarEntry("mclite/Placeholder.class"));
            out.write(new byte[]{1, 2, 3});
            out.closeEntry();
        }
        return path;
    }

    private static String plist(String name, String version) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<plist version=\"1.0\"><dict>\n"
                + "<key>CFBundleDisplayName</key>\n<string>" + name + "</string>\n"
                + "<key>CFBundleName</key>\n<string>" + name + "</string>\n"
                + "<key>CFBundleShortVersionString</key>\n<string>" + version + "</string>\n"
                + "<key>CFBundleVersion</key>\n<string>" + version + "</string>\n"
                + "</dict></plist>\n";
    }

    private static void deleteTree(Path root) {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (var walk = Files.walk(root)) {
            for (Path path : walk.sorted(java.util.Comparator.reverseOrder()).toList()) {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                }
            }
        } catch (IOException ignored) {
        }
    }
}

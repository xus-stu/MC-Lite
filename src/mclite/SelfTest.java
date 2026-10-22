package mclite;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

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
            passed += 4;

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
}

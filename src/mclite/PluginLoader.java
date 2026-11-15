package mclite;

import mclite.plugin.LauncherPlugin;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.ServiceLoader;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * 启动器插件加载：{@code ~/.mclite/plugins/*.jar} + ServiceLoader。
 *
 * <p>插件 jar 不合法、类加载失败、ServiceLoader 抛错都只影响该插件本身；
 * classloader 保留在静态字段里，避免插件类被回收。</p>
 */
final class PluginLoader {

    record Loaded(String id, Path jar, LauncherPlugin plugin) {
    }

    private static URLClassLoader classLoader;

    private PluginLoader() {
    }

    static Path pluginsDir(Path appHome) {
        return appHome.resolve("plugins");
    }

    static List<Loaded> load(Path appHome, Consumer<String> warn) {
        List<Loaded> out = new ArrayList<>();
        if (!Files.isDirectory(pluginsDir(appHome))) {
            return out;
        }
        List<URL> urls = new ArrayList<>();
        try (Stream<Path> stream = Files.list(pluginsDir(appHome))) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".jar"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .forEach(path -> {
                        try {
                            urls.add(path.toUri().toURL());
                        } catch (Exception ignored) {
                            // 非法路径跳过。
                        }
                    });
        } catch (IOException ex) {
            warn.accept("读取插件目录失败：" + ex.getMessage());
            return out;
        }
        if (urls.isEmpty()) {
            return out;
        }
        classLoader = new URLClassLoader(urls.toArray(URL[]::new),
                PluginLoader.class.getClassLoader());
        try {
            for (LauncherPlugin plugin : ServiceLoader.load(LauncherPlugin.class, classLoader)) {
                out.add(new Loaded(idOf(plugin), jarOf(plugin), plugin));
            }
        } catch (Throwable ex) {
            warn.accept("插件加载失败：" + ex.getMessage());
        }
        return out;
    }

    private static String idOf(LauncherPlugin plugin) {
        Path jar = jarOf(plugin);
        String name = jar == null ? plugin.getClass().getSimpleName() : jar.getFileName().toString();
        return name.replaceFirst("\\.jar$", "");
    }

    private static Path jarOf(LauncherPlugin plugin) {
        try {
            Object source = plugin.getClass().getProtectionDomain().getCodeSource();
            if (source instanceof java.security.CodeSource code) {
                return Path.of(code.getLocation().toURI());
            }
        } catch (Exception ignored) {
            // 代码源拿不到就返回 null。
        }
        return null;
    }

    static void shutdown() {
        URLClassLoader loader = classLoader;
        classLoader = null;
        if (loader != null) {
            try {
                loader.close();
            } catch (IOException ignored) {
                // 退出阶段忽略。
            }
        }
    }
}

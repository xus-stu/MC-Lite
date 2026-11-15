package mclite.plugin;

/**
 * 启动器插件入口。
 *
 * <p>插件 jar 放在 {@code ~/.mclite/plugins/}，并用
 * {@code META-INF/services/mclite.plugin.LauncherPlugin} 声明实现类；
 * 启动器启动时通过 {@link java.util.ServiceLoader} 加载并调用 {@link #onAttach(Host)}。</p>
 *
 * <p>插件只依赖启动器公开的类（{@code mclite.Theme}、{@code mclite.Ui}、{@code mclite.plugin.*}），
 * 不要反射启动器内部实现。</p>
 */
public interface LauncherPlugin {

    /** 被启动器加载时调用；抛异常只会让这一个插件失效，不影响启动器。 */
    void onAttach(Host host) throws Exception;
}

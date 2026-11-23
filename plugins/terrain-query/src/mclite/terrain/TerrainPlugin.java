package mclite.terrain;

import mclite.plugin.Host;
import mclite.plugin.LauncherPlugin;

/** 启动器插件入口：注册「地形查询」。 */
public final class TerrainPlugin implements LauncherPlugin {

    @Override
    public void onAttach(Host host) {
        host.addFeature("terrain-query", "地形查询",
                "扫描已生成区块的生物群系，定位沼泽 / 红树林沼泽 / 蘑菇岛等",
                "地形 · 生物群系", () -> new TerrainPage(host).build());
    }
}

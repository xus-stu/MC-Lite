package mclite;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** GitHub 原始地址 + 镜像回退；国内直连 GitHub 经常超时。 */
final class GithubSources {

    /** 实测在国内可用性排序：ghfast/gh-proxy 稳定，其余作为最后兜底。 */
    private static final String[] MIRRORS = {
            "https://ghfast.top/",
            "https://gh-proxy.com/"
    };
    private static final String[] EXTRA = {
            "https://ghproxy.net/",
            "https://github.moeyy.xyz/"
    };

    private GithubSources() {
    }

    static List<String> candidates(String direct) {
        LinkedHashSet<String> urls = new LinkedHashSet<>();
        // 国内直连 GitHub 经常超时：先走两个实测可用的镜像，再试直连和兜底
        for (String prefix : MIRRORS) {
            urls.add(prefix + direct);
        }
        urls.add(direct);
        for (String prefix : EXTRA) {
            urls.add(prefix + direct);
        }
        return new ArrayList<>(urls);
    }
}

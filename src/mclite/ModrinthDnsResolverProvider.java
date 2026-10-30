package mclite;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.net.spi.InetAddressResolver;
import java.net.spi.InetAddressResolverProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Modrinth 的 CDN 使用 Fastly。部分国内 DNS 会把 cdn.modrinth.com 解析到
 * 146.75.x.x 节点，速度可能只有几十 KB/s；公共 DNS 返回的 151.101.x.x
 * 节点通常快很多。这里只对 cdn.modrinth.com 优先返回 151.101 节点，
 * 其他域名完全走系统解析。
 *
 * <p>可用 -Dmclite.modrinth.dns=off 关闭，或用
 * -Dmclite.modrinth.dns.ips=ip1,ip2 覆盖地址列表。</p>
 */
public final class ModrinthDnsResolverProvider extends InetAddressResolverProvider {
    private static final String HOST = "cdn.modrinth.com";
    private static final String[] DEFAULT_IPS = {
            "151.101.65.242",
            "151.101.129.242",
            "151.101.1.242",
            "151.101.193.242"
    };

    @Override
    public String name() {
        return "mclite-modrinth-cdn";
    }

    @Override
    public InetAddressResolver get(Configuration configuration) {
        InetAddressResolver builtin = configuration.builtinResolver();
        if ("off".equalsIgnoreCase(System.getProperty("mclite.modrinth.dns", "auto"))) {
            return builtin;
        }
        List<byte[]> preferred = preferredAddresses();
        if (preferred.isEmpty()) {
            return builtin;
        }
        return new InetAddressResolver() {
            @Override
            public Stream<InetAddress> lookupByName(String host, LookupPolicy policy)
                    throws UnknownHostException {
                if (!HOST.equals(host.toLowerCase(Locale.ROOT))) {
                    return builtin.lookupByName(host, policy);
                }
                List<InetAddress> result = new ArrayList<>();
                for (byte[] address : preferred) {
                    result.add(InetAddress.getByAddress(host, address));
                }
                try {
                    builtin.lookupByName(host, policy).forEach(result::add);
                } catch (UnknownHostException ignored) {
                }
                return result.stream().distinct();
            }

            @Override
            public String lookupByAddress(byte[] address) throws UnknownHostException {
                return builtin.lookupByAddress(address);
            }
        };
    }

    private static List<byte[]> preferredAddresses() {
        String configured = System.getProperty("mclite.modrinth.dns.ips", "");
        String[] values = configured.isBlank() ? DEFAULT_IPS : configured.split(",");
        List<byte[]> result = new ArrayList<>();
        for (String value : values) {
            String ip = value.trim();
            if (ip.matches("\\d{1,3}(\\.\\d{1,3}){3}")) {
                byte[] bytes = parseIpv4(ip);
                if (bytes != null) {
                    result.add(bytes);
                }
            }
        }
        return result;
    }

    private static byte[] parseIpv4(String value) {
        String[] parts = value.split("\\.");
        if (parts.length != 4) {
            return null;
        }
        byte[] bytes = new byte[4];
        try {
            for (int i = 0; i < 4; i++) {
                int number = Integer.parseInt(parts[i]);
                if (number < 0 || number > 255) {
                    return null;
                }
                bytes[i] = (byte) number;
            }
            return bytes;
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}

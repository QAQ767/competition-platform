package com.campus.competition.service;

import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * SSRF 防护策略：只允许 HTTP/HTTPS 公网目标，限制端口，拒绝回环、私网、链路本地、
 * 云元数据、IPv6 唯一本地地址等。抓取器会把连接固定到已校验的 IP，避免二次解析被
 * DNS 重绑定绕过；每次重定向都会重新执行本策略。
 */
@Service
public class UrlSafetyPolicy {

    private static final int MAX_URL_LENGTH = 2000;

    private final boolean allowFakeIp;

    public UrlSafetyPolicy() {
        this(false);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public UrlSafetyPolicy(com.campus.competition.config.DiscoveryProperties properties) {
        this(properties.getFetch().isAllowFakeIp());
    }

    UrlSafetyPolicy(boolean allowFakeIp) {
        this.allowFakeIp = allowFakeIp;
    }

    public URI validate(String raw) throws FetchException {
        if (raw == null || raw.isBlank()) {
            throw new FetchException("URL_INVALID", "URL 不能为空");
        }
        String value = raw.trim();
        if (value.length() > MAX_URL_LENGTH) {
            throw new FetchException("URL_INVALID", "URL 过长");
        }
        URI uri;
        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException e) {
            throw new FetchException("URL_INVALID", "URL 格式不正确");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new FetchException("URL_SCHEME_BLOCKED", "只允许 http/https 链接");
        }
        if (uri.getUserInfo() != null) {
            throw new FetchException("URL_BLOCKED", "不允许带用户名或密码的 URL");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new FetchException("URL_INVALID", "URL 缺少主机名");
        }
        host = host.toLowerCase(Locale.ROOT);
        if (host.startsWith("[") && host.endsWith("]")) {
            host = host.substring(1, host.length() - 1);
        }
        if (host.equals("localhost") || host.endsWith(".localhost")
                || host.endsWith(".local") || host.endsWith(".internal")
                || host.endsWith(".home.arpa")) {
            throw new FetchException("URL_BLOCKED", "不允许访问本机或内部域名");
        }
        if (isIpLiteral(host)) {
            try {
                InetAddress address = InetAddress.getByName(host);
                if (!isPublic(address)) {
                    throw new FetchException("URL_PRIVATE_BLOCKED", "不允许访问非公网 IP");
                }
            } catch (UnknownHostException e) {
                throw new FetchException("URL_INVALID", "IP 地址格式不正确");
            }
        }
        int port = uri.getPort();
        if (port != -1 && port != 80 && port != 443 && port != 8080 && port != 8443) {
            throw new FetchException("URL_PORT_BLOCKED", "只允许 80/443/8080/8443 端口");
        }
        return uri;
    }

    /** 解析并校验所有地址；任意一个非公网地址都会拒绝，防止混合解析绕过。 */
    public List<InetAddress> resolvePublic(URI uri) throws FetchException {
        String host = uri.getHost();
        if (host == null) throw new FetchException("URL_INVALID", "URL 缺少主机名");
        if (host.startsWith("[") && host.endsWith("]")) {
            host = host.substring(1, host.length() - 1);
        }
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw new FetchException("DNS_FAILED", "无法解析域名：" + host);
        }
        if (addresses.length == 0) {
            throw new FetchException("DNS_FAILED", "域名没有可用地址");
        }
        List<InetAddress> result = new ArrayList<>();
        for (InetAddress address : addresses) {
            if (!isPublic(address)) {
                throw new FetchException("URL_PRIVATE_BLOCKED",
                        "目标解析到非公网地址，已拒绝抓取：" + address.getHostAddress());
            }
            result.add(address);
        }
        return result;
    }

    private boolean isIpLiteral(String host) {
        return host.contains(":") || host.matches("\\d{1,3}(\\.\\d{1,3}){3}");
    }

    public boolean isPublic(InetAddress address) {        if (address == null) return false;
        if (address.isAnyLocalAddress() || address.isLoopbackAddress()
                || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return false;
        }
        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            int b0 = bytes[0] & 0xff;
            int b1 = bytes[1] & 0xff;
            if (b0 == 0) return false; // 0.0.0.0/8
            if (b0 == 100 && (b1 & 0xc0) == 64) return false; // 100.64/10 CGNAT
            if (b0 == 169 && b1 == 254) return false; // 链路本地/云元数据
            if (b0 == 192 && b1 == 0 && (bytes[2] & 0xff) == 0) return false; // 192.0.0.0/24
            if (b0 == 192 && b1 == 0 && (bytes[2] & 0xff) == 2) return false; // 文档地址
            if (b0 == 198 && (b1 == 18 || b1 == 19)) return allowFakeIp; // 本机代理 fake-IP，默认拒绝
            if (b0 == 198 && b1 == 51 && (bytes[2] & 0xff) == 100) return false;
            if (b0 == 203 && b1 == 0 && (bytes[2] & 0xff) == 113) return false;
            if (b0 >= 224) return false; // 组播/保留
            return true;
        }
        if (bytes.length == 16) {
            int b0 = bytes[0] & 0xff;
            if ((b0 & 0xfe) == 0xfc) return false; // fc00::/7 唯一本地地址
            if (b0 == 0xfe && (bytes[1] & 0xc0) == 0x80) return false; // fe80::/10
            if (b0 == 0x20 && (bytes[1] & 0xff) == 0x01 && (bytes[2] & 0xff) == 0x0d
                    && (bytes[3] & 0xff) == 0xb8) return false; // 2001:db8::/32 文档
            // IPv4-mapped：::ffff:a.b.c.d
            boolean mapped = true;
            for (int i = 0; i < 10; i++) {
                if (bytes[i] != 0) { mapped = false; break; }
            }
            if (mapped && (bytes[10] & 0xff) == 0xff && (bytes[11] & 0xff) == 0xff) {
                byte[] v4 = new byte[]{bytes[12], bytes[13], bytes[14], bytes[15]};
                try {
                    return isPublic(InetAddress.getByAddress(v4));
                } catch (UnknownHostException e) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }
}

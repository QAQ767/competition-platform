package com.campus.competition.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** SSRF 防护：回环、私网、链路本地、云元数据、IPv6 本地地址都必须拒绝。 */
class UrlSafetyPolicyTest {

    private final UrlSafetyPolicy policy = new UrlSafetyPolicy();

    @Test
    @DisplayName("私网与元数据地址一律拒绝")
    void blocksPrivateAndMetadata() throws Exception {
        for (String ip : new String[]{"127.0.0.1", "10.0.0.1", "172.16.0.1", "192.168.1.1",
                "169.254.169.254", "100.100.100.200", "0.0.0.0", "224.0.0.1"}) {
            assertFalse(policy.isPublic(InetAddress.getByName(ip)), ip);
        }
        for (String ip : new String[]{"::1", "fc00::1", "fd12:3456::1", "fe80::1"}) {
            assertFalse(policy.isPublic(InetAddress.getByName(ip)), ip);
        }
    }

    @Test
    @DisplayName("公网地址允许")
    void allowsPublicAddress() throws Exception {
        assertTrue(policy.isPublic(InetAddress.getByName("8.8.8.8")));
        assertTrue(policy.isPublic(InetAddress.getByName("2001:4860:4860::8888")));
    }

    @Test
    @DisplayName("非 HTTP/HTTPS、内网域名、危险端口和带凭据 URL 被拒绝")
    void blocksUnsafeUris() {
        assertThrows(FetchException.class, () -> policy.validate("file:///C:/Windows/win.ini"));
        assertThrows(FetchException.class, () -> policy.validate("http://localhost:8080/a"));
        assertThrows(FetchException.class, () -> policy.validate("http://127.0.0.1/a"));
        assertThrows(FetchException.class, () -> policy.validate("http://example.com:22/a"));
        assertThrows(FetchException.class, () -> policy.validate("http://user:pass@example.com/a"));
        assertThrows(FetchException.class, () -> policy.validate("not a url"));
    }

    @Test
    @DisplayName("正常公网 URL 通过格式校验")
    void acceptsPublicUri() throws Exception {
        URI uri = policy.validate("https://www.mcm.edu.cn/html_cn/node/abc.html");
        assertTrue(uri.getHost().contains("mcm.edu.cn"));
    }
}

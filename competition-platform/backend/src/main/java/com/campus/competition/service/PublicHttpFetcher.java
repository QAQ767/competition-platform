package com.campus.competition.service;

import com.campus.competition.config.DiscoveryProperties;
import org.springframework.stereotype.Service;

import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

/**
 * 受限网页抓取：把 TCP 连接固定到已校验的公网 IP，HTTPS 仍按原域名做证书与主机名校验，
 * 因此 DNS 重绑定不能在“校验后”把连接换到内网。每次重定向重新校验。
 */
@Service
public class PublicHttpFetcher implements WebContentFetcher {

    private static final int MAX_HEADER_BYTES = 32 * 1024;
    private static final int MAX_HEADERS = 80;

    private final DiscoveryProperties properties;
    private final UrlSafetyPolicy policy;

    public PublicHttpFetcher(DiscoveryProperties properties, UrlSafetyPolicy policy) {
        this.properties = properties;
        this.policy = policy;
    }

    @Override
    public FetchedPage fetch(String url) throws FetchException {
        String current = url;
        for (int redirect = 0; redirect <= properties.getFetch().getMaxRedirects(); redirect++) {
            URI uri = policy.validate(current);
            List<InetAddress> addresses = policy.resolvePublic(uri);
            Response response = request(uri, addresses.get(0));
            if (isRedirect(response.status)) {
                String location = response.headers.get("location");
                if (location == null || location.isBlank()) {
                    throw new FetchException("REDIRECT_INVALID", "重定向缺少 Location");
                }
                try {
                    current = uri.resolve(location.trim()).toString();
                } catch (Exception e) {
                    throw new FetchException("REDIRECT_INVALID", "重定向地址无效");
                }
                continue;
            }
            if (response.status != 200) {
                throw new FetchException("HTTP_" + response.status,
                        "网页返回 HTTP " + response.status + "，无法读取正文");
            }
            String contentType = response.headers.getOrDefault("content-type", "").toLowerCase(Locale.ROOT);
            if (!contentType.isEmpty() && !contentType.contains("text/html")
                    && !contentType.contains("text/plain") && !contentType.contains("application/xhtml")
                    && !contentType.contains("application/xml") && !contentType.contains("text/xml")) {
                throw new FetchException("NOT_TEXT", "目标不是 HTML/文本页面（" + contentType + "），请粘贴公告正文");
            }
            byte[] bytes = decodeContent(response);
            String body = new String(bytes, charset(contentType));
            String text = HtmlTextExtractor.toText(body);
            if (text.isBlank()) {
                throw new FetchException("EMPTY_CONTENT", "网页没有可读正文，请粘贴公告文本");
            }
            FetchedPage page = new FetchedPage();
            page.setUrl(url);
            page.setFinalUrl(uri.toString());
            page.setTitle(HtmlTextExtractor.title(body));
            page.setText(limit(text, 60000));
            page.setStatusCode(200);
            page.setFetchedAt(LocalDateTime.now());
            page.setSourceType(sourceType(uri.getHost()));
            return page;
        }
        throw new FetchException("TOO_MANY_REDIRECTS",
                "重定向超过 " + properties.getFetch().getMaxRedirects() + " 次，已停止抓取");
    }

    private Response request(URI uri, InetAddress target) throws FetchException {
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        boolean https = "https".equals(scheme);
        String host = uri.getHost();
        if (host == null) throw new FetchException("URL_INVALID", "URL 缺少主机名");
        if (host.startsWith("[") && host.endsWith("]")) host = host.substring(1, host.length() - 1);
        int port = uri.getPort() == -1 ? (https ? 443 : 80) : uri.getPort();
        int timeout = Math.max(1, properties.getFetch().getTimeoutSeconds()) * 1000;

        Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(target, port), timeout);
            socket.setSoTimeout(timeout);
            if (https) {
                SSLSocket ssl = (SSLSocket) SSLContext.getDefault().getSocketFactory()
                        .createSocket(socket, host, port, true);
                SSLParameters params = ssl.getSSLParameters();
                params.setEndpointIdentificationAlgorithm("HTTPS");
                if (!isIpLiteral(host)) {
                    params.setServerNames(List.of(new SNIHostName(host)));
                }
                ssl.setSSLParameters(params);
                ssl.startHandshake();
                socket = ssl;
            }
            String path = uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
            if (uri.getRawQuery() != null) path += "?" + uri.getRawQuery();
            String hostHeader = host + (isDefaultPort(port, https) ? "" : ":" + port);
            String request = "GET " + path + " HTTP/1.1\r\n"
                    + "Host: " + hostHeader + "\r\n"
                    + "User-Agent: " + properties.getFetch().getUserAgent() + "\r\n"
                    + "Accept: text/html,application/xhtml+xml,text/plain;q=0.9,*/*;q=0.5\r\n"
                    + "Accept-Encoding: identity\r\n"
                    + "Connection: close\r\n\r\n";
            OutputStream out = socket.getOutputStream();
            out.write(request.getBytes(StandardCharsets.ISO_8859_1));
            out.flush();

            InputStream in = socket.getInputStream();
            String statusLine = readLine(in, 2048);
            if (statusLine == null || !statusLine.startsWith("HTTP/")) {
                throw new FetchException("BAD_RESPONSE", "网页返回格式不正确");
            }
            String[] parts = statusLine.split(" ");
            int status;
            try {
                status = Integer.parseInt(parts[1]);
            } catch (Exception e) {
                throw new FetchException("BAD_RESPONSE", "无法识别网页状态码");
            }
            Map<String, String> headers = readHeaders(in);
            byte[] body = readBody(in, headers);
            Response response = new Response();
            response.status = status;
            response.headers = headers;
            response.body = body;
            return response;
        } catch (FetchException e) {
            throw e;
        } catch (IOException e) {
            throw new FetchException("FETCH_FAILED", "连接或读取网页失败：" + safeMessage(e));
        } catch (Exception e) {
            throw new FetchException("FETCH_FAILED", "抓取网页失败：" + safeMessage(e));
        } finally {
            try {
                socket.close();
            } catch (IOException ignored) {
                // 关闭失败不影响结果
            }
        }
    }

    private Map<String, String> readHeaders(InputStream in) throws IOException, FetchException {
        Map<String, String> headers = new LinkedHashMap<>();
        int total = 0;
        for (int i = 0; i < MAX_HEADERS; i++) {
            String line = readLine(in, MAX_HEADER_BYTES);
            if (line == null) throw new FetchException("BAD_RESPONSE", "网页响应头不完整");
            if (line.isEmpty()) return headers;
            total += line.length();
            if (total > MAX_HEADER_BYTES) {
                throw new FetchException("HEADERS_TOO_LARGE", "网页响应头过大");
            }
            int colon = line.indexOf(':');
            if (colon > 0) {
                headers.put(line.substring(0, colon).trim().toLowerCase(Locale.ROOT),
                        line.substring(colon + 1).trim());
            }
        }
        throw new FetchException("HEADERS_TOO_LARGE", "网页响应头过多");
    }

    private byte[] readBody(InputStream in, Map<String, String> headers) throws IOException, FetchException {
        int max = Math.max(1024, properties.getFetch().getMaxBytes());
        String transfer = headers.getOrDefault("transfer-encoding", "").toLowerCase(Locale.ROOT);
        if (transfer.contains("chunked")) {
            return readChunked(in, max);
        }
        String length = headers.get("content-length");
        if (length != null) {
            try {
                long value = Long.parseLong(length.trim());
                if (value > max) value = max;
                return readExactly(in, (int) value);
            } catch (NumberFormatException ignored) {
                // 回退到读到 EOF
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) {
            int remaining = max - out.size();
            if (remaining <= 0) break;
            out.write(buffer, 0, Math.min(read, remaining));
        }
        return out.toByteArray();
    }

    private byte[] readChunked(InputStream in, int max) throws IOException, FetchException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        while (true) {
            String line = readLine(in, 1024);
            if (line == null) throw new FetchException("BAD_RESPONSE", "分块响应不完整");
            int semicolon = line.indexOf(';');
            String hex = (semicolon >= 0 ? line.substring(0, semicolon) : line).trim();
            int size;
            try {
                size = Integer.parseInt(hex, 16);
            } catch (NumberFormatException e) {
                throw new FetchException("BAD_RESPONSE", "分块大小无法解析");
            }
            if (size == 0) {
                // 消费 trailer
                while (true) {
                    String trailer = readLine(in, 1024);
                    if (trailer == null || trailer.isEmpty()) break;
                }
                return out.toByteArray();
            }
            int remaining = max - out.size();
            if (remaining <= 0) return out.toByteArray();
            byte[] chunk = readExactly(in, Math.min(size, remaining));
            out.write(chunk);
            readLine(in, 16); // CRLF
        }
    }

    private byte[] readExactly(InputStream in, int length) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(16, length));
        byte[] buffer = new byte[8192];
        int remaining = length;
        while (remaining > 0) {
            int read = in.read(buffer, 0, Math.min(buffer.length, remaining));
            if (read == -1) break;
            out.write(buffer, 0, read);
            remaining -= read;
        }
        return out.toByteArray();
    }

    private byte[] decodeContent(Response response) throws FetchException {
        String encoding = response.headers.getOrDefault("content-encoding", "").toLowerCase(Locale.ROOT);
        int max = Math.max(1024, properties.getFetch().getMaxBytes());
        try {
            if (encoding.contains("gzip")) {
                try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(response.body))) {
                    return readLimited(in, max);
                }
            }
            if (encoding.contains("deflate")) {
                try (InputStream in = new InflaterInputStream(new ByteArrayInputStream(response.body))) {
                    return readLimited(in, max);
                }
            }
            if (!encoding.isBlank() && !encoding.contains("identity")) {
                throw new FetchException("UNSUPPORTED_ENCODING", "网页压缩格式不支持：" + encoding);
            }
            return response.body;
        } catch (IOException e) {
            throw new FetchException("DECODE_FAILED", "网页内容解压失败");
        }
    }

    /** 解压后仍限制大小，避免压缩炸弹。 */
    private byte[] readLimited(InputStream in, int max) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) {
            int remaining = max - out.size();
            if (remaining <= 0) break;
            out.write(buffer, 0, Math.min(read, remaining));
        }
        return out.toByteArray();
    }

    private String readLine(InputStream in, int max) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int value;
        while ((value = in.read()) != -1) {
            if (value == '\n') break;
            if (value != '\r') out.write(value);
            if (out.size() > max) break;
        }
        if (value == -1 && out.size() == 0) return null;
        return out.toString(StandardCharsets.ISO_8859_1);
    }

    private Charset charset(String contentType) {
        int index = contentType.indexOf("charset=");
        if (index >= 0) {
            String name = contentType.substring(index + 8).split("[;\\s]")[0].replace("\"", "");
            try {
                return Charset.forName(name);
            } catch (Exception ignored) {
                // 回退 UTF-8
            }
        }
        return StandardCharsets.UTF_8;
    }

    private boolean isRedirect(int status) {
        return status == 301 || status == 302 || status == 303 || status == 307 || status == 308;
    }

    private boolean isDefaultPort(int port, boolean https) {
        return (https && port == 443) || (!https && port == 80);
    }

    private boolean isIpLiteral(String host) {
        return host.contains(":") || host.matches("\\d{1,3}(\\.\\d{1,3}){3}");
    }

    private String sourceType(String host) {
        if (host == null) return "UNKNOWN";
        String value = host.toLowerCase(Locale.ROOT);
        return value.endsWith(".gov.cn") || value.equals("gov.cn")
                || value.endsWith(".edu.cn") || value.equals("edu.cn")
                ? "OFFICIAL" : "UNKNOWN";
    }

    private String limit(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    private String safeMessage(Exception e) {
        String message = e.getMessage();
        if (message == null) return e.getClass().getSimpleName();
        return message.length() > 180 ? message.substring(0, 180) : message;
    }

    private static final class Response {
        int status;
        Map<String, String> headers = new LinkedHashMap<>();
        byte[] body = new byte[0];
    }
}

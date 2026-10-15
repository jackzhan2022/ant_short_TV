package com.antshorttv.drama;

import com.fasterxml.jackson.databind.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class WechatComponentClient {
    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
        .followRedirects(HttpClient.Redirect.NEVER).build();
    @org.springframework.beans.factory.annotation.Autowired
    public WechatComponentClient(ObjectMapper json) { this(json, newCodeHttp()); }
    WechatComponentClient(ObjectMapper json, org.apache.http.impl.client.CloseableHttpClient codeHttp) {
        this.json = json; this.codeHttp = codeHttp;
    }
    public JsonNode post(String api, String token, Map<String, String> body) {
        String parameter = "api_query_auth".equals(api) || "api_authorizer_token".equals(api) || "api_get_authorizer_info".equals(api) ? "access_token" : "component_access_token";
        return send("https://api.weixin.qq.com/cgi-bin/component/" + api
            + (token == null ? "" : "?" + parameter + "=" + encode(token)), body);
    }
    public String login(String appid, String code, String component, String token) {
        JsonNode result = send("https://api.weixin.qq.com/sns/component/jscode2session?appid=" + encode(appid)
            + "&js_code=" + encode(code) + "&component_appid=" + encode(component)
            + "&component_access_token=" + encode(token) + "&grant_type=authorization_code", null);
        if (result.path("openid").asText().isBlank() || result.path("session_key").asText().isBlank()) throw WechatMiniappIdentity.unauthorized();
        return result.path("openid").asText();
    }
    private JsonNode send(String url, Map<String, String> body) {
        try {
            var builder = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10));
            if (body == null) builder.GET();
            else builder.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
            var response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200 || response.body().length() > 65536) throw WechatComponentCrypto.rejected();
            JsonNode result = json.readTree(response.body());
            if (result == null || !result.isObject() || result.path("errcode").asInt() != 0) throw WechatComponentCrypto.rejected();
            return result;
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw WechatComponentCrypto.rejected(); }
        catch (Exception e) { throw WechatComponentCrypto.rejected(); }
    }
    static final int MAX_CODE_JSON = 1024 * 1024;
    static final int MAX_QRCODE = 2 * 1024 * 1024;

    // 独立传输通道：显式禁用底层自动重试、重定向和 Cookie，避免 commit 被隐式重放。
    private final org.apache.http.impl.client.CloseableHttpClient codeHttp;
    static org.apache.http.impl.client.CloseableHttpClient newCodeHttp() {
        return org.apache.http.impl.client.HttpClients.custom()
        .disableAutomaticRetries().disableRedirectHandling().disableCookieManagement()
        .setDefaultRequestConfig(org.apache.http.client.config.RequestConfig.custom()
            .setConnectTimeout(5000).setConnectionRequestTimeout(5000).setSocketTimeout(10000).build()).build();
    }

    @jakarta.annotation.PreDestroy
    void closeCodeHttp() throws java.io.IOException { codeHttp.close(); }

    public JsonNode getTemplates(String token) {
        return codeJson(codeExchange("gettemplatelist", token, "&template_type=0", null, MAX_CODE_JSON), false);
    }

    public JsonNode commitCode(String token, Map<String, Object> body) {
        String payload;
        try { payload = json.writeValueAsString(body); }
        catch (Exception e) { throw WechatCodeModels.upstream(); }
        return codeJson(codeExchange("commit", token, "", payload, MAX_CODE_JSON), true);
    }

    public WechatCodeModels.QrCode trialQrCode(String token, String path) {
        var result = codeExchange("get_qrcode", token, path == null ? "" : "&path=" + encode(path), null, MAX_QRCODE);
        String type = result.contentType().split(";", 2)[0].trim().toLowerCase(java.util.Locale.ROOT);
        byte[] bytes = result.bytes();
        if ("application/json".equals(type) || "text/json".equals(type)
            || new String(bytes, 0, Math.min(bytes.length, 32), StandardCharsets.UTF_8).stripLeading().startsWith("{")) {
            codeJson(result, false);
            throw WechatCodeModels.upstream();
        }
        boolean png = bytes.length >= 8 && java.util.Arrays.equals(java.util.Arrays.copyOf(bytes, 8),
            new byte[]{(byte)137, 80, 78, 71, 13, 10, 26, 10});
        boolean jpeg = bytes.length >= 4 && bytes[0] == (byte)255 && bytes[1] == (byte)216
            && bytes[2] == (byte)255 && bytes[bytes.length - 2] == (byte)255 && bytes[bytes.length - 1] == (byte)217;
        if (!("image/png".equals(type) && png || "image/jpeg".equals(type) && jpeg)) throw WechatCodeModels.upstream();
        return new WechatCodeModels.QrCode(type, bytes);
    }

    record CodeResponse(String contentType, byte[] bytes) {}

    CodeResponse codeExchange(String api, String token, String query, String payload, int limit) {
        String url = "https://api.weixin.qq.com/wxa/" + api + "?access_token=" + encode(token) + query;
        org.apache.http.client.methods.HttpRequestBase request;
        if (payload == null) request = new org.apache.http.client.methods.HttpGet(url);
        else {
            var post = new org.apache.http.client.methods.HttpPost(url);
            post.setEntity(new org.apache.http.entity.StringEntity(payload, org.apache.http.entity.ContentType.APPLICATION_JSON));
            request = post;
        }
        // 总时限同时约束响应头和慢速响应体，不只依赖单次 socket 读超时。
        var deadline = java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
        var abort = deadline.schedule(request::abort, 15, java.util.concurrent.TimeUnit.SECONDS);
        try (var response = codeHttp.execute(request)) {
            if (response.getStatusLine().getStatusCode() != 200 || response.getEntity() == null) throw new java.io.IOException();
            var entity = response.getEntity();
            if (entity.getContentLength() > limit) throw new java.io.IOException();
            var stream = entity.getContent();
            try {
                byte[] bytes = readBounded(stream, limit);
                var header = entity.getContentType();
                return new CodeResponse(header == null ? "" : header.getValue(), bytes);
            } catch (Exception e) {
                // 先中止连接再关闭流，防止 HttpClient 为连接复用继续排空超限响应体。
                request.abort();
                throw e;
            } finally { stream.close(); }
        } catch (Exception e) {
            // 不传播包含 access_token URL 的底层异常，也不刷新 token 后重发上传。
            throw payload == null ? WechatCodeModels.upstream() : WechatCodeModels.unknown();
        } finally { abort.cancel(false); deadline.shutdownNow(); request.releaseConnection(); }
    }

    static byte[] readBounded(java.io.InputStream stream, int limit) throws java.io.IOException {
        byte[] bytes = stream.readNBytes(limit + 1);
        if (bytes.length > limit) throw new java.io.IOException("响应超过大小限制");
        return bytes;
    }

    private JsonNode codeJson(CodeResponse response, boolean commit) {
        JsonNode result;
        try { result = json.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(response.bytes()); }
        catch (Exception e) { throw commit ? WechatCodeModels.unknown() : WechatCodeModels.upstream(); }
        if (result == null || !result.isObject() || !result.path("errcode").isIntegralNumber()
            || !result.path("errcode").canConvertToInt()) throw commit ? WechatCodeModels.unknown() : WechatCodeModels.upstream();
        if (result.path("errcode").intValue() != 0) throw WechatCodeModels.rejected(result.path("errcode").intValue());
        return result;
    }

    static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
}

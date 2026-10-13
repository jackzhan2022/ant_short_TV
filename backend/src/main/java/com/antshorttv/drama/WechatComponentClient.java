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
    public WechatComponentClient(ObjectMapper json) { this.json = json; }
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
    static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
}

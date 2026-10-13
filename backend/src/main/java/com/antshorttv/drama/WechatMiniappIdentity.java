package com.antshorttv.drama;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class WechatMiniappIdentity {
    private final Environment environment;
    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
        .followRedirects(HttpClient.Redirect.NEVER).build();

    public WechatMiniappIdentity(Environment environment, ObjectMapper json) {
        this.environment = environment; this.json = json;
    }

    public String exchange(String appid, String code) {
        String secret = environment.getProperty("miniapp.apps." + appid + ".secret");
        if (secret == null || secret.isBlank()) {
            throw DramaLibraryService.invalid("该小程序未配置服务端 appid/secret，真实微信登录暂不可用；手工绑定不等于官方授权。");
        }
        try {
            URI uri = URI.create("https://api.weixin.qq.com/sns/jscode2session?appid=" + encode(appid)
                + "&secret=" + encode(secret) + "&js_code=" + encode(code) + "&grant_type=authorization_code");
            HttpResponse<String> response = http.send(HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(10)).GET().build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200 || response.body().length() > 65536) throw unauthorized();
            JsonNode body = json.readTree(response.body());
            if (body.path("errcode").asInt(0) != 0 || !body.path("openid").isTextual()
                || body.path("openid").asText().isBlank()
                || !body.path("session_key").isTextual() || body.path("session_key").asText().isBlank()) {
                throw unauthorized();
            }
            return body.path("openid").asText();
        } catch (BusinessException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt(); throw unauthorized();
        } catch (Exception exception) {
            // 不传播包含 secret/code/session_key 的上游异常或响应。
            throw unauthorized();
        }
    }

    private String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    static BusinessException unauthorized() { return new BusinessException(ErrorCode.UNAUTHORIZED, "小程序身份无效或已过期，请重新微信登录。"); }
}

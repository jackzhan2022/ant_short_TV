package com.antshorttv.monitoring;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class FeishuWebhookClient {
    private final MonitoringProperties properties;
    private final ObjectMapper json;
    private final HttpClient http;

    @Autowired
    public FeishuWebhookClient(MonitoringProperties properties, ObjectMapper json) {
        this(properties, json, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
    }

    FeishuWebhookClient(MonitoringProperties properties, ObjectMapper json, HttpClient http) {
        this.properties = properties;
        this.json = json;
        this.http = http;
    }

    public boolean send(String text) {
        if (!properties.enabled() || properties.feishuWebhookUrl().isBlank()) return false;
        try {
            var payload = new LinkedHashMap<String, Object>();
            payload.put("msg_type", "text");
            payload.put("content", Map.of("text", text));
            if (!properties.feishuSecret().isBlank()) {
                String timestamp = String.valueOf(Instant.now().getEpochSecond());
                Mac mac = Mac.getInstance("HmacSHA256");
                mac.init(new SecretKeySpec((timestamp + "\n" + properties.feishuSecret()).getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
                payload.put("timestamp", timestamp);
                payload.put("sign", Base64.getEncoder().encodeToString(mac.doFinal(new byte[0])));
            }
            var request = HttpRequest.newBuilder(URI.create(properties.feishuWebhookUrl()))
                .timeout(Duration.ofSeconds(5)).header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload))).build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() / 100 != 2 || response.body().length() > 8192) return false;
            var result = json.readTree(response.body());
            var code = result.has("code") ? result.get("code") : result.get("StatusCode");
            return code != null && code.isIntegralNumber() && code.asInt() == 0;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception exception) {
            // Exception messages can contain the webhook URL. Log only batch metadata in the scheduler.
            return false;
        }
    }
}

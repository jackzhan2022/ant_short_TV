package com.antshorttv.monitoring;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class FeishuWebhookClientTest {
    @Test
    void requiresBusinessSuccessAndIncludesOptionalSignatureWithoutLeakingWebhook() throws Exception {
        var body = new AtomicReference<String>();
        var response = new AtomicReference<>("{\"code\":0}");
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/hook", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = response.get().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            var props = new MonitoringProperties(true, "http://127.0.0.1:" + server.getAddress().getPort() + "/hook",
                "signing-secret", "test", "one", 10, true);
            var json = new ObjectMapper();
            var client = new FeishuWebhookClient(props, json, HttpClient.newHttpClient());
            assertThat(client.send("系统监测测试")).isTrue();
            var payload = json.readTree(body.get());
            assertThat(payload.path("msg_type").asText()).isEqualTo("text");
            assertThat(payload.path("content").path("text").asText()).isEqualTo("系统监测测试");
            String timestamp = payload.path("timestamp").asText();
            var mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec((timestamp + "\nsigning-secret").getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            assertThat(payload.path("sign").asText()).isEqualTo(java.util.Base64.getEncoder().encodeToString(mac.doFinal(new byte[0])));
            response.set("{\"code\":19024,\"msg\":\"failure\"}");
            assertThat(client.send("test")).isFalse();
            response.set("{}");
            assertThat(client.send("test")).isFalse();
            response.set("{\"StatusCode\":0}");
            assertThat(client.send("test")).isTrue();
        } finally {
            server.stop(0);
        }
    }
}

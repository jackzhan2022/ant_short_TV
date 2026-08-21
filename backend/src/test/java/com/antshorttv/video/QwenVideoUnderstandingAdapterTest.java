package com.antshorttv.video;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.antshorttv.ai.AiGatewayException;
import com.antshorttv.ai.AiProviderConfigEntity;
import com.antshorttv.ai.AiProviderEntity;
import com.antshorttv.ai.AiSecretCodec;
import com.antshorttv.ai.AiModelEntity;
import com.antshorttv.common.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class QwenVideoUnderstandingAdapterTest {

    @Test
    void sendsVideoUrlAndStructuredPromptToDashscopeCompatibleEndpoint() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = """
                {
                  "id":"qwen-video-test",
                  "choices":[{"message":{"content":"{\\"characters\\":[],\\"scenes\\":[],\\"props\\":[],\\"timeline\\":[],\\"dialogue\\":[],\\"actions\\":[],\\"emotions\\":[]}"}}],
                  "usage":{"prompt_tokens":12,"completion_tokens":8,"total_tokens":20}
                }
                """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        try {
            QwenVideoUnderstandingAdapter adapter = new QwenVideoUnderstandingAdapter(
                new AiSecretCodec("test-secret"),
                new ObjectMapper()
            );
            var response = adapter.videoUnderstanding(
                provider(),
                config("http://127.0.0.1:%d/v1".formatted(server.getAddress().getPort()), "sk-real-qwen"),
                model(),
                new VideoUnderstandingRequest(
                    "https://cdn.example.com/episode-1.mp4",
                    "只返回合法 JSON，必须包含 characters、scenes、props、timeline、dialogue、actions、emotions。"
                )
            );

            assertThat(requestBody.get()).contains("\"model\":\"qwen3.7-plus\"");
            assertThat(requestBody.get()).contains("\"type\":\"video_url\"");
            assertThat(requestBody.get()).contains("https://cdn.example.com/episode-1.mp4");
            assertThat(requestBody.get()).contains("只返回合法 JSON");
            assertThat(response.providerRequestId()).isEqualTo("qwen-video-test");
            assertThat(response.totalTokens()).isEqualTo(20);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void mapsRateLimitAndNonJsonProviderFailures() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/rate-limit", exchange -> {
            byte[] body = "{\"error\":{\"message\":\"too many requests\"}}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(429, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/v1/non-json", exchange -> {
            byte[] body = "<html>bad gateway</html>".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/html");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        try {
            QwenVideoUnderstandingAdapter adapter = new QwenVideoUnderstandingAdapter(
                new AiSecretCodec("test-secret"),
                new ObjectMapper()
            );
            assertThatThrownBy(() -> adapter.videoUnderstanding(
                    provider(),
                    config("http://127.0.0.1:%d/v1/rate-limit".formatted(server.getAddress().getPort()), "sk-real-qwen"),
                    model(),
                    new VideoUnderstandingRequest("https://cdn.example.com/episode-1.mp4", "只返回 JSON")
                ))
                .isInstanceOf(AiGatewayException.class)
                .extracting(exception -> ((AiGatewayException) exception).getErrorCode())
                .isEqualTo(ErrorCode.AI_RATE_LIMIT);

            assertThatThrownBy(() -> adapter.videoUnderstanding(
                    provider(),
                    config("http://127.0.0.1:%d/v1/non-json".formatted(server.getAddress().getPort()), "sk-real-qwen"),
                    model(),
                    new VideoUnderstandingRequest("https://cdn.example.com/episode-1.mp4", "只返回 JSON")
                ))
                .isInstanceOf(AiGatewayException.class)
                .extracting(exception -> ((AiGatewayException) exception).getErrorCode())
                .isEqualTo(ErrorCode.AI_PROVIDER_ERROR);
        } finally {
            server.stop(0);
        }
    }

    private AiProviderEntity provider() {
        AiProviderEntity provider = new AiProviderEntity();
        provider.setId(1L);
        provider.setCode("阿里云百炼");
        return provider;
    }

    private AiProviderConfigEntity config(String baseUrl, String apiKey) {
        AiProviderConfigEntity config = new AiProviderConfigEntity();
        AiSecretCodec codec = new AiSecretCodec("test-secret");
        config.setBaseUrl(baseUrl);
        config.setApiKeyCipher(codec.encrypt(apiKey));
        config.setStatus("ENABLED");
        return config;
    }

    private AiModelEntity model() {
        AiModelEntity model = new AiModelEntity();
        model.setId(10L);
        model.setName("Qwen3.7 Plus");
        model.setModelCode("qwen3.7-plus");
        model.setServiceType("VIDEO_UNDERSTANDING");
        return model;
    }
}

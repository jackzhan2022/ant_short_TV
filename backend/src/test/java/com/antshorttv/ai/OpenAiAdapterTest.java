package com.antshorttv.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class OpenAiAdapterTest {

    private final AiSecretCodec aiSecretCodec = new AiSecretCodec("test-ai-secret-key");
    private final OpenAiAdapter adapter = new OpenAiAdapter(aiSecretCodec, new ObjectMapper());

    @Test
    void sendsChatCompletionRequestAndParsesTextResponse() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = """
                {
                  "id":"chatcmpl-test",
                  "choices":[{"message":{"content":"真实文本结果"}}],
                  "usage":{"prompt_tokens":7,"completion_tokens":3,"total_tokens":10}
                }
                """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        try {
            AiTextResponse response = adapter.text(
                provider(),
                config("http://127.0.0.1:%d/v1".formatted(server.getAddress().getPort()), "sk-real-123"),
                model("gpt-test", "TEXT"),
                new AiTextRequest("系统提示", "用户提示", 0.3, 256, null)
            );

            assertThat(authorization.get()).isEqualTo("Bearer sk-real-123");
            assertThat(requestBody.get()).contains("\"model\":\"gpt-test\"");
            assertThat(requestBody.get()).contains("系统提示");
            assertThat(requestBody.get()).contains("用户提示");
            assertThat(response.content()).isEqualTo("真实文本结果");
            assertThat(response.providerRequestId()).isEqualTo("chatcmpl-test");
            assertThat(response.promptTokens()).isEqualTo(7);
            assertThat(response.completionTokens()).isEqualTo(3);
            assertThat(response.totalTokens()).isEqualTo(10);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void sendsImageGenerationRequestAndParsesImageUrls() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/images/generations", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = """
                {
                  "created":123,
                  "data":[
                    {"url":"https://cdn.example.com/a.png"},
                    {"url":"https://cdn.example.com/b.png"}
                  ]
                }
                """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        try {
            AiImageResponse response = adapter.image(
                provider(),
                config("http://127.0.0.1:%d/v1".formatted(server.getAddress().getPort()), "sk-real-456"),
                model("gpt-image-test", "IMAGE"),
                new AiImageRequest("生成女主首帧", "低清", "1024x1792", "9:16", 2, null)
            );

            assertThat(requestBody.get()).contains("\"model\":\"gpt-image-test\"");
            assertThat(requestBody.get()).contains("生成女主首帧");
            assertThat(requestBody.get()).contains("\"n\":2");
            assertThat(response.imageUrls()).containsExactly("https://cdn.example.com/a.png", "https://cdn.example.com/b.png");
            assertThat(response.providerRequestId()).isNotBlank();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void reportsNonJsonTextResponseWithProviderBodySummary() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            byte[] body = "<html><body>proxy error</body></html>".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/html");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        try {
            assertThatThrownBy(() -> adapter.text(
                provider(),
                config("http://127.0.0.1:%d/v1".formatted(server.getAddress().getPort()), "sk-real-123"),
                model("gpt-test", "TEXT"),
                new AiTextRequest(null, "用户提示", 0.3, 256, null)
            ))
                .isInstanceOf(AiGatewayException.class)
                .hasMessageContaining("AI 服务商返回非 JSON 响应")
                .hasMessageContaining("text/html")
                .hasMessageContaining("proxy error");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void reportsHttpErrorWithProviderBodySummary() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            byte[] body = "<html><body>bad gateway</body></html>".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/html");
            exchange.sendResponseHeaders(502, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        try {
            assertThatThrownBy(() -> adapter.text(
                provider(),
                config("http://127.0.0.1:%d/v1".formatted(server.getAddress().getPort()), "sk-real-123"),
                model("gpt-test", "TEXT"),
                new AiTextRequest(null, "用户提示", 0.3, 256, null)
            ))
                .isInstanceOf(AiGatewayException.class)
                .hasMessageContaining("AI 服务商返回 HTTP 502")
                .hasMessageContaining("bad gateway");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void rejectsUndecryptableApiKeyBeforeCallingProvider() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            requests.incrementAndGet();
            byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        try {
            AiProviderConfigEntity config = new AiProviderConfigEntity();
            config.setBaseUrl("http://127.0.0.1:%d/v1".formatted(server.getAddress().getPort()));
            config.setApiKeyCipher(new AiSecretCodec("other-secret").encrypt("sk-real-123"));
            config.setStatus("ENABLED");

            assertThatThrownBy(() -> adapter.text(
                provider(),
                config,
                model("gpt-test", "TEXT"),
                new AiTextRequest(null, "用户提示", 0.3, 256, null)
            ))
                .isInstanceOf(AiGatewayException.class)
                .extracting("errorCode")
                .isEqualTo(com.antshorttv.common.ErrorCode.AI_AUTH_FAILED);
            assertThat(requests).hasValue(0);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void returnsStructuredJsonForLocalExtractionMock() throws Exception {
        AiTextResponse response = adapter.text(
            provider(),
            config("mock://local", "test-key"),
            model("deepseek-v4-pro", "TEXT"),
            new AiTextRequest(
                null,
                """
                    你是短剧剧本结构化信息提取助手。
                    请仅基于剧本内容提取角色信息，只返回合法 JSON，不要解释，不要 Markdown，不要代码块。
                    剧本内容：林晚在天台拿出录音笔。
                    """,
                0.2,
                512,
                null
            )
        );

        assertThat(response.content()).isEqualTo("""
            {"characters":[{"name":"林晚","roleType":"LEAD","gender":"","ageRange":"","identity":"","personality":[],"appearance":"","prompt":""}]}
            """.trim());
        assertThat(response.providerRequestId()).startsWith("local-");
    }

    private AiProviderEntity provider() {
        AiProviderEntity provider = new AiProviderEntity();
        provider.setId(1L);
        provider.setName("OpenAI");
        provider.setCode("OpenAI");
        return provider;
    }

    private AiProviderConfigEntity config(String baseUrl, String apiKey) {
        AiProviderConfigEntity config = new AiProviderConfigEntity();
        config.setBaseUrl(baseUrl);
        config.setApiKeyCipher(aiSecretCodec.encrypt(apiKey));
        config.setStatus("ENABLED");
        return config;
    }

    private AiModelEntity model(String modelCode, String serviceType) {
        AiModelEntity model = new AiModelEntity();
        model.setId(10L);
        model.setName(modelCode);
        model.setModelCode(modelCode);
        model.setServiceType(serviceType);
        return model;
    }
}

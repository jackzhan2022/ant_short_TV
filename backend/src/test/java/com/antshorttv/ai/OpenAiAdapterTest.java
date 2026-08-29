package com.antshorttv.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

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
                  "choices":[{"finish_reason":"stop","message":{"content":"真实文本结果"}}],
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
            assertThat(response.finishReason()).isEqualTo("stop");
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
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.AI_AUTH_FAILED);
            assertThat(requests).hasValue(0);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void usesLogicalExecutionKeyInsteadOfReusingAContentHash() throws Exception {
        CopyOnWriteArrayList<String> keys = new CopyOnWriteArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/images/generations", exchange -> {
            keys.add(exchange.getRequestHeaders().getFirst("Idempotency-Key"));
            exchange.getRequestBody().readAllBytes();
            byte[] body = "{\"data\":[]}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        try {
            AiProviderConfigEntity config = config(
                "http://127.0.0.1:%d/v1".formatted(server.getAddress().getPort()), "sk-real-456");
            AiImageRequest request = new AiImageRequest("同一个提示词", null, null, "9:16", 1, null);
            adapter.image(provider(), config, model("gpt-image-test", "IMAGE"), request, "execution-101-attempt-1");
            adapter.image(provider(), config, model("gpt-image-test", "IMAGE"), request, "execution-102-attempt-1");

            assertThat(keys).containsExactly("execution-101-attempt-1", "execution-102-attempt-1");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void reusesTheSameLogicalKeyAcrossProviderRetries() throws Exception {
        CopyOnWriteArrayList<String> keys = new CopyOnWriteArrayList<>();
        AtomicInteger attempts = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            keys.add(exchange.getRequestHeaders().getFirst("Idempotency-Key"));
            exchange.getRequestBody().readAllBytes();
            int status = attempts.incrementAndGet() == 1 ? 502 : 200;
            byte[] body = (status == 200
                ? "{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}"
                : "{\"error\":\"temporary\"}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        try {
            AiTextResponse response = adapter.text(provider(),
                config("http://127.0.0.1:%d/v1".formatted(server.getAddress().getPort()), "sk-real-123"),
                model("gpt-test", "TEXT"),
                new AiTextRequest(null, "retry", 0.2, 128, null, false, null, 5, 1),
                "execution-201-attempt-1");

            assertThat(response.content()).isEqualTo("ok");
            assertThat(keys).containsExactly("execution-201-attempt-1", "execution-201-attempt-1");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void exposesLengthFinishReasonForTruncatedJson() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            byte[] body = "{\"choices\":[{\"finish_reason\":\"length\",\"message\":{\"content\":\"{}\"}}]}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            AiTextResponse response = adapter.text(provider(),
                config("http://127.0.0.1:%d/v1".formatted(server.getAddress().getPort()), "sk-real-123"),
                model("gpt-test", "TEXT"), new AiTextRequest(null, "用户提示", 0.2, 256, null));
            assertThat(response.finishReason()).isEqualTo("length");
            assertThat(response.truncated()).isTrue();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void sendsConfiguredTopPAndJsonResponseFormat() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = """
                {"id":"chatcmpl-json","choices":[{"message":{"content":"{}"}}]}
                """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        try {
            adapter.text(
                provider(),
                config("http://127.0.0.1:%d/v1".formatted(server.getAddress().getPort()), "sk-real-123"),
                model("gpt-test", "TEXT"),
                new AiTextRequest(null, "只返回 JSON", 0.2, 8192, 0.8, true, null)
            );

            assertThat(requestBody.get()).contains("\"max_tokens\":8192");
            assertThat(requestBody.get()).contains("\"top_p\":0.8");
            assertThat(requestBody.get()).contains("\"response_format\":{\"type\":\"json_object\"}");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void longScriptAnalysisUsesExpandedTokenBudgetAndJsonMode() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\"{}\"}}]}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            String longScript = "场景：林晚在天台发现录音笔。".repeat(2000);
            adapter.text(provider(), config("http://127.0.0.1:%d/v1".formatted(server.getAddress().getPort()), "sk-real-123"),
                model("gpt-long-script", "TEXT"), new AiTextRequest(null, longScript, 0.2, 8192, 0.8, true, null));

            assertThat(requestBody.get()).contains("\"max_tokens\":8192", "\"top_p\":0.8", "\"response_format\":{\"type\":\"json_object\"}");
            assertThat(requestBody.get()).contains(longScript.substring(0, 100));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void doesNotUseLocalMockWithoutExplicitTestSwitch() {
        assertThatThrownBy(() -> adapter.text(
            provider(),
            config("mock://local", "test-key"),
            model("deepseek-v4-pro", "TEXT"),
            new AiTextRequest(null, "prompt", 0.2, 32, null)
        ))
            .isInstanceOf(AiGatewayException.class)
            .hasMessageContaining("AI 文本调用失败");
    }

    @Test
    void returnsStructuredJsonForLocalExtractionMock() throws Exception {
        ReflectionTestUtils.setField(adapter, "mockProviderEnabled", true);
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

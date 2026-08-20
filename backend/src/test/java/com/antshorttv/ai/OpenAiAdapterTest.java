package com.antshorttv.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
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

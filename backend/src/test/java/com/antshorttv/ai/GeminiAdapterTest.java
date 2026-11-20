package com.antshorttv.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.antshorttv.common.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class GeminiAdapterTest {
    private final ObjectMapper json = new ObjectMapper();
    private final AiSecretCodec codec = new AiSecretCodec("gemini-test-secret");

    @Test
    void sendsNativeGeminiImageRequestAndReturnsInlineImages() throws Exception {
        AtomicReference<String> path = new AtomicReference<>();
        AtomicReference<String> key = new AtomicReference<>();
        AtomicReference<String> idempotency = new AtomicReference<>();
        AtomicReference<JsonNode> payload = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/reference", exchange -> {
            byte[] body = "url-image".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "image/png");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/", exchange -> {
            path.set(exchange.getRequestURI().getPath());
            key.set(exchange.getRequestHeaders().getFirst("x-goog-api-key"));
            idempotency.set(exchange.getRequestHeaders().getFirst("Idempotency-Key"));
            payload.set(json.readTree(exchange.getRequestBody()));
            byte[] body = """
                {"responseId":"gemini-response","candidates":[{"content":{"parts":[
                  {"text":"generated"},{"inlineData":{"mimeType":"image/png","data":"aW1hZ2U="}}
                ]}}]}
                """.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            var response = new GeminiAdapter(codec, json).image(provider(), config(server, ""), model(),
                new AiImageRequest("a cat", null, "2K", "9:16", 1,
                    List.of("data:image/jpeg;base64,cmVm", "http://127.0.0.1:" + server.getAddress().getPort() + "/reference")), "gemini-once");
            assertThat(path.get()).isEqualTo("/v1beta/models/gemini-image:generateContent");
            assertThat(key.get()).isEqualTo("test-gemini-key");
            assertThat(idempotency.get()).isEqualTo("gemini-once");
            assertThat(payload.get().at("/contents/0/parts/0/text").asText()).isEqualTo("a cat");
            assertThat(payload.get().at("/contents/0/parts/1/inlineData/data").asText()).isEqualTo("cmVm");
            assertThat(payload.get().at("/contents/0/parts/2/inlineData/data").asText()).isEqualTo("dXJsLWltYWdl");
            assertThat(payload.get().at("/generationConfig/imageConfig/aspectRatio").asText()).isEqualTo("9:16");
            assertThat(payload.get().at("/generationConfig/imageConfig/imageSize").asText()).isEqualTo("2K");
            assertThat(response.imageUrls()).containsExactly("data:image/png;base64,aW1hZ2U=");
            assertThat(response.providerRequestId()).isEqualTo("gemini-response");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void rejectsSuccessfulResponsesWithoutAnImageAndPreservesAuthErrors() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1beta/", exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] body = "{\"candidates\":[]}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/denied/v1beta/", exchange -> {
            exchange.getRequestBody().readAllBytes();
            exchange.sendResponseHeaders(403, -1);
            exchange.close();
        });
        server.start();
        try {
            var adapter = new GeminiAdapter(codec, json);
            var request = new AiImageRequest("cat", null, null, "1:1", 1, List.of());
            assertThatThrownBy(() -> adapter.image(provider(), config(server, "/v1beta"), model(), request))
                .isInstanceOfSatisfying(AiGatewayException.class,
                    error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.AI_RESPONSE_INVALID));
            assertThatThrownBy(() -> adapter.image(provider(), config(server, "/denied"), model(), request))
                .isInstanceOfSatisfying(AiGatewayException.class,
                    error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.AI_AUTH_FAILED));
        } finally {
            server.stop(0);
        }
    }

    private AiProviderEntity provider() {
        var provider = new AiProviderEntity();
        provider.setCode("Gemini");
        return provider;
    }

    private AiModelEntity model() {
        var model = new AiModelEntity();
        model.setModelCode("gemini-image");
        return model;
    }

    private AiProviderConfigEntity config(HttpServer server, String suffix) {
        var config = new AiProviderConfigEntity();
        config.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort() + suffix);
        config.setApiKeyCipher(codec.encrypt("test-gemini-key"));
        return config;
    }
}

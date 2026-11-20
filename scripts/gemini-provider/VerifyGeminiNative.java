package com.antshorttv.ai;

import com.antshorttv.common.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Runs against classes extracted from the candidate, without contacting an upstream. */
public class VerifyGeminiNative {
    public static void main(String[] args) throws Exception {
        var json = new ObjectMapper();
        var codec = new AiSecretCodec("offline-gemini-verification");
        var adapter = new GeminiAdapter(codec, json);
        var calls = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            try {
                require(exchange.getRequestURI().getPath().equals("/v1beta/models/gemini-image:generateContent"), "native path");
                require("offline-test-key".equals(exchange.getRequestHeaders().getFirst("x-goog-api-key")), "native auth");
                require("offline-once".equals(exchange.getRequestHeaders().getFirst("Idempotency-Key")), "idempotency");
                var payload = json.readTree(exchange.getRequestBody());
                require(payload.at("/contents/0/parts/0/text").asText().equals("cat"), "prompt");
                require(payload.at("/contents/0/parts/1/inlineData/data").asText().equals("cmVm"), "reference");
                require(payload.at("/generationConfig/imageConfig/aspectRatio").asText().equals("9:16"), "aspect ratio");
                int attempt = calls.incrementAndGet();
                byte[] body = (attempt == 1 ? "{\"responseId\":\"offline-image\",\"candidates\":[{\"content\":{\"parts\":[{\"inlineData\":{\"mimeType\":\"image/png\",\"data\":\"aW1hZ2U=\"}}]}}]}" : "{\"candidates\":[]}").getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(attempt == 3 ? 403 : 200, body.length);
                exchange.getResponseBody().write(body);
            } finally { exchange.close(); }
        });
        server.start();
        try {
            var provider = new AiProviderEntity(); provider.setCode("Gemini");
            var config = new AiProviderConfigEntity();
            config.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
            config.setApiKeyCipher(codec.encrypt("offline-test-key"));
            var model = new AiModelEntity(); model.setModelCode("gemini-image");
            var request = new AiImageRequest("cat", null, "2K", "9:16", 1, List.of("data:image/jpeg;base64,cmVm"));
            var result = adapter.image(provider, config, model, request, "offline-once");
            require(result.imageUrls().equals(List.of("data:image/png;base64,aW1hZ2U=")), "image decoding");
            require("offline-image".equals(result.providerRequestId()), "request identity");
            config.setBaseUrl(config.getBaseUrl() + "/v1beta");
            for (var expected : List.of(ErrorCode.AI_RESPONSE_INVALID, ErrorCode.AI_AUTH_FAILED)) {
                try {
                    adapter.image(provider, config, model, request, "offline-once");
                    throw new AssertionError("Expected " + expected);
                } catch (AiGatewayException error) { require(error.getErrorCode() == expected, "error classification"); }
            }
            require(calls.get() == 3, "no duplicate calls");
            config.setApiKeyCipher(null);
            try {
                adapter.image(provider, config, model, request, "offline-once");
                throw new AssertionError("Missing key accepted");
            } catch (AiGatewayException error) { require(error.getErrorCode() == ErrorCode.AI_AUTH_FAILED, "missing key"); }
            System.out.println("Gemini native candidate verification: PASS");
        } finally { server.stop(0); }
    }

    private static void require(boolean condition, String detail) {
        if (!condition) throw new AssertionError(detail);
    }
}

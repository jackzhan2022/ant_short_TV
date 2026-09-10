package com.antshorttv.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class XiongXiongAiAdapterTest {
    @Test
    void exposesTheDedicatedOpenAiCompatibleProviderCode() {
        XiongXiongAiAdapter adapter = new XiongXiongAiAdapter(new AiSecretCodec("test-secret"), new ObjectMapper());

        assertThat(adapter.providerCode()).isEqualTo("OpenAI");
    }

    @Test
    void sendsGptImageOptionsAndParsesBase64ImageResults() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/images/generations", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = "{\"id\":\"img-123\",\"data\":[{\"b64_json\":\"YWJj\"}]}"
                .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        try {
            AiSecretCodec secretCodec = new AiSecretCodec("test-secret");
            XiongXiongAiAdapter adapter = new XiongXiongAiAdapter(secretCodec, new ObjectMapper());
            AiProviderConfigEntity config = new AiProviderConfigEntity();
            config.setBaseUrl("http://127.0.0.1:%d/v1".formatted(server.getAddress().getPort()));
            config.setApiKeyCipher(secretCodec.encrypt("sk-image"));
            AiModelEntity model = new AiModelEntity();
            model.setModelCode("gpt-image-2.5-sunburst");
            model.setConfigJson("""
                {"quality":"xhigh","background":"transparent","outputFormat":"webp",
                 "outputCompression":65,"moderation":"low"}
                """);

            AiImageResponse response = adapter.image(provider(), config, model,
                new AiImageRequest("透明的剧集海报", null, "2048x1152", "16:9", 1, List.of()));

            JsonNode body = new ObjectMapper().readTree(requestBody.get());
            assertThat(body.path("model").asText()).isEqualTo("gpt-image-2.5-sunburst");
            assertThat(body.path("size").asText()).isEqualTo("2048x1152");
            assertThat(body.path("quality").asText()).isEqualTo("xhigh");
            assertThat(body.path("background").asText()).isEqualTo("transparent");
            assertThat(body.path("output_format").asText()).isEqualTo("webp");
            assertThat(body.path("output_compression").asInt()).isEqualTo(65);
            assertThat(body.path("moderation").asText()).isEqualTo("low");
            assertThat(response.imageUrls()).containsExactly("data:image/webp;base64,YWJj");
            assertThat(response.providerRequestId()).isEqualTo("img-123");
        } finally {
            server.stop(0);
        }
    }

    private AiProviderEntity provider() {
        AiProviderEntity provider = new AiProviderEntity();
        provider.setCode("XiongXiongAI");
        return provider;
    }
}

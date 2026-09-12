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

class XiongXiongAiAdapterTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void exposesTheDedicatedOpenAiCompatibleProviderCode() {
        assertThat(new XiongXiongAiAdapter(new AiSecretCodec("test-secret"), objectMapper).providerCode()).isEqualTo("OpenAI");
    }

    @Test
    void submitsResponsesGenerateRequestWithConfiguredResponsesAndImageModels() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = responsesServer(requestBody,
            "{\"id\":\"resp-123\",\"output\":[{\"type\":\"image_generation_call\",\"result\":\"YWJj\"}]}"
        );
        try {
            AiImageResponse response = adapter().image(provider(), config(server), model(
                "{\"responsesModel\":\"gpt-5.6-terra-custom\",\"quality\":\"high\",\"outputFormat\":\"png\"}"),
                new AiImageRequest("透明的剧集海报", null, "1536x1536", "1:1", 1, List.of()));

            JsonNode body = objectMapper.readTree(requestBody.get());
            assertThat(body.path("model").asText()).isEqualTo("gpt-5.6-terra-custom");
            assertThat(body.at("/input/0/content/0/type").asText()).isEqualTo("input_text");
            assertThat(body.at("/input/0/content/0/text").asText()).isEqualTo("透明的剧集海报");
            assertThat(body.at("/tools/0/type").asText()).isEqualTo("image_generation");
            assertThat(body.at("/tools/0/model").asText()).isEqualTo("gpt-image-2.5-sunburst");
            assertThat(body.at("/tools/0/size").asText()).isEqualTo("1536x1536");
            assertThat(body.at("/tools/0/quality").asText()).isEqualTo("high");
            assertThat(body.at("/tools/0/action").asText()).isEqualTo("generate");
            assertThat(response.imageUrls()).containsExactly("data:image/png;base64,YWJj");
            assertThat(response.providerRequestId()).isEqualTo("resp-123");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void submitsEditRequestWithDefaultResponsesModelAndReferenceImages() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = responsesServer(requestBody,
            "{\"id\":\"resp-456\",\"output\":[{\"type\":\"image_generation_call\",\"result\":\"YWJj\",\"output_format\":\"webp\"}]}"
        );
        try {
            AiImageResponse response = adapter().image(provider(), config(server), model("{}"),
                new AiImageRequest("将角色改为红色斗篷", null, null, "1:1", 1,
                    List.of("data:image/png;base64,cmVmZXJlbmNl")));

            JsonNode body = objectMapper.readTree(requestBody.get());
            assertThat(body.path("model").asText()).isEqualTo("gpt-5.6-terra");
            assertThat(body.at("/input/0/content/1/type").asText()).isEqualTo("input_image");
            assertThat(body.at("/input/0/content/1/image_url").asText()).isEqualTo("data:image/png;base64,cmVmZXJlbmNl");
            assertThat(body.at("/tools/0/action").asText()).isEqualTo("edit");
            assertThat(response.imageUrls()).containsExactly("data:image/webp;base64,YWJj");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void waitsMoreThanOneMinuteForImageGenerationResponse() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = responsesServer(requestBody,
            "{\"id\":\"resp-slow\",\"output\":[{\"type\":\"image_generation_call\",\"result\":\"YWJj\"}]}",
            61_000
        );
        try {
            AiImageResponse response = adapter().image(provider(), config(server), model("{}"),
                new AiImageRequest("慢速图片", null, null, "1:1", 1, List.of()));

            assertThat(response.providerRequestId()).isEqualTo("resp-slow");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void rejectsResponsesWithoutImageGenerationOutput() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = responsesServer(requestBody, "{\"id\":\"resp-empty\",\"output\":[]}");
        try {
            assertThatThrownBy(() -> adapter().image(provider(), config(server), model("{}"),
                new AiImageRequest("海报", null, null, "1:1", 1, List.of())))
                .isInstanceOf(AiGatewayException.class)
                .extracting(exception -> ((AiGatewayException) exception).getErrorCode())
                .isEqualTo(ErrorCode.AI_RESPONSE_INVALID);
        } finally {
            server.stop(0);
        }
    }

    private HttpServer responsesServer(AtomicReference<String> requestBody, String response) throws Exception {
        return responsesServer(requestBody, response, 0);
    }

    private HttpServer responsesServer(AtomicReference<String> requestBody, String response, long delayMillis) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/responses", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            if (delayMillis > 0) {
                try {
                    Thread.sleep(delayMillis);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    exchange.close();
                    return;
                }
            }
            byte[] body = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        return server;
    }

    private XiongXiongAiAdapter adapter() {
        return new XiongXiongAiAdapter(new AiSecretCodec("test-secret"), objectMapper);
    }

    private AiProviderConfigEntity config(HttpServer server) {
        AiSecretCodec secretCodec = new AiSecretCodec("test-secret");
        AiProviderConfigEntity config = new AiProviderConfigEntity();
        config.setBaseUrl("http://127.0.0.1:%d/v1".formatted(server.getAddress().getPort()));
        config.setApiKeyCipher(secretCodec.encrypt("sk-image"));
        return config;
    }

    private AiModelEntity model(String configJson) {
        AiModelEntity model = new AiModelEntity();
        model.setModelCode("gpt-image-2.5-sunburst");
        model.setConfigJson(configJson);
        return model;
    }

    private AiProviderEntity provider() {
        AiProviderEntity provider = new AiProviderEntity();
        provider.setCode("XiongXiongAI");
        return provider;
    }
}

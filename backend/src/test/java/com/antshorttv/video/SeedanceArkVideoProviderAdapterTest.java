package com.antshorttv.video;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.antshorttv.ai.AiModelEntity;
import com.antshorttv.ai.AiProviderConfigEntity;
import com.antshorttv.ai.AiSecretCodec;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class SeedanceArkVideoProviderAdapterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AiSecretCodec secretCodec = new AiSecretCodec("seedance-test-secret");

    @Test
    void submitsImageToVideoTaskUsingArkContentContract() throws Exception {
        AtomicReference<JsonNode> requestBody = new AtomicReference<>();
        HttpServer server = server(exchange -> {
            requestBody.set(objectMapper.readTree(exchange.getRequestBody().readAllBytes()));
            writeJson(exchange, 200, """
                {"id":"ark-task-1","request_id":"ark-request-1","status":"queued"}
                """);
        });
        try {
            SeedanceArkVideoProviderAdapter adapter = new SeedanceArkVideoProviderAdapter(secretCodec, objectMapper);

            var outcome = adapter.submit(config(server), model("ep-seedance-fast"), task("https://cdn.example.com/frame.png"), "request-1");

            assertThat(outcome.externalTaskId()).isEqualTo("ark-task-1");
            assertThat(outcome.providerRequestId()).isEqualTo("ark-request-1");
            assertThat(requestBody.get().path("model").asText()).isEqualTo("ep-seedance-fast");
            assertThat(requestBody.get().path("content")).hasSize(2);
            assertThat(requestBody.get().at("/content/0/type").asText()).isEqualTo("text");
            assertThat(requestBody.get().at("/content/1/type").asText()).isEqualTo("image_url");
            assertThat(requestBody.get().path("duration").asInt()).isEqualTo(5);
            assertThat(requestBody.get().path("ratio").asText()).isEqualTo("9:16");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void omitsImageContentForTextToVideoTask() throws Exception {
        AtomicReference<JsonNode> requestBody = new AtomicReference<>();
        HttpServer server = server(exchange -> {
            requestBody.set(objectMapper.readTree(exchange.getRequestBody().readAllBytes()));
            writeJson(exchange, 200, "{" + "\"id\":\"ark-task-2\"}");
        });
        try {
            SeedanceArkVideoProviderAdapter adapter = new SeedanceArkVideoProviderAdapter(secretCodec, objectMapper);

            adapter.submit(config(server), model("ep-seedance-standard"), task(null), "request-2");

            assertThat(requestBody.get().path("content")).hasSize(1);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void mapsRunningAndSuccessfulArkTaskResponses() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        HttpServer server = server(exchange -> {
            if (calls.incrementAndGet() == 1) {
                writeJson(exchange, 200, "{" + "\"id\":\"ark-task-3\",\"status\":\"running\"}");
                return;
            }
            writeJson(exchange, 200, """
                {"id":"ark-task-3","status":"succeeded","content":{"video_url":"https://video.example.com/result.mp4"}}
                """);
        });
        try {
            SeedanceArkVideoProviderAdapter adapter = new SeedanceArkVideoProviderAdapter(secretCodec, objectMapper);

            var running = adapter.poll(config(server), model("ep-seedance-25"), "ark-task-3", "poll-1");
            var completed = adapter.poll(config(server), model("ep-seedance-25"), "ark-task-3", "poll-2");

            assertThat(running.externalTaskId()).isEqualTo("ark-task-3");
            assertThat(completed.response().status()).isEqualTo("SUCCEEDED");
            assertThat(completed.response().videoUrl()).isEqualTo("https://video.example.com/result.mp4");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void mapsFailedArkTaskResponseToTerminalVideoFailure() throws Exception {
        HttpServer server = server(exchange -> writeJson(exchange, 200, """
            {"id":"ark-task-4","status":"failed","error":{"message":"quota exhausted"}}
            """));
        try {
            SeedanceArkVideoProviderAdapter adapter = new SeedanceArkVideoProviderAdapter(secretCodec, objectMapper);

            var outcome = adapter.poll(config(server), model("ep-seedance-fast"), "ark-task-4", "poll-4");

            assertThat(outcome.response().status()).isEqualTo("FAILED");
            assertThat(outcome.response().errorMessage()).isEqualTo("quota exhausted");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void rejectsEndpointPlaceholderBeforeProviderContact() {
        SeedanceArkVideoProviderAdapter adapter = new SeedanceArkVideoProviderAdapter(secretCodec, objectMapper);

        assertThatThrownBy(() -> adapter.submit(config(null), model("__SEEDANCE_2_0_FAST_ENDPOINT_ID__"), task(null), "request-3"))
            .hasMessageContaining("Endpoint ID");
    }

    private AiProviderConfigEntity config(HttpServer server) {
        AiProviderConfigEntity config = new AiProviderConfigEntity();
        config.setBaseUrl(server == null ? "http://127.0.0.1:1/api/v3" : "http://127.0.0.1:%d/api/v3".formatted(server.getAddress().getPort()));
        config.setApiKeyCipher(secretCodec.encrypt("ark-test-key"));
        return config;
    }

    private AiModelEntity model(String endpointId) {
        AiModelEntity model = new AiModelEntity();
        model.setModelCode(endpointId);
        return model;
    }

    private AiVideoTaskEntity task(String firstFrameUrl) {
        AiVideoTaskEntity task = new AiVideoTaskEntity();
        task.prompt = "雨中的人物缓慢回眸";
        task.firstFrameUrl = firstFrameUrl;
        task.durationSeconds = 5;
        task.aspectRatio = "9:16";
        task.resolution = "720p";
        return task;
    }

    private HttpServer server(com.sun.net.httpserver.HttpHandler handler) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v3/contents/generations/tasks", handler);
        server.start();
        return server;
    }

    private void writeJson(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}

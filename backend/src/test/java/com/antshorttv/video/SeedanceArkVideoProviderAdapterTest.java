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
    void submitsFrozenMultimodalTaskUsingArkContentContract() throws Exception {
        AtomicReference<JsonNode> requestBody = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> idempotency = new AtomicReference<>();
        HttpServer server = server(exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            idempotency.set(exchange.getRequestHeaders().getFirst("Idempotency-Key"));
            requestBody.set(objectMapper.readTree(exchange.getRequestBody().readAllBytes()));
            writeJson(exchange, 200, """
                {"id":"ark-task-1","request_id":"ark-request-1","status":"queued"}
                """);
        });
        try {
            SeedanceArkVideoProviderAdapter adapter = new SeedanceArkVideoProviderAdapter(secretCodec, objectMapper);

            var outcome = adapter.submit(config(server), model("ep-seedance-fast"), multimodalTask(), "request-1");

            assertThat(outcome.externalTaskId()).isEqualTo("ark-task-1");
            assertThat(outcome.providerRequestId()).isEqualTo("ark-request-1");
            assertThat(authorization.get()).isEqualTo("Bearer ark-test-key");
            assertThat(idempotency.get()).isEqualTo("request-1");
            assertThat(requestBody.get().path("model").asText()).isEqualTo("ep-seedance-fast");
            assertThat(requestBody.get().path("content")).hasSize(4);
            assertThat(requestBody.get().at("/content/0/type").asText()).isEqualTo("text");
            assertThat(requestBody.get().at("/content/0/text").asText()).isEqualTo("使用图片1，参考视频1和音频1");
            assertThat(requestBody.get().at("/content/1/type").asText()).isEqualTo("image_url");
            assertThat(requestBody.get().at("/content/1/role").asText()).isEqualTo("reference_image");
            assertThat(requestBody.get().at("/content/1/image_url/url").asText()).isEqualTo("https://cdn.example/image.png");
            assertThat(requestBody.get().at("/content/2/type").asText()).isEqualTo("video_url");
            assertThat(requestBody.get().at("/content/2/role").asText()).isEqualTo("reference_video");
            assertThat(requestBody.get().at("/content/2/video_url/url").asText()).isEqualTo("https://cdn.example/video.mp4");
            assertThat(requestBody.get().at("/content/3/type").asText()).isEqualTo("audio_url");
            assertThat(requestBody.get().at("/content/3/role").asText()).isEqualTo("reference_audio");
            assertThat(requestBody.get().at("/content/3/audio_url/url").asText()).isEqualTo("https://cdn.example/audio.mp3");
            assertThat(requestBody.get().path("generate_audio").asBoolean()).isTrue();
            assertThat(requestBody.get().path("duration").asInt()).isEqualTo(11);
            assertThat(requestBody.get().path("ratio").asText()).isEqualTo("16:9");
            assertThat(requestBody.get().path("resolution").asText()).isEqualTo("720p");
            assertThat(requestBody.get().path("watermark").asBoolean()).isFalse();
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
                {"id":"ark-task-3","status":"succeeded","content":{"video_url":"https://video.example.com/result.mp4"},
                 "usage":{"completion_tokens":108900,"total_tokens":108900},
                 "created_at":1779348818,"updated_at":1779348874,"seed":78674,
                 "resolution":"720p","ratio":"16:9","duration":5,"framespersecond":24,
                 "service_tier":"default","execution_expires_after":172800,
                 "generate_audio":true,"draft":false,"priority":0}
                """);
        });
        try {
            SeedanceArkVideoProviderAdapter adapter = new SeedanceArkVideoProviderAdapter(secretCodec, objectMapper);

            var running = adapter.poll(config(server), model("ep-seedance-25"), "ark-task-3", "poll-1");
            var completed = adapter.poll(config(server), model("ep-seedance-25"), "ark-task-3", "poll-2");

            assertThat(running.externalTaskId()).isEqualTo("ark-task-3");
            assertThat(completed.response().status()).isEqualTo("SUCCEEDED");
            assertThat(completed.response().videoUrl()).isEqualTo("https://video.example.com/result.mp4");
            assertThat(completed.response().durationSeconds()).isEqualByComparingTo("5");
            assertThat(completed.response().resolution()).isEqualTo("720p");
            assertThat(completed.response().ratio()).isEqualTo("16:9");
            assertThat(completed.response().seed()).isEqualTo(78674L);
            assertThat(completed.response().fps()).isEqualByComparingTo("24");
            assertThat(completed.response().serviceTier()).isEqualTo("default");
            assertThat(completed.response().generateAudio()).isTrue();
            assertThat(completed.response().completionTokens()).isEqualTo(108900L);
            assertThat(completed.response().totalTokens()).isEqualTo(108900L);
            assertThat(completed.response().metadataJson()).contains("\"executionExpiresAfter\":172800");
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
    void mapsCanceledExpiredAndUnknownArkStatuses() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        HttpServer server = server(exchange -> {
            int call = calls.incrementAndGet();
            if (call == 1) {
                writeJson(exchange, 200, "{\"id\":\"cancel-1\",\"status\":\"canceled\"}");
            } else if (call == 2) {
                writeJson(exchange, 200, "{\"id\":\"expired-1\",\"status\":\"expired\",\"message\":\"result expired\"}");
            } else {
                writeJson(exchange, 200, "{\"id\":\"queued-1\",\"status\":\"queued\"}");
            }
        });
        try {
            SeedanceArkVideoProviderAdapter adapter = new SeedanceArkVideoProviderAdapter(secretCodec, objectMapper);

            var canceled = adapter.poll(config(server), model("ep-seedance-fast"), "cancel-1", "poll-cancel");
            var expired = adapter.poll(config(server), model("ep-seedance-fast"), "expired-1", "poll-expired");
            var queued = adapter.poll(config(server), model("ep-seedance-fast"), "queued-1", "poll-queued");

            assertThat(canceled.response().status()).isEqualTo("CANCELED");
            assertThat(expired.response().status()).isEqualTo("FAILED");
            assertThat(expired.response().errorMessage()).isEqualTo("result expired");
            assertThat(queued.externalTaskId()).isEqualTo("queued-1");
            assertThat(queued.response()).isNull();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void includesTopLevelAndNestedProviderErrorsInDiagnostics() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        HttpServer server = server(exchange -> {
            if (calls.incrementAndGet() == 1) {
                writeJson(exchange, 400, "{\"code\":\"InvalidParameter\",\"message\":\"bad duration\"}");
            } else {
                writeJson(exchange, 429, "{\"error\":{\"code\":\"RateLimit\",\"message\":\"try later\"}}");
            }
        });
        try {
            SeedanceArkVideoProviderAdapter adapter = new SeedanceArkVideoProviderAdapter(secretCodec, objectMapper);

            assertThatThrownBy(() -> adapter.poll(config(server), model("ep-seedance-fast"), "bad-1", "poll-bad"))
                .hasMessageContaining("InvalidParameter")
                .hasMessageContaining("bad duration");
            assertThatThrownBy(() -> adapter.poll(config(server), model("ep-seedance-fast"), "bad-2", "poll-rate"))
                .hasMessageContaining("RateLimit")
                .hasMessageContaining("try later");
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

    @Test
    void cancelsArkTaskWithDeleteAndTreatsMissingTaskAsIdempotent() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<String> method = new AtomicReference<>();
        AtomicReference<String> path = new AtomicReference<>();
        HttpServer server = server(exchange -> {
            method.set(exchange.getRequestMethod());
            path.set(exchange.getRequestURI().getPath());
            if (calls.incrementAndGet() == 1) {
                writeJson(exchange, 200, "{\"Result\":{}}");
            } else {
                writeJson(exchange, 404, "{\"code\":\"TaskNotFound\",\"message\":\"already gone\"}");
            }
        });
        try {
            SeedanceArkVideoProviderAdapter adapter = new SeedanceArkVideoProviderAdapter(secretCodec, objectMapper);

            adapter.cancel(config(server), model("ep-seedance-fast"), "ark-cancel-1", "cancel-1");
            adapter.cancel(config(server), model("ep-seedance-fast"), "ark-cancel-1", "cancel-2");

            assertThat(method.get()).isEqualTo("DELETE");
            assertThat(path.get()).endsWith("/contents/generations/tasks/ark-cancel-1");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void reportsProviderCancellationFailure() throws Exception {
        HttpServer server = server(exchange ->
            writeJson(exchange, 500, "{\"error\":{\"code\":\"CancelFailed\",\"message\":\"provider busy\"}}"));
        try {
            SeedanceArkVideoProviderAdapter adapter = new SeedanceArkVideoProviderAdapter(secretCodec, objectMapper);

            assertThatThrownBy(() -> adapter.cancel(
                config(server), model("ep-seedance-fast"), "ark-cancel-2", "cancel-failed"))
                .hasMessageContaining("CancelFailed")
                .hasMessageContaining("provider busy");
        } finally {
            server.stop(0);
        }
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

    private AiVideoTaskEntity multimodalTask() {
        AiVideoTaskEntity task = new AiVideoTaskEntity();
        task.prompt = "界面提示词";
        task.compiledPrompt = "使用图片1，参考视频1和音频1";
        task.firstFrameUrl = "https://legacy.example/frame.png";
        task.durationSeconds = 11;
        task.aspectRatio = "16:9";
        task.resolution = "720p";
        task.generateAudio = true;
        task.watermark = false;
        task.requestSnapshotJson = """
            {"references":[
              {"reference":{"mediaType":"IMAGE","providerRole":"reference_image"},"providerUrl":"https://cdn.example/image.png"},
              {"reference":{"mediaType":"VIDEO","providerRole":"reference_video"},"providerUrl":"https://cdn.example/video.mp4"},
              {"reference":{"mediaType":"AUDIO","providerRole":"reference_audio"},"providerUrl":"https://cdn.example/audio.mp3"}
            ]}
            """;
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

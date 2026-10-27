package com.antshorttv.video;

import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.*;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class MediaKitSubtitleClientTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test void submitsStandardAndProWithStableIdempotencyAndCorrectRegionMode() throws Exception {
        AtomicReference<JsonNode> body = new AtomicReference<>();
        AtomicReference<String> path = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/tools/", exchange -> {
            assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer mediakit-test-key");
            body.set(json.readTree(exchange.getRequestBody())); path.set(exchange.getRequestURI().getPath());
            byte[] bytes="{\"success\":true,\"task_id\":\"erase-task-1\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();
        });server.start();
        try {
            var client = new MediaKitSubtitleClient(json,"http://127.0.0.1:"+server.getAddress().getPort(),"mediakit-test-key","queue-1");
            assertThat(client.submit("https://ark.example/video.mp4?signature=original",new SubtitleEraseRequest("AUTO","STANDARD",null),"stable-token"))
                .isEqualTo("erase-task-1");
            assertThat(path.get()).endsWith("/erase-video-subtitle");
            assertThat(body.get().path("client_token").asText()).isEqualTo("stable-token");
            assertThat(body.get().path("video_url").asText()).contains("signature=original");
            assertThat(body.get().path("queue_id").asText()).isEqualTo("queue-1");
            assertThat(body.get().has("mode")).isFalse();
            var region=new SubtitleEraseRequest.Region(.1,.15,.9,.4);
            client.submit("https://cdn.example/video.mp4",new SubtitleEraseRequest("REGION","PRO",region),"stable-token-2");
            assertThat(path.get()).endsWith("/erase-video-subtitle-pro");
            assertThat(body.get().path("mode").asText()).isEqualTo("Text");
            assertThat(body.get().path("model_version").asText()).isEqualTo("v5");
            assertThat(body.get().at("/erase_ratio_location/0/top_left_y").asDouble()).isEqualTo(.15);
            client.submit("https://cdn.example/video.mp4",new SubtitleEraseRequest("AUTO","PRO",null),"stable-token-3");
            assertThat(body.get().path("mode").asText()).isEqualTo("Subtitle");
            assertThat(body.get().has("erase_ratio_location")).isFalse();
        } finally {server.stop(0);}
    }

    @Test void pollsCompletedResultAndHonorsNonrenewableExpiry() throws Exception {
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/api/v1/tasks/erase-task-1",exchange->{
            byte[] bytes="""
                {"success":true,"task_id":"erase-task-1","status":"completed","finished_at":1793073600,
                 "expires_at":1793156400,"result":{"video_url":"https://output.example/clean.mp4","duration":4.2,"resolution":"720p"}}
                """.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();
        });server.start();
        try {
            var result=new MediaKitSubtitleClient(json,"http://127.0.0.1:"+server.getAddress().getPort(),"key","").poll("erase-task-1");
            assertThat(result.status()).isEqualTo("completed");
            assertThat(result.videoUrl()).isEqualTo("https://output.example/clean.mp4");
            assertThat(result.expiresAt()).isEqualTo(Instant.ofEpochSecond(1793156400));
            assertThat(result.duration()).isEqualByComparingTo("4.2");
        } finally {server.stop(0);}
    }

    @Test void rejectsMissingCredentialsAndDoesNotExposeSourceUrlsInProviderErrors() throws Exception {
        assertThatThrownBy(()->new MediaKitSubtitleClient(json,"https://mediakit.cn-beijing.volces.com","","").requireConfigured())
            .hasMessageContaining("AI MediaKit");
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/api/v1/tasks/failed",exchange->{
            byte[] bytes="""
                {"success":true,"status":"failed","error":{"code":"DownloadFailed","message":"https://private.example/v.mp4?secret=sensitive"}}
                """.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();
        });server.start();
        try {
            var result=new MediaKitSubtitleClient(json,"http://127.0.0.1:"+server.getAddress().getPort(),"key","").poll("failed");
            assertThat(result.error()).contains("DownloadFailed").doesNotContain("private.example","sensitive");
        } finally {server.stop(0);}
    }
}

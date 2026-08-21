package com.antshorttv.inspiration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.antshorttv.storage.ObjectStorageService;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

@SpringBootTest
class InspirationCreationImportServiceTest {

    @Autowired
    private InspirationCreationImportService importService;

    @Autowired
    private InspirationCreationMapper mapper;

    @MockBean
    private ObjectStorageService objectStorageService;

    @Test
    void importsListAndDetailWithLocalMediaOnly() throws Exception {
        mapper.delete(null);
        when(objectStorageService.enabled()).thenReturn(true);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/creations", exchange -> {
            String base = "http://127.0.0.1:%d".formatted(server.getAddress().getPort());
            byte[] body = """
                {"success":true,"data":[{"id":"842344185310472160","taskId":"task-1","creationType":"IMAGE","taskType":"TEXT_TO_IMAGE","title":"外部标题","authorName":"外部作者","url":"%s/media/source.png","createdAt":"2026-08-21T12:00:00"}]}
                """.formatted(base).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/creations/842344185310472160", exchange -> {
            String base = "http://127.0.0.1:%d".formatted(server.getAddress().getPort());
            byte[] body = """
                {"success":true,"data":{"id":"842344185310472160","url":"%s/media/source.png","coverUrl":"%s/media/cover.png","nested":{"videoUrl":"%s/media/source.mp4"},"prompt":"保留文本"}}
                """.formatted(base, base, base).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/media/source.png", exchange -> {
            byte[] body = "image-bytes".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "image/png");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        try {
            importService.importFrom(new InspirationCreationImportRequest(
                "http://127.0.0.1:%d/creations".formatted(server.getAddress().getPort()),
                "http://127.0.0.1:%d/creations/{id}".formatted(server.getAddress().getPort()),
                Map.of("Authorization", "Bearer test-token")
            ));
        } finally {
            server.stop(0);
        }

        InspirationCreationEntity entity = mapper.selectByExternalId("842344185310472160");
        assertThat(entity.getImportStatus()).isEqualTo(InspirationCreationImportStatus.IMPORTED.name());
        assertThat(entity.getAuthorName()).isEqualTo("管理员");
        assertThat(entity.getStoragePath()).isEqualTo("inspiration/creations/842344185310472160/original.png");
        assertThat(entity.getUrl()).isEqualTo("/api/inspiration-creations/%d/file".formatted(entity.getId()));
        assertThat(entity.getDetailJson()).contains("\"url\":\"/api/inspiration-creations/%d/file\"".formatted(entity.getId()));
        assertThat(entity.getDetailJson()).doesNotContain("127.0.0.1");
        assertThat(entity.getDetailJson()).contains("保留文本");

        ArgumentCaptor<byte[]> bytes = ArgumentCaptor.forClass(byte[].class);
        verify(objectStorageService).upload(
            eq("inspiration/creations/842344185310472160/original.png"),
            bytes.capture(),
            eq("image/png")
        );
        assertThat(bytes.getValue()).containsExactly("image-bytes".getBytes());
    }

    @Test
    void upsertsDuplicateExternalIdAndIsolatesItemFailures() throws Exception {
        mapper.delete(null);
        when(objectStorageService.enabled()).thenReturn(true);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/creations", exchange -> {
            String base = "http://127.0.0.1:%d".formatted(server.getAddress().getPort());
            byte[] body = """
                {"success":true,"data":[
                  {"id":"valid-creation","creationType":"VIDEO","taskType":"IMAGE_TO_VIDEO","title":"第一次标题","url":"%s/media/source.mp4"},
                  {"id":"missing-media","creationType":"IMAGE","taskType":"TEXT_TO_IMAGE","title":"缺失媒体"}
                ]}
                """.formatted(base).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/creations/valid-creation", exchange -> {
            String base = "http://127.0.0.1:%d".formatted(server.getAddress().getPort());
            byte[] body = """
                {"success":true,"data":{"id":"valid-creation","url":"%s/media/source.mp4","title":"详情标题"}}
                """.formatted(base).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/creations/missing-media", exchange -> {
            byte[] body = "{\"success\":true,\"data\":{\"id\":\"missing-media\"}}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/media/source.mp4", exchange -> {
            byte[] body = "video-bytes".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "video/mp4");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        try {
            InspirationCreationImportRequest request = new InspirationCreationImportRequest(
                "http://127.0.0.1:%d/creations".formatted(server.getAddress().getPort()),
                "http://127.0.0.1:%d/creations/{id}".formatted(server.getAddress().getPort()),
                Map.of()
            );
            importService.importFrom(request);
            importService.importFrom(request);
        } finally {
            server.stop(0);
        }

        assertThat(mapper.selectCount(null)).isEqualTo(2);
        InspirationCreationEntity imported = mapper.selectByExternalId("valid-creation");
        InspirationCreationEntity failed = mapper.selectByExternalId("missing-media");
        assertThat(imported.getImportStatus()).isEqualTo(InspirationCreationImportStatus.IMPORTED.name());
        assertThat(imported.getMimeType()).isEqualTo("video/mp4");
        assertThat(imported.getTitle()).isEqualTo("详情标题");
        assertThat(failed.getImportStatus()).isEqualTo(InspirationCreationImportStatus.FAILED.name());
        assertThat(failed.getImportError()).contains("媒体URL不能为空");
    }

    @Test
    void derivesDeterministicObjectStoragePath() {
        assertThat(InspirationCreationMediaStorage.storagePath("abc-123", "https://example.com/file.jpeg", "image/jpeg"))
            .isEqualTo("inspiration/creations/abc-123/original.jpeg");
        assertThat(InspirationCreationMediaStorage.storagePath("abc-123", "https://example.com/file", "video/mp4"))
            .isEqualTo("inspiration/creations/abc-123/original.mp4");
    }

    @Test
    void transfersMediaToObjectStorage() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/source.png", exchange -> {
            byte[] body = "image-bytes".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "image/png");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        ObjectStorageService objectStorageService = mock(ObjectStorageService.class);
        InspirationCreationMediaStorage storage = new InspirationCreationMediaStorage(objectStorageService);

        try {
            InspirationCreationMediaTransfer transfer = storage.transfer(
                "abc-123",
                "http://127.0.0.1:%d/source.png".formatted(server.getAddress().getPort())
            );

            assertThat(transfer.storagePath()).isEqualTo("inspiration/creations/abc-123/original.png");
            assertThat(transfer.mimeType()).isEqualTo("image/png");
            assertThat(transfer.fileSize()).isEqualTo(11L);
        } finally {
            server.stop(0);
        }

        ArgumentCaptor<byte[]> bytes = ArgumentCaptor.forClass(byte[].class);
        verify(objectStorageService).upload(eq("inspiration/creations/abc-123/original.png"), bytes.capture(), eq("image/png"));
        assertThat(bytes.getValue()).containsExactly("image-bytes".getBytes());
    }
}

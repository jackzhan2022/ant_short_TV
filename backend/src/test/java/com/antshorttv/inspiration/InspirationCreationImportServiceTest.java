package com.antshorttv.inspiration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.antshorttv.storage.ObjectStorageService;
import com.antshorttv.storage.ImageDisplayRenditionService;
import com.antshorttv.storage.MediaObjectIdentity;
import com.antshorttv.storage.RegisteredImageDisplay;
import com.antshorttv.storage.StoredObject;
import com.sun.net.httpserver.HttpServer;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
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

    @MockBean
    private ImageDisplayRenditionService imageRenditions;

    @Test
    void importsListAndDetailWithLocalMediaOnly() throws Exception {
        mapper.delete(null);
        byte[] sourcePng = png();
        when(objectStorageService.uploadOriginal(
            any(String.class), any(java.nio.file.Path.class), eq("image/png")
        )).thenAnswer(invocation -> new StoredObject(
            invocation.getArgument(0), sourcePng.length, "image/png", "etag-original",
            "INTELLIGENT_TIERING"
        ));
        when(imageRenditions.registerOriginalAndSubmit(
            any(MediaObjectIdentity.class), any(StoredObject.class), eq(12), eq(8), any(String.class)
        )).thenAnswer(invocation -> {
            StoredObject original = invocation.getArgument(1);
            return new RegisteredImageDisplay(
                original.key(), original.key().replace("/original.png", "/derived/display.png"),
                "SUBMITTED"
            );
        });
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
            byte[] body = sourcePng;
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
        assertThat(entity.getImportStatus()).isEqualTo(InspirationCreationImportStatus.PROCESSING.name());
        assertThat(entity.getAuthorName()).isEqualTo("管理员");
        assertThat(entity.getStoragePath())
            .contains("/" + entity.getId() + "/842344185310472160/original.png");
        assertThat(entity.getUrl()).isEqualTo("/api/inspiration-creations/%d/file".formatted(entity.getId()));
        assertThat(entity.getThumbnailStatus()).isEqualTo("PENDING");
        assertThat(entity.getThumbnailPath())
            .contains("/" + entity.getId() + "/842344185310472160/derived/display.png");
        assertThat(entity.getThumbnailMimeType()).isEqualTo("image/png");
        assertThat(entity.getThumbnailFileSize()).isNull();
        assertThat(entity.getThumbnailUrl()).isEqualTo("/api/inspiration-creations/%d/thumbnail".formatted(entity.getId()));
        assertThat(entity.getDetailJson()).contains("\"url\":\"/api/inspiration-creations/%d/file\"".formatted(entity.getId()));
        assertThat(entity.getDetailJson()).doesNotContain("127.0.0.1");
        assertThat(entity.getDetailJson()).contains("保留文本");

        verify(objectStorageService).uploadOriginal(
            org.mockito.ArgumentMatchers.contains(
                "/" + entity.getId() + "/842344185310472160/original.png"
            ),
            any(java.nio.file.Path.class),
            eq("image/png")
        );
        verify(imageRenditions).registerOriginalAndSubmit(
            eq(new MediaObjectIdentity(
                0L, null, "INSPIRATION_CREATION", entity.getId(), entity.getExternalId()
            )),
            any(StoredObject.class),
            eq(12),
            eq(8),
            eq("inspiration-creation:" + entity.getId())
        );
    }

    @Test
    void upsertsDuplicateExternalIdAndIsolatesItemFailures() throws Exception {
        mapper.delete(null);
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
        assertThat(imported.getThumbnailStatus()).isEqualTo("FAILED");
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
    void transfersVideoToObjectStorageWithoutChangingTheExistingFlow() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/source.mp4", exchange -> {
            byte[] body = "video-bytes".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "video/mp4");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        ObjectStorageService objectStorageService = mock(ObjectStorageService.class);
        AtomicReference<byte[]> uploaded = new AtomicReference<>();
        doAnswer(invocation -> {
            uploaded.set(Files.readAllBytes(invocation.getArgument(1)));
            return null;
        }).when(objectStorageService).uploadFile(
            eq("inspiration/creations/abc-123/original.mp4"),
            any(java.nio.file.Path.class),
            eq("video/mp4")
        );
        InspirationCreationMediaStorage storage = new InspirationCreationMediaStorage(
            objectStorageService,
            new com.antshorttv.storage.ObjectStorageKeyFactory(),
            mock(ImageDisplayRenditionService.class)
        );

        try {
            InspirationCreationMediaTransfer transfer = storage.transfer(
                44L,
                "abc-123",
                "http://127.0.0.1:%d/source.mp4".formatted(server.getAddress().getPort())
            );

            assertThat(transfer.storagePath()).isEqualTo("inspiration/creations/abc-123/original.mp4");
            assertThat(transfer.mimeType()).isEqualTo("video/mp4");
            assertThat(transfer.fileSize()).isEqualTo(11L);
        } finally {
            server.stop(0);
        }

        verify(objectStorageService).uploadFile(
            eq("inspiration/creations/abc-123/original.mp4"),
            any(java.nio.file.Path.class),
            eq("video/mp4")
        );
        assertThat(uploaded.get()).containsExactly("video-bytes".getBytes());
    }

    private byte[] png() throws Exception {
        BufferedImage image = new BufferedImage(12, 8, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }
}

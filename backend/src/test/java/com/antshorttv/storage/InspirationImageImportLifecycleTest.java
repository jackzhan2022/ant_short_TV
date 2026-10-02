package com.antshorttv.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.antshorttv.storage.CloudInfiniteProcessingService;
import com.antshorttv.inspiration.InspirationCreationEntity;
import com.antshorttv.inspiration.InspirationCreationImportRequest;
import com.antshorttv.inspiration.InspirationCreationImportService;
import com.antshorttv.inspiration.InspirationCreationMapper;
import com.antshorttv.storage.MediaObjectIdentity;
import com.antshorttv.storage.MediaObjectRegistry;
import com.antshorttv.storage.ObjectStorageService;
import com.antshorttv.storage.StoredObject;
import com.antshorttv.storage.SubmitImageDisplayJob;
import com.antshorttv.storage.SubmittedMediaProcessingJob;
import com.sun.net.httpserver.HttpServer;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = "inspiration.image-rendition.scheduler.fixed-delay-ms=3600000")
class InspirationImageImportLifecycleTest {
    @Autowired private InspirationCreationImportService imports;
    @Autowired private InspirationCreationMapper mapper;
    @Autowired private MediaObjectRegistry registry;
    @Autowired private JdbcTemplate jdbc;
    @MockBean private ObjectStorageService objects;
    @MockBean private CloudInfiniteProcessingService processing;
    private final AtomicInteger downloads = new AtomicInteger();
    private final AtomicReference<String> title = new AtomicReference<>("First title");
    private final AtomicInteger detailStatus = new AtomicInteger(200);

    @BeforeEach
    void setUp() {
        mapper.delete(null);
        jdbc.update("delete from media_object");
        when(objects.uploadOriginal(any(String.class), any(java.nio.file.Path.class), eq("image/png")))
            .thenAnswer(call -> new StoredObject(call.getArgument(0), 123L, "image/png", "original-etag", "INTELLIGENT_TIERING"));
        when(processing.submitImageDisplay(any())).thenAnswer(call -> {
            SubmitImageDisplayJob command = call.getArgument(0);
            String display = command.inputKey().replace("/original.png", "/derived/display.png");
            registry.registerPendingRendition(command.identity(), "DISPLAY_IMAGE_SLIM", display, "image/png", "INTELLIGENT_TIERING");
            return new SubmittedMediaProcessingJob("job-1", "SUBMITTED", display);
        });
    }

    @Test
    void repeatedImportReusesOriginalAndJobAndKeepsReadyBusinessState() throws Exception {
        HttpServer server = server();
        try {
            InspirationCreationImportRequest request = request(server);
            imports.importFrom(request);
            InspirationCreationEntity first = mapper.selectByExternalId("stable-image");
            jdbc.update("update media_object set status = 'READY', file_size = 45 where rendition_type = 'DISPLAY_IMAGE_SLIM'");
            mapper.markImageRenditionReady(first.getId(), first.getThumbnailPath(), "image/png", 45L);
            title.set("Refreshed title");

            imports.importFrom(request);

            InspirationCreationEntity repeated = mapper.selectByExternalId("stable-image");
            assertThat(repeated.getId()).isEqualTo(first.getId());
            assertThat(repeated.getStoragePath()).isEqualTo(first.getStoragePath());
            assertThat(repeated.getImportStatus()).isEqualTo("IMPORTED");
            assertThat(repeated.getThumbnailStatus()).isEqualTo("READY");
            assertThat(repeated.getThumbnailFileSize()).isEqualTo(45L);
            assertThat(repeated.getTitle()).isEqualTo("Refreshed title");
            assertThat(downloads.get()).isEqualTo(1);
            verify(objects, times(1)).uploadOriginal(any(String.class), any(java.nio.file.Path.class), eq("image/png"));
            verify(processing, times(1)).submitImageDisplay(any());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void explicitReimportRetriesFailedDisplayUsingOriginalFromPreviousMonth() throws Exception {
        InspirationCreationEntity entity = registeredFailedImage();
        MediaObjectIdentity identity = new MediaObjectIdentity(0L, null, "INSPIRATION_CREATION", entity.getId(), entity.getExternalId());
        String original = entity.getStoragePath();
        String display = entity.getThumbnailPath();
        HttpServer server = server();
        try {
            imports.importFrom(request(server));

            InspirationCreationEntity retried = mapper.selectById(entity.getId());
            assertThat(retried.getStoragePath()).isEqualTo(original);
            assertThat(retried.getThumbnailPath()).isEqualTo(display);
            assertThat(retried.getExternalId()).isEqualTo("stable-image");
            assertThat(retried.getImportStatus()).isEqualTo("PROCESSING");
            assertThat(retried.getThumbnailStatus()).isEqualTo("PENDING");
            assertThat(retried.getImportError()).isNull();
            assertThat(retried.getThumbnailError()).isNull();
            assertThat(downloads.get()).isZero();
            verify(objects, times(0)).uploadOriginal(any(String.class), any(java.nio.file.Path.class), eq("image/png"));
            verify(processing, times(1)).submitImageDisplay(org.mockito.ArgumentMatchers.argThat(command -> command.identity().equals(identity) && command.inputKey().equals(original)));
            assertThat(registry.details(identity, "ORIGINAL").objectKey()).isEqualTo(original);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void failedDisplaySubmissionKeepsRegisteredOriginalForAnotherExplicitRetry() throws Exception {
        InspirationCreationEntity entity = registeredFailedImage();
        org.mockito.Mockito.doThrow(new IllegalStateException("Submission unavailable"))
            .when(processing).submitImageDisplay(any());
        HttpServer server = server();
        try {
            imports.importFrom(request(server));

            InspirationCreationEntity failed = mapper.selectById(entity.getId());
            assertThat(failed.getImportStatus()).isEqualTo("FAILED");
            assertThat(failed.getStoragePath()).isEqualTo(entity.getStoragePath());
            assertThat(failed.getThumbnailPath()).isEqualTo(entity.getThumbnailPath());
            assertThat(downloads.get()).isZero();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void readyStorageRecoveryClearsFailedBusinessErrorsOnReimport() throws Exception {
        InspirationCreationEntity entity = registeredFailedImage();
        jdbc.update("update media_object set status = 'READY', file_size = 45 where rendition_type = 'DISPLAY_IMAGE_SLIM'");
        HttpServer server = server();
        try {
            imports.importFrom(request(server));

            InspirationCreationEntity ready = mapper.selectById(entity.getId());
            assertThat(ready.getImportStatus()).isEqualTo("IMPORTED");
            assertThat(ready.getThumbnailStatus()).isEqualTo("READY");
            assertThat(ready.getImportError()).isNull();
            assertThat(ready.getThumbnailError()).isNull();
            assertThat(ready.getStoragePath()).isEqualTo(entity.getStoragePath());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void failedMetadataRefreshKeepsAlreadyReadyImageVisible() throws Exception {
        HttpServer server = server();
        try {
            InspirationCreationImportRequest request = request(server);
            imports.importFrom(request);
            InspirationCreationEntity first = mapper.selectByExternalId("stable-image");
            mapper.markImageRenditionReady(first.getId(), first.getThumbnailPath(), "image/png", 45L);
            detailStatus.set(503);

            imports.importFrom(request);

            InspirationCreationEntity existing = mapper.selectById(first.getId());
            assertThat(existing.getImportStatus()).isEqualTo("IMPORTED");
            assertThat(existing.getThumbnailStatus()).isEqualTo("READY");
            assertThat(existing.getStoragePath()).isEqualTo(first.getStoragePath());
            assertThat(existing.getThumbnailFileSize()).isEqualTo(45L);
        } finally {
            server.stop(0);
        }
    }

    private InspirationCreationEntity registeredFailedImage() {
        InspirationCreationEntity entity = failedEntity();
        MediaObjectIdentity identity = new MediaObjectIdentity(0L, null, "INSPIRATION_CREATION", entity.getId(), entity.getExternalId());
        String original = "materials/0/inspiration_creation/202609/" + entity.getId() + "/stable-image/original.png";
        String display = original.replace("/original.png", "/derived/display.png");
        registry.registerOriginal(identity, new StoredObject(original, 123L, "image/png", "original-etag", "INTELLIGENT_TIERING"), null, 12, 8);
        registry.registerPendingRendition(identity, "DISPLAY_IMAGE_SLIM", display, "image/png", "INTELLIGENT_TIERING");
        jdbc.update("update media_object set status = 'FAILED' where rendition_type = 'DISPLAY_IMAGE_SLIM'");
        entity.setStoragePath(original);
        entity.setThumbnailPath(display);
        mapper.updateById(entity);
        return entity;
    }

    private InspirationCreationEntity failedEntity() {
        InspirationCreationEntity entity = new InspirationCreationEntity();
        entity.setExternalId("stable-image");
        entity.setCreationType("IMAGE");
        entity.setTaskType("TEXT_TO_IMAGE");
        entity.setTitle("Failed image");
        entity.setAuthorName("Admin");
        entity.setUrl("");
        entity.setStoragePath("");
        entity.setMimeType("image/png");
        entity.setFileSize(123L);
        entity.setImportStatus("FAILED");
        entity.setImportError("Prior processing failure");
        entity.setThumbnailStatus("FAILED");
        entity.setThumbnailError("Prior processing failure");
        entity.setPublishStatus("PUBLISHED");
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        mapper.insert(entity);
        return entity;
    }

    private HttpServer server() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/list", exchange -> {
            byte[] body = "[{\"id\":\"stable-image\",\"creationType\":\"IMAGE\"}]".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/detail", exchange -> {
            byte[] body = ("{\"title\":\"" + title.get() + "\",\"url\":\"http://127.0.0.1:" + server.getAddress().getPort() + "/source.png\"}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(detailStatus.get(), body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        byte[] image = png();
        server.createContext("/source.png", exchange -> {
            downloads.incrementAndGet();
            exchange.getResponseHeaders().add("Content-Type", "image/png");
            exchange.sendResponseHeaders(200, image.length);
            exchange.getResponseBody().write(image);
            exchange.close();
        });
        server.start();
        return server;
    }

    private InspirationCreationImportRequest request(HttpServer server) {
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        return new InspirationCreationImportRequest(base + "/list", base + "/detail?id={id}", Map.of());
    }

    private byte[] png() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(12, 8, BufferedImage.TYPE_INT_RGB), "png", output);
        return output.toByteArray();
    }
}

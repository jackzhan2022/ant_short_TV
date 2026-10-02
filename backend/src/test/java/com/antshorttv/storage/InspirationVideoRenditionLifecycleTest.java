package com.antshorttv.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.antshorttv.inspiration.InspirationCreationEntity;
import com.antshorttv.inspiration.InspirationCreationImportRequest;
import com.antshorttv.inspiration.InspirationCreationImportService;
import com.antshorttv.inspiration.InspirationCreationMapper;
import com.antshorttv.inspiration.InspirationImageRenditionReconciler;
import com.antshorttv.inspiration.InspirationManagementService;
import com.antshorttv.inspiration.InspirationVideoTestRequests;
import com.qcloud.cos.COS;
import com.sun.net.httpserver.HttpServer;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = "inspiration.image-rendition.scheduler.fixed-delay-ms=3600000")
class InspirationVideoRenditionLifecycleTest {
    private static final String SOURCE = "materials/11/uploads/202610/video-session/v1/original.mp4";
    @Autowired private InspirationManagementService management;
    @Autowired private InspirationCreationImportService imports;
    @Autowired private InspirationCreationMapper mapper;
    @Autowired private MediaObjectRegistry registry;
    @Autowired private JdbcTemplate jdbc;
    @MockBean private COS cos;
    @MockBean private com.qcloud.cos.transfer.TransferManager transfers;
    @MockBean private ObjectStorageService objects;
    @MockBean private MediaUploadSessionService uploads;
    @MockBean private CloudInfiniteProcessingService processing;
    private byte[] jpeg;

    @BeforeEach
    void setUp() throws Exception {
        mapper.delete(null);
        jdbc.update("delete from media_object");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(12, 8, BufferedImage.TYPE_INT_RGB), "jpg", output);
        jpeg = output.toByteArray();
        when(uploads.requireCompleted(7L, 11L, "video-session"))
            .thenReturn(new VerifiedMediaUpload("video-session", SOURCE, "video/mp4", 123L, "source-etag"));
        when(objects.resource(SOURCE)).thenReturn(new ByteArrayResource(new byte[123]));
        when(objects.metadata(SOURCE)).thenReturn(new StoredObject(SOURCE, 123L, "video/mp4", "source-etag", "INTELLIGENT_TIERING"));
        when(objects.copyCompletedUploadOriginal(any(), any())).thenAnswer(call ->
            new StoredObject(call.getArgument(1), 123L, "video/mp4", "video-etag", "INTELLIGENT_TIERING"));
        when(cos.getSnapshot(any())).thenAnswer(call -> new ByteArrayInputStream(jpeg));
        when(objects.uploadOriginal(any(String.class), any(byte[].class), eq("image/jpeg")))
            .thenAnswer(call -> new StoredObject(call.getArgument(0), jpeg.length, "image/jpeg", "cover-etag", "INTELLIGENT_TIERING"));
        when(objects.uploadOriginal(any(String.class), any(java.nio.file.Path.class), eq("video/mp4")))
            .thenAnswer(call -> new StoredObject(call.getArgument(0), 123L, "video/mp4", "video-etag", "INTELLIGENT_TIERING"));
        when(processing.submitImageDisplay(any())).thenAnswer(call -> {
            SubmitImageDisplayJob command = call.getArgument(0);
            InspirationCreationEntity active = mapper.selectById(command.identity().assetId());
            assertThat(active.getStoragePath()).endsWith("/original.mp4");
            assertThat(active.getImportStatus()).isIn("PROCESSING", "FAILED");
            assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            String display = command.inputKey().replace("/original.jpg", "/derived/display.jpg");
            registry.registerPendingRendition(command.identity(), "DISPLAY_IMAGE_SLIM", display, "image/jpeg", "INTELLIGENT_TIERING");
            return new SubmittedMediaProcessingJob("video-cover-job", "SUBMITTED", display);
        });
    }

    @Test
    void videoRemainsHiddenUntilVerifiedCoverMetadataIsReady() {
        long id = InspirationVideoTestRequests.create(management);
        InspirationCreationEntity pending = mapper.selectById(id);
        assertThat(pending.getCreationType()).isEqualTo("VIDEO");
        assertThat(pending.getImportStatus()).isEqualTo("PROCESSING");
        assertThat(pending.getThumbnailStatus()).isEqualTo("PENDING");
        assertThat(pending.getThumbnailFileSize()).isNull();
        assertThat(pending.getStoragePath()).startsWith("materials/0/inspiration_creation/").endsWith("/original.mp4");
        assertThat(mapper.selectImportedById(id)).isNull();
        MediaObjectIdentity identity = identity(pending);
        assertThat(registry.details(identity, "ORIGINAL").objectKey()).endsWith("/cover/original.jpg");
        jdbc.update("update media_object set status = 'READY', file_size = 45, mime_type = 'image/jpeg' where rendition_type = 'DISPLAY_IMAGE_SLIM'");

        assertThat(new InspirationImageRenditionReconciler(mapper,
            new ImageDisplayRenditionService(registry, processing)).reconcilePending(20)).isEqualTo(1);

        InspirationCreationEntity ready = mapper.selectImportedById(id);
        assertThat(ready).isNotNull();
        assertThat(ready.getThumbnailFileSize()).isEqualTo(45L);
        assertThat(ready.getThumbnailMimeType()).isEqualTo("image/jpeg");
        assertThat(ready.getStoragePath()).isEqualTo(pending.getStoragePath());
        verify(objects).copyCompletedUploadOriginal(any(), eq(ready.getStoragePath()));
        verify(objects, org.mockito.Mockito.never()).delete(SOURCE);
    }

    @Test
    void failedCoverStaysHiddenAndExplicitRetryReusesBothAcceptedOriginals() {
        long id = InspirationVideoTestRequests.create(management);
        InspirationCreationEntity pending = mapper.selectById(id);
        jdbc.update("update media_object set status = 'FAILED', error_message = 'Cover failed' where rendition_type = 'DISPLAY_IMAGE_SLIM'");
        ImageDisplayRenditionService renditions = new ImageDisplayRenditionService(registry, processing);
        new InspirationImageRenditionReconciler(mapper, renditions).reconcilePending(20);
        assertThat(mapper.selectById(id).getImportStatus()).isEqualTo("FAILED");
        assertThat(mapper.selectImportedById(id)).isNull();

        InspirationVideoTestRequests.publish(management, id);

        InspirationCreationEntity retried = mapper.selectById(id);
        assertThat(retried.getImportStatus()).isEqualTo("PROCESSING");
        assertThat(retried.getThumbnailStatus()).isEqualTo("PENDING");
        assertThat(retried.getStoragePath()).isEqualTo(pending.getStoragePath());
        verify(cos, times(1)).getSnapshot(any());
        verify(objects, times(1)).uploadOriginal(any(String.class), any(byte[].class), eq("image/jpeg"));
        verify(objects, times(1)).copyCompletedUploadOriginal(any(), any());
        verify(processing, times(2)).submitImageDisplay(any());
    }

    @Test
    void snapshotFailurePreservesVideoAndPublishRetryUsesIt() {
        when(cos.getSnapshot(any())).thenThrow(new IllegalStateException("Snapshot unavailable"));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> InspirationVideoTestRequests.create(management))
            .hasMessageContaining("Snapshot unavailable");
        InspirationCreationEntity failed = mapper.selectList(null).get(0);
        assertThat(failed.getCreationType()).isEqualTo("VIDEO");
        assertThat(failed.getImportStatus()).isEqualTo("FAILED");
        assertThat(failed.getStoragePath()).endsWith("/original.mp4");
        assertThat(failed.getFileSize()).isEqualTo(123L);
        assertThat(mapper.selectImportedById(failed.getId())).isNull();
        org.mockito.Mockito.doAnswer(call -> new ByteArrayInputStream(jpeg)).when(cos).getSnapshot(any());

        InspirationVideoTestRequests.publish(management, failed.getId());

        InspirationCreationEntity retried = mapper.selectById(failed.getId());
        assertThat(retried.getImportStatus()).isEqualTo("PROCESSING");
        assertThat(retried.getThumbnailStatus()).isEqualTo("PENDING");
        assertThat(retried.getStoragePath()).isEqualTo(failed.getStoragePath());
        verify(objects, times(1)).copyCompletedUploadOriginal(any(), any());
        verify(objects, org.mockito.Mockito.never()).delete(any());
    }

    @Test
    void repeatedVideoImportReusesVideoPathAndCoverOriginalWithoutOverwriting() throws Exception {
        AtomicInteger downloads = new AtomicInteger();
        java.util.concurrent.atomic.AtomicReference<String> sourceType = new java.util.concurrent.atomic.AtomicReference<>("VIDEO");
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/list", exchange -> {
            byte[] body = ("[{\"id\":\"stable-video\",\"creationType\":\"" + sourceType.get() + "\"}]").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/detail", exchange -> {
            byte[] body = ("{\"title\":\"Video\",\"url\":\"http://127.0.0.1:" + server.getAddress().getPort() + "/source.mp4\"}")
                .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/source.mp4", exchange -> {
            downloads.incrementAndGet();
            exchange.getResponseHeaders().add("Content-Type", "video/mp4");
            exchange.sendResponseHeaders(200, 123L);
            exchange.getResponseBody().write(new byte[123]);
            exchange.close();
        });
        server.start();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            InspirationCreationImportRequest request = new InspirationCreationImportRequest(base + "/list", base + "/detail?id={id}", Map.of());
            imports.importFrom(request);
            InspirationCreationEntity first = mapper.selectByExternalId("stable-video");
            assertThat(first.getImportStatus()).isEqualTo("PROCESSING");
            when(objects.metadata(first.getStoragePath())).thenReturn(new StoredObject(first.getStoragePath(), 123L,
                "video/mp4", "video-etag", "INTELLIGENT_TIERING"));
            jdbc.update("update media_object set status = 'READY', file_size = 45 where rendition_type = 'DISPLAY_IMAGE_SLIM'");
            new InspirationImageRenditionReconciler(mapper, new ImageDisplayRenditionService(registry, processing)).reconcilePending(20);
            sourceType.set("IMAGE");

            imports.importFrom(request);

            InspirationCreationEntity repeated = mapper.selectByExternalId("stable-video");
            assertThat(repeated.getStoragePath()).isEqualTo(first.getStoragePath()).endsWith("/original.mp4");
            assertThat(repeated.getMimeType()).isEqualTo("video/mp4");
            assertThat(repeated.getCreationType()).isEqualTo("VIDEO");
            assertThat(repeated.getImportStatus()).isEqualTo("IMPORTED");
            assertThat(repeated.getThumbnailStatus()).isEqualTo("READY");
            assertThat(repeated.getThumbnailFileSize()).isEqualTo(45L);
            assertThat(repeated.getThumbnailPath()).isEqualTo(first.getThumbnailPath());
            assertThat(downloads.get()).isEqualTo(1);
            verify(objects, times(1)).uploadOriginal(any(String.class), any(java.nio.file.Path.class), eq("video/mp4"));
            verify(objects, times(1)).uploadOriginal(any(String.class), any(byte[].class), eq("image/jpeg"));
            verify(cos, times(1)).getSnapshot(any());
            verify(processing, times(1)).submitImageDisplay(any());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void deletingVideoRetiresCoverGraphBeforeCleaningObjects() {
        long id = InspirationVideoTestRequests.create(management);
        InspirationCreationEntity video = mapper.selectById(id);

        management.delete(id);

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(processing, objects);
        order.verify(processing).retireImage(identity(video));
        order.verify(objects).delete(video.getStoragePath());
        order.verify(objects).delete(video.getThumbnailPath());
        order.verify(objects).delete(video.getStoragePath().replace("/original.mp4", "/cover/original.jpg"));
        assertThat(mapper.selectImportedById(id)).isNull();
    }

    @Test
    void fastReadyCallbackCannotPublishBeforeCoverPathIsAttached() {
        org.mockito.Mockito.doAnswer(call -> {
            SubmitImageDisplayJob command = call.getArgument(0);
            String display = command.inputKey().replace("/original.jpg", "/derived/display.jpg");
            registry.registerPendingRendition(command.identity(), "DISPLAY_IMAGE_SLIM", display, "image/jpeg", "INTELLIGENT_TIERING");
            jdbc.update("update media_object set status = 'READY', file_size = 45 where rendition_type = 'DISPLAY_IMAGE_SLIM'");
            InspirationImageRenditionReconciler reconciler = new InspirationImageRenditionReconciler(mapper,
                new ImageDisplayRenditionService(registry, processing));
            assertThat(reconciler.reconcilePending(20)).isZero();
            assertThat(mapper.selectImportedById(command.identity().assetId())).isNull();
            return new SubmittedMediaProcessingJob("fast-cover-job", "READY", display);
        }).when(processing).submitImageDisplay(any());

        long id = InspirationVideoTestRequests.create(management);

        InspirationCreationEntity pending = mapper.selectById(id);
        assertThat(pending.getImportStatus()).isEqualTo("PROCESSING");
        assertThat(pending.getThumbnailPath()).endsWith("/cover/derived/display.jpg");
        assertThat(new InspirationImageRenditionReconciler(mapper,
            new ImageDisplayRenditionService(registry, processing)).reconcilePending(20)).isEqualTo(1);
        assertThat(mapper.selectImportedById(id).getThumbnailFileSize()).isEqualTo(45L);
    }

    @Test
    void failedInitialSubmissionRetainsRegisteredCoverForExplicitRetry() {
        org.mockito.Mockito.doThrow(new IllegalStateException("Submission unavailable"))
            .when(processing).submitImageDisplay(any());
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> InspirationVideoTestRequests.create(management))
            .hasMessageContaining("Submission unavailable");
        InspirationCreationEntity failed = mapper.selectList(null).get(0);
        String cover = registry.details(identity(failed), "ORIGINAL").objectKey();
        assertThat(failed.getStoragePath()).endsWith("/original.mp4");
        assertThat(failed.getImportStatus()).isEqualTo("FAILED");
        org.mockito.Mockito.doAnswer(call -> {
            SubmitImageDisplayJob command = call.getArgument(0);
            assertThat(command.inputKey()).isEqualTo(cover);
            String display = cover.replace("/original.jpg", "/derived/display.jpg");
            registry.registerPendingRendition(command.identity(), "DISPLAY_IMAGE_SLIM", display, "image/jpeg", "INTELLIGENT_TIERING");
            return new SubmittedMediaProcessingJob("retry-cover-job", "SUBMITTED", display);
        }).when(processing).submitImageDisplay(any());

        InspirationVideoTestRequests.publish(management, failed.getId());

        assertThat(mapper.selectById(failed.getId()).getImportStatus()).isEqualTo("PROCESSING");
        verify(cos, times(1)).getSnapshot(any());
        verify(objects, times(1)).uploadOriginal(any(String.class), any(byte[].class), eq("image/jpeg"));
        verify(objects, times(1)).copyCompletedUploadOriginal(any(), any());
    }

    private MediaObjectIdentity identity(InspirationCreationEntity entity) {
        return new MediaObjectIdentity(0L, null, "INSPIRATION_CREATION", entity.getId(), entity.getExternalId());
    }
}

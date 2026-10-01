package com.antshorttv.style;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.storage.ImageDisplayRenditionService;
import com.antshorttv.storage.MediaObjectIdentity;
import com.antshorttv.storage.MediaObjectRegistry;
import com.antshorttv.storage.ObjectStorageKeyFactory;
import com.antshorttv.storage.StoredObject;
import com.qcloud.cos.COS;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.model.PutObjectResult;
import com.qcloud.cos.model.ciModel.job.MediaJobObject;
import com.qcloud.cos.model.ciModel.job.MediaJobResponse;
import com.qcloud.cos.transfer.TransferManager;
import com.sun.net.httpserver.HttpServer;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = {
    "object-storage.ci-callback-url=https://app.example/api/media-processing/callbacks/tencent-ci",
    "inspiration.image-rendition.scheduler.fixed-delay-ms=3600000"
})
class StyleLibraryImageStorageTest {
    @Autowired private StyleLibraryImageStorage storage;
    @Autowired private StyleLibraryMapper mapper;
    @Autowired private MediaObjectRegistry registry;
    @Autowired private ImageDisplayRenditionService renditions;
    @Autowired private ObjectStorageKeyFactory keys;
    @Autowired private JdbcTemplate jdbc;
    @MockBean private COS cos;
    @MockBean private TransferManager transfers;
    private StyleLibraryEntity style;
    private final AtomicInteger downloads = new AtomicInteger();
    private final Map<String, ObjectMetadata> objects = new HashMap<>();
    private final Map<String, byte[]> bodies = new HashMap<>();

    @BeforeEach
    void setUp() throws Exception {
        jdbc.update("delete from media_processing_job");
        jdbc.update("delete from media_object");
        jdbc.update("delete from style_library where external_id = 'async-style'");
        style = new StyleLibraryEntity();
        style.setExternalId("async-style");
        style.setName("Async style");
        style.setCategory("Test");
        style.setDescription("");
        style.setSourceImageUrl("");
        style.setStoragePath("");
        style.setImageUrl("/api/style-library/images/async-style");
        style.setIsPublic(true);
        style.setSortOrder(140);
        style.setCreatedAt(LocalDateTime.now());
        style.setUpdatedAt(LocalDateTime.now());
        mapper.insert(style);
        downloads.set(0);
        objects.clear();
        bodies.clear();
        when(cos.putObject(any(PutObjectRequest.class))).thenAnswer(call -> {
            PutObjectRequest request = call.getArgument(0);
            byte[] bytes = request.getInputStream().readAllBytes();
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(bytes.length);
            metadata.setContentType(request.getMetadata().getContentType());
            metadata.setETag("head-etag");
            metadata.setHeader("x-cos-storage-class", "INTELLIGENT_TIERING");
            objects.put(request.getKey(), metadata);
            bodies.put(request.getKey(), bytes);
            PutObjectResult result = new PutObjectResult();
            result.setETag("put-etag");
            return result;
        });
        when(cos.getObjectMetadata(anyString(), anyString())).thenAnswer(call -> {
            ObjectMetadata found = objects.get(call.getArgument(1));
            return found == null ? new ObjectMetadata() : found;
        });
        when(cos.createPicProcessJob(any())).thenAnswer(call -> {
            MediaJobObject detail = new MediaJobObject();
            detail.setJobId("style-job-1");
            detail.setState("Submitted");
            MediaJobResponse response = mock(MediaJobResponse.class);
            when(response.getJobsDetail()).thenReturn(detail);
            return response;
        });
    }

    @Test
    void uploadsVerifiedOriginalAndPersistsOnePendingDisplayJob() throws Exception {
        byte[] bytes = image("png");
        withSource(bytes, url -> {
            StoredStyleImage stored = storage.transfer(style.getExternalId(), url);
            StyleLibraryEntity persisted = mapper.selectById(style.getId());
            String original = original("png");
            String display = original.replace("/original.png", "/derived/display.png");

            assertThat(stored.storagePath()).isEqualTo(display);
            assertThat(stored.fileSize()).isEqualTo(bytes.length);
            assertThat(persisted.getStoragePath()).isEqualTo(display);
            assertThat(persisted.getImageWidth()).isEqualTo(12);
            assertThat(persisted.getImageHeight()).isEqualTo(8);
            assertThat(bodies).containsOnlyKeys(original);
            assertThat(bodies.get(original)).isEqualTo(bytes);
            assertThat(registry.details(identity(), "ORIGINAL").status()).isEqualTo("READY");
            assertThat(registry.details(identity(), "ORIGINAL").mimeType()).isEqualTo("image/png");
            assertThat(registry.details(identity(), "DISPLAY_IMAGE_SLIM").status()).isEqualTo("PENDING");
            assertThat(registry.details(identity(), "DISPLAY_IMAGE_SLIM").fileSize()).isZero();
            assertThat(jdbc.queryForObject("select width from media_object where rendition_type = 'DISPLAY_IMAGE_SLIM'", Integer.class)).isNull();
            assertThat(jdbc.queryForObject("select count(*) from media_processing_job", Integer.class)).isEqualTo(1);
            ArgumentCaptor<PutObjectRequest> upload = ArgumentCaptor.forClass(PutObjectRequest.class);
            verify(cos).putObject(upload.capture());
            assertThat(upload.getValue().getPicOperations()).isNull();
            verify(cos).getObjectMetadata("antv-1418200553", original);
            verify(cos).createPicProcessJob(any());
            assertUnavailable(persisted);
        });
    }

    @Test
    void repeatedTransferReusesOriginalAndPersistentJobWithoutDownloadingAgain() throws Exception {
        withSource(image("png"), url -> {
            StoredStyleImage first = storage.transfer(style.getExternalId(), url);
            StoredStyleImage second = storage.transfer(style.getExternalId(), url);

            assertThat(second).isEqualTo(first);
            assertThat(downloads.get()).isEqualTo(1);
            verify(cos, times(1)).putObject(any(PutObjectRequest.class));
            verify(cos, times(1)).createPicProcessJob(any());
            assertThat(jdbc.queryForObject("select count(*) from media_processing_job", Integer.class)).isEqualTo(1);
        });
    }

    @Test
    void failedDisplayRetriesUsingPersistedOriginalFromPreviousMonth() {
        String original = keys.tenantOriginal(0L, "style_library", style.getId(), style.getExternalId(), LocalDate.of(2026, 9, 1), "png");
        renditions.registerOriginalAndSubmit(identity(), new StoredObject(original, 123L, "image/png", "etag", "INTELLIGENT_TIERING"), 12, 8, "style-" + style.getId());
        jdbc.update("update media_object set status = 'FAILED' where rendition_type = 'DISPLAY_IMAGE_SLIM'");
        jdbc.update("update media_processing_job set status = 'FAILED'");
        style.setStoragePath(original.replace("/original.png", "/derived/display.png"));
        style.setImageWidth(12);
        style.setImageHeight(8);
        mapper.updateById(style);
        assertUnavailable(style);

        StoredStyleImage retried = storage.transfer(style.getExternalId(), "http://127.0.0.1:1/unreachable.jpg");

        assertThat(retried.storagePath()).isEqualTo(style.getStoragePath());
        assertThat(retried.fileSize()).isEqualTo(123L);
        assertThat(registry.details(identity(), "ORIGINAL").objectKey()).isEqualTo(original);
        assertThat(registry.details(identity(), "DISPLAY_IMAGE_SLIM").status()).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("select attempt_no from media_processing_job", Integer.class)).isEqualTo(2);
        verify(cos, times(0)).putObject(any(PutObjectRequest.class));
        verify(cos, times(2)).createPicProcessJob(any());
    }

    @Test
    void readyDisplayDeliversExactRegisteredUrlAndRejectsMismatchedBusinessKey() throws Exception {
        withSource(image("png"), url -> {
            storage.transfer(style.getExternalId(), url);
            StyleLibraryEntity persisted = mapper.selectById(style.getId());
            completeDisplay();

            assertThat(storage.deliveryUrl(persisted)).startsWith("https://antvcdn.aixmax.cn/" + persisted.getStoragePath() + "?");
            storage.transfer(style.getExternalId(), url);
            assertThat(downloads.get()).isEqualTo(1);
            verify(cos, times(1)).createPicProcessJob(any());
            persisted.setStoragePath(original("png"));
            assertUnavailable(persisted);
        });
    }

    @Test
    void readyDisplayWithMissingResultMetadataRemainsUnavailable() throws Exception {
        withSource(image("png"), url -> {
            storage.transfer(style.getExternalId(), url);
            jdbc.update("update media_object set status = 'READY' where rendition_type = 'DISPLAY_IMAGE_SLIM'");
            assertUnavailable(mapper.selectById(style.getId()));
        });
    }

    @ParameterizedTest
    @MethodSource("formats")
    void detectsSupportedFormatAndDimensionsFromBytesDespiteMisleadingUrl(String format, String extension, String mimeType, int width, int height) throws Exception {
        byte[] bytes = format.equals("webp")
            ? Base64.getDecoder().decode("UklGRiIAAABXRUJQVlA4IBYAAAAwAQCdASoBAAEADsD+JaQAA3AAAAAA")
            : image(format);
        withSource(bytes, url -> {
            storage.transfer(style.getExternalId(), url);
            assertThat(registry.details(identity(), "ORIGINAL").objectKey()).isEqualTo(original(extension));
            assertThat(registry.details(identity(), "ORIGINAL").mimeType()).isEqualTo(mimeType);
            StyleLibraryEntity persisted = mapper.selectById(style.getId());
            assertThat(persisted.getImageWidth()).isEqualTo(width);
            assertThat(persisted.getImageHeight()).isEqualTo(height);
        });
    }

    static Stream<Arguments> formats() {
        return Stream.of(
            Arguments.of("jpeg", "jpg", "image/jpeg", 12, 8),
            Arguments.of("png", "png", "image/png", 12, 8),
            Arguments.of("gif", "gif", "image/gif", 12, 8),
            Arguments.of("webp", "webp", "image/webp", 1, 1)
        );
    }

    @Test
    void rejectsUnknownStylesAndInvalidImageBytesBeforeUpload() throws Exception {
        assertThatThrownBy(() -> storage.transfer("unknown-style", "http://127.0.0.1:1/unreachable.jpg"))
            .isInstanceOfSatisfying(BusinessException.class, error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));
        withSource(new byte[] {1, 2, 3}, url -> assertThatThrownBy(() -> storage.transfer(style.getExternalId(), url))
            .isInstanceOfSatisfying(BusinessException.class, error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR)));
        verify(cos, times(0)).putObject(any(PutObjectRequest.class));
    }

    @Test
    void rejectsTruncatedWebpImagePayloadBeforeUpload() throws Exception {
        byte[] bytes = Arrays.copyOf(Base64.getDecoder().decode("UklGRiIAAABXRUJQVlA4IBYAAAAwAQCdASoBAAEADsD+JaQAA3AAAAAA"), 30);
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(4, 22);
        buffer.putInt(16, 10);
        withSource(bytes, url -> assertThatThrownBy(() -> storage.transfer(style.getExternalId(), url))
            .isInstanceOfSatisfying(BusinessException.class, error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR)));
        verify(cos, times(0)).putObject(any(PutObjectRequest.class));
    }

    @Test
    void failsTransferWhenStyleDisappearsBeforePendingDisplayWrite() throws Exception {
        when(cos.getObjectMetadata(anyString(), anyString())).thenAnswer(call -> {
            mapper.deleteById(style.getId());
            return objects.get(call.getArgument(1));
        });
        withSource(image("png"), url -> assertThatThrownBy(() -> storage.transfer(style.getExternalId(), url))
            .isInstanceOfSatisfying(BusinessException.class, error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR)));
        verify(cos, times(0)).createPicProcessJob(any());
    }

    @Test
    void historicalStyleDeliveryPreservesPathAndExcludesTransfer() throws Exception {
        StyleLibraryEntity historical = mapper.selectById(1L);
        String path = historical.getStoragePath();
        assertThat(path).isEqualTo("style-library/public/864621266010645040/cover-compressed.jpg");
        assertThat(storage.deliveryUrl(historical)).startsWith("https://antvcdn.aixmax.cn/" + path + "?");
        withSource(image("png"), url -> assertThatThrownBy(() -> storage.transfer(historical.getExternalId(), url))
            .isInstanceOf(BusinessException.class));
        assertThat(downloads.get()).isZero();
        assertThat(mapper.selectById(historical.getId()).getStoragePath()).isEqualTo(path);
        assertThat(jdbc.queryForObject("select count(*) from media_object", Integer.class)).isZero();
        verify(cos, times(0)).createPicProcessJob(any());
    }

    private void completeDisplay() {
        jdbc.update("update media_object set status = 'READY', file_size = 45, width = 12, height = 8, etag = 'display-etag' where rendition_type = 'DISPLAY_IMAGE_SLIM'");
        jdbc.update("update media_processing_job set status = 'SUCCEEDED'");
    }

    private void assertUnavailable(StyleLibraryEntity entity) {
        assertThatThrownBy(() -> storage.deliveryUrl(entity)).isInstanceOfSatisfying(BusinessException.class,
            error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    private MediaObjectIdentity identity() {
        return new MediaObjectIdentity(0L, null, "STYLE_LIBRARY", style.getId(), style.getExternalId());
    }

    private String original(String extension) {
        return keys.tenantOriginal(0L, "style_library", style.getId(), style.getExternalId(), LocalDate.now(), extension);
    }

    private byte[] image(String format) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(12, 8, BufferedImage.TYPE_INT_RGB), format, bytes);
        return bytes.toByteArray();
    }

    private void withSource(byte[] bytes, SourceAction action) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/misleading.jpg", exchange -> {
            downloads.incrementAndGet();
            exchange.getResponseHeaders().add("Content-Type", "image/jpeg");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            action.run("http://127.0.0.1:" + server.getAddress().getPort() + "/misleading.jpg");
        } finally {
            server.stop(0);
        }
    }

    private interface SourceAction {
        void run(String url) throws Exception;
    }
}

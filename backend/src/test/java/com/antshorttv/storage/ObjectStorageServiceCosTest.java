package com.antshorttv.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.qcloud.cos.COS;
import com.antshorttv.common.BusinessException;
import com.qcloud.cos.model.COSObject;
import com.qcloud.cos.model.CopyObjectRequest;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.model.PutObjectResult;
import com.qcloud.cos.model.UploadResult;
import com.qcloud.cos.transfer.TransferManager;
import com.qcloud.cos.transfer.Copy;
import com.qcloud.cos.transfer.Upload;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ObjectStorageServiceCosTest {

    @Test
    void streamsObjectWithMetadataAndIntelligentTiering() throws Exception {
        COS cos = mock(COS.class);
        PutObjectResult result = new PutObjectResult();
        result.setETag("etag-1");
        when(cos.putObject(any(PutObjectRequest.class))).thenReturn(result);
        ObjectStorageService service = service(cos);

        StoredObject stored = service.upload(
            "materials/11/22/images/202609/33/v1/original.png",
            new ByteArrayInputStream(new byte[] {1, 2, 3}),
            3,
            "image/png"
        );

        ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(cos).putObject(request.capture());
        verify(cos, org.mockito.Mockito.never()).getObjectMetadata(any(), any());
        assertThat(request.getValue().getKey()).isEqualTo("materials/11/22/images/202609/33/v1/original.png");
        assertThat(request.getValue().getMetadata().getContentLength()).isEqualTo(3);
        assertThat(request.getValue().getMetadata().getContentType()).isEqualTo("image/png");
        assertThat(request.getValue().getStorageClass()).isEqualTo("Intelligent_Tiering");
        assertThat(stored.eTag()).isEqualTo("etag-1");
        assertThat(request.getValue().getPicOperations()).isNull();
    }

    @Test
    void uploadsOriginalWithoutSynchronousProcessingAndReturnsHeadMetadata() {
        COS cos = mock(COS.class);
        PutObjectResult result = new PutObjectResult();
        result.setETag("put-etag");
        when(cos.putObject(any(PutObjectRequest.class))).thenReturn(result);
        String key = "materials/11/22/images/202609/44/result-44/original.jpg";
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(3);
        metadata.setContentType("image/jpeg");
        metadata.setETag("head-etag");
        metadata.setHeader("x-cos-storage-class", "INTELLIGENT_TIERING");
        when(cos.getObjectMetadata("antv-1418200553", key)).thenReturn(metadata);
        ObjectStorageService service = service(cos);

        StoredObject stored = service.uploadOriginal(
            key, new byte[] {1, 2, 3}, "image/jpeg"
        );

        ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(cos).putObject(request.capture());
        verify(cos).getObjectMetadata("antv-1418200553", key);
        assertThat(request.getValue().getPicOperations()).isNull();
        assertThat(request.getValue().getCustomRequestHeaders()).containsEntry("x-cos-forbid-overwrite", "true");
        assertThat(stored).isEqualTo(new StoredObject(
            key, 3L, "image/jpeg", "head-etag", "INTELLIGENT_TIERING"
        ));
    }

    @Test
    void uploadsOriginalPathWithoutSynchronousProcessingAndReturnsHeadMetadata() throws Exception {
        COS cos = mock(COS.class);
        TransferManager transfers = mock(TransferManager.class);
        Upload upload = mock(Upload.class);
        when(transfers.upload(any(PutObjectRequest.class))).thenReturn(upload);
        when(upload.waitForUploadResult()).thenReturn(new UploadResult());
        String key = "materials/0/inspiration_creation/202610/44/external-44/original.png";
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(3);
        metadata.setContentType("image/png");
        metadata.setETag("head-etag");
        metadata.setHeader("x-cos-storage-class", "INTELLIGENT_TIERING");
        when(cos.getObjectMetadata("antv-1418200553", key)).thenReturn(metadata);
        Path file = Files.createTempFile("verified-original-", ".png");
        Files.write(file, new byte[] {1, 2, 3});
        try {
            ObjectStorageService service = service(cos, transfers);

            StoredObject stored = service.uploadOriginal(key, file, "image/png");

            ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
            verify(transfers).upload(request.capture());
            assertThat(request.getValue().getPicOperations()).isNull();
            assertThat(stored).isEqualTo(new StoredObject(
                key, 3L, "image/png", "head-etag", "INTELLIGENT_TIERING"
            ));
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    void ordinaryWebpWriteStoresOnlyTheRequestedOriginal() {
        COS cos = mock(COS.class);
        when(cos.putObject(any(PutObjectRequest.class))).thenReturn(new PutObjectResult());
        ObjectStorageService service = service(cos);

        service.upload(
            "materials/11/22/images/202609/33/v1/original.webp",
            new ByteArrayInputStream(new byte[] {1}),
            1,
            "image/webp"
        );

        ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(cos).putObject(request.capture());
        assertThat(request.getValue().getPicOperations()).isNull();
        verify(cos, org.mockito.Mockito.never()).getObjectMetadata(any(), any());
    }

    @Test
    void doesNotCreateImageRenditionsForVideo() {
        COS cos = mock(COS.class);
        when(cos.putObject(any(PutObjectRequest.class))).thenReturn(new PutObjectResult());
        ObjectStorageService service = service(cos);

        service.upload(
            "materials/11/22/videos/202609/33/v1/original.mp4",
            new ByteArrayInputStream(new byte[] {1}),
            1,
            "video/mp4"
        );

        ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(cos).putObject(request.capture());
        assertThat(request.getValue().getPicOperations()).isNull();
    }

    @Test
    void readsVerifiedObjectMetadata() {
        COS cos = mock(COS.class);
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(99);
        metadata.setContentType("video/mp4");
        metadata.setETag("etag-2");
        when(cos.getObjectMetadata("antv-1418200553", "materials/11/video.mp4")).thenReturn(metadata);
        ObjectStorageService service = service(cos);

        StoredObject stored = service.metadata("materials/11/video.mp4");

        assertThat(stored.size()).isEqualTo(99);
        assertThat(stored.contentType()).isEqualTo("video/mp4");
        assertThat(stored.eTag()).isEqualTo("etag-2");
    }

    @Test
    void returnsCosObjectAsStreamingResource() throws Exception {
        COS cos = mock(COS.class);
        COSObject object = new COSObject();
        object.setObjectContent(new ByteArrayInputStream(new byte[] {4, 5}));
        when(cos.getObject("antv-1418200553", "materials/11/image.webp")).thenReturn(object);
        ObjectStorageService service = service(cos);

        assertThat(service.resource("materials/11/image.webp").getInputStream().readAllBytes())
            .containsExactly(4, 5);
    }

    @Test
    void modelUrlUsesPublicHostEvenWhenStorageClientUsesInternalEndpoint() throws Exception {
        ObjectStorageProperties properties = new ObjectStorageProperties();
        com.qcloud.cos.ClientConfig config = TencentCosConfiguration.clientConfig(properties);
        com.qcloud.cos.COSClient client = new com.qcloud.cos.COSClient(
            new com.qcloud.cos.auth.BasicSessionCredentials("test-id", "test-secret", "test-token"), config);
        try {
            java.net.URI url = java.net.URI.create(service(client).modelAccessUrl(
                "materials/11/reference image.png", Duration.ofMinutes(50)));
            assertThat(url.getHost()).isEqualTo("antv-1418200553.cos.ap-guangzhou.myqcloud.com");
            assertThat(url.getScheme()).isEqualTo("https");
            assertThat(url.getPath()).isEqualTo("/materials/11/reference image.png");
            assertThat(url.getQuery()).contains("q-signature=", "x-cos-security-token=test-token");
            java.util.Map<String, String> query = java.util.Arrays.stream(url.getRawQuery().split("&"))
                .map(part -> part.split("=", 2)).collect(java.util.stream.Collectors.toMap(
                    part -> part[0], part -> java.net.URLDecoder.decode(part[1], java.nio.charset.StandardCharsets.UTF_8)));
            String[] signTime = query.get("q-sign-time").split(";");
            String expectedSignature = new com.qcloud.cos.auth.COSSigner().buildAuthorizationStr(
                com.qcloud.cos.http.HttpMethodName.GET, url.getPath(),
                java.util.Map.of("Host", url.getHost()), java.util.Map.of(),
                new com.qcloud.cos.auth.BasicSessionCredentials("test-id", "test-secret", "test-token"),
                new java.util.Date(Long.parseLong(signTime[0]) * 1000),
                new java.util.Date(Long.parseLong(signTime[1]) * 1000), true);
            assertThat(query.get("q-header-list")).isEqualTo("host");
            assertThat(query.get("q-signature")).isEqualTo(expectedSignature.substring(
                expectedSignature.indexOf("q-signature=") + "q-signature=".length()));
            assertThat(config.getEndPointSuffix()).isEqualTo("cos-internal.ap-guangzhou.tencentcos.cn");
        } finally {
            client.shutdown();
        }
    }

    @Test
    void modelUrlUsesRequestedTaskWindow() throws Exception {
        COS cos = mock(COS.class);
        when(cos.generatePresignedUrl(any(com.qcloud.cos.model.GeneratePresignedUrlRequest.class), eq(true)))
            .thenReturn(java.net.URI.create("https://cos.example/signed?q-signature=test").toURL());
        ObjectStorageService service = service(cos);

        assertThat(service.modelAccessUrl("materials/11/video.mp4", Duration.ofMinutes(50)))
            .isEqualTo("https://antv-1418200553.cos.ap-guangzhou.myqcloud.com/signed?q-signature=test");
        ArgumentCaptor<com.qcloud.cos.model.GeneratePresignedUrlRequest> request =
            ArgumentCaptor.forClass(com.qcloud.cos.model.GeneratePresignedUrlRequest.class);
        verify(cos).generatePresignedUrl(request.capture(), eq(true));
        assertThat(request.getValue().getExpiration().toInstant())
            .isBetween(java.time.Instant.now().plusSeconds(2990), java.time.Instant.now().plusSeconds(3010));
    }

    @Test
    void reportsHistoricalObjectAsUnavailableWhenCosDoesNotContainIt() {
        COS cos = mock(COS.class);
        when(cos.getObject("antv-1418200553", "legacy/minio-only.mp4"))
            .thenThrow(new IllegalStateException("NoSuchKey"));

        assertThatThrownBy(() -> service(cos).resource("legacy/minio-only.mp4"))
            .isInstanceOf(BusinessException.class)
            .hasMessage("对象文件不存在。");
    }

    @Test
    void backendFileUploadUsesManagedMultipartTransfer() throws Exception {
        COS cos = mock(COS.class);
        TransferManager transfers = mock(TransferManager.class);
        Upload upload = mock(Upload.class);
        UploadResult result = new UploadResult();
        result.setETag("multipart-etag");
        when(transfers.upload(any(PutObjectRequest.class))).thenReturn(upload);
        when(upload.waitForUploadResult()).thenReturn(result);
        Path file = Files.createTempFile("cos-multipart-", ".mp4");
        Files.write(file, new byte[] {1, 2, 3});
        try {
            ObjectStorageService service = service(cos, transfers);

            service.uploadFile("materials/11/video/original.mp4", file, "video/mp4");

            ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
            verify(transfers).upload(request.capture());
            assertThat(request.getValue().getKey()).isEqualTo("materials/11/video/original.mp4");
            assertThat(request.getValue().getMetadata().getContentType()).isEqualTo("video/mp4");
        } finally {
            Files.deleteIfExists(file);
        }
    }

    private ObjectStorageService service(COS cos) {
        ObjectStorageProperties properties = new ObjectStorageProperties();
        properties.setBucket("antv-1418200553");
        properties.setRegion("ap-guangzhou");
        properties.setCdnDomain("https://antvcdn.aixmax.cn");
        properties.setCdnTypeDKey("test-key");
        return new ObjectStorageService(
            properties, cos, new ObjectStorageKeyFactory(), metrics()
        );
    }

    private ObjectStorageService service(COS cos, TransferManager transfers) {
        ObjectStorageProperties properties = new ObjectStorageProperties();
        properties.setBucket("antv-1418200553");
        properties.setRegion("ap-guangzhou");
        properties.setCdnDomain("https://antvcdn.aixmax.cn");
        properties.setCdnTypeDKey("test-key");
        return new ObjectStorageService(
            properties, cos, transfers, new ObjectStorageKeyFactory(), metrics()
        );
    }

    @Test
    void promotesVerifiedUploadToImmutableKeyWithEtagConstraint() throws Exception {
        COS cos = mock(COS.class);
        TransferManager transfers = mock(TransferManager.class);
        Copy copy = mock(Copy.class);
        when(transfers.copy(any(CopyObjectRequest.class))).thenReturn(copy);
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(3);
        metadata.setContentType("video/mp4");
        metadata.setETag("etag-1");
        metadata.setHeader("x-cos-storage-class", "INTELLIGENT_TIERING");
        String target = "materials/11/uploads/202609/session-1/v1/original.mp4";
        when(cos.getObjectMetadata("antv-1418200553", target)).thenReturn(metadata);
        ObjectStorageService service = service(cos, transfers);

        StoredObject promoted = service.promoteVerifiedUpload(
            new StoredObject(
                "uploads/11/session-1/source.mp4", 3, "video/mp4", "etag-1",
                "INTELLIGENT_TIERING"
            ),
            target
        );

        ArgumentCaptor<CopyObjectRequest> request =
            ArgumentCaptor.forClass(CopyObjectRequest.class);
        verify(transfers).copy(request.capture());
        verify(copy).waitForCopyResult();
        assertThat(request.getValue().getSourceKey())
            .isEqualTo("uploads/11/session-1/source.mp4");
        assertThat(request.getValue().getDestinationKey()).isEqualTo(target);
        assertThat(request.getValue().getMatchingETagConstraints()).containsExactly("etag-1");
        assertThat(request.getValue().getStorageClass()).isEqualTo("Intelligent_Tiering");
        assertThat(promoted.key()).isEqualTo(target);
        assertThat(promoted.eTag()).isEqualTo("etag-1");
    }

    @Test
    void inspirationCopiesCompletedUploadOriginalWithEtagConstraintAndRetainsSessionObject() throws Exception {
        COS cos = mock(COS.class);
        ObjectStorageService objects = org.mockito.Mockito.spy(service(cos));
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(12, 8, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", output);
        byte[] bytes = output.toByteArray();
        String sourceKey = "materials/11/uploads/202609/session-1/v1/original.png";
        String targetKey = new ObjectStorageKeyFactory().tenantOriginal(0L, "inspiration_creation", 44L, "external-44", java.time.LocalDate.now(), "png");
        StoredObject source = new StoredObject(sourceKey, bytes.length, "image/png", "source-etag", "INTELLIGENT_TIERING");
        org.mockito.Mockito.doReturn(new org.springframework.core.io.ByteArrayResource(bytes)).when(objects).resource(sourceKey);
        org.mockito.Mockito.doReturn(source).when(objects).metadata(sourceKey);
        org.mockito.Mockito.doReturn(new StoredObject(targetKey, bytes.length, "image/png", "target-etag", "INTELLIGENT_TIERING"))
            .when(objects).metadata(targetKey);
        ImageDisplayRenditionService renditions = mock(ImageDisplayRenditionService.class);
        when(renditions.registerOriginalAndSubmit(any(), any(), org.mockito.ArgumentMatchers.eq(12), org.mockito.ArgumentMatchers.eq(8), any()))
            .thenReturn(new RegisteredImageDisplay(targetKey, targetKey.replace("/original.png", "/derived/display.png"), "SUBMITTED"));
        com.antshorttv.inspiration.InspirationCreationMediaStorage storage = new com.antshorttv.inspiration.InspirationCreationMediaStorage(objects, new ObjectStorageKeyFactory(), renditions);

        org.assertj.core.api.Assertions.assertThatCode(() -> storage.storeUploadedImage(44L, "external-44",
            new VerifiedMediaUpload("session-1", sourceKey, "image/png", bytes.length, "source-etag")))
            .doesNotThrowAnyException();

        ArgumentCaptor<CopyObjectRequest> copy = ArgumentCaptor.forClass(CopyObjectRequest.class);
        verify(cos).copyObject(copy.capture());
        assertThat(copy.getValue().getSourceKey()).isEqualTo(sourceKey);
        assertThat(copy.getValue().getDestinationKey()).isEqualTo(targetKey);
        assertThat(copy.getValue().getMatchingETagConstraints()).containsExactly("source-etag");
        verify(objects, org.mockito.Mockito.never()).delete(sourceKey);
    }

    @Test
    void inspirationVideoCopiesRealCompletedSessionOriginalWithEtagConstraint() throws Exception {
        COS cos = mock(COS.class);
        ObjectStorageService objects = org.mockito.Mockito.spy(service(cos));
        String sourceKey = "materials/11/uploads/202610/video-session/v1/original.mp4";
        String targetKey = new ObjectStorageKeyFactory().tenantOriginal(0L, "inspiration_creation", 44L, "video-44", java.time.LocalDate.now(), "mp4");
        StoredObject source = new StoredObject(sourceKey, 123L, "video/mp4", "source-etag", "INTELLIGENT_TIERING");
        org.mockito.Mockito.doReturn(source).when(objects).metadata(sourceKey);
        org.mockito.Mockito.doReturn(new StoredObject(targetKey, 123L, "video/mp4", "target-etag", "INTELLIGENT_TIERING"))
            .when(objects).metadata(targetKey);
        com.antshorttv.inspiration.InspirationCreationMediaStorage storage = new com.antshorttv.inspiration.InspirationCreationMediaStorage(
            objects, new ObjectStorageKeyFactory(), mock(ImageDisplayRenditionService.class));

        org.assertj.core.api.Assertions.assertThatCode(() -> storage.storeUploadedVideo(44L, "video-44",
            new VerifiedMediaUpload("video-session", sourceKey, "video/mp4", 123L, "source-etag")))
            .doesNotThrowAnyException();

        ArgumentCaptor<CopyObjectRequest> copy = ArgumentCaptor.forClass(CopyObjectRequest.class);
        verify(cos).copyObject(copy.capture());
        assertThat(copy.getValue().getSourceKey()).isEqualTo(sourceKey);
        assertThat(copy.getValue().getDestinationKey()).isEqualTo(targetKey);
        assertThat(copy.getValue().getMatchingETagConstraints()).containsExactly("source-etag");
        verify(objects, org.mockito.Mockito.never()).delete(sourceKey);
    }

    private CosStorageMetrics metrics() {
        return new CosStorageMetrics(new SimpleMeterRegistry());
    }

    @Test
    void reusesMatchingMultipartCopyAfterAnImmutableTargetConflict() throws Exception {
        CopyReplayFixture fixture = new CopyReplayFixture("source-parts-3", "123456789", "123456789");

        StoredObject result = fixture.service.promoteVerifiedUpload(fixture.source, fixture.targetKey);

        assertThat(result.key()).isEqualTo(fixture.targetKey);
        assertThat(result.eTag()).isEqualTo("target-parts-7");
        verify(fixture.cos, org.mockito.Mockito.never()).putObject(any(PutObjectRequest.class));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"checksum", "source-etag", "missing-checksum"})
    void rejectsExistingCopyWhenContentEvidenceDoesNotMatch(String mismatch) throws Exception {
        CopyReplayFixture fixture = new CopyReplayFixture(
            "source-etag".equals(mismatch) ? "changed-parts-3" : "source-parts-3",
            "missing-checksum".equals(mismatch) ? null : "123456789",
            "checksum".equals(mismatch) ? "987654321" : "123456789"
        );

        assertThatThrownBy(() -> fixture.service.promoteVerifiedUpload(fixture.source, fixture.targetKey))
            .isInstanceOf(BusinessException.class);
    }

    private final class CopyReplayFixture {
        private final COS cos = mock(COS.class);
        private final String targetKey = "materials/11/uploads/202610/session-replay/v1/original.mp4";
        private final StoredObject source = new StoredObject(
            "uploads/11/session-replay/source.mp4", 5_000_000_001L, "video/mp4",
            "source-parts-3", "INTELLIGENT_TIERING"
        );
        private final ObjectStorageService service;

        private CopyReplayFixture(String actualSourceEtag, String sourceChecksum, String targetChecksum) throws Exception {
            TransferManager transfers = mock(TransferManager.class);
            Copy copy = mock(Copy.class);
            when(transfers.copy(any(CopyObjectRequest.class))).thenReturn(copy);
            com.qcloud.cos.exception.CosServiceException conflict = new com.qcloud.cos.exception.CosServiceException("already exists");
            conflict.setStatusCode(409);
            conflict.setErrorCode("ObjectAlreadyExists");
            when(copy.waitForCopyResult()).thenThrow(conflict);
            when(cos.getObjectMetadata("antv-1418200553", source.key()))
                .thenReturn(metadata(actualSourceEtag, sourceChecksum));
            when(cos.getObjectMetadata("antv-1418200553", targetKey))
                .thenReturn(metadata("target-parts-7", targetChecksum));
            service = service(cos, transfers);
        }

        private ObjectMetadata metadata(String etag, String checksum) {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(source.size());
            metadata.setContentType(source.contentType());
            metadata.setETag(etag);
            metadata.setHeader("x-cos-storage-class", "INTELLIGENT_TIERING");
            if (checksum != null) metadata.setHeader("x-cos-hash-crc64ecma", checksum);
            return metadata;
        }
    }

}

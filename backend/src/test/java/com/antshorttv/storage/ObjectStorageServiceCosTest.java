package com.antshorttv.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
        verify(cos).getObjectMetadata(
            "antv-1418200553",
            "materials/11/22/images/202609/33/v1/derived/display.png"
        );
        assertThat(request.getValue().getKey()).isEqualTo("materials/11/22/images/202609/33/v1/original.png");
        assertThat(request.getValue().getMetadata().getContentLength()).isEqualTo(3);
        assertThat(request.getValue().getMetadata().getContentType()).isEqualTo("image/png");
        assertThat(request.getValue().getStorageClass()).isEqualTo("Intelligent_Tiering");
        assertThat(stored.eTag()).isEqualTo("etag-1");
        assertThat(request.getValue().getPicOperations().getRules())
            .extracting(rule -> rule.getFileId())
            .containsExactly("materials/11/22/images/202609/33/v1/derived/display.png");
        assertThat(request.getValue().getPicOperations().getRules())
            .extracting(rule -> rule.getRule())
            .containsExactly("imageSlim");
    }

    @Test
    void convertsWebpToPngBeforeApplyingImageSlim() {
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
        assertThat(request.getValue().getPicOperations().getRules())
            .extracting(rule -> rule.getFileId(), rule -> rule.getRule())
            .containsExactly(org.assertj.core.groups.Tuple.tuple(
                "materials/11/22/images/202609/33/v1/derived/display.png",
                "imageMogr2/format/png|imageSlim"
            ));
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
    void modelUrlUsesRequestedTaskWindow() throws Exception {
        COS cos = mock(COS.class);
        when(cos.generatePresignedUrl(any(), any(), any(), any()))
            .thenReturn(java.net.URI.create("https://cos.example/signed").toURL());
        ObjectStorageService service = service(cos);

        assertThat(service.modelAccessUrl("materials/11/video.mp4", Duration.ofMinutes(50)))
            .isEqualTo("https://cos.example/signed");
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
            properties, cos, new ObjectStorageKeyFactory(), metrics(), planner()
        );
    }

    private ObjectStorageService service(COS cos, TransferManager transfers) {
        ObjectStorageProperties properties = new ObjectStorageProperties();
        properties.setBucket("antv-1418200553");
        properties.setRegion("ap-guangzhou");
        properties.setCdnDomain("https://antvcdn.aixmax.cn");
        properties.setCdnTypeDKey("test-key");
        return new ObjectStorageService(
            properties, cos, transfers, new ObjectStorageKeyFactory(), metrics(), planner()
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

    private CosStorageMetrics metrics() {
        return new CosStorageMetrics(new SimpleMeterRegistry());
    }

    private ImageDisplayRenditionPlanner planner() {
        return new ImageDisplayRenditionPlanner(new ObjectStorageKeyFactory());
    }
}

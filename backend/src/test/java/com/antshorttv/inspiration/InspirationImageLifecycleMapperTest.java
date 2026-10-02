package com.antshorttv.inspiration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.antshorttv.storage.ImageDisplayRenditionService;
import com.antshorttv.storage.RegisteredMediaDetails;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

@SpringBootTest(properties = "inspiration.image-rendition.scheduler.fixed-delay-ms=3600000")
class InspirationImageLifecycleMapperTest {
    @Autowired private InspirationCreationMapper mapper;
    @MockBean private ImageDisplayRenditionService renditions;
    private InspirationImageRenditionReconciler reconciler;

    @BeforeEach
    void setUp() {
        mapper.delete(null);
        reconciler = new InspirationImageRenditionReconciler(mapper, renditions);
    }

    @Test
    void readyBeforeMediaIsAppliedCannotPublish() {
        InspirationCreationEntity entity = insert("PROCESSING", "PENDING", "", null, 123L);
        assertThat(mapper.markImageRenditionReady(entity.getId(), "derived/display.png", "image/png", 45L))
            .isZero();
        assertThat(mapper.selectById(entity.getId()).getImportStatus()).isEqualTo("PROCESSING");
        assertThat(mapper.selectImportedById(entity.getId())).isNull();
    }

    @Test
    void readyRequiresPositiveOriginalSizeAndExpectedDisplayKey() {
        InspirationCreationEntity entity = insert("PROCESSING", "PENDING", "original.png", "derived/display.png", 0L);
        assertThat(mapper.markImageRenditionReady(entity.getId(), "derived/display.png", "image/png", 45L))
            .isZero();
        entity.setFileSize(123L);
        mapper.updateById(entity);
        assertThat(mapper.markImageRenditionReady(entity.getId(), "other/derived/display.png", "image/png", 45L))
            .isZero();
        assertThat(mapper.markImageRenditionReady(entity.getId(), "derived/display.png", "image/png", 45L))
            .isEqualTo(1);
    }

    @Test
    void failedBusinessRowRecoversWhenStorageRetryBecomesReady() {
        InspirationCreationEntity entity = insert("FAILED", "FAILED", "original.png", "derived/display.png", 123L);
        ready(entity);

        assertThat(reconciler.reconcilePending(20)).isEqualTo(1);
        assertThat(mapper.selectById(entity.getId()).getImportStatus()).isEqualTo("IMPORTED");
        assertThat(mapper.selectById(entity.getId()).getThumbnailStatus()).isEqualTo("READY");
    }

    @Test
    void submittedStorageRetryRestoresProcessingWithoutResubmitting() {
        InspirationCreationEntity entity = insert("FAILED", "FAILED", "original.png", "derived/display.png", 123L);
        when(renditions.displayDetails(InspirationImageRenditionReconciler.identity(entity)))
            .thenReturn(new RegisteredMediaDetails(72L, InspirationImageRenditionReconciler.identity(entity),
                "DISPLAY_IMAGE_SLIM", "derived/display.png", "image/png", 0L, "SUBMITTED", null));

        assertThat(reconciler.reconcilePending(20)).isEqualTo(1);
        assertThat(mapper.selectById(entity.getId()).getImportStatus()).isEqualTo("PROCESSING");
        assertThat(mapper.selectById(entity.getId()).getThumbnailStatus()).isEqualTo("PENDING");
        verify(renditions, never()).retryFailedDisplay(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void pendingFirstBatchDoesNotStarveLaterReadyRows() {
        for (int index = 0; index < 20; index++) {
            insert("PROCESSING", "PENDING", "original.png", "derived/display.png", 123L);
        }
        InspirationCreationEntity later = insert("PROCESSING", "PENDING", "original.png", "derived/display.png", 123L);
        ready(later);

        assertThat(reconciler.reconcilePending(20)).isZero();
        assertThat(reconciler.reconcilePending(20)).isEqualTo(1);
        assertThat(mapper.selectById(later.getId()).getImportStatus()).isEqualTo("IMPORTED");
    }

    @Test
    void videoCoverRequiresAttachedOriginalAndPositiveVerifiedDisplayMetadata() {
        InspirationCreationEntity video = insert("PROCESSING", "PENDING", "", null, 123L);
        video.setCreationType("VIDEO");
        video.setMimeType("video/mp4");
        mapper.updateById(video);
        assertThat(mapper.markImageRenditionReady(video.getId(), "cover/derived/display.jpg", "image/jpeg", 45L)).isZero();
        video.setStoragePath("materials/0/inspiration_creation/202610/44/video/original.mp4");
        video.setThumbnailPath("cover/derived/display.jpg");
        mapper.updateById(video);

        assertThat(mapper.selectImageRenditionCandidates(20)).extracting(InspirationCreationEntity::getId)
            .contains(video.getId());
        assertThat(mapper.markImageRenditionReady(video.getId(), "cover/derived/display.jpg", "image/jpeg", 45L)).isEqualTo(1);
        assertThat(mapper.selectImportedById(video.getId()).getThumbnailFileSize()).isEqualTo(45L);
    }

    private void ready(InspirationCreationEntity entity) {
        when(renditions.displayDetails(InspirationImageRenditionReconciler.identity(entity)))
            .thenReturn(new RegisteredMediaDetails(72L, InspirationImageRenditionReconciler.identity(entity),
                "DISPLAY_IMAGE_SLIM", "derived/display.png", "image/png", 45L, "READY", null));
    }

    private InspirationCreationEntity insert(String status, String thumbnailStatus, String original, String display, Long size) {
        InspirationCreationEntity entity = new InspirationCreationEntity();
        entity.setExternalId("lifecycle-" + java.util.UUID.randomUUID());
        entity.setCreationType("IMAGE");
        entity.setTaskType("TEXT_TO_IMAGE");
        entity.setTitle("Lifecycle");
        entity.setAuthorName("Admin");
        entity.setUrl("");
        entity.setStoragePath(original);
        entity.setMimeType("image/png");
        entity.setFileSize(size);
        entity.setThumbnailPath(display);
        entity.setThumbnailStatus(thumbnailStatus);
        entity.setImportStatus(status);
        entity.setPublishStatus("PUBLISHED");
        entity.setSortOrder(10);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        mapper.insert(entity);
        return entity;
    }
}

package com.antshorttv.inspiration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.antshorttv.storage.ImageDisplayRenditionService;
import com.antshorttv.storage.MediaObjectIdentity;
import com.antshorttv.storage.VerifiedMediaUpload;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InspirationManagementImageCreationServiceTest {
    private InspirationCreationMapper mapper;
    private InspirationManagementMediaService media;
    private ImageDisplayRenditionService renditions;
    private InspirationManagementImageCreationService service;

    @BeforeEach
    void setUp() {
        mapper = org.mockito.Mockito.mock(InspirationCreationMapper.class);
        media = org.mockito.Mockito.mock(InspirationManagementMediaService.class);
        renditions = org.mockito.Mockito.mock(ImageDisplayRenditionService.class);
        service = new InspirationManagementImageCreationService(
            mapper, media, renditions, new ObjectMapper()
        );
    }

    @Test
    void createsHiddenProcessingRowBeforeSubmittingManualImage() {
        VerifiedMediaUpload upload = upload();
        InspirationCreateUploadRequest request = new InspirationCreateUploadRequest(
            "session-1", "人工灵感", List.of("都市"), "完整提示词", "PUBLISHED"
        );
        doAnswer(invocation -> {
            InspirationCreationEntity inserted = invocation.getArgument(0);
            assertThat(inserted.getId()).isNull();
            assertThat(inserted.getImportStatus()).isEqualTo("PROCESSING");
            assertThat(inserted.getThumbnailStatus()).isEqualTo("PENDING");
            assertThat(inserted.getPublishStatus()).isEqualTo("PUBLISHED");
            assertThat(inserted.getStoragePath()).isEmpty();
            inserted.setId(44L);
            return 1;
        }).when(mapper).insert(any(InspirationCreationEntity.class));
        when(media.storeImage(44L, "manual-stable", upload)).thenReturn(
            new ManagedInspirationMedia(
                "IMAGE", "image/png", 321L,
                "materials/0/inspiration_creation/202610/44/manual-stable/original.png",
                "materials/0/inspiration_creation/202610/44/manual-stable/derived/display.png",
                "image/png", null, "PENDING"
            )
        );
        when(mapper.updateById(any(InspirationCreationEntity.class))).thenReturn(1);

        InspirationCreationEntity result = service.create(
            "manual-stable", upload, request, "PUBLISHED", 10
        );

        assertThat(result.getId()).isEqualTo(44L);
        assertThat(result.getImportStatus()).isEqualTo("PROCESSING");
        assertThat(result.getThumbnailStatus()).isEqualTo("PENDING");
        assertThat(result.getThumbnailFileSize()).isNull();
        assertThat(result.getThumbnailPath()).endsWith("/derived/display.png");
        assertThat(result.getPublishStatus()).isEqualTo("PUBLISHED");
        verify(media).storeImage(44L, "manual-stable", upload);
        verify(renditions, never()).retire(any());
    }

    @Test
    void retiresSubmittedGraphAndKeepsRowHiddenWhenFinalDatabaseWriteFails() {
        VerifiedMediaUpload upload = upload();
        InspirationCreateUploadRequest request = new InspirationCreateUploadRequest(
            "session-1", "人工灵感", List.of(), "完整提示词", "PUBLISHED"
        );
        doAnswer(invocation -> {
            ((InspirationCreationEntity) invocation.getArgument(0)).setId(45L);
            return 1;
        }).when(mapper).insert(any(InspirationCreationEntity.class));
        when(media.storeImage(45L, "manual-failed", upload)).thenReturn(
            new ManagedInspirationMedia(
                "IMAGE", "image/png", 321L,
                "materials/0/inspiration_creation/202610/45/manual-failed/original.png",
                "materials/0/inspiration_creation/202610/45/manual-failed/derived/display.png",
                "image/png", null, "PENDING"
            )
        );
        when(mapper.updateById(any(InspirationCreationEntity.class)))
            .thenThrow(new IllegalStateException("database unavailable"))
            .thenReturn(1);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.create(
            "manual-failed", upload, request, "PUBLISHED", 10
        )).isInstanceOf(IllegalStateException.class);

        verify(renditions).retire(new MediaObjectIdentity(
            0L, null, "INSPIRATION_CREATION", 45L, "manual-failed"
        ));
        verify(mapper, org.mockito.Mockito.times(2))
            .updateById(any(InspirationCreationEntity.class));
    }

    private VerifiedMediaUpload upload() {
        return new VerifiedMediaUpload(
            "session-1", "uploads/11/session-1/source.png", "image/png", 321L, "etag-source"
        );
    }
}

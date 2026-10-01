package com.antshorttv.inspiration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.antshorttv.storage.ImageDisplayRenditionService;
import com.antshorttv.storage.MediaObjectIdentity;
import com.antshorttv.storage.RegisteredMediaDetails;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InspirationImageRenditionReconcilerTest {
    private InspirationCreationMapper mapper;
    private ImageDisplayRenditionService renditions;
    private InspirationImageRenditionReconciler reconciler;
    private InspirationCreationEntity entity;
    private MediaObjectIdentity identity;

    @BeforeEach
    void setUp() {
        mapper = org.mockito.Mockito.mock(InspirationCreationMapper.class);
        renditions = org.mockito.Mockito.mock(ImageDisplayRenditionService.class);
        reconciler = new InspirationImageRenditionReconciler(mapper, renditions);
        entity = new InspirationCreationEntity();
        entity.setId(44L);
        entity.setExternalId("external-44");
        entity.setCreationType("IMAGE");
        entity.setImportStatus("PROCESSING");
        entity.setThumbnailStatus("PENDING");
        identity = new MediaObjectIdentity(
            0L, null, "INSPIRATION_CREATION", 44L, "external-44"
        );
        when(mapper.selectImageRenditionCandidates(20)).thenReturn(List.of(entity));
    }

    @Test
    void leavesMissingOrPendingRenditionHidden() {
        when(renditions.displayDetails(identity)).thenReturn(new RegisteredMediaDetails(
            72L, identity, "DISPLAY_IMAGE_SLIM", "expected/derived/display.png",
            "image/png", 0L, "PENDING", null
        ));

        assertThat(reconciler.reconcilePending(20)).isZero();

        verify(mapper, never()).markImageRenditionReady(
            org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyLong()
        );
        verify(mapper, never()).markImageRenditionFailed(
            org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString()
        );
    }

    @Test
    void publishesOnlyVerifiedReadyMetadata() {
        when(renditions.displayDetails(identity)).thenReturn(new RegisteredMediaDetails(
            72L, identity, "DISPLAY_IMAGE_SLIM", "verified/derived/display.png",
            "image/png", 987L, "READY", null
        ));
        when(mapper.markImageRenditionReady(
            44L, "verified/derived/display.png", "image/png", 987L
        )).thenReturn(1);

        assertThat(reconciler.reconcilePending(20)).isEqualTo(1);

        verify(mapper).markImageRenditionReady(
            44L, "verified/derived/display.png", "image/png", 987L
        );
    }

    @Test
    void recordsFailureWithoutFallingBackToOriginal() {
        when(renditions.displayDetails(identity)).thenReturn(new RegisteredMediaDetails(
            72L, identity, "DISPLAY_IMAGE_SLIM", "expected/derived/display.png",
            "image/png", 0L, "FAILED", "CI output metadata invalid"
        ));
        when(mapper.markImageRenditionFailed(44L, "CI output metadata invalid")).thenReturn(1);

        assertThat(reconciler.reconcilePending(20)).isEqualTo(1);

        verify(mapper).markImageRenditionFailed(44L, "CI output metadata invalid");
        verify(mapper, never()).markImageRenditionReady(
            org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyLong()
        );
    }
}

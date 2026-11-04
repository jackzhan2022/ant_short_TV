package com.antshorttv.style;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.antshorttv.common.BusinessException;
import com.antshorttv.storage.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AssetArtStyleImageServiceTest {
    private final ObjectStorageService storage = mock(ObjectStorageService.class);
    private final ImageDisplayRenditionService renditions = mock(ImageDisplayRenditionService.class);
    private final MediaDeliveryGrantService grants = mock(MediaDeliveryGrantService.class);
    private final ObjectStorageKeyFactory keys = new ObjectStorageKeyFactory();
    private final AssetArtStyleImageService images = new AssetArtStyleImageService(storage, renditions, keys, grants, new ObjectMapper());
    private final String code = "2D_90s_japanese_anime";

    @Test void rejectsUnknownCodesBeforeAccessingStorage() {
        for (String value : new String[]{"../other", "2D_unknown", "", "/materials/1/private.png"})
            assertThatThrownBy(() -> images.deliveryUrl(value)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(storage, renditions, grants);
    }

    @Test void neverSignsUnavailableOrMismatchedDisplay() {
        assertThatThrownBy(() -> images.deliveryUrl(code)).isInstanceOf(BusinessException.class);
        String path = new ImageDisplayRenditionPlanner(keys).plan(images.originalKey(code), "image/png").objectKey();
        for (String status : new String[]{"PENDING", "FAILED", "RETIRED"}) {
            when(renditions.displayDetails(images.identity(code))).thenReturn(details(path, 123, status));
            assertThatThrownBy(() -> images.deliveryUrl(code)).isInstanceOf(BusinessException.class);
        }
        when(renditions.displayDetails(images.identity(code))).thenReturn(details("private/object.png", 123, "READY"));
        assertThatThrownBy(() -> images.deliveryUrl(code)).isInstanceOf(BusinessException.class);
        when(renditions.displayDetails(images.identity(code))).thenReturn(details(path, 0, "READY"));
        assertThatThrownBy(() -> images.deliveryUrl(code)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(grants);
    }

    @Test void signsOnlyTheReviewedReadyDisplay() {
        String path = new ImageDisplayRenditionPlanner(keys).plan(images.originalKey(code), "image/png").objectKey();
        when(renditions.displayDetails(images.identity(code))).thenReturn(details(path, 123, "READY"));
        var identity = images.identity(code);
        var request = new DeliveryGrantRequest(0L, null, 0L, "STYLE_LIBRARY_V2", identity.assetId(), identity.versionId(), "DISPLAY", path, false);
        when(grants.issue(request)).thenReturn(new DeliveryGrant("https://cdn.example/signed", Instant.now()));
        assertThat(images.deliveryUrl(code)).isEqualTo("https://cdn.example/signed");
        verify(grants).issue(request);
    }

    @Test void rejectsDifferentSourceBeforeUploading() {
        assertThatThrownBy(() -> images.importImage(code, new byte[]{1,2,3})).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(storage, renditions, grants);
    }

    private RegisteredMediaDetails details(String path, long size, String status) {
        return new RegisteredMediaDetails(1L, images.identity(code), "DISPLAY", path, "image/png", size, status, null);
    }
}

package com.antshorttv.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ObjectStorageKeyFactoryTest {

    private final ObjectStorageKeyFactory keys = new ObjectStorageKeyFactory();

    @Test
    void buildsImmutableProjectOriginalAndRenditionKeys() {
        String original = keys.projectOriginal(
            11L, 22L, "images", 33L, "version-44", LocalDate.of(2026, 9, 29), "png"
        );

        assertThat(original).isEqualTo("materials/11/22/images/202609/33/version-44/original.png");
        assertThat(keys.rendition(original, "display", "png"))
            .isEqualTo("materials/11/22/images/202609/33/version-44/derived/display.png");
    }

    @Test
    void buildsTenantUploadKeyWithoutFabricatingProject() {
        assertThat(keys.tenantUpload(11L, "session-22", "episode.mp4"))
            .isEqualTo("uploads/11/session-22/episode.mp4");
    }

    @Test
    void buildsImmutableVerifiedUploadOriginalKeys() {
        assertThat(keys.verifiedUploadOriginal(
            11L, 22L, "session-22", LocalDate.of(2026, 9, 29), "mp4"
        )).isEqualTo(
            "materials/11/22/uploads/202609/session-22/v1/original.mp4"
        );
        assertThat(keys.verifiedUploadOriginal(
            11L, null, "session-22", LocalDate.of(2026, 9, 29), "mp4"
        )).isEqualTo(
            "materials/11/uploads/202609/session-22/v1/original.mp4"
        );
    }

    @Test
    void rejectsUnsafeSegments() {
        assertThatThrownBy(() -> keys.tenantUpload(11L, "../other", "episode.mp4"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> keys.rendition("/materials/11/original.png", "display", "png"))
            .isInstanceOf(IllegalArgumentException.class);
    }
}

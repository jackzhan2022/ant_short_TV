package com.antshorttv.video;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.antshorttv.common.BusinessException;
import com.antshorttv.storage.ObjectStorageService;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class ModelAccessibleVideoUrlResolverTest {

    @Test
    void buildsTaskBoundedCosUrlForQwenAccess() {
        ObjectStorageService storage = mock(ObjectStorageService.class);
        when(storage.modelAccessUrl("materials/1/2/episode-1.mp4", Duration.ofMinutes(50)))
            .thenReturn("https://cos.example/episode-1.mp4?signed=yes");
        ModelAccessibleVideoUrlResolver resolver = new ModelAccessibleVideoUrlResolver(storage, 20);

        String url = resolver.resolve("/materials/1/2/episode-1.mp4");

        assertThat(url).isEqualTo("https://cos.example/episode-1.mp4?signed=yes");
        verify(storage).modelAccessUrl("materials/1/2/episode-1.mp4", Duration.ofMinutes(50));
    }

    @Test
    void preservesValidExternalProviderUrl() {
        ModelAccessibleVideoUrlResolver resolver = new ModelAccessibleVideoUrlResolver(
            mock(ObjectStorageService.class), 20
        );

        assertThat(resolver.resolve("https://provider.example/video.mp4"))
            .isEqualTo("https://provider.example/video.mp4");
    }

    @Test
    void rejectsExternalUrlContainingProviderCredential() {
        ModelAccessibleVideoUrlResolver resolver = new ModelAccessibleVideoUrlResolver(
            mock(ObjectStorageService.class), 20
        );

        assertThatThrownBy(() -> resolver.resolve("https://provider.example/video.mp4?api_key=secret"))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("服务商凭据");
    }
}

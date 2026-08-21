package com.antshorttv.video;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.antshorttv.common.BusinessException;
import com.antshorttv.material.MaterialFileAccessService;
import org.junit.jupiter.api.Test;

class ModelAccessibleVideoUrlResolverTest {

    @Test
    void buildsAbsoluteSignedMaterialUrlForQwenAccess() {
        MaterialFileAccessService accessService = mock(MaterialFileAccessService.class);
        when(accessService.publicUrl("/materials/1/2/episode-1.mp4"))
            .thenReturn("/materials/1/2/episode-1.mp4?token=signed");
        ModelAccessibleVideoUrlResolver resolver = new ModelAccessibleVideoUrlResolver(
            accessService,
            "https://api.example.com"
        );

        String url = resolver.resolve("/materials/1/2/episode-1.mp4");

        assertThat(url).isEqualTo("https://api.example.com/materials/1/2/episode-1.mp4?token=signed");
    }

    @Test
    void rejectsRelativeMaterialUrlWhenPublicBaseUrlIsMissing() {
        MaterialFileAccessService accessService = mock(MaterialFileAccessService.class);
        when(accessService.publicUrl("/materials/1/2/episode-1.mp4"))
            .thenReturn("/materials/1/2/episode-1.mp4?token=signed");
        ModelAccessibleVideoUrlResolver resolver = new ModelAccessibleVideoUrlResolver(accessService, "");

        assertThatThrownBy(() -> resolver.resolve("/materials/1/2/episode-1.mp4"))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("外部访问地址");
    }
}

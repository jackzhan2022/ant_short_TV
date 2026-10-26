package com.antshorttv.script;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.antshorttv.aiimage.*;
import com.antshorttv.common.BusinessException;
import com.antshorttv.storage.LocalImageUploadService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

class StoryboardImageUploadServiceTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final LocalImageUploadService uploads = mock(LocalImageUploadService.class);
    private final MaterialMapper materials = mock(MaterialMapper.class);
    private final AiImageResultMapper images = mock(AiImageResultMapper.class);
    private final StoryboardImageUploadService service = new StoryboardImageUploadService(jdbc, uploads, materials, images);
    private AiImageResultEntity image;

    @BeforeEach
    void setup() {
        when(jdbc.queryForList(anyString(), eq(3L), eq(1L), eq(2L))).thenReturn(List.of(Map.of("id", 3L)));
        image = new AiImageResultEntity();
        image.setId(99L);
        image.setImageUrl("/api/projects/2/ai-image-results/99/display");
        image.setStoragePath("original.png");
        image.setMimeType("image/png");
        image.setFileSize(123L);
        image.setWidth(40);
        image.setHeight(50);
        when(uploads.storeUpload(1L, 2L, "STORYBOARD_REFERENCE", 3L, 4L, "token")).thenReturn(image);
        when(materials.insert(any(MaterialEntity.class))).thenAnswer(call -> {
            MaterialEntity material = call.getArgument(0);
            material.setId(100L);
            when(materials.selectById(100L)).thenReturn(material);
            return 1;
        });
    }

    @Test
    void storesImageMaterialWithMetadataWithoutReplacingFirstFrame() {
        var response = service.upload(1L, 2L, 3L, 4L, "token", "参考图.png");
        assertThat(response.materialId()).isEqualTo(100L);
        assertThat(response.name()).isEqualTo("参考图.png");
        var captor = ArgumentCaptor.forClass(MaterialEntity.class);
        verify(materials).insert(captor.capture());
        var material = captor.getValue();
        assertThat(material.getMaterialType()).isEqualTo("IMAGE");
        assertThat(material.getSourceType()).isEqualTo("LOCAL_UPLOAD");
        assertThat(material.getStatus()).isEqualTo("ACTIVE");
        assertThat(material.getTenantId()).isEqualTo(1L);
        assertThat(material.getProjectId()).isEqualTo(2L);
        assertThat(material.getStoragePath()).isEqualTo("original.png");
        assertThat(material.getWidth()).isEqualTo(40);
        assertThat(image.getMaterialId()).isEqualTo(100L);
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void repeatedBindingReturnsSameMaterial() {
        service.upload(1L, 2L, 3L, 4L, "token", "参考图.png");
        var retry = service.upload(1L, 2L, 3L, 4L, "token", "different.png");
        assertThat(retry.materialId()).isEqualTo(100L);
        assertThat(retry.name()).isEqualTo("参考图.png");
        verify(materials, times(1)).insert(any(MaterialEntity.class));
    }

    @Test
    void rejectsMissingOrForeignStoryboardBeforeUploading() {
        when(jdbc.queryForList(anyString(), eq(3L), eq(1L), eq(2L))).thenReturn(List.of());
        assertThatThrownBy(() -> service.upload(1L, 2L, 3L, 4L, "token", "image.png"))
            .isInstanceOf(BusinessException.class);
        verifyNoInteractions(uploads, images, materials);
    }

    @Test
    void failedUploadDoesNotCreateAReference() {
        when(uploads.storeUpload(anyLong(), anyLong(), anyString(), anyLong(), anyLong(), anyString()))
            .thenThrow(new IllegalArgumentException("invalid image"));
        assertThatThrownBy(() -> service.upload(1L, 2L, 3L, 4L, "token", "image.png"))
            .hasMessage("invalid image");
        verifyNoInteractions(images, materials);
    }
}

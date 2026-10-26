package com.antshorttv.video;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

import com.antshorttv.aiimage.AiImageResultEntity;
import com.antshorttv.aiimage.AiImageResultMapper;
import com.antshorttv.storage.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class AiVideoCoverServiceTest {
    @Test
    void retriesReuseTheSameImageIdentityAndOnlyPublishAfterCaptureSucceeds() {
        var images = mock(AiImageResultMapper.class);
        var covers = mock(CloudInfiniteVideoCoverService.class);
        var renditions = mock(ImageDisplayRenditionService.class);
        var service = new AiVideoCoverService(images, covers, renditions, new ObjectStorageKeyFactory());
        var task = new AiVideoTaskEntity();
        task.id = 3L; task.tenantId = 1L; task.projectId = 2L;
        var result = new AiVideoResultEntity();
        result.storagePath = "materials/1/2/videos/202610/3/result/original.mp4";
        result.width = 720; result.height = 1280;
        var saved = new java.util.concurrent.atomic.AtomicReference<AiImageResultEntity>();
        when(images.selectList(any())).thenAnswer(call -> saved.get() == null ? List.of() : List.of(saved.get()));
        when(images.insert(any(AiImageResultEntity.class))).thenAnswer(call -> { saved.set(call.getArgument(0)); saved.get().setId(44L); return 1; });
        when(covers.create(any(), anyString(), anyString())).thenThrow(new IllegalStateException("截帧暂不可用"))
            .thenAnswer(call -> new RegisteredImageDisplay(call.getArgument(2), call.<String>getArgument(2).replace("/original.jpg", "/derived/display.jpg"), "SUBMITTED"));
        when(renditions.originalDetails(any())).thenAnswer(call -> new RegisteredMediaDetails(1L, call.getArgument(0),
            "ORIGINAL", saved.get().getStoragePath(), "image/jpeg", 1234L, "READY", null));
        assertThatThrownBy(() -> service.prepare(task, result)).isInstanceOf(IllegalStateException.class);
        assertThat(saved.get().getStatus()).isEqualTo("PROCESSING");
        var image = service.prepare(task, result);
        verify(images, times(1)).insert(any(AiImageResultEntity.class));
        verify(images, never()).updateById(any(AiImageResultEntity.class));
        assertThat(image.getStatus()).isEqualTo("ACTIVE");
        assertThat(image.getFileSize()).isEqualTo(1234L);
        assertThat(image.getTargetType()).isEqualTo("VIDEO_COVER");
        assertThat(image.getTargetId()).isEqualTo(task.id);
        assertThat(result.coverUrl).isEqualTo("/api/projects/2/ai-image-results/" + image.getId() + "/display");
        service.publish(image);
        verify(images).updateById(image);
        verify(covers, times(2)).create(new MediaObjectIdentity(1L, 2L, "AI_IMAGE_RESULT", image.getId(), "result-" + image.getId()),
            result.storagePath, image.getStoragePath());
    }
}

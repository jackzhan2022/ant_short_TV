package com.antshorttv.aiimage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.antshorttv.rbac.ProjectPermissionGuard;
import com.antshorttv.security.TenantContext;
import com.antshorttv.storage.DeliveryGrant;
import com.antshorttv.storage.DeliveryGrantRequest;
import com.antshorttv.storage.MediaDeliveryGrantService;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AiImageDeliveryServiceTest {

    @Test
    void authorizesAndIssuesDisplayRendition() {
        AiImageResultMapper mapper = mock(AiImageResultMapper.class);
        ProjectPermissionGuard permissions = mock(ProjectPermissionGuard.class);
        MediaDeliveryGrantService grants = mock(MediaDeliveryGrantService.class);
        AiImageResultEntity result = result();
        when(mapper.selectById(44L)).thenReturn(result);
        when(permissions.require(11L, 22L, "AI_IMAGE_TASK:VIEW"))
            .thenReturn(new TenantContext(33L, 11L, 55L, "MEMBER"));
        when(grants.issue(any())).thenReturn(
            new DeliveryGrant("https://antvcdn.aixmax.cn/display.webp?sign=x&t=y", Instant.now())
        );
        AiImageDeliveryService service = new AiImageDeliveryService(mapper, permissions, grants);

        DeliveryGrant delivered = service.issue(22L, 44L, ImageRendition.DISPLAY);

        assertThat(delivered.url()).contains("display.webp");
        ArgumentCaptor<DeliveryGrantRequest> request = ArgumentCaptor.forClass(DeliveryGrantRequest.class);
        verify(grants).issue(request.capture());
        assertThat(request.getValue().userId()).isEqualTo(33L);
        assertThat(request.getValue().objectKey()).isEqualTo(result.getDisplayPath());
        assertThat(request.getValue().renditionType()).isEqualTo("DISPLAY");
    }

    private AiImageResultEntity result() {
        AiImageResultEntity value = new AiImageResultEntity();
        value.setId(44L);
        value.setTenantId(11L);
        value.setProjectId(22L);
        value.setStoragePath("materials/11/22/images/44/original.png");
        value.setDisplayPath("materials/11/22/images/44/derived/display.webp");
        value.setThumbnailPath("materials/11/22/images/44/derived/thumbnail.webp");
        value.setStatus("ACTIVE");
        return value;
    }
}

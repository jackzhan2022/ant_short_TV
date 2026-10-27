package com.antshorttv.freecreation;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.antshorttv.aiimage.*;
import com.antshorttv.common.*;
import com.antshorttv.rbac.ProjectPermissionGuard;
import com.antshorttv.security.TenantContext;
import com.antshorttv.storage.ObjectStorageKeyFactory;
import org.junit.jupiter.api.Test;

class ProjectImageReferenceServiceTest {
    final AiImageResultMapper images=mock(AiImageResultMapper.class);
    final ProjectPermissionGuard permissions=mock(ProjectPermissionGuard.class);
    final ProjectImageReferenceService service=new ProjectImageReferenceService(images,permissions,new ObjectStorageKeyFactory());
    final TenantContext context=new TenantContext(2L,1L,3L,"MEMBER");
    final FreeCreationRequest.ProjectImage source=new FreeCreationRequest.ProjectImage(5L,9L);

    AiImageResultEntity image() {
        var row=new AiImageResultEntity();row.setId(9L);row.setTenantId(1L);row.setProjectId(5L);
        row.setStatus("ACTIVE");row.setStoragePath("materials/1/5/character/202610/8/v1/original.png");
        row.setDisplayPath("materials/1/5/character/202610/8/v1/display.webp");
        row.setMimeType("image/png");row.setFileSize(123L);row.setWidth(512);row.setHeight(768);
        when(images.selectById(9L)).thenReturn(row);
        return row;
    }
    @Test void readsProjectImageRequestsAndValidatesNestedIdentifiers() throws Exception {
        var json=new com.fasterxml.jackson.databind.ObjectMapper();
        var reference=json.readValue("{\"kind\":\"IMAGE\",\"projectImage\":{\"projectId\":5,\"resultId\":9}}",FreeCreationRequest.Reference.class);
        assertThat(reference.projectImage()).isEqualTo(source);
        var legacy=json.readValue("{\"kind\":\"IMAGE\",\"uploadSessionToken\":\"verified\"}",FreeCreationRequest.Reference.class);
        assertThat(legacy.uploadSessionToken()).isEqualTo("verified");
        assertThat(legacy.projectImage()).isNull();
        try(var factory=jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            var invalid=new FreeCreationRequest.Reference("IMAGE",null,null,new FreeCreationRequest.ProjectImage(5L,null));
            assertThat(factory.getValidator().validate(invalid)).isNotEmpty();
        }
    }

    @Test void resolvesAuthorizedOriginalWithItsMetadata() {
        image();
        var resolved=service.resolve(context,source);
        assertThat(resolved.objectKey).endsWith("original.png");
        assertThat(resolved.mimeType).isEqualTo("image/png");
        assertThat(resolved.fileSize).isEqualTo(123L);
        assertThat(resolved.width).isEqualTo(512);
        verify(permissions).require(1L,5L,"AI_IMAGE_TASK:VIEW");
    }
    @Test void rejectsCrossTenantProjectAndDeletedImages() {
        var row=image();row.setTenantId(7L);
        assertThatThrownBy(()->service.resolve(context,source)).isInstanceOf(BusinessException.class);
        row.setTenantId(1L);row.setProjectId(6L);
        assertThatThrownBy(()->service.resolve(context,source)).isInstanceOf(BusinessException.class);
        row.setProjectId(5L);row.setStatus("DELETED");
        assertThatThrownBy(()->service.resolve(context,source)).isInstanceOf(BusinessException.class);
    }
    @Test void rejectsMissingProjectPermissionBeforeReadingImage() {
        doThrow(new BusinessException(ErrorCode.FORBIDDEN,"无权访问该项目")).when(permissions).require(1L,5L,"AI_IMAGE_TASK:VIEW");
        assertThatThrownBy(()->service.resolve(context,source)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(images);
    }
    @Test void rejectsStoragePathsOutsideTheSelectedProject() {
        var row=image();row.setStoragePath("materials/1/6/character/original.png");
        assertThatThrownBy(()->service.resolve(context,source)).isInstanceOf(BusinessException.class);
    }
}

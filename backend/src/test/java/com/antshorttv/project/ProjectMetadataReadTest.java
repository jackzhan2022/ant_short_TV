package com.antshorttv.project;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import java.util.*;
import org.junit.jupiter.api.Test;

class ProjectMetadataReadTest {
    @Test
    void metadataUsesAuthorizedProjectionWithoutReadingTheFullProject() throws Exception {
        Map<Class<?>,Object> dependencies = new HashMap<>();
        var constructor = ProjectService.class.getConstructors()[0];
        var service = (ProjectService)constructor.newInstance(Arrays.stream(constructor.getParameterTypes())
            .map(type -> dependencies.computeIfAbsent(type, org.mockito.Mockito::mock)).toArray());
        var resolver = (ProjectAccessResolver)dependencies.get(ProjectAccessResolver.class);
        var mapper = (ProjectMapper)dependencies.get(ProjectMapper.class);
        var access = mock(ProjectAccessContext.class);
        var project = new ProjectEntity();
        project.id=33L;project.tenantId=10L;project.ownerId=1L;project.name="Workbench";
        project.videoResolution="1080p";
        when(resolver.requireView(10L,33L)).thenReturn(access);
        when(access.project()).thenReturn(project);
        when(access.effectivePermissions()).thenReturn(Set.of("PROJECT:VIEW"));
        var response = service.metadata(10L,33L);
        assertThat(response.name()).isEqualTo("Workbench");
        assertThat(response.videoResolution()).isEqualTo("1080p");
        assertThat(response.initialScriptContent()).isNull();
        assertThat(response.effectivePermissions()).contains("PROJECT:VIEW");
        verify(mapper, never()).selectByTenantIdAndId(anyLong(), anyLong());
        when(resolver.requireView(99L,33L)).thenThrow(new BusinessException(ErrorCode.FORBIDDEN,"denied"));
        assertThatThrownBy(() -> service.metadata(99L,33L)).isInstanceOf(BusinessException.class);
    }
}

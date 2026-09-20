package com.antshorttv.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.antshorttv.operationlog.OperationLogService;
import com.antshorttv.project.ProjectEntity;
import com.antshorttv.project.ProjectMapper;
import com.antshorttv.security.TenantContext;
import com.antshorttv.security.TenantContextResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProjectAiConfigServiceTest {

    @Test
    void exposesSafeVideoConstraintsWithoutEndpointConfiguration() throws Exception {
        TenantContextResolver tenants = mock(TenantContextResolver.class);
        ProjectMapper projects = mock(ProjectMapper.class);
        ProjectAiConfigMapper configs = mock(ProjectAiConfigMapper.class);
        AiModelMapper models = mock(AiModelMapper.class);
        AiModelRouter router = mock(AiModelRouter.class);
        ObjectMapper objectMapper = new ObjectMapper();
        ProjectAiConfigService service = new ProjectAiConfigService(
            tenants, projects, configs, models, router, mock(OperationLogService.class), objectMapper);
        when(tenants.requireActiveMember(7L)).thenReturn(new TenantContext(9L, 7L, 11L, "OWNER"));
        ProjectEntity project = new ProjectEntity();
        project.id = 13L;
        project.tenantId = 7L;
        when(projects.selectByTenantIdAndId(7L, 13L)).thenReturn(project);

        AiModelEntity mini = new AiModelEntity();
        mini.setId(17L);
        mini.setName("Seedance 2.0 mini");
        mini.setDescription("safe description");
        mini.setModelCode("ep-sensitive-mini");
        mini.setServiceType("VIDEO");
        mini.setStatus("ENABLED");
        mini.setConfigJson("{\"videoGeneration\":{\"duration\":{\"min\":4,\"max\":15},\"resolutions\":[\"480p\",\"720p\"]},\"internal\":\"do-not-expose\"}");
        when(models.selectList(any())).thenReturn(List.of(), List.of(), List.of(mini), List.of());
        when(router.route(17L, "VIDEO")).thenReturn(mock(AiModelRoute.class));

        ProjectAiModelsResponse response = service.availableModels(7L, 13L);
        String json = objectMapper.writeValueAsString(response);

        assertThat(response.videoModels()).hasSize(1);
        assertThat(json).contains("constraints", "resolutions");
        assertThat(json).doesNotContain("ep-sensitive-mini", "do-not-expose", "modelCode");
    }
}

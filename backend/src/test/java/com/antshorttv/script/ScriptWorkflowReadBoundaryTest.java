package com.antshorttv.script;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.antshorttv.project.ProjectAccessContext;
import com.antshorttv.project.ProjectAccessResolver;
import com.antshorttv.project.ProjectEntity;
import com.antshorttv.security.TenantContext;
import com.antshorttv.security.TenantContextResolver;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class ScriptWorkflowReadBoundaryTest {
    @Test
    void focusedWorkspacesDoNotUseTheFullEpisodeReadForNavigation() throws Exception {
        Map<Class<?>, Object> dependencies = new HashMap<>();
        var constructor = ScriptWorkflowService.class.getConstructors()[0];
        Object[] arguments = Arrays.stream(constructor.getParameterTypes())
            .map(type -> dependencies.computeIfAbsent(type, org.mockito.Mockito::mock))
            .toArray();
        ScriptWorkflowService service = (ScriptWorkflowService) constructor.newInstance(arguments);
        var tenant = (TenantContextResolver) dependencies.get(TenantContextResolver.class);
        when(tenant.requireActiveMember(10L)).thenReturn(new TenantContext(1L, 10L, 1L, "OWNER"));
        var access = org.mockito.Mockito.mock(ProjectAccessContext.class);
        var project = new ProjectEntity();
        project.id = 33L;
        when(access.project()).thenReturn(project);
        when(((ProjectAccessResolver) dependencies.get(ProjectAccessResolver.class)).requireView(10L, 33L))
            .thenReturn(access);
        var script = new ScriptEntity();
        script.setId(7L);
        script.setTenantId(10L);
        script.setProjectId(33L);
        script.setContent("fallback only");
        when(((ScriptMapper) dependencies.get(ScriptMapper.class)).selectCurrentByProject(10L, 33L))
            .thenReturn(script);
        var episodes = org.mockito.Mockito.mock(ScriptEpisodeService.class);
        set(service, "scriptEpisodeService", episodes);
        when(episodes.currentEpisodes(10L, 33L, 7L)).thenReturn(List.of(
            new ScriptEpisodeResponse(1L, 1, "第1集", "large persisted episode body", "摘要", "hash", null, null)
        ));
        var jdbc = (JdbcTemplate) dependencies.get(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Long.class), eq(10L), eq(33L), anyInt())).thenReturn(0L);
        when(jdbc.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), eq(10L), eq(33L), anyInt(),
            anyInt(), anyInt())).thenReturn(List.of());

        service.scriptPageWorkspace(10L, 33L);
        service.storyboardWorkspace(10L, 33L, 1, 1, 20);

        verify(episodes, org.mockito.Mockito.times(2)).currentEpisodeNavigation(10L, 33L, 7L);
        verify(episodes, never()).currentEpisodes(anyLong(), anyLong(), anyLong());
    }

    private void set(Object target, String fieldName, Object value) throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}

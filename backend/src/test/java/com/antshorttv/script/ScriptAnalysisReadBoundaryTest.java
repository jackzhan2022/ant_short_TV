package com.antshorttv.script;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

import com.antshorttv.project.ProjectAccessContext;
import com.antshorttv.project.ProjectAccessResolver;
import com.antshorttv.project.ProjectEntity;
import com.antshorttv.security.TenantContext;
import com.antshorttv.security.TenantContextResolver;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ScriptAnalysisReadBoundaryTest {
    @Test
    void currentAnalysisDoesNotReadEachStageResultIndividually() throws Exception {
        Map<Class<?>, Object> dependencies = new HashMap<>();
        var constructor = ScriptWorkflowService.class.getConstructors()[0];
        Object[] arguments = Arrays.stream(constructor.getParameterTypes())
            .map(type -> dependencies.computeIfAbsent(type, key -> mock(key)))
            .toArray();
        ScriptWorkflowService service = (ScriptWorkflowService) constructor.newInstance(arguments);
        var tenant = (TenantContextResolver) dependencies.get(TenantContextResolver.class);
        when(tenant.requireActiveMember(10L)).thenReturn(new TenantContext(1L, 10L, 1L, "OWNER"));
        var access = mock(ProjectAccessContext.class);
        var project = new ProjectEntity();
        project.id = 33L;
        when(access.project()).thenReturn(project);
        when(((ProjectAccessResolver) dependencies.get(ProjectAccessResolver.class)).requireView(10L, 33L))
            .thenReturn(access);
        var script = new ScriptEntity();
        script.setId(7L);
        script.setCurrentVersionId(8L);
        when(((ScriptMapper) dependencies.get(ScriptMapper.class)).selectCurrentByProject(10L, 33L))
            .thenReturn(script);
        var task = new ScriptAnalysisTaskEntity();
        task.setId(9L);
        task.setTenantId(10L);
        task.setProjectId(33L);
        task.setScriptId(7L);
        task.setScriptVersionId(8L);
        when(((ScriptAnalysisTaskMapper) dependencies.get(ScriptAnalysisTaskMapper.class))
            .selectLatestByVersion(10L, 33L, 8L)).thenReturn(task);
        var first = stage(11L, 9L, "GLOBAL_UNDERSTANDING", 1);
        var second = stage(12L, 9L, "CHARACTER_SCENE_RECOGNITION", 2);
        when(((ScriptAnalysisStageMapper) dependencies.get(ScriptAnalysisStageMapper.class)).selectByTask(9L))
            .thenReturn(List.of(first, second));

        service.currentAnalysis(10L, 33L);

        verify((ScriptAnalysisResultMapper) dependencies.get(ScriptAnalysisResultMapper.class), never())
            .selectLatestByStage(11L);
        verify((ScriptAnalysisResultMapper) dependencies.get(ScriptAnalysisResultMapper.class), never())
            .selectLatestByStage(12L);
        verify((org.springframework.jdbc.core.JdbcTemplate) dependencies.get(org.springframework.jdbc.core.JdbcTemplate.class),
            never()).queryForList(anyString(), eq(Long.class), eq(11L));
        verify((org.springframework.jdbc.core.JdbcTemplate) dependencies.get(org.springframework.jdbc.core.JdbcTemplate.class),
            never()).queryForList(anyString(), eq(Long.class), eq(12L));
        verify((org.springframework.jdbc.core.JdbcTemplate) dependencies.get(org.springframework.jdbc.core.JdbcTemplate.class),
            never()).queryForList(anyString(), eq(11L));
        verify((org.springframework.jdbc.core.JdbcTemplate) dependencies.get(org.springframework.jdbc.core.JdbcTemplate.class),
            never()).queryForList(anyString(), eq(12L));
    }

    private static ScriptAnalysisStageEntity stage(Long id, Long taskId, String code, int order) {
        var stage = new ScriptAnalysisStageEntity();
        stage.setId(id);
        stage.setTaskId(taskId);
        stage.setStageCode(code);
        stage.setStageOrder(order);
        return stage;
    }
}

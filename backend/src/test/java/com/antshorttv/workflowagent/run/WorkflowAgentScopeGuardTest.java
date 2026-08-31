package com.antshorttv.workflowagent.run;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.assertj.core.api.Assertions.assertThat;

import com.antshorttv.common.BusinessException;
import com.antshorttv.rbac.ProjectPermissionGuard;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class WorkflowAgentScopeGuardTest {
    private final ProjectPermissionGuard permissions = mock(ProjectPermissionGuard.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private WorkflowAgentScopeGuard guard;

    @BeforeEach
    void setUp() {
        guard = new WorkflowAgentScopeGuard(permissions, jdbc);
    }

    @Test
    void requiresTrustedScriptScopeForCurrentScriptTools() {
        WorkflowAgentRunInput input = new WorkflowAgentRunInput(
            "short-drama-global-understanding", "run", 7L, 25L, null, null, null, null, 9L);

        assertThatThrownBy(() -> guard.requireAuthorized(input, List.of("read_current_script")))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("剧本");
    }

    @Test
    void rejectsScriptOutsideTrustedProjectAndDeletedScript() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(), any(), any())).thenReturn(0);
        WorkflowAgentRunInput input = new WorkflowAgentRunInput(
            "short-drama-global-understanding", "run", 7L, 25L, null, 77L, null, null, 9L);

        assertThatThrownBy(() -> guard.requireAuthorized(input, List.of("read_current_script")))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("不属于");
    }

    @Test
    void requiresEditPermissionForGlobalUnderstandingSave() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(), any(), any())).thenReturn(1);
        WorkflowAgentRunInput input = new WorkflowAgentRunInput(
            "short-drama-global-understanding", "run", 7L, 25L, null, 77L, null, null, 9L);

        guard.requireAuthorized(input, List.of("read_current_script", "save_global_understanding"));

        verify(permissions).require(7L, 25L, "SCRIPT:EDIT");
    }

    @Test
    void trustedBackgroundExecutionUsesPersistedExecutionIdentityWithoutHttpPrincipal() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class),
            any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(1);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(), any(), any())).thenReturn(1);
        when(jdbc.queryForObject(anyString(), eq(Integer.class),
            any(), any(), any(), any(), any(), any())).thenReturn(1);
        WorkflowAgentRunInput input = new WorkflowAgentRunInput(
            "short-drama-global-understanding", "run", 7L, 25L, null, 77L, 41L, 42L, 9L,
            501L, 502L, 3, 8L);

        guard.requireAuthorized(input, List.of("read_current_script", "save_global_understanding"));

        verify(permissions, never()).require(any(), any(), anyString());
    }

    @Test
    void mapsEachAnalysisAgentToItsOwnStage() {
        assertThat(WorkflowAgentScopeGuard.expectedStageCode("short-drama-global-understanding"))
            .isEqualTo("GLOBAL_UNDERSTANDING");
        assertThat(WorkflowAgentScopeGuard.expectedStageCode("short-drama-episode-splitting"))
            .isEqualTo("EPISODE_SPLITTING");
        assertThat(WorkflowAgentScopeGuard.expectedStageCode("short-drama-episode-summary"))
            .isEqualTo("EPISODE_SUMMARY");
        assertThat(WorkflowAgentScopeGuard.expectedStageCode("short-drama-asset-recognition"))
            .isEqualTo("CHARACTER_SCENE_RECOGNITION");
    }

    @Test
    void requiresScriptScopeForSplittingAndEpisodeScopeForSummaryAndRecognition() {
        WorkflowAgentRunInput split = new WorkflowAgentRunInput(
            "short-drama-episode-splitting", "run", 7L, 25L, null, null, null, null, 9L);
        assertThatThrownBy(() -> guard.requireAuthorized(split,
            List.of("read_current_script", "save_episode_splitting")))
            .isInstanceOf(BusinessException.class).hasMessageContaining("剧本");

        for (String save : List.of("save_episode_summary", "save_episode_assets")) {
            WorkflowAgentRunInput episode = new WorkflowAgentRunInput(
                "agent", "run", 7L, 25L, null, 77L, null, null, 9L);
            assertThatThrownBy(() -> guard.requireAuthorized(episode,
                List.of("read_current_episode", save)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("剧集");
        }
    }
}

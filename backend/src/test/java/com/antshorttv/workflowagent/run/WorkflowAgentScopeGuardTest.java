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

import org.mockito.ArgumentCaptor;

import com.antshorttv.common.BusinessException;
import com.antshorttv.rbac.ProjectPermissionGuard;
import com.antshorttv.workflowagent.tool.ReviewToolScope;
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
    void storyboardAgentRequiresItsStoryboardOperationExecutionIdentity() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class),
            eq(502L), eq(501L), eq(3), eq(7L), eq(25L), eq(9L), eq(41L), eq(7L), eq(25L), eq(77L)))
            .thenReturn(1);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(), any(), any())).thenReturn(1);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(), any(), any(), any()))
            .thenReturn(1);
        WorkflowAgentRunInput input = new WorkflowAgentRunInput(
            "short-drama-storyboard", "run", 7L, 25L, 31L, 77L, 41L, null, 9L,
            501L, 502L, 3, 8L);

        guard.requireAuthorized(input, List.of(
            "read_current_episode", "read_adjacent_episodes", "read_script_analysis",
            "read_project_context", "read_script_assets", "save_episode_storyboards"));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForObject(sql.capture(), eq(Integer.class),
            eq(502L), eq(501L), eq(3), eq(7L), eq(25L), eq(9L), eq(41L), eq(7L), eq(25L), eq(77L));
        assertThat(sql.getValue())
            .contains("SCRIPT_AI_OPERATION", "STORYBOARD_BREAKDOWN", "operation.execution_id = execution.id");
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
    void executionActivityIsBoundToTheCurrentStartedAttempt() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class),
            eq(501L), eq(3), eq(502L))).thenReturn(1);
        WorkflowAgentRunInput input = new WorkflowAgentRunInput(
            "short-drama-asset-recognition", "run", 7L, 25L, 31L, 77L, 41L, 42L, 9L,
            501L, 502L, 3, 8L);

        guard.requireExecutionActive(input);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForObject(sql.capture(), eq(Integer.class),
            eq(501L), eq(3), eq(502L));
        assertThat(sql.getValue()).contains("attempt.status = 'STARTED'");
    }

    @Test
    void rejectsAnExpiredAttemptAfterAnotherAttemptRestartsTheExecution() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class),
            eq(501L), eq(3), eq(502L))).thenReturn(0);
        WorkflowAgentRunInput input = new WorkflowAgentRunInput(
            "short-drama-asset-recognition", "run", 7L, 25L, 31L, 77L, 41L, 42L, 9L,
            501L, 502L, 3, 8L);

        assertThatThrownBy(() -> guard.requireExecutionActive(input))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("执行权已失效");
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

    @Test
    void authorizesQuickReviewFromTrustedExecutionAndRejectsOutOfPhaseTools() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class),
            any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(1, 0);
        WorkflowAgentRunInput input = reviewInput(new ReviewToolScope(
            301L, 302L, null, null, 1, "QUICK", List.of("台词合理性")));

        guard.requireAuthorized(input, List.of(
            "read_review_context", "read_review_content", "save_review_result"));
        assertThatThrownBy(() -> guard.requireAuthorized(input, List.of("save_review_unit_result")))
            .isInstanceOf(BusinessException.class).hasMessageContaining("阶段");
    }

    @Test
    void rejectsForeignReviewTaskVersionSnapshotAndUnitScopes() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(0);
        WorkflowAgentRunInput foreignTask = reviewInput(new ReviewToolScope(
            301L, 999L, null, null, 1, "QUICK", List.of("台词合理性")));
        assertThatThrownBy(() -> guard.requireAuthorized(foreignTask, List.of("read_review_context")))
            .isInstanceOf(BusinessException.class).hasMessageContaining("不匹配");
    }

    @Test
    void deepChildRequiresItsOwnSnapshotUnitAndPhaseAllowlist() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(1);
        WorkflowAgentRunInput input = reviewInput(new ReviewToolScope(
            301L, 302L, 303L, 304L, 1, "DEEP_CHILD", List.of("道具连续性")));
        guard.requireAuthorized(input, List.of(
            "read_review_context", "read_review_content", "save_review_unit_result"));
        assertThatThrownBy(() -> guard.requireAuthorized(input, List.of("read_review_unit_results")))
            .isInstanceOf(BusinessException.class).hasMessageContaining("阶段");
        assertThatThrownBy(() -> guard.requireAuthorized(input, List.of("save_review_result")))
            .isInstanceOf(BusinessException.class).hasMessageContaining("阶段");
    }

    private WorkflowAgentRunInput reviewInput(ReviewToolScope scope) {
        return new WorkflowAgentRunInput(
            "script-review", "run", 7L, 301L, null, null, 305L, null, 9L,
            501L, 502L, 3, 8L, scope);
    }
}

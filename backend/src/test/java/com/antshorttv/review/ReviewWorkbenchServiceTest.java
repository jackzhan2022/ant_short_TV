package com.antshorttv.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.antshorttv.ai.AiInvocationService;
import com.antshorttv.ai.AiModelMapper;
import com.antshorttv.ai.AiProviderMapper;
import com.antshorttv.execution.AiExecutionResponseMapper;
import com.antshorttv.execution.AiExecutionService;
import com.antshorttv.points.AiPointReservationMapper;
import com.antshorttv.points.AiPointSettlementService;
import com.antshorttv.points.TeamPointService;
import com.antshorttv.security.TenantContextResolver;
import com.antshorttv.workflowagent.run.WorkflowAgentModelCall;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReviewWorkbenchServiceTest {

    @Test
    void completedDeepTaskWithUnreconciledAggregationIsRecovered() {
        ReviewTaskMapper tasks = mock(ReviewTaskMapper.class);
        ReviewDeepAgentCoordinator coordinator = mock(ReviewDeepAgentCoordinator.class);
        ReviewTaskEntity task = new ReviewTaskEntity();
        task.setId(7L);
        task.setReviewMode("DEEP");
        task.setStatus("COMPLETED");
        task.setFanoutSnapshotId(50L);
        task.setWorkflowAgentRunId(200L);
        task.setAggregationRunId(200L);
        WorkflowAgentModelCall call = new WorkflowAgentModelCall(
            99L, 8L, 2L, "request", "SUCCESS", "SUCCESS", 14L,
            100, 20, 80, 10);
        when(tasks.selectById(7L)).thenReturn(task);
        when(coordinator.enabled()).thenReturn(true);
        when(coordinator.canRecoverCommittedAggregation(task)).thenReturn(true);
        when(coordinator.recoverCommittedAggregation(task)).thenReturn(
            new ReviewDeepAgentCoordinator.Execution(50L, 200L, List.of(call)));
        ReviewWorkbenchService service = new ReviewWorkbenchService(
            mock(TenantContextResolver.class), mock(ReviewAccessGuard.class),
            mock(ReviewProjectMapper.class), mock(ReviewScriptVersionMapper.class), tasks,
            mock(ReviewIssueMapper.class), mock(ReviewIssueHitMapper.class),
            mock(ReviewIssueEventMapper.class), mock(ReviewBatchRepairMapper.class),
            mock(ReviewExportRecordMapper.class), mock(AiInvocationService.class),
            mock(TeamPointService.class), mock(AiModelMapper.class), mock(AiProviderMapper.class),
            new ObjectMapper(), mock(AiExecutionService.class), mock(AiExecutionResponseMapper.class),
            mock(AiPointReservationMapper.class), mock(AiPointSettlementService.class),
            mock(ReviewContentService.class), mock(ReviewFanoutSnapshotMapper.class),
            mock(ReviewFanoutUnitMapper.class), mock(ReviewQuickAgentAdapter.class), coordinator,
            mock(ReviewObservabilityRepository.class), new ReviewWorkflowFeatureFlags(true, true, true, true),
            50000, "target/review-exports");

        ReviewExecutionOutcome outcome = service.executeTask(7L, null);

        assertThat(outcome.modelCalls()).containsExactly(call);
        verify(coordinator).recoverCommittedAggregation(task);
    }
}

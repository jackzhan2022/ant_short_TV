package com.antshorttv.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.antshorttv.ai.AiModelEntity;
import com.antshorttv.ai.AiInvocationService;
import com.antshorttv.ai.AiModelMapper;
import com.antshorttv.ai.AiProviderEntity;
import com.antshorttv.ai.AiProviderMapper;
import com.antshorttv.execution.AiExecutionTaskEntity;
import com.antshorttv.execution.AiExecutionResponseMapper;
import com.antshorttv.execution.AiExecutionService;
import com.antshorttv.points.AiPointReservationMapper;
import com.antshorttv.points.AiPointSettlementService;
import com.antshorttv.points.TeamPointService;
import com.antshorttv.security.TenantContext;
import com.antshorttv.security.TenantContextResolver;
import com.antshorttv.scriptcontent.ScriptContentParser;
import com.antshorttv.workflowagent.run.WorkflowAgentModelCall;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;

class ReviewWorkbenchServiceTest {

    @Test
    void createTaskPersistsMarkdownFormatWhenMarkdownQuickReviewIsEnabled() {
        TenantContextResolver tenantContexts = mock(TenantContextResolver.class);
        ReviewAccessGuard accessGuard = mock(ReviewAccessGuard.class);
        ReviewProjectMapper projects = mock(ReviewProjectMapper.class);
        ReviewScriptVersionMapper versions = mock(ReviewScriptVersionMapper.class);
        ReviewTaskMapper tasks = mock(ReviewTaskMapper.class);
        AiModelMapper models = mock(AiModelMapper.class);
        AiProviderMapper providers = mock(AiProviderMapper.class);
        AiExecutionService executions = mock(AiExecutionService.class);
        AiExecutionResponseMapper executionResponses = mock(AiExecutionResponseMapper.class);
        ReviewContentService reviewContent = mock(ReviewContentService.class);
        ReviewQuickAgentAdapter quickAgent = mock(ReviewQuickAgentAdapter.class);

        TenantContext context = new TenantContext(3L, 1L, 2L, "OWNER");
        ReviewProjectEntity project = new ReviewProjectEntity();
        project.setId(5L);
        project.setTenantId(1L);
        ReviewScriptVersionEntity version = new ReviewScriptVersionEntity();
        version.setId(7L);
        version.setTenantId(1L);
        version.setProjectId(5L);
        version.setContent("第1集\n林夏：你为什么骗我？");
        AiModelEntity model = new AiModelEntity();
        model.setId(11L);
        model.setProviderId(12L);
        model.setServiceType("TEXT");
        model.setStatus("ENABLED");
        model.setIsDefault(true);
        AiProviderEntity provider = new AiProviderEntity();
        provider.setId(12L);
        provider.setStatus("ENABLED");
        AiExecutionTaskEntity execution = new AiExecutionTaskEntity();
        execution.id = 13L;

        when(tenantContexts.requireActiveMember(1L)).thenReturn(context);
        when(projects.selectByTenantAndId(1L, 5L)).thenReturn(project);
        when(versions.selectById(7L)).thenReturn(version);
        when(tasks.selectList(any())).thenReturn(List.of());
        when(reviewContent.freeze(any(), any(), any(), any())).thenReturn(
            new ReviewContentService.FrozenReview("正文", "version", "scope", "dimensions", "snapshot", List.of(), 1));
        when(models.selectOne(any())).thenReturn(model);
        when(providers.selectById(12L)).thenReturn(provider);
        when(quickAgent.enabled()).thenReturn(true);
        when(executions.createWithReservation(any(), any(), any())).thenReturn(execution);
        doAnswer(invocation -> {
            ((ReviewTaskEntity) invocation.getArgument(0)).setId(21L);
            return 1;
        }).when(tasks).insert(any(ReviewTaskEntity.class));

        ReviewWorkbenchService service = new ReviewWorkbenchService(
            tenantContexts, accessGuard, projects, versions, tasks,
            mock(ReviewIssueMapper.class), mock(ReviewIssueHitMapper.class),
            mock(ReviewIssueEventMapper.class), mock(ReviewBatchRepairMapper.class),
            mock(ReviewExportRecordMapper.class), mock(AiInvocationService.class),
            mock(TeamPointService.class), models, providers,
            new ObjectMapper(), executions, executionResponses,
            mock(AiPointReservationMapper.class), mock(AiPointSettlementService.class),
            reviewContent, mock(ReviewFanoutSnapshotMapper.class),
            mock(ReviewFanoutUnitMapper.class), quickAgent, mock(ReviewDeepAgentCoordinator.class),
            mock(ReviewObservabilityRepository.class), new ReviewWorkflowFeatureFlags(true, true, true, true, true),
            new ScriptContentParser(),
            50000, "target/review-exports");

        service.createTask(1L, 5L, new CreateReviewTaskRequest(
            7L, "QUICK", List.of("台词合理性"), "ALL", Map.of(), null));

        ArgumentCaptor<ReviewTaskEntity> task = ArgumentCaptor.forClass(ReviewTaskEntity.class);
        verify(tasks).insert(task.capture());
        assertThat(task.getValue().getResultFormat()).isEqualTo("MARKDOWN");
    }

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
            new ScriptContentParser(),
            50000, "target/review-exports");

        ReviewExecutionOutcome outcome = service.executeTask(7L, null);

        assertThat(outcome.modelCalls()).containsExactly(call);
        verify(coordinator).recoverCommittedAggregation(task);
    }
}

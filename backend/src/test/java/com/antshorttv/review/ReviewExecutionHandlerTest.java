package com.antshorttv.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.antshorttv.accounting.AiUsageAccountingService;
import com.antshorttv.accounting.AiUsageCommand;
import com.antshorttv.accounting.AiUsageExtractor;
import com.antshorttv.accounting.AiUsageMetric;
import com.antshorttv.accounting.AiExecutionCostSummary;
import com.antshorttv.accounting.AiUsageCostStatus;
import com.antshorttv.ai.AiGatewayException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.execution.AiExecutionAttemptMapper;
import com.antshorttv.execution.AiExecutionClaim;
import com.antshorttv.execution.AiExecutionContext;
import com.antshorttv.execution.AiExecutionService;
import com.antshorttv.execution.AiExecutionTaskEntity;
import com.antshorttv.execution.AiExecutionTaskMapper;
import com.antshorttv.points.AiPointReservationMapper;
import com.antshorttv.points.AiPointReservationEntity;
import com.antshorttv.points.AiPointSettlementService;
import com.antshorttv.points.AiSettlementOutcome;
import com.antshorttv.workflowagent.run.WorkflowAgentRunRepository;
import com.antshorttv.workflowagent.run.WorkflowAgentModelCall;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class ReviewExecutionHandlerTest {

    @Test
    void recordsCallAndAllProviderTokenCategoriesIdempotently() {
        AiUsageAccountingService accounting = mock(AiUsageAccountingService.class);
        ReviewExecutionHandler handler = new ReviewExecutionHandler(
            mock(ReviewTaskMapper.class), mock(ReviewWorkbenchService.class),
            mock(AiExecutionAttemptMapper.class), mock(AiPointReservationMapper.class),
            mock(AiPointSettlementService.class), mock(AiExecutionService.class),
            mock(AiExecutionTaskMapper.class), accounting, new AiUsageExtractor(),
            mock(WorkflowAgentRunRepository.class), new ObjectMapper());
        AiExecutionTaskEntity task = new AiExecutionTaskEntity();
        task.id = 12L; task.tenantId = 3L; task.executionVersion = 1;
        AiExecutionContext context = new AiExecutionContext(task,
            new AiExecutionClaim(12L, 14L, "claim", 1, "AI_REVIEW"));

        @SuppressWarnings("unchecked")
        Set<AiUsageMetric> metrics = (Set<AiUsageMetric>) ReflectionTestUtils.invokeMethod(handler,
            "recordCallUsage", context, 99L, 8L, 14L, 100, 20, 80, 10);

        ArgumentCaptor<AiUsageCommand> commands = ArgumentCaptor.forClass(AiUsageCommand.class);
        verify(accounting, times(5)).recordIfAbsent(commands.capture());
        assertThat(commands.getAllValues()).extracting(AiUsageCommand::metric)
            .containsExactly(AiUsageMetric.CALL, AiUsageMetric.INPUT_TOKEN,
                AiUsageMetric.CACHED_INPUT_TOKEN, AiUsageMetric.CACHE_WRITE_TOKEN,
                AiUsageMetric.OUTPUT_TOKEN);
        assertThat(metrics).containsExactlyInAnyOrderElementsOf(commands.getAllValues().stream()
            .map(AiUsageCommand::metric).collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void partialCacheBreakdownRequiresUnclassifiedInputAndCannotLookComplete() {
        AiUsageAccountingService accounting = mock(AiUsageAccountingService.class);
        ReviewExecutionHandler handler = new ReviewExecutionHandler(
            mock(ReviewTaskMapper.class), mock(ReviewWorkbenchService.class),
            mock(AiExecutionAttemptMapper.class), mock(AiPointReservationMapper.class),
            mock(AiPointSettlementService.class), mock(AiExecutionService.class),
            mock(AiExecutionTaskMapper.class), accounting, new AiUsageExtractor(),
            mock(WorkflowAgentRunRepository.class), new ObjectMapper());
        AiExecutionTaskEntity task = new AiExecutionTaskEntity();
        task.id = 12L; task.tenantId = 3L; task.executionVersion = 1;
        AiExecutionContext context = new AiExecutionContext(task,
            new AiExecutionClaim(12L, 14L, "claim", 1, "AI_REVIEW"));

        @SuppressWarnings("unchecked")
        Set<AiUsageMetric> metrics = (Set<AiUsageMetric>) ReflectionTestUtils.invokeMethod(handler,
            "recordCallUsage", context, 99L, 8L, 14L, 100, 20, 80, null);

        assertThat(metrics).contains(AiUsageMetric.INPUT_TOKEN, AiUsageMetric.CACHED_INPUT_TOKEN);
        ArgumentCaptor<AiUsageCommand> commands = ArgumentCaptor.forClass(AiUsageCommand.class);
        verify(accounting, times(3)).recordIfAbsent(commands.capture());
        assertThat(commands.getAllValues()).extracting(AiUsageCommand::metric)
            .doesNotContain(AiUsageMetric.INPUT_TOKEN);
    }

    @Test
    void failedWorkflowRecordsPersistedCallsFromAllAttempts() {
        ReviewWorkbenchService review = mock(ReviewWorkbenchService.class);
        WorkflowAgentRunRepository runs = mock(WorkflowAgentRunRepository.class);
        AiUsageAccountingService accounting = mock(AiUsageAccountingService.class);
        when(review.executeTask(anyLong(), any())).thenThrow(new IllegalStateException("tool failed"));
        when(runs.modelCallsForExecution(12L, 3L)).thenReturn(List.of(
            new WorkflowAgentModelCall(98L, 8L, 2L, "p1", "SUCCEEDED", "SUCCEEDED", 13L,
                100, 20, 80, 10),
            new WorkflowAgentModelCall(99L, 8L, 2L, "p2", "SUCCEEDED", "SUCCEEDED", 14L,
                100, 20, 80, 10)));
        when(accounting.priceExecution(anyLong(), any())).thenReturn(
            new AiExecutionCostSummary(12L, AiUsageCostStatus.PRICED, Map.of()));
        ReviewExecutionHandler handler = new ReviewExecutionHandler(
            mock(ReviewTaskMapper.class), review, mock(AiExecutionAttemptMapper.class),
            mock(AiPointReservationMapper.class), mock(AiPointSettlementService.class),
            mock(AiExecutionService.class), mock(AiExecutionTaskMapper.class), accounting,
            new AiUsageExtractor(), runs, new ObjectMapper());
        AiExecutionTaskEntity task = new AiExecutionTaskEntity();
        task.id = 12L; task.tenantId = 3L; task.businessId = 7L; task.executionVersion = 1;
        AiExecutionContext context = new AiExecutionContext(task,
            new AiExecutionClaim(12L, 14L, "claim", 1, "AI_REVIEW"));

        assertThatThrownBy(() -> handler.execute(context)).isInstanceOf(IllegalStateException.class);

        verify(accounting, times(10)).recordIfAbsent(any(AiUsageCommand.class));
    }

    @Test
    void completedWorkflowReplayBillsAllCallsWithoutCopyingOldAttemptEvidence() {
        ReviewWorkbenchService review = mock(ReviewWorkbenchService.class);
        WorkflowAgentRunRepository runs = mock(WorkflowAgentRunRepository.class);
        AiUsageAccountingService accounting = mock(AiUsageAccountingService.class);
        AiExecutionAttemptMapper attempts = mock(AiExecutionAttemptMapper.class);
        AiPointReservationMapper reservations = mock(AiPointReservationMapper.class);
        AiPointSettlementService settlements = mock(AiPointSettlementService.class);
        when(review.executeTask(anyLong(), any())).thenReturn(ReviewExecutionOutcome.empty());
        when(runs.modelCallsForExecution(12L, 3L)).thenReturn(List.of(
            new WorkflowAgentModelCall(98L, 8L, 2L, "old-1", "SUCCESS", "SUCCESS", 10L,
                100, 20, 80, 10),
            new WorkflowAgentModelCall(99L, 8L, 2L, "old-2", "SUCCESS", "SUCCESS", 11L,
                100, 20, 80, 10)));
        when(accounting.priceExecution(anyLong(), any())).thenReturn(
            new AiExecutionCostSummary(12L, AiUsageCostStatus.PRICED, Map.of()));
        AiPointReservationEntity reservation = new AiPointReservationEntity();
        reservation.id = 20L; reservation.status = "RESERVED";
        when(reservations.selectByExecutionId(12L)).thenReturn(reservation);
        when(settlements.finalizeOutcome(anyLong(), eq(AiSettlementOutcome.SUCCESS), anyMap(),
            anyLong(), isNull(), any())).thenReturn(reservation);
        ReviewExecutionHandler handler = new ReviewExecutionHandler(
            mock(ReviewTaskMapper.class), review, attempts, reservations, settlements,
            mock(AiExecutionService.class), mock(AiExecutionTaskMapper.class), accounting,
            new AiUsageExtractor(), runs, new ObjectMapper());
        AiExecutionTaskEntity task = new AiExecutionTaskEntity();
        task.id = 12L; task.tenantId = 3L; task.businessId = 7L; task.executionVersion = 1;
        AiExecutionContext context = new AiExecutionContext(task,
            new AiExecutionClaim(12L, 14L, "claim", 1, "AI_REVIEW"));

        handler.execute(context);

        verify(attempts, never()).update(isNull(), any());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<AiUsageMetric, java.math.BigDecimal>> actualUsage =
            ArgumentCaptor.forClass(Map.class);
        verify(settlements).finalizeOutcome(eq(20L), eq(AiSettlementOutcome.SUCCESS),
            actualUsage.capture(), eq(14L), isNull(), any());
        assertThat(actualUsage.getValue()).containsEntry(AiUsageMetric.CALL,
            java.math.BigDecimal.valueOf(2));
    }

    @Test
    void multiCallPartialCacheBreakdownPersistsIncompleteCostStatus() {
        AiUsageAccountingService accounting = mock(AiUsageAccountingService.class);
        AiExecutionTaskMapper executionTasks = mock(AiExecutionTaskMapper.class);
        when(accounting.priceExecution(anyLong(), any())).thenReturn(
            new AiExecutionCostSummary(12L, AiUsageCostStatus.PRICED, Map.of()));
        ReviewExecutionHandler handler = new ReviewExecutionHandler(
            mock(ReviewTaskMapper.class), mock(ReviewWorkbenchService.class),
            mock(AiExecutionAttemptMapper.class), mock(AiPointReservationMapper.class),
            mock(AiPointSettlementService.class), mock(AiExecutionService.class), executionTasks,
            accounting, new AiUsageExtractor(), mock(WorkflowAgentRunRepository.class),
            new ObjectMapper());
        AiExecutionTaskEntity task = new AiExecutionTaskEntity();
        task.id = 12L; task.tenantId = 3L; task.executionVersion = 1;
        AiExecutionContext context = new AiExecutionContext(task,
            new AiExecutionClaim(12L, 14L, "claim", 1, "AI_REVIEW"));
        List<WorkflowAgentModelCall> calls = List.of(
            new WorkflowAgentModelCall(98L, 8L, 2L, "partial", "SUCCESS", "SUCCESS", 14L,
                100, 20, 80, null),
            new WorkflowAgentModelCall(99L, 8L, 2L, "complete", "SUCCESS", "SUCCESS", 14L,
                100, 20, 80, 10));

        ReflectionTestUtils.invokeMethod(handler, "recordWorkflowUsageAndCost", context, calls);

        @SuppressWarnings({"rawtypes", "unchecked"})
        ArgumentCaptor<UpdateWrapper<AiExecutionTaskEntity>> update =
            (ArgumentCaptor) ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(executionTasks).update(isNull(), update.capture());
        assertThat(update.getValue().getParamNameValuePairs()).containsValue("INCOMPLETE");
    }

    @Test
    void directGatewayBilledFailureRecordsCallUsage() {
        ReviewWorkbenchService review = mock(ReviewWorkbenchService.class);
        WorkflowAgentRunRepository runs = mock(WorkflowAgentRunRepository.class);
        AiUsageAccountingService accounting = mock(AiUsageAccountingService.class);
        when(review.executeTask(anyLong(), any())).thenThrow(
            new AiGatewayException(ErrorCode.AI_PROVIDER_ERROR, "provider failed", 99L));
        when(runs.modelCallsForExecution(12L, 3L)).thenReturn(List.of());
        when(runs.modelCallForExecution(99L, 12L, 3L)).thenReturn(Optional.of(
            new WorkflowAgentModelCall(99L, 8L, 2L, "request", "FAILED", "FAILED", 14L,
                null, null, null, null)));
        when(accounting.priceExecution(anyLong(), any())).thenReturn(
            new AiExecutionCostSummary(12L, AiUsageCostStatus.PRICED, Map.of()));
        ReviewExecutionHandler handler = new ReviewExecutionHandler(
            mock(ReviewTaskMapper.class), review, mock(AiExecutionAttemptMapper.class),
            mock(AiPointReservationMapper.class), mock(AiPointSettlementService.class),
            mock(AiExecutionService.class), mock(AiExecutionTaskMapper.class), accounting,
            new AiUsageExtractor(), runs, new ObjectMapper());
        AiExecutionTaskEntity task = new AiExecutionTaskEntity();
        task.id = 12L; task.tenantId = 3L; task.businessId = 7L; task.executionVersion = 1;
        AiExecutionContext context = new AiExecutionContext(task,
            new AiExecutionClaim(12L, 14L, "claim", 1, "AI_REVIEW"));

        assertThatThrownBy(() -> handler.execute(context)).isInstanceOf(AiGatewayException.class);

        verify(accounting).recordIfAbsent(any(AiUsageCommand.class));
        verify(accounting).priceExecution(eq(12L), eq(Set.of(AiUsageMetric.CALL)));
    }
}

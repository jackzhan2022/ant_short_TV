package com.antshorttv.script;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.antshorttv.accounting.*;
import com.antshorttv.execution.*;
import com.antshorttv.points.*;
import com.antshorttv.workflowagent.run.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ScriptAnalysisExecutionHandlerTest {
    @Test void rejectedNormalizationDisablesAnalysisRetry() {
        var handler=new ScriptAnalysisExecutionHandler(mock(ScriptAnalysisTaskMapper.class),mock(ScriptAnalysisExecutionService.class),
            mock(AiPointReservationMapper.class),mock(AiPointSettlementService.class),mock(AiExecutionService.class),
            mock(AiExecutionAttemptMapper.class),mock(AiExecutionTaskMapper.class),mock(AiUsageAccountingService.class),
            new ObjectMapper(),mock(WorkflowAgentRunRepository.class));
        assertThat(handler.retryPolicy(new NonRetryableAssetNormalizationException(new com.antshorttv.common.BusinessException(
            com.antshorttv.common.ErrorCode.WORKFLOW_AGENT_TOOL_INVALID,"归一判定无效")))).isEqualTo(AiExecutionRetryPolicy.none());
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void replaySettlesPersistedTokensFromEveryAttempt(boolean terminalFailure) {
        var analysis = mock(ScriptAnalysisExecutionService.class);
        var attempts = mock(AiExecutionAttemptMapper.class);
        var attempt = new AiExecutionAttemptEntity();attempt.attemptNo=3;
        when(attempts.selectById(14L)).thenReturn(attempt);
        var reservations = mock(AiPointReservationMapper.class);
        var settlements = mock(AiPointSettlementService.class);
        var accounting = mock(AiUsageAccountingService.class);
        var runs = mock(WorkflowAgentRunRepository.class);
        when(analysis.executeTask(eq(7L), any())).thenReturn(new ScriptAnalysisExecutionOutcome(List.of()));
        if (terminalFailure) when(analysis.executeTask(eq(7L), any())).thenThrow(new IllegalStateException("validation failed"));
        when(runs.modelCallsForExecution(12L, 3L)).thenReturn(List.of(
            new WorkflowAgentModelCall(90L, 8L, 2L, "first", "SUCCESS", "SUCCESS", 13L, 100, 20, 80, null),
            new WorkflowAgentModelCall(91L, 8L, 2L, "second", "SUCCESS", "SUCCESS", 14L, 200, 30, null, null)));
        when(accounting.priceExecution(anyLong(), any())).thenReturn(
            new AiExecutionCostSummary(12L, AiUsageCostStatus.PRICED, Map.of()));
        var reservation = new AiPointReservationEntity(); reservation.id = 20L; reservation.status = "RESERVED";
        when(reservations.selectByExecutionId(12L)).thenReturn(reservation);
        when(settlements.usesTokenPricing(20L)).thenReturn(true);
        var handler = new ScriptAnalysisExecutionHandler(mock(ScriptAnalysisTaskMapper.class), analysis,
            reservations, settlements, mock(AiExecutionService.class), attempts,
            mock(AiExecutionTaskMapper.class), accounting, new ObjectMapper(), runs);
        var task = new AiExecutionTaskEntity(); task.id=12L; task.tenantId=3L; task.businessId=7L; task.executionVersion=1;
        var context = new AiExecutionContext(task,new AiExecutionClaim(12L,14L,"claim",1,"RUN"));
        if (terminalFailure) org.assertj.core.api.Assertions.assertThatThrownBy(() -> handler.execute(context))
            .isInstanceOf(IllegalStateException.class);
        else handler.execute(context);
        ArgumentCaptor<Map<AiUsageMetric, BigDecimal>> usage = ArgumentCaptor.forClass(Map.class);
        verify(settlements).finalizeCumulativeOutcome(eq(20L),eq(terminalFailure ? AiSettlementOutcome.BUSINESS_FAILURE : AiSettlementOutcome.SUCCESS),usage.capture(),eq(14L),any(),any());
        assertThat(usage.getValue()).containsEntry(AiUsageMetric.CALL, BigDecimal.valueOf(2))
            .containsEntry(AiUsageMetric.INPUT_TOKEN,BigDecimal.valueOf(220))
            .containsEntry(AiUsageMetric.CACHED_INPUT_TOKEN,BigDecimal.valueOf(80))
            .containsEntry(AiUsageMetric.OUTPUT_TOKEN,BigDecimal.valueOf(50));
        verify(accounting,times(7)).recordIfAbsent(any());
    }
}

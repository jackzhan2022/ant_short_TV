package com.antshorttv.script;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.antshorttv.accounting.AiExecutionCostSummary;
import com.antshorttv.accounting.AiUsageAccountingService;
import com.antshorttv.accounting.AiUsageCommand;
import com.antshorttv.accounting.AiUsageCostStatus;
import com.antshorttv.execution.AiExecutionAttemptEntity;
import com.antshorttv.execution.AiExecutionAttemptMapper;
import com.antshorttv.execution.AiExecutionClaim;
import com.antshorttv.execution.AiExecutionContext;
import com.antshorttv.execution.AiExecutionService;
import com.antshorttv.execution.AiExecutionTaskEntity;
import com.antshorttv.execution.AiExecutionTaskMapper;
import com.antshorttv.ai.AiGatewayException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.points.AiPointReservationEntity;
import com.antshorttv.points.AiPointReservationMapper;
import com.antshorttv.points.AiPointSettlementService;
import com.antshorttv.points.AiSettlementOutcome;
import com.antshorttv.workflowagent.run.WorkflowAgentModelCall;
import com.antshorttv.workflowagent.run.WorkflowAgentRunRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ScriptAiOperationExecutionHandlerTest {

    @Test
    void disablesRetryForDeterministicStoryboardFailureButKeepsTransportRetry() {
        ScriptAiOperationExecutionHandler handler = new ScriptAiOperationExecutionHandler(
            mock(ScriptAiOperationMapper.class), mock(ScriptWorkflowService.class),
            mock(AiExecutionAttemptMapper.class), mock(AiPointReservationMapper.class),
            mock(AiPointSettlementService.class), mock(AiExecutionService.class),
            mock(AiUsageAccountingService.class), mock(AiExecutionTaskMapper.class),
            new ObjectMapper(), mock(WorkflowAgentRunRepository.class));

        assertThat(handler.retryPolicy(new NonRetryableStoryboardException("invalid", null)))
            .isEqualTo(com.antshorttv.execution.AiExecutionRetryPolicy.none());
        assertThat(handler.retryPolicy(new AiGatewayException(ErrorCode.AI_PROVIDER_TIMEOUT, "timeout")))
            .isEqualTo(handler.retryPolicy());
    }

    @Test
    void recoversWorkflowCallsWhenFormalValidationFailsOnFinalAttempt() {
        ScriptAiOperationMapper operationMapper = mock(ScriptAiOperationMapper.class);
        ScriptWorkflowService workflowService = mock(ScriptWorkflowService.class);
        AiExecutionAttemptMapper attemptMapper = mock(AiExecutionAttemptMapper.class);
        AiPointReservationMapper reservationMapper = mock(AiPointReservationMapper.class);
        AiPointSettlementService settlementService = mock(AiPointSettlementService.class);
        AiExecutionService executionService = mock(AiExecutionService.class);
        AiUsageAccountingService accounting = mock(AiUsageAccountingService.class);
        AiExecutionTaskMapper taskMapper = mock(AiExecutionTaskMapper.class);
        WorkflowAgentRunRepository runs = mock(WorkflowAgentRunRepository.class);

        ScriptAiOperationEntity operation = new ScriptAiOperationEntity();
        operation.id = 41L;
        operation.operationType = "STORYBOARD_BREAKDOWN";
        operation.redactedInputJson = "{\"episodeId\":7}";
        when(operationMapper.selectById(41L)).thenReturn(operation);
        when(workflowService.executeStoryboardOperation(eq(operation), any(), any()))
            .thenThrow(new IllegalStateException("Agent 未成功调用保存工具。"));

        AiExecutionTaskEntity task = new AiExecutionTaskEntity();
        task.id = 51L;
        task.tenantId = 61L;
        task.businessId = 41L;
        task.executionVersion = 1;
        AiExecutionAttemptEntity attempt = new AiExecutionAttemptEntity();
        attempt.id = 71L;
        attempt.attemptNo = 3;
        when(attemptMapper.selectById(71L)).thenReturn(attempt);

        List<WorkflowAgentModelCall> calls = List.of(
            new WorkflowAgentModelCall(81L, 91L, 101L, "provider-1", "SUCCESS", "SUCCESS", 71L),
            new WorkflowAgentModelCall(82L, 91L, 101L, "provider-2", "SUCCESS", "INVALID", 71L)
        );
        when(runs.modelCallsForExecutionAttempt(51L, 71L, 61L)).thenReturn(calls);
        when(runs.modelCallsForExecution(51L, 61L)).thenReturn(List.of(
            new WorkflowAgentModelCall(79L, 91L, 101L, "provider-0", "SUCCESS", "INVALID", 69L),
            new WorkflowAgentModelCall(80L, 91L, 101L, "provider-0b", "SUCCESS", "INVALID", 70L),
            calls.get(0), calls.get(1)
        ));
        when(accounting.priceExecution(eq(51L), any())).thenReturn(
            new AiExecutionCostSummary(51L, AiUsageCostStatus.PRICED, Map.of("CNY", BigDecimal.ONE))
        );
        AiPointReservationEntity reservation = new AiPointReservationEntity();
        reservation.id = 111L;
        reservation.status = "RESERVED";
        when(reservationMapper.selectByExecutionId(51L)).thenReturn(reservation);
        AiPointReservationEntity settled = new AiPointReservationEntity();
        when(settlementService.finalizeOutcome(
            eq(111L), eq(AiSettlementOutcome.PROVIDER_BILLED_FAILURE), any(), eq(71L), eq(82L), any()
        )).thenReturn(settled);

        ScriptAiOperationExecutionHandler handler = new ScriptAiOperationExecutionHandler(
            operationMapper, workflowService, attemptMapper, reservationMapper, settlementService,
            executionService, accounting, taskMapper, new ObjectMapper(), runs
        );

        assertThatThrownBy(() -> handler.execute(new AiExecutionContext(
            task, new AiExecutionClaim(51L, 71L, "claim", 1, "RUN")
        ))).isInstanceOf(IllegalStateException.class);

        verify(accounting, times(4)).recordIfAbsent(any(AiUsageCommand.class));
        verify(settlementService).finalizeOutcome(
            eq(111L), eq(AiSettlementOutcome.PROVIDER_BILLED_FAILURE),
            eq(Map.of(com.antshorttv.accounting.AiUsageMetric.CALL, BigDecimal.valueOf(4))),
            eq(71L), eq(82L), any()
        );
        verify(executionService).updateSettlementSummary(settled);

        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<AiExecutionAttemptEntity>> update =
            ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper.class);
        verify(attemptMapper).update(eq(null), update.capture());
    }
}

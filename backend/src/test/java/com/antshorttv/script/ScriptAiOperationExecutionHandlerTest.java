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
    void scopedCompletionCannotPublishOrSettleAfterClaimReplacement() {
        var operations=mock(ScriptAiOperationMapper.class);
        var workflow=mock(ScriptWorkflowService.class);
        var executions=mock(AiExecutionService.class);
        var settlements=mock(AiPointSettlementService.class);
        var reservations=mock(AiPointReservationMapper.class);
        var operation=new ScriptAiOperationEntity();
        operation.id=1L;operation.operationType="SCOPED_ASSET_REEXTRACTION";
        operation.redactedInputJson="{\"targetType\":\"PROP\",\"promptPolicy\":\"FILL_EMPTY\"}";
        when(operations.selectById(1L)).thenReturn(operation);
        var task=new AiExecutionTaskEntity();task.id=2L;task.businessId=1L;task.executionVersion=1;
        task.status="RUNNING";task.claimToken="current";task.claimExpiresAt=java.time.LocalDateTime.now().plusMinutes(5);
        var replacement=new AiExecutionTaskEntity();replacement.id=2L;replacement.executionVersion=1;
        replacement.status="RUNNING";replacement.claimToken="replacement";
        replacement.claimExpiresAt=java.time.LocalDateTime.now().plusMinutes(5);
        when(executions.requireTaskForUpdate(2L)).thenReturn(task,replacement);
        when(workflow.executeScopedAssetReextractionOperation(eq(operation),any(),any()))
            .thenReturn(new ScriptAiOperationExecutionResult("SCOPED_ASSET_REEXTRACTION",9L,List.of()));
        var handler=new ScriptAiOperationExecutionHandler(operations,workflow,mock(AiExecutionAttemptMapper.class),
            reservations,settlements,executions,mock(AiUsageAccountingService.class),mock(AiExecutionTaskMapper.class),
            new ObjectMapper(),mock(WorkflowAgentRunRepository.class));
        var transactions=mock(org.springframework.transaction.PlatformTransactionManager.class);
        when(transactions.getTransaction(any())).thenAnswer(invocation->new org.springframework.transaction.support.SimpleTransactionStatus());
        org.springframework.test.util.ReflectionTestUtils.setField(handler,"transactions",transactions);
        assertThatThrownBy(()->handler.execute(new AiExecutionContext(task,new AiExecutionClaim(2L,3L,"current",1,"RUN"))))
            .isInstanceOf(com.antshorttv.execution.AiExecutionClaimLostException.class);
        verify(operations,times(1)).updateById(operation); // only initial RUNNING publication
        assertThat(operation.status).isEqualTo("RUNNING");
        org.mockito.Mockito.verifyNoInteractions(settlements,reservations);
    }

    @Test
    void registersScopedAssetReextractionExecutionScene() {
        ScriptAiOperationExecutionHandler handler = new ScriptAiOperationExecutionHandler(
            mock(ScriptAiOperationMapper.class), mock(ScriptWorkflowService.class),
            mock(AiExecutionAttemptMapper.class), mock(AiPointReservationMapper.class),
            mock(AiPointSettlementService.class), mock(AiExecutionService.class),
            mock(AiUsageAccountingService.class), mock(AiExecutionTaskMapper.class),
            new ObjectMapper(), mock(WorkflowAgentRunRepository.class));

        assertThat(handler.scenes()).contains("scoped_asset_reextraction", "script_episode_split",
            "script_episode_summary", "script_character_scene_recognition");
    }


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
        assertThat(handler.retryPolicy(new com.antshorttv.common.BusinessException(ErrorCode.ANALYSIS_EPISODE_SNAPSHOT_CHANGED,"changed")))
            .isEqualTo(com.antshorttv.execution.AiExecutionRetryPolicy.none());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"STORYBOARD_BREAKDOWN","SCOPED_ASSET_REEXTRACTION",
        "SCRIPT_EPISODE_SPLIT","SCRIPT_EPISODE_SUMMARY","SCRIPT_EPISODE_ASSETS"})
    void recoversWorkflowCallsWhenFormalValidationFailsOnFinalAttempt(String operationType) {
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
        operation.operationType = operationType;
        operation.redactedInputJson = operationType.equals("STORYBOARD_BREAKDOWN") ? "{\"episodeId\":7}"
            : "{\"targetType\":\"PROP\",\"promptPolicy\":\"FILL_EMPTY\"}";
        when(operationMapper.selectById(41L)).thenReturn(operation);
        when(workflowService.executeStoryboardOperation(eq(operation), any(), any()))
            .thenThrow(new IllegalStateException("Agent 未成功调用保存工具。"));
        when(workflowService.executeScopedAssetReextractionOperation(eq(operation), any(), any()))
            .thenThrow(new IllegalStateException("Agent 未成功调用保存工具。"));
        when(workflowService.executeEpisodeRegenerationOperation(eq(operation), any()))
            .thenThrow(new IllegalStateException("Agent 未成功调用保存工具。"));

        AiExecutionTaskEntity task = new AiExecutionTaskEntity();
        task.id = 51L;
        task.tenantId = 61L;
        task.businessId = 41L;
        task.executionVersion = 1;
        AiExecutionAttemptEntity attempt = new AiExecutionAttemptEntity();
        attempt.id = 71L;
        attempt.attemptNo = 3;
        when(attemptMapper.selectCount(any())).thenReturn(2L);
        when(attemptMapper.selectById(71L)).thenReturn(attempt);

        List<WorkflowAgentModelCall> calls = List.of(
            new WorkflowAgentModelCall(81L, 91L, 101L, "provider-1", "SUCCESS", "SUCCESS", 71L, 100, 20, 80, null),
            new WorkflowAgentModelCall(82L, 91L, 101L, "provider-2", "SUCCESS", "INVALID", 71L, 100, 20, 80, null)
        );
        when(runs.modelCallsForExecutionAttempt(51L, 71L, 61L)).thenReturn(calls);
        when(runs.modelCallsForExecution(51L, 61L)).thenReturn(List.of(
            new WorkflowAgentModelCall(79L, 91L, 101L, "provider-0", "SUCCESS", "INVALID", 69L, 100, 20, 80, null),
            new WorkflowAgentModelCall(80L, 91L, 101L, "provider-0b", "SUCCESS", "INVALID", 70L, 100, 20, 80, null),
            calls.get(0), calls.get(1)
        ));
        when(accounting.priceExecution(eq(51L), any())).thenReturn(
            new AiExecutionCostSummary(51L, AiUsageCostStatus.PRICED, Map.of("CNY", BigDecimal.ONE))
        );
        AiPointReservationEntity reservation = new AiPointReservationEntity();
        reservation.id = 111L;
        reservation.status = "RESERVED";
        when(reservationMapper.selectByExecutionId(51L)).thenReturn(reservation);
        when(settlementService.usesTokenPricing(111L)).thenReturn(true);
        AiPointReservationEntity settled = new AiPointReservationEntity();
        when(settlementService.finalizeCumulativeOutcome(
            eq(111L), eq(AiSettlementOutcome.PROVIDER_BILLED_FAILURE), any(), eq(71L), eq(82L), any()
        )).thenReturn(settled);

        ScriptAiOperationExecutionHandler handler = new ScriptAiOperationExecutionHandler(
            operationMapper, workflowService, attemptMapper, reservationMapper, settlementService,
            executionService, accounting, taskMapper, new ObjectMapper(), runs
        );

        assertThatThrownBy(() -> handler.execute(new AiExecutionContext(
            task, new AiExecutionClaim(51L, 71L, "claim", 1, "RUN")
        ))).isInstanceOf(IllegalStateException.class);

        verify(accounting, times(16)).recordIfAbsent(any(AiUsageCommand.class));
        verify(settlementService).finalizeCumulativeOutcome(
            eq(111L), eq(AiSettlementOutcome.PROVIDER_BILLED_FAILURE),
            eq(Map.of(com.antshorttv.accounting.AiUsageMetric.CALL, BigDecimal.valueOf(4),
                com.antshorttv.accounting.AiUsageMetric.INPUT_TOKEN, BigDecimal.valueOf(80),
                com.antshorttv.accounting.AiUsageMetric.CACHED_INPUT_TOKEN, BigDecimal.valueOf(320),
                com.antshorttv.accounting.AiUsageMetric.CACHE_WRITE_TOKEN, BigDecimal.ZERO,
                com.antshorttv.accounting.AiUsageMetric.OUTPUT_TOKEN, BigDecimal.valueOf(80))),
            eq(71L), eq(82L), any()
        );
        verify(executionService).updateSettlementSummary(settled);

        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<AiExecutionAttemptEntity>> update =
            ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper.class);
        verify(attemptMapper).update(eq(null), update.capture());
    }
}

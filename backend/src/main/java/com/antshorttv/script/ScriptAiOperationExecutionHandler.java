package com.antshorttv.script;

import com.antshorttv.accounting.AiExecutionCostSummary;
import com.antshorttv.accounting.AiUsageAccountingService;
import com.antshorttv.accounting.AiUsageCommand;
import com.antshorttv.accounting.AiUsageContext;
import com.antshorttv.accounting.AiUsageMetric;
import com.antshorttv.ai.AiGatewayException;
import com.antshorttv.ai.AiInvocationResult;
import com.antshorttv.ai.AiTextResponse;
import com.antshorttv.execution.AiExecutionAttemptEntity;
import com.antshorttv.execution.AiExecutionAttemptMapper;
import com.antshorttv.execution.AiExecutionClaimLostException;
import com.antshorttv.execution.AiExecutionContext;
import com.antshorttv.execution.AiExecutionHandler;
import com.antshorttv.execution.AiExecutionHandlerResult;
import com.antshorttv.execution.AiExecutionRetryPolicy;
import com.antshorttv.execution.AiExecutionService;
import com.antshorttv.execution.AiExecutionTaskEntity;
import com.antshorttv.execution.AiExecutionTaskMapper;
import com.antshorttv.points.AiPointReservationEntity;
import com.antshorttv.points.AiPointReservationMapper;
import com.antshorttv.points.AiPointSettlementService;
import com.antshorttv.points.AiSettlementOutcome;
import com.antshorttv.workflowagent.run.WorkflowAgentModelCall;
import com.antshorttv.workflowagent.run.WorkflowAgentRunRepository;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class ScriptAiOperationExecutionHandler extends AiExecutionHandler {
    private final ScriptAiOperationMapper operationMapper;
    private final ScriptWorkflowService workflowService;
    private final AiExecutionAttemptMapper attemptMapper;
    private final AiPointReservationMapper reservationMapper;
    private final AiPointSettlementService settlementService;
    private final AiExecutionService executionService;
    private final AiUsageAccountingService usageAccountingService;
    private final AiExecutionTaskMapper executionTaskMapper;
    private final ObjectMapper objectMapper;
    private final WorkflowAgentRunRepository workflowAgentRuns;

    public ScriptAiOperationExecutionHandler(
        ScriptAiOperationMapper operationMapper,
        ScriptWorkflowService workflowService,
        AiExecutionAttemptMapper attemptMapper,
        AiPointReservationMapper reservationMapper,
        AiPointSettlementService settlementService,
        AiExecutionService executionService,
        AiUsageAccountingService usageAccountingService,
        AiExecutionTaskMapper executionTaskMapper,
        ObjectMapper objectMapper,
        WorkflowAgentRunRepository workflowAgentRuns
    ) {
        this.operationMapper = operationMapper;
        this.workflowService = workflowService;
        this.attemptMapper = attemptMapper;
        this.reservationMapper = reservationMapper;
        this.settlementService = settlementService;
        this.executionService = executionService;
        this.usageAccountingService = usageAccountingService;
        this.executionTaskMapper = executionTaskMapper;
        this.objectMapper = objectMapper;
        this.workflowAgentRuns = workflowAgentRuns;
    }

    @Override
    public String scene() {
        return "script_generate";
    }

    @Override
    public List<String> scenes() {
        return List.of(
            "script_generate",
            "script_rewrite",
            "script_element_extract",
            "character_extract",
            "scene_extract",
            "prop_extract",
            "storyboard_breakdown",
            "prompt_generate"
        );
    }

    @Override
    public AiExecutionRetryPolicy retryPolicy() {
        return new AiExecutionRetryPolicy(3, Duration.ofSeconds(5));
    }

    @Override
    public AiExecutionRetryPolicy retryPolicy(Throwable failure) {
        return failure instanceof NonRetryableStoryboardException
            ? AiExecutionRetryPolicy.none()
            : retryPolicy();
    }

    @Override
    public void validate(AiExecutionTaskEntity task) {
        ScriptAiOperationEntity operation = operationMapper.selectById(task.businessId);
        if (operation == null || !task.id.equals(operation.executionId)) {
            throw new IllegalStateException("Script operation is not linked to execution " + task.id);
        }
    }

    @Override
    public AiExecutionHandlerResult execute(AiExecutionContext context) {
        ScriptAiOperationEntity operation = operationMapper.selectById(context.task().businessId);
        markRunning(operation);
        ScriptAiOperationExecutionResult result;
        try {
            result = executeOperation(operation, context);
        } catch (AiExecutionClaimLostException exception) {
            markCanceled(operation);
            throw exception;
        } catch (RuntimeException exception) {
            Long callLogId = exception instanceof AiGatewayException gateway ? gateway.getAiCallLogId() : null;
            List<WorkflowAgentModelCall> attemptAgentCalls = workflowAgentRuns.modelCallsForExecutionAttempt(
                context.task().id, context.claim().attemptId(), context.task().tenantId);
            List<WorkflowAgentModelCall> executionAgentCalls = "STORYBOARD_BREAKDOWN".equals(operation.operationType)
                ? workflowAgentRuns.modelCallsForExecution(context.task().id, context.task().tenantId)
                : attemptAgentCalls;
            WorkflowAgentModelCall lastAttemptAgentCall = attemptAgentCalls.isEmpty()
                ? null : attemptAgentCalls.get(attemptAgentCalls.size() - 1);
            WorkflowAgentModelCall settlementAgentCall = lastAttemptAgentCall != null
                ? lastAttemptAgentCall
                : executionAgentCalls.isEmpty() ? null : executionAgentCalls.get(executionAgentCalls.size() - 1);
            if (lastAttemptAgentCall != null) {
                markAttempt(context, new ScriptAiOperationExecutionResult(
                    null, null, List.of(), attemptAgentCalls));
                callLogId = lastAttemptAgentCall.callLogId();
            }
            recordUsageAndCost(context, List.of(), executionAgentCalls);
            markFailed(operation, exception);
            int executionCallCount = executionAgentCalls.isEmpty()
                ? (callLogId == null ? 0 : 1)
                : executionAgentCalls.size();
            settleTerminalFailure(context, settlementAgentCall, callLogId,
                executionCallCount, retryPolicy(exception).maxAttempts());
            throw exception;
        }
        markAttempt(context, result);
        markSucceeded(operation, result);
        List<WorkflowAgentModelCall> executionAgentCalls = "STORYBOARD_BREAKDOWN".equals(operation.operationType)
            ? workflowAgentRuns.modelCallsForExecution(context.task().id, context.task().tenantId)
            : result.agentModelCalls();
        recordUsageAndCost(context, result.invocations(), executionAgentCalls);
        int executionCallCount = executionAgentCalls.isEmpty()
            ? result.invocations().size()
            : executionAgentCalls.size();
        WorkflowAgentModelCall settlementAgentCall = result.lastAgentModelCall() != null
            ? result.lastAgentModelCall()
            : executionAgentCalls.isEmpty() ? null : executionAgentCalls.get(executionAgentCalls.size() - 1);
        settle(context, result.lastInvocation(), settlementAgentCall, AiSettlementOutcome.SUCCESS,
            executionCallCount);
        return new AiExecutionHandlerResult(result.resultType(), result.resultId());
    }

    private ScriptAiOperationExecutionResult executeOperation(
        ScriptAiOperationEntity operation,
        AiExecutionContext context
    ) {
        try {
            if (operation.resultId != null) {
                return new ScriptAiOperationExecutionResult(operation.resultType, operation.resultId, List.of());
            }
            if ("SCRIPT_GENERATE".equals(operation.operationType)) {
                GenerateScriptRequest request = objectMapper.readValue(
                    operation.redactedInputJson,
                    GenerateScriptRequest.class
                );
                return workflowService.executeGenerateOperation(operation, request, context);
            }
            if ("SCRIPT_REWRITE".equals(operation.operationType)) {
                RewriteScriptRequest request = objectMapper.readValue(
                    operation.redactedInputJson,
                    RewriteScriptRequest.class
                );
                return workflowService.executeRewriteOperation(operation, request, context);
            }
            if ("ELEMENT_EXTRACT".equals(operation.operationType)) {
                ExtractScriptElementsRequest request = objectMapper.readValue(
                    operation.redactedInputJson,
                    ExtractScriptElementsRequest.class
                );
                return workflowService.executeElementExtractionOperation(operation, request, context);
            }
            if ("STORYBOARD_BREAKDOWN".equals(operation.operationType)) {
                StoryboardBreakdownRequest request = objectMapper.readValue(
                    operation.redactedInputJson,
                    StoryboardBreakdownRequest.class
                );
                return workflowService.executeStoryboardOperation(operation, request, context);
            }
            if ("PROMPT_GENERATE".equals(operation.operationType)) {
                GeneratePromptRequest request = objectMapper.readValue(
                    operation.redactedInputJson,
                    GeneratePromptRequest.class
                );
                return workflowService.executePromptOperation(operation, request, context);
            }
            throw new IllegalArgumentException("Unsupported script operation: " + operation.operationType);
        } catch (AiExecutionClaimLostException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Script operation input cannot be restored.", exception);
        }
    }

    private void markAttempt(AiExecutionContext context, AiInvocationResult<AiTextResponse> invocation) {
        if (invocation == null) {
            return;
        }
        attemptMapper.update(null, new UpdateWrapper<AiExecutionAttemptEntity>()
            .set("provider_contacted", true)
            .set("provider_contacted_at", LocalDateTime.now())
            .set("provider_id", invocation.providerId())
            .set("model_id", invocation.resolvedModelId())
            .set("provider_request_id", invocation.providerRequestId())
            .set("ai_call_log_id", invocation.aiCallLogId())
            .set("transport_outcome", invocation.transportOutcome())
            .set("business_outcome", invocation.businessOutcome())
            .eq("id", context.claim().attemptId()));
    }

    private void markAttempt(AiExecutionContext context, ScriptAiOperationExecutionResult result) {
        if (result.lastInvocation() != null) {
            markAttempt(context, result.lastInvocation());
            return;
        }
        var call = result.lastAgentModelCall();
        if (call == null) return;
        attemptMapper.update(null, new UpdateWrapper<AiExecutionAttemptEntity>()
            .set("provider_contacted", true)
            .set("provider_contacted_at", LocalDateTime.now())
            .set("provider_id", call.providerId())
            .set("model_id", call.modelId())
            .set("provider_request_id", call.providerRequestId())
            .set("ai_call_log_id", call.callLogId())
            .set("transport_outcome", call.transportOutcome())
            .set("business_outcome", call.businessOutcome())
            .eq("id", context.claim().attemptId()));
    }

    private void recordUsageAndCost(
        AiExecutionContext context,
        ScriptAiOperationExecutionResult result
    ) {
        recordUsageAndCost(context, result.invocations(), result.agentModelCalls());
    }

    private void recordUsageAndCost(
        AiExecutionContext context,
        List<AiInvocationResult<AiTextResponse>> invocations,
        List<WorkflowAgentModelCall> agentModelCalls
    ) {
        if (invocations.isEmpty() && agentModelCalls.isEmpty()) {
            return;
        }
        LocalDateTime observedAt = LocalDateTime.now();
        for (AiInvocationResult<AiTextResponse> invocation : invocations) {
            usageAccountingService.recordIfAbsent(AiUsageCommand.requestDerived(
                new AiUsageContext(
                    context.task().tenantId,
                    context.task().id,
                    context.claim().attemptId(),
                    invocation.aiCallLogId(),
                    invocation.resolvedModelId()
                ),
                AiUsageMetric.CALL,
                "1",
                Map.of(),
                observedAt
            ));
        }
        for (var call : agentModelCalls) {
            usageAccountingService.recordIfAbsent(AiUsageCommand.requestDerived(
                new AiUsageContext(context.task().tenantId, context.task().id,
                    call.attemptId() == null ? context.claim().attemptId() : call.attemptId(),
                    call.callLogId(), call.modelId()),
                AiUsageMetric.CALL, "1", Map.of(), observedAt));
        }
        AiExecutionCostSummary cost = usageAccountingService.priceExecution(
            context.task().id,
            Set.of(AiUsageMetric.CALL)
        );
        try {
            executionTaskMapper.update(null, new UpdateWrapper<AiExecutionTaskEntity>()
                .set("usage_cost_status", cost.status().name())
                .set("provider_cost_summary_json", objectMapper.writeValueAsString(cost.totalsByCurrency()))
                .eq("id", context.task().id));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to persist script operation cost summary.", exception);
        }
    }

    private void settle(
        AiExecutionContext context,
        AiInvocationResult<AiTextResponse> invocation,
        com.antshorttv.workflowagent.run.WorkflowAgentModelCall agentCall,
        AiSettlementOutcome outcome,
        int providerCallCount
    ) {
        AiPointReservationEntity reservation = reservationMapper.selectByExecutionId(context.task().id);
        if (reservation == null || !"RESERVED".equals(reservation.status)) {
            return;
        }
        AiPointReservationEntity settled = settlementService.finalizeOutcome(
            reservation.id,
            outcome,
            Map.of(AiUsageMetric.CALL, BigDecimal.valueOf(providerCallCount)),
            context.claim().attemptId(),
            invocation != null ? invocation.aiCallLogId() : agentCall == null ? null : agentCall.callLogId(),
            "execution:%d:v%d:%s".formatted(
                context.task().id,
                context.task().executionVersion,
                outcome.name().toLowerCase()
            )
        );
        executionService.updateSettlementSummary(settled);
    }

    private void settleTerminalFailure(
        AiExecutionContext context,
        WorkflowAgentModelCall agentCall,
        Long callLogId,
        int providerCallCount,
        int maxAttempts
    ) {
        AiExecutionAttemptEntity attempt = attemptMapper.selectById(context.claim().attemptId());
        if (attempt == null || attempt.attemptNo < maxAttempts) {
            return;
        }
        settle(context, null, agentCall,
            providerCallCount == 0 ? AiSettlementOutcome.PROVIDER_REJECTION
                : AiSettlementOutcome.PROVIDER_BILLED_FAILURE,
            providerCallCount);
    }

    private void markRunning(ScriptAiOperationEntity operation) {
        operation.status = "RUNNING";
        operation.updatedAt = LocalDateTime.now();
        operationMapper.updateById(operation);
    }

    private void markSucceeded(ScriptAiOperationEntity operation, ScriptAiOperationExecutionResult result) {
        operation.status = "SUCCEEDED";
        operation.resultType = result.resultType();
        operation.resultId = result.resultId();
        operation.completedAt = LocalDateTime.now();
        operation.updatedAt = operation.completedAt;
        operationMapper.updateById(operation);
    }

    private void markFailed(ScriptAiOperationEntity operation, RuntimeException exception) {
        operation.status = "FAILED";
        operation.errorCode = "SCRIPT_OPERATION_FAILED";
        operation.errorMessage = exception.getMessage();
        operation.completedAt = LocalDateTime.now();
        operation.updatedAt = operation.completedAt;
        operationMapper.updateById(operation);
    }

    private void markCanceled(ScriptAiOperationEntity operation) {
        operation.status = "CANCELED";
        operation.completedAt = LocalDateTime.now();
        operation.updatedAt = operation.completedAt;
        operationMapper.updateById(operation);
    }
}

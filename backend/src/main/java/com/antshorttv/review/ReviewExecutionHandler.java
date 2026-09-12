package com.antshorttv.review;

import com.antshorttv.accounting.AiUsageAccountingService;
import com.antshorttv.accounting.AiUsageCommand;
import com.antshorttv.accounting.AiUsageContext;
import com.antshorttv.accounting.AiUsageExtractor;
import com.antshorttv.accounting.AiUsageMetric;
import com.antshorttv.accounting.AiExecutionCostSummary;
import com.antshorttv.accounting.AiUsageCostStatus;
import com.antshorttv.ai.AiGatewayException;
import com.antshorttv.execution.AiExecutionAttemptEntity;
import com.antshorttv.execution.AiExecutionAttemptMapper;
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
import java.util.Map;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class ReviewExecutionHandler extends AiExecutionHandler {
    private final ReviewTaskMapper taskMapper;
    private final ReviewWorkbenchService reviewService;
    private final AiExecutionAttemptMapper attemptMapper;
    private final AiPointReservationMapper reservationMapper;
    private final AiPointSettlementService settlementService;
    private final AiExecutionService executionService;
    private final AiExecutionTaskMapper executionTaskMapper;
    private final AiUsageAccountingService usageAccountingService;
    private final AiUsageExtractor usageExtractor;
    private final WorkflowAgentRunRepository workflowAgentRuns;
    private final ObjectMapper objectMapper;

    public ReviewExecutionHandler(
        ReviewTaskMapper taskMapper,
        ReviewWorkbenchService reviewService,
        AiExecutionAttemptMapper attemptMapper,
        AiPointReservationMapper reservationMapper,
        AiPointSettlementService settlementService,
        AiExecutionService executionService,
        AiExecutionTaskMapper executionTaskMapper,
        AiUsageAccountingService usageAccountingService,
        AiUsageExtractor usageExtractor,
        WorkflowAgentRunRepository workflowAgentRuns,
        ObjectMapper objectMapper
    ) {
        this.taskMapper = taskMapper;
        this.reviewService = reviewService;
        this.attemptMapper = attemptMapper;
        this.reservationMapper = reservationMapper;
        this.settlementService = settlementService;
        this.executionService = executionService;
        this.executionTaskMapper = executionTaskMapper;
        this.usageAccountingService = usageAccountingService;
        this.usageExtractor = usageExtractor;
        this.workflowAgentRuns = workflowAgentRuns;
        this.objectMapper = objectMapper;
    }

    @Override
    public String scene() {
        return "script_review";
    }

    @Override
    public AiExecutionRetryPolicy retryPolicy() {
        return new AiExecutionRetryPolicy(3, Duration.ofSeconds(5));
    }

    @Override
    public void validate(AiExecutionTaskEntity execution) {
        ReviewTaskEntity task = taskMapper.selectById(execution.businessId);
        if (task == null || !execution.id.equals(task.getExecutionId())) {
            throw new IllegalStateException("Review task is not linked to execution " + execution.id);
        }
    }

    @Override
    public AiExecutionHandlerResult execute(AiExecutionContext context) {
        try {
            ReviewExecutionOutcome outcome = reviewService.executeTask(context.task().businessId, context);
            List<WorkflowAgentModelCall> persisted = workflowCallsForExecution(context);
            List<WorkflowAgentModelCall> billable = persisted.isEmpty() ? outcome.modelCalls() : persisted;
            if (!billable.isEmpty()) {
                latestCallForCurrentAttempt(context, billable)
                    .ifPresent(call -> markWorkflowAttempt(context, call));
                recordWorkflowUsageAndCost(context, billable);
            }
            settle(context, billable.size());
            return new AiExecutionHandlerResult("REVIEW_TASK", context.task().businessId);
        } catch (AiGatewayException exception) {
            WorkflowUsageRecovery recovery = recordFailedWorkflowUsage(context);
            if (recovery.callCount() == 0 && exception.getAiCallLogId() != null) {
                recovery = recordDirectGatewayFailureUsageAndCost(context, exception.getAiCallLogId());
            }
            markGatewayFailure(context, exception);
            settleTerminalFailure(
                context,
                exception.getAiCallLogId() == null
                    ? AiSettlementOutcome.PROVIDER_REJECTION
                    : AiSettlementOutcome.PROVIDER_BILLED_FAILURE,
                exception.getAiCallLogId(),
                recovery.callCount()
            );
            throw exception;
        } catch (RuntimeException exception) {
            WorkflowUsageRecovery recovery = recordFailedWorkflowUsage(context);
            settleTerminalFailure(context, AiSettlementOutcome.BUSINESS_FAILURE,
                recovery.lastCallId(), recovery.callCount());
            throw exception;
        }
    }

    private WorkflowUsageRecovery recordFailedWorkflowUsage(AiExecutionContext context) {
        java.util.List<WorkflowAgentModelCall> calls = workflowCallsForExecution(context);
        if (calls.isEmpty()) return new WorkflowUsageRecovery(null, 0);
        latestCallForCurrentAttempt(context, calls).ifPresent(call -> markWorkflowAttempt(context, call));
        recordWorkflowUsageAndCost(context, calls);
        return new WorkflowUsageRecovery(calls.get(calls.size() - 1).callLogId(), calls.size());
    }

    private WorkflowUsageRecovery recordDirectGatewayFailureUsageAndCost(
        AiExecutionContext context,
        Long callLogId
    ) {
        java.util.Optional<WorkflowAgentModelCall> persisted = workflowAgentRuns.modelCallForExecution(
            callLogId, context.task().id, context.task().tenantId);
        if (persisted.isEmpty() || persisted.get().modelId() == null) {
            return new WorkflowUsageRecovery(null, 0);
        }
        WorkflowAgentModelCall call = persisted.get();
        Set<AiUsageMetric> required = recordCallUsage(context, call.callLogId(), call.modelId(),
            call.attemptId(), call.promptTokens(), call.completionTokens(),
            call.cachedInputTokens(), call.cacheWriteTokens());
        AiExecutionCostSummary cost = usageAccountingService.priceExecution(context.task().id, required);
        if (hasIncompleteTokenClassification(call)) {
            cost = new AiExecutionCostSummary(cost.executionId(), AiUsageCostStatus.INCOMPLETE,
                cost.totalsByCurrency());
        }
        persistCostSummary(context, cost);
        return new WorkflowUsageRecovery(call.callLogId(), 1);
    }

    private java.util.Optional<WorkflowAgentModelCall> latestCallForCurrentAttempt(
        AiExecutionContext context,
        java.util.List<WorkflowAgentModelCall> calls
    ) {
        return calls.stream()
            .filter(call -> context.claim().attemptId().equals(call.attemptId()))
            .reduce((first, second) -> second);
    }

    private java.util.List<WorkflowAgentModelCall> workflowCallsForExecution(AiExecutionContext context) {
        return workflowAgentRuns.modelCallsForExecution(context.task().id, context.task().tenantId);
    }

    private void markWorkflowAttempt(AiExecutionContext context, WorkflowAgentModelCall call) {
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

    private void recordWorkflowUsageAndCost(AiExecutionContext context, java.util.List<WorkflowAgentModelCall> calls) {
        Set<AiUsageMetric> required = EnumSet.noneOf(AiUsageMetric.class);
        for (WorkflowAgentModelCall call : calls) {
            required.addAll(recordCallUsage(context, call.callLogId(), call.modelId(),
                call.attemptId(), call.promptTokens(), call.completionTokens(),
                call.cachedInputTokens(), call.cacheWriteTokens()));
        }
        AiExecutionCostSummary cost = usageAccountingService.priceExecution(context.task().id, required);
        if (calls.stream().anyMatch(this::hasIncompleteTokenClassification)) {
            cost = new AiExecutionCostSummary(cost.executionId(), AiUsageCostStatus.INCOMPLETE,
                cost.totalsByCurrency());
        }
        persistCostSummary(context, cost);
    }

    private void persistCostSummary(AiExecutionContext context, AiExecutionCostSummary cost) {
        try {
            executionTaskMapper.update(null, new UpdateWrapper<AiExecutionTaskEntity>()
                .set("usage_cost_status", cost.status().name())
                .set("provider_cost_summary_json", objectMapper.writeValueAsString(cost.totalsByCurrency()))
                .eq("id", context.task().id));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to persist review cost summary.", exception);
        }
    }

    private Set<AiUsageMetric> recordCallUsage(AiExecutionContext context, Long callLogId, Long modelId,
        Long providerAttemptId, Integer inputTokens, Integer outputTokens,
        Integer cachedInputTokens, Integer cacheWriteTokens) {
        LocalDateTime observedAt = LocalDateTime.now();
        AiUsageContext usageContext = new AiUsageContext(context.task().tenantId, context.task().id,
            providerAttemptId == null ? context.claim().attemptId() : providerAttemptId, callLogId, modelId);
        List<AiUsageCommand> commands = new java.util.ArrayList<>();
        commands.add(usageExtractor.requestCall(usageContext, observedAt));
        commands.addAll(usageExtractor.providerTokens(usageContext, inputTokens, outputTokens,
            cachedInputTokens, cacheWriteTokens, observedAt));
        commands.forEach(usageAccountingService::recordIfAbsent);
        Set<AiUsageMetric> metrics = EnumSet.of(AiUsageMetric.CALL);
        if (inputTokens != null) metrics.add(AiUsageMetric.INPUT_TOKEN);
        if (outputTokens != null) metrics.add(AiUsageMetric.OUTPUT_TOKEN);
        if (cachedInputTokens != null) metrics.add(AiUsageMetric.CACHED_INPUT_TOKEN);
        if (cacheWriteTokens != null) metrics.add(AiUsageMetric.CACHE_WRITE_TOKEN);
        return metrics;
    }

    private boolean hasIncompleteTokenClassification(WorkflowAgentModelCall call) {
        return hasIncompleteTokenClassification(
            call.promptTokens(), call.cachedInputTokens(), call.cacheWriteTokens());
    }

    private boolean hasIncompleteTokenClassification(
        Integer inputTokens,
        Integer cachedInputTokens,
        Integer cacheWriteTokens
    ) {
        return inputTokens != null && (cachedInputTokens == null) != (cacheWriteTokens == null);
    }

    private void markGatewayFailure(AiExecutionContext context, AiGatewayException exception) {
        UpdateWrapper<AiExecutionAttemptEntity> update = new UpdateWrapper<AiExecutionAttemptEntity>()
            .set("ai_call_log_id", exception.getAiCallLogId())
            .eq("id", context.claim().attemptId());
        if (exception.getAiCallLogId() != null) {
            update.set("provider_contacted", true)
                .set("provider_contacted_at", LocalDateTime.now());
        }
        attemptMapper.update(null, update);
    }

    private void settle(AiExecutionContext context, int workflowCallCount) {
        AiPointReservationEntity reservation = reservationMapper.selectByExecutionId(context.task().id);
        AiPointReservationEntity settled = settlementService.finalizeOutcome(
            reservation.id,
            AiSettlementOutcome.SUCCESS,
            callUsage(workflowCallCount),
            context.claim().attemptId(),
            null,
            "execution:%d:v%d:success".formatted(context.task().id, context.task().executionVersion)
        );
        executionService.updateSettlementSummary(settled);
    }

    private void settleTerminalFailure(
        AiExecutionContext context,
        AiSettlementOutcome outcome,
        Long callLogId,
        int callCount
    ) {
        AiExecutionAttemptEntity attempt = attemptMapper.selectById(context.claim().attemptId());
        if (attempt == null || attempt.attemptNo < retryPolicy().maxAttempts()) {
            return;
        }
        AiPointReservationEntity reservation = reservationMapper.selectByExecutionId(context.task().id);
        if (reservation == null || !"RESERVED".equals(reservation.status)) {
            return;
        }
        AiPointReservationEntity settled = settlementService.finalizeOutcome(
            reservation.id,
            outcome,
            callUsage(callCount),
            context.claim().attemptId(),
            callLogId,
            "execution:%d:v%d:failure".formatted(context.task().id, context.task().executionVersion)
        );
        executionService.updateSettlementSummary(settled);
    }

    private Map<AiUsageMetric, BigDecimal> callUsage(int callCount) {
        return Map.of(AiUsageMetric.CALL, BigDecimal.valueOf(Math.max(0, callCount)));
    }

    private record WorkflowUsageRecovery(Long lastCallId, int callCount) {}
}

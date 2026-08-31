package com.antshorttv.review;

import com.antshorttv.accounting.AiUsageAccountingService;
import com.antshorttv.accounting.AiUsageCommand;
import com.antshorttv.accounting.AiUsageContext;
import com.antshorttv.accounting.AiUsageMetric;
import com.antshorttv.ai.AiGatewayException;
import com.antshorttv.ai.AiInvocationResult;
import com.antshorttv.ai.AiTextResponse;
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
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
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
            AiInvocationResult<AiTextResponse> invocation = outcome.invocation();
            if (invocation != null) {
                markAttempt(context, invocation);
                recordUsageAndCost(context, invocation);
            } else if (!outcome.modelCalls().isEmpty()) {
                markWorkflowAttempt(context, outcome.modelCalls().get(outcome.modelCalls().size() - 1));
                recordWorkflowUsageAndCost(context, outcome.modelCalls());
            }
            settle(context, invocation, !outcome.modelCalls().isEmpty());
            return new AiExecutionHandlerResult("REVIEW_TASK", context.task().businessId);
        } catch (ReviewInvocationException exception) {
            markAttempt(context, exception.invocation());
            recordUsageAndCost(context, exception.invocation());
            settleTerminalFailure(
                context,
                AiSettlementOutcome.BUSINESS_FAILURE,
                exception.invocation().aiCallLogId()
            );
            throw exception;
        } catch (AiGatewayException exception) {
            markGatewayFailure(context, exception);
            settleTerminalFailure(
                context,
                exception.getAiCallLogId() == null
                    ? AiSettlementOutcome.PROVIDER_REJECTION
                    : AiSettlementOutcome.PROVIDER_BILLED_FAILURE,
                exception.getAiCallLogId()
            );
            throw exception;
        }
    }

    private void markAttempt(AiExecutionContext context, AiInvocationResult<AiTextResponse> invocation) {
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
        for (WorkflowAgentModelCall call : calls) {
            usageAccountingService.record(AiUsageCommand.requestDerived(
                new AiUsageContext(context.task().tenantId, context.task().id,
                    context.claim().attemptId(), call.callLogId(), call.modelId()),
                AiUsageMetric.CALL, "1", Map.of(), LocalDateTime.now()));
        }
        var cost = usageAccountingService.priceExecution(context.task().id, Set.of(AiUsageMetric.CALL));
        try {
            executionTaskMapper.update(null, new UpdateWrapper<AiExecutionTaskEntity>()
                .set("usage_cost_status", cost.status().name())
                .set("provider_cost_summary_json", objectMapper.writeValueAsString(cost.totalsByCurrency()))
                .eq("id", context.task().id));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to persist review cost summary.", exception);
        }
    }

    private void recordUsageAndCost(AiExecutionContext context, AiInvocationResult<AiTextResponse> invocation) {
        usageAccountingService.record(AiUsageCommand.requestDerived(
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
            LocalDateTime.now()
        ));
        var cost = usageAccountingService.priceExecution(context.task().id, Set.of(AiUsageMetric.CALL));
        try {
            executionTaskMapper.update(null, new UpdateWrapper<AiExecutionTaskEntity>()
                .set("usage_cost_status", cost.status().name())
                .set("provider_cost_summary_json", objectMapper.writeValueAsString(cost.totalsByCurrency()))
                .eq("id", context.task().id));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to persist review cost summary.", exception);
        }
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

    private void settle(AiExecutionContext context, AiInvocationResult<AiTextResponse> invocation, boolean workflowContacted) {
        AiPointReservationEntity reservation = reservationMapper.selectByExecutionId(context.task().id);
        AiPointReservationEntity settled = settlementService.finalizeOutcome(
            reservation.id,
            AiSettlementOutcome.SUCCESS,
            callUsage(invocation != null || workflowContacted),
            context.claim().attemptId(),
            invocation == null ? null : invocation.aiCallLogId(),
            "execution:%d:v%d:success".formatted(context.task().id, context.task().executionVersion)
        );
        executionService.updateSettlementSummary(settled);
    }

    private void settleTerminalFailure(
        AiExecutionContext context,
        AiSettlementOutcome outcome,
        Long callLogId
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
            callUsage(callLogId != null),
            context.claim().attemptId(),
            callLogId,
            "execution:%d:v%d:failure".formatted(context.task().id, context.task().executionVersion)
        );
        executionService.updateSettlementSummary(settled);
    }

    private Map<AiUsageMetric, BigDecimal> callUsage(boolean providerContacted) {
        return Map.of(AiUsageMetric.CALL, providerContacted ? BigDecimal.ONE : BigDecimal.ZERO);
    }
}

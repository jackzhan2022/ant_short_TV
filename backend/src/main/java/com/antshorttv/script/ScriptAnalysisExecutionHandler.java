package com.antshorttv.script;

import com.antshorttv.accounting.AiUsageMetric;
import com.antshorttv.accounting.AiUsageExtractor;
import com.antshorttv.accounting.AiTextUsage;
import com.antshorttv.accounting.AiUsageCostStatus;
import com.antshorttv.workflowagent.run.WorkflowAgentModelCall;
import java.util.List;
import com.antshorttv.accounting.AiExecutionCostSummary;
import com.antshorttv.accounting.AiUsageAccountingService;
import com.antshorttv.accounting.AiUsageContext;
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
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Set;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class ScriptAnalysisExecutionHandler extends AiExecutionHandler {
    private final ScriptAnalysisTaskMapper taskMapper;
    private final ScriptAnalysisExecutionService analysisService;
    private final AiPointReservationMapper reservationMapper;
    private final AiPointSettlementService settlementService;
    private final AiExecutionService executionService;
    private final AiExecutionAttemptMapper attemptMapper;
    private final AiExecutionTaskMapper executionTaskMapper;
    private final AiUsageAccountingService usageAccountingService;
    private final ObjectMapper objectMapper;
    private final com.antshorttv.workflowagent.run.WorkflowAgentRunRepository workflowAgentRuns;

    public ScriptAnalysisExecutionHandler(
        ScriptAnalysisTaskMapper taskMapper,
        ScriptAnalysisExecutionService analysisService,
        AiPointReservationMapper reservationMapper,
        AiPointSettlementService settlementService,
        AiExecutionService executionService,
        AiExecutionAttemptMapper attemptMapper,
        AiExecutionTaskMapper executionTaskMapper,
        AiUsageAccountingService usageAccountingService,
        ObjectMapper objectMapper,
        com.antshorttv.workflowagent.run.WorkflowAgentRunRepository workflowAgentRuns
    ) {
        this.taskMapper = taskMapper;
        this.analysisService = analysisService;
        this.reservationMapper = reservationMapper;
        this.settlementService = settlementService;
        this.executionService = executionService;
        this.attemptMapper = attemptMapper;
        this.executionTaskMapper = executionTaskMapper;
        this.usageAccountingService = usageAccountingService;
        this.objectMapper = objectMapper;
        this.workflowAgentRuns = workflowAgentRuns;
    }

    private boolean tokenPricing(AiExecutionContext context) {
        AiPointReservationEntity reservation = reservationMapper.selectByExecutionId(context.task().id);
        return reservation == null || settlementService.usesTokenPricing(reservation.id);
    }

    @Override
    public String scene() {
        return ScriptAnalysisExecutionCoordinator.SCENE;
    }

    @Override
    public AiExecutionRetryPolicy retryPolicy() {
        return new AiExecutionRetryPolicy(3, Duration.ofSeconds(5));
    }

    @Override
    public AiExecutionRetryPolicy retryPolicy(Throwable failure) {
        return failure instanceof NonRetryableAssetNormalizationException
            ? AiExecutionRetryPolicy.none() : retryPolicy();
    }

    @Override
    public void validate(AiExecutionTaskEntity execution) {
        ScriptAnalysisTaskEntity task = taskMapper.selectById(execution.businessId);
        if (task == null || !execution.id.equals(task.getExecutionId())) {
            throw new IllegalStateException("Script analysis task is not linked to execution " + execution.id);
        }
    }

    @Override
    public AiExecutionHandlerResult execute(AiExecutionContext context) {
        try {
            ScriptAnalysisExecutionOutcome outcome = analysisService.executeTask(context.task().businessId, context);
            List<WorkflowAgentModelCall> calls = callsForExecution(context, outcome);
            recordUsageAndAttempt(context, calls);
            settle(context, calls, AiSettlementOutcome.SUCCESS);
            return new AiExecutionHandlerResult("SCRIPT_ANALYSIS_TASK", context.task().businessId);
        } catch (com.antshorttv.execution.AiExecutionDeferredException | com.antshorttv.execution.AiExecutionClaimLostException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            List<WorkflowAgentModelCall> calls = callsForExecution(context, new ScriptAnalysisExecutionOutcome(List.of()));
            if (!calls.isEmpty()) recordUsageAndAttempt(context, calls);
            AiExecutionAttemptEntity attempt = attemptMapper.selectById(context.claim().attemptId());
            if (attempt != null && attempt.attemptNo >= retryPolicy(exception).maxAttempts()) {
                settle(context, calls, calls.isEmpty() ? AiSettlementOutcome.PROVIDER_REJECTION : AiSettlementOutcome.BUSINESS_FAILURE);
            }
            throw exception;
        }
    }

    private List<WorkflowAgentModelCall> callsForExecution(AiExecutionContext context, ScriptAnalysisExecutionOutcome outcome) {
        List<WorkflowAgentModelCall> calls = new java.util.ArrayList<>(
            workflowAgentRuns.modelCallsForExecution(context.task().id, context.task().tenantId));
        for (ScriptAnalysisCallEvidence call : outcome.calls()) {
            calls.add(new WorkflowAgentModelCall(call.callLogId(), call.modelId(), call.providerId(),
                call.providerRequestId(), call.transportOutcome(), call.businessOutcome(), context.claim().attemptId()));
        }
        return AiTextUsage.distinctCalls(calls);
    }

    private void settle(AiExecutionContext context, List<WorkflowAgentModelCall> calls, AiSettlementOutcome outcome) {
        AiPointReservationEntity reservation = reservationMapper.selectByExecutionId(context.task().id);
        AiPointReservationEntity settled = settlementService.finalizeCumulativeOutcome(
            reservation.id, outcome, AiTextUsage.totals(calls), context.claim().attemptId(),
            calls.isEmpty() ? null : calls.get(calls.size() - 1).callLogId(),
            "execution:%d:v%d:attempt:%d:%s".formatted(context.task().id, context.task().executionVersion,
                context.claim().attemptId(), outcome.name().toLowerCase())
        );
        executionService.updateSettlementSummary(settled);
    }

    private void recordUsageAndAttempt(AiExecutionContext context, List<WorkflowAgentModelCall> calls) {
        LocalDateTime observedAt = LocalDateTime.now();
        AiUsageExtractor extractor = new AiUsageExtractor();
        boolean tokens = tokenPricing(context);
        Set<AiUsageMetric> required = java.util.EnumSet.of(AiUsageMetric.CALL);
        for (WorkflowAgentModelCall call : calls) {
            AiUsageContext usageContext = new AiUsageContext(context.task().tenantId, context.task().id,
                call.attemptId() == null ? context.claim().attemptId() : call.attemptId(), call.callLogId(), call.modelId());
            usageAccountingService.recordIfAbsent(extractor.requestCall(usageContext, observedAt));
            if (tokens && AiTextUsage.complete(call)) {
                extractor.providerTokens(usageContext, call.promptTokens(), call.completionTokens(),
                    call.cachedInputTokens(), call.cacheWriteTokens(), observedAt).forEach(command -> {
                        usageAccountingService.recordIfAbsent(command);
                        required.add(command.metric());
                    });
            }
        }
        AiExecutionCostSummary cost = usageAccountingService.priceExecution(
            context.task().id,
            required
        );
        if (tokens && calls.stream().anyMatch(call -> !AiTextUsage.complete(call))) {
            cost = new AiExecutionCostSummary(cost.executionId(), AiUsageCostStatus.INCOMPLETE, cost.totalsByCurrency());
        }
        try {
            executionTaskMapper.update(null, new UpdateWrapper<AiExecutionTaskEntity>()
                .set("usage_cost_status", cost.status().name())
                .set("provider_cost_summary_json", objectMapper.writeValueAsString(cost.totalsByCurrency()))
                .eq("id", context.task().id));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to persist script analysis cost summary.", exception);
        }
        WorkflowAgentModelCall last = calls.stream()
            .filter(call -> context.claim().attemptId().equals(call.attemptId()))
            .reduce((first, second) -> second).orElse(null);
        if (last != null) {
            attemptMapper.update(null, new UpdateWrapper<AiExecutionAttemptEntity>()
                .set("provider_contacted", true)
                .set("provider_contacted_at", observedAt)
                .set("provider_id", last.providerId())
                .set("model_id", last.modelId())
                .set("provider_request_id", last.providerRequestId())
                .set("ai_call_log_id", last.callLogId())
                .set("transport_outcome", last.transportOutcome())
                .set("business_outcome", last.businessOutcome())
                .eq("id", context.claim().attemptId()));
        }
    }
}

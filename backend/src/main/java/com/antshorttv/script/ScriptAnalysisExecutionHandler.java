package com.antshorttv.script;

import com.antshorttv.accounting.AiUsageMetric;
import com.antshorttv.accounting.AiExecutionCostSummary;
import com.antshorttv.accounting.AiUsageAccountingService;
import com.antshorttv.accounting.AiUsageCommand;
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
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
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

    public ScriptAnalysisExecutionHandler(
        ScriptAnalysisTaskMapper taskMapper,
        ScriptAnalysisExecutionService analysisService,
        AiPointReservationMapper reservationMapper,
        AiPointSettlementService settlementService,
        AiExecutionService executionService,
        AiExecutionAttemptMapper attemptMapper,
        AiExecutionTaskMapper executionTaskMapper,
        AiUsageAccountingService usageAccountingService,
        ObjectMapper objectMapper
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
    public void validate(AiExecutionTaskEntity execution) {
        ScriptAnalysisTaskEntity task = taskMapper.selectById(execution.businessId);
        if (task == null || !execution.id.equals(task.getExecutionId())) {
            throw new IllegalStateException("Script analysis task is not linked to execution " + execution.id);
        }
    }

    @Override
    public AiExecutionHandlerResult execute(AiExecutionContext context) {
        ScriptAnalysisExecutionOutcome outcome = analysisService.executeTask(context.task().businessId, context);
        recordUsageAndAttempt(context, outcome);
        AiPointReservationEntity reservation = reservationMapper.selectByExecutionId(context.task().id);
        AiPointReservationEntity settled = settlementService.finalizeOutcome(
            reservation.id,
            AiSettlementOutcome.SUCCESS,
            Map.of(AiUsageMetric.CALL, BigDecimal.valueOf(outcome.providerCallCount())),
            context.claim().attemptId(),
            outcome.lastCallLogId(),
            "execution:%d:v%d:success".formatted(context.task().id, context.task().executionVersion)
        );
        executionService.updateSettlementSummary(settled);
        return new AiExecutionHandlerResult("SCRIPT_ANALYSIS_TASK", context.task().businessId);
    }

    private void recordUsageAndAttempt(AiExecutionContext context, ScriptAnalysisExecutionOutcome outcome) {
        LocalDateTime observedAt = LocalDateTime.now();
        for (ScriptAnalysisCallEvidence call : outcome.calls()) {
            usageAccountingService.record(AiUsageCommand.requestDerived(
                new AiUsageContext(
                    context.task().tenantId, context.task().id, context.claim().attemptId(),
                    call.callLogId(), call.modelId()
                ),
                AiUsageMetric.CALL,
                "1",
                Map.of(),
                observedAt
            ));
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
            throw new IllegalStateException("Unable to persist script analysis cost summary.", exception);
        }
        ScriptAnalysisCallEvidence last = outcome.lastCall();
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

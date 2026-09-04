package com.antshorttv.accounting;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.antshorttv.execution.AiExecutionTaskEntity;
import com.antshorttv.execution.AiExecutionTaskMapper;

@Service
public class AiUsageAccountingService {
    private static final int COST_SCALE = 8;

    private final AiUsageLineMapper usageLineMapper;
    private final AiUsageCostLineMapper costLineMapper;
    private final AiModelPriceComponentMapper priceComponentMapper;
    private final AiModelPriceVersionMapper priceVersionMapper;
    private final AiModelPriceResolver priceResolver;
    private final AiExecutionTaskMapper executionTaskMapper;

    public AiUsageAccountingService(
        AiUsageLineMapper usageLineMapper,
        AiUsageCostLineMapper costLineMapper,
        AiModelPriceComponentMapper priceComponentMapper,
        AiModelPriceVersionMapper priceVersionMapper,
        AiModelPriceResolver priceResolver,
        AiExecutionTaskMapper executionTaskMapper
    ) {
        this.usageLineMapper = usageLineMapper;
        this.costLineMapper = costLineMapper;
        this.priceComponentMapper = priceComponentMapper;
        this.priceVersionMapper = priceVersionMapper;
        this.priceResolver = priceResolver;
        this.executionTaskMapper = executionTaskMapper;
    }

    @Transactional
    public AiUsageLineEntity record(AiUsageCommand command) {
        AiUsageLineEntity line = new AiUsageLineEntity();
        line.tenantId = command.context().tenantId();
        line.executionId = command.context().executionId();
        line.attemptId = command.context().attemptId();
        line.aiCallLogId = command.context().aiCallLogId();
        line.modelId = command.context().modelId();
        line.metric = command.metric().name();
        line.quantity = command.quantity();
        line.unit = command.metric().unit();
        line.source = command.source().name();
        line.dimensionsJson = AiAccountingJson.write(command.dimensions());
        line.dimensionsKey = AiAccountingJson.canonicalKey(command.dimensions());
        line.observedAt = command.observedAt();
        line.adjustmentOfUsageLineId = command.adjustmentOfUsageLineId();
        line.createdAt = LocalDateTime.now();
        usageLineMapper.insert(line);
        return line;
    }

    @Transactional
    public AiUsageLineEntity recordIfAbsent(AiUsageCommand command) {
        AiUsageContext context = command.context();
        String dimensionsKey = AiAccountingJson.canonicalKey(command.dimensions());
        AiUsageLineEntity existing = usageLineMapper.selectOne(new QueryWrapper<AiUsageLineEntity>()
            .eq("tenant_id", context.tenantId())
            .eq("execution_id", context.executionId())
            .eq("attempt_id", context.attemptId())
            .eq("ai_call_log_id", context.aiCallLogId())
            .eq("model_id", context.modelId())
            .eq("metric", command.metric().name())
            .eq("source", command.source().name())
            .eq("dimensions_key", dimensionsKey)
            .isNull("adjustment_of_usage_line_id")
            .last("limit 1"));
        return existing == null ? record(command) : existing;
    }

    @Transactional
    public AiUsageLineEntity adjustQuantity(
        Long originalUsageLineId,
        BigDecimal correctedQuantity,
        LocalDateTime observedAt
    ) {
        AiUsageLineEntity original = requireUsageLine(originalUsageLineId);
        BigDecimal currentNet = usageLineMapper.selectByExecutionId(original.executionId).stream()
            .filter(line -> originalUsageLineId.equals(line.id)
                || originalUsageLineId.equals(line.adjustmentOfUsageLineId))
            .map(line -> line.quantity)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal adjustment = correctedQuantity.subtract(currentNet);
        return record(new AiUsageCommand(
            new AiUsageContext(
                original.tenantId, original.executionId, original.attemptId,
                original.aiCallLogId, original.modelId
            ),
            AiUsageMetric.valueOf(original.metric),
            adjustment,
            AiUsageSource.ADJUSTMENT,
            AiAccountingJson.read(original.dimensionsJson),
            observedAt,
            original.id
        ));
    }

    @Transactional
    public AiExecutionCostSummary priceExecution(Long executionId, Set<AiUsageMetric> requiredMetrics) {
        List<AiUsageLineEntity> usageLines = usageLineMapper.selectByExecutionId(executionId);
        for (AiUsageLineEntity usageLine : usageLines) {
            if (costLineMapper.selectByUsageLineId(usageLine.id) == null) {
                costLineMapper.insert(price(usageLine));
            }
        }

        Set<AiUsageMetric> presentMetrics = EnumSet.noneOf(AiUsageMetric.class);
        usageLines.forEach(line -> presentMetrics.add(AiUsageMetric.valueOf(line.metric)));
        List<AiUsageCostLineEntity> costLines = costLineMapper.selectByExecutionId(executionId);
        AiUsageCostStatus status;
        if (!presentMetrics.containsAll(requiredMetrics)) {
            status = AiUsageCostStatus.INCOMPLETE;
        } else if (costLines.stream().anyMatch(line -> AiUsageCostStatus.UNPRICED.name().equals(line.pricingStatus))) {
            status = AiUsageCostStatus.UNPRICED;
        } else {
            status = AiUsageCostStatus.PRICED;
        }
        return new AiExecutionCostSummary(executionId, status, totals(costLines));
    }

    public AiUsageCostReconciliation reconcileExecution(Long executionId) {
        int usageCount = usageLineMapper.selectByExecutionId(executionId).size();
        int costCount = costLineMapper.selectByExecutionId(executionId).size();
        return new AiUsageCostReconciliation(executionId, usageCount, costCount, usageCount == costCount);
    }

    public Map<String, BigDecimal> summarizeCallLog(Long callLogId) {
        return totals(costLineMapper.selectByCallLogId(callLogId));
    }

    private AiUsageCostLineEntity price(AiUsageLineEntity usageLine) {
        AiResolvedPrice resolvedPrice = resolvePrice(usageLine);
        AiUsageCostLineEntity cost = baseCost(usageLine);
        if (resolvedPrice == null) {
            cost.pricingStatus = AiUsageCostStatus.UNPRICED.name();
            cost.missingReason = "No effective price for model=" + usageLine.modelId
                + ", metric=" + usageLine.metric
                + ", dimensions=" + usageLine.dimensionsKey;
            return cost;
        }

        AiModelPriceComponentEntity component = resolvedPrice.component();
        cost.priceVersionId = resolvedPrice.version().id;
        cost.priceComponentId = component.id;
        cost.unitSize = component.unitSize;
        cost.unitPrice = component.unitPrice;
        cost.currency = component.currency;
        cost.rawCost = usageLine.quantity
            .divide(component.unitSize, 12, RoundingMode.HALF_UP)
            .multiply(component.unitPrice)
            .setScale(12, RoundingMode.HALF_UP);
        cost.roundedCost = cost.rawCost.setScale(COST_SCALE, RoundingMode.HALF_UP);
        cost.pricingStatus = AiUsageCostStatus.PRICED.name();
        if (usageLine.adjustmentOfUsageLineId != null) {
            AiUsageCostLineEntity originalCost = costLineMapper.selectByUsageLineId(usageLine.adjustmentOfUsageLineId);
            cost.adjustmentOfCostLineId = originalCost == null ? null : originalCost.id;
        }
        return cost;
    }

    private AiResolvedPrice resolvePrice(AiUsageLineEntity usageLine) {
        if (usageLine.adjustmentOfUsageLineId != null) {
            AiUsageCostLineEntity originalCost = costLineMapper.selectByUsageLineId(usageLine.adjustmentOfUsageLineId);
            if (originalCost != null && originalCost.priceComponentId != null) {
                AiModelPriceComponentEntity component = priceComponentMapper.selectById(originalCost.priceComponentId);
                AiModelPriceVersionEntity version = priceVersionMapper.selectById(originalCost.priceVersionId);
                return new AiResolvedPrice(version, component);
            }
        }
        AiExecutionTaskEntity execution = executionTaskMapper.selectById(usageLine.executionId);
        if (execution != null && execution.costPriceVersionId != null) {
            AiModelPriceVersionEntity version = priceVersionMapper.selectById(execution.costPriceVersionId);
            AiModelPriceComponentEntity component = priceComponentMapper
                .selectByVersionAndMetric(execution.costPriceVersionId, usageLine.metric)
                .stream()
                .filter(candidate -> matches(
                    AiAccountingJson.read(candidate.dimensionsJson),
                    AiAccountingJson.read(usageLine.dimensionsJson)
                ))
                .max(Comparator.comparingInt(candidate ->
                    AiAccountingJson.read(candidate.dimensionsJson).size()))
                .orElse(null);
            return version == null || component == null ? null : new AiResolvedPrice(version, component);
        }
        return priceResolver.resolve(
            usageLine.modelId,
            AiUsageMetric.valueOf(usageLine.metric),
            AiAccountingJson.read(usageLine.dimensionsJson),
            usageLine.observedAt
        );
    }

    private boolean matches(Map<String, String> priceDimensions, Map<String, String> usageDimensions) {
        return priceDimensions.entrySet().stream()
            .allMatch(entry -> entry.getValue().equals(usageDimensions.get(entry.getKey())));
    }

    private AiUsageCostLineEntity baseCost(AiUsageLineEntity usageLine) {
        AiUsageCostLineEntity cost = new AiUsageCostLineEntity();
        cost.tenantId = usageLine.tenantId;
        cost.executionId = usageLine.executionId;
        cost.attemptId = usageLine.attemptId;
        cost.aiCallLogId = usageLine.aiCallLogId;
        cost.usageLineId = usageLine.id;
        cost.modelId = usageLine.modelId;
        cost.metric = usageLine.metric;
        cost.quantity = usageLine.quantity;
        cost.createdAt = LocalDateTime.now();
        return cost;
    }

    private Map<String, BigDecimal> totals(List<AiUsageCostLineEntity> costLines) {
        Map<String, BigDecimal> totals = new HashMap<>();
        costLines.stream()
            .filter(line -> AiUsageCostStatus.PRICED.name().equals(line.pricingStatus))
            .forEach(line -> totals.merge(line.currency, line.roundedCost, BigDecimal::add));
        totals.replaceAll((currency, value) -> value.setScale(COST_SCALE, RoundingMode.HALF_UP));
        return Map.copyOf(totals);
    }

    private AiUsageLineEntity requireUsageLine(Long usageLineId) {
        AiUsageLineEntity line = usageLineMapper.selectById(usageLineId);
        if (line == null) {
            throw new IllegalArgumentException("Usage line not found: " + usageLineId);
        }
        return line;
    }
}

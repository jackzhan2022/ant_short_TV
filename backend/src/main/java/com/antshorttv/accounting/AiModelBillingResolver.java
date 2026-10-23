package com.antshorttv.accounting;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.Map;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class AiModelBillingResolver {
    private final AiModelPriceVersionMapper costVersionMapper;
    private final AiModelPriceComponentMapper costComponentMapper;
    private final AiModelPointPriceVersionMapper pointVersionMapper;
    private final AiModelPointPriceComponentMapper pointComponentMapper;

    public AiModelBillingResolver(
        AiModelPriceVersionMapper costVersionMapper,
        AiModelPriceComponentMapper costComponentMapper,
        AiModelPointPriceVersionMapper pointVersionMapper,
        AiModelPointPriceComponentMapper pointComponentMapper
    ) {
        this.costVersionMapper = costVersionMapper;
        this.costComponentMapper = costComponentMapper;
        this.pointVersionMapper = pointVersionMapper;
        this.pointComponentMapper = pointComponentMapper;
    }

    public ModelBillingSnapshot requireComplete(
        Long modelId,
        Set<AiUsageMetric> metrics,
        Map<String, String> dimensions,
        LocalDateTime at
    ) {
        if (modelId == null) {
            throw new ModelBillingMissingException("Model billing requires a resolved model.");
        }
        if (metrics == null || metrics.isEmpty()) {
            throw new ModelBillingMissingException("Model billing requires at least one usage metric.");
        }
        AiModelPriceVersionEntity costVersion = costVersionMapper.selectEffective(modelId, at);
        if (costVersion == null) {
            throw new ModelBillingMissingException("No effective model cost price.");
        }
        AiModelPointPriceVersionEntity pointVersion = pointVersionMapper.selectEffective(modelId, at);
        if (pointVersion == null) {
            throw new ModelBillingMissingException("No effective model point price.");
        }
        Map<AiUsageMetric, AiModelPriceComponentEntity> costs = new EnumMap<>(AiUsageMetric.class);
        Map<AiUsageMetric, AiModelPointPriceComponentEntity> points = new EnumMap<>(AiUsageMetric.class);
        var costComponents = costComponentMapper.selectByVersion(costVersion.id);
        var pointComponents = pointComponentMapper.selectByVersion(pointVersion.id);
        for (AiUsageMetric metric : metrics) {
            costs.put(metric, bestCost(costComponents, metric, dimensions));
            points.put(metric, bestPoint(pointComponents, metric, dimensions));
        }
        return new ModelBillingSnapshot(costVersion.id, pointVersion.id, Map.copyOf(costs), Map.copyOf(points));
    }

    private AiModelPriceComponentEntity bestCost(List<AiModelPriceComponentEntity> components, AiUsageMetric metric, Map<String, String> dimensions) {
        return components.stream()
            .filter(component -> metric.name().equals(component.metric))
            .filter(component -> matches(component.dimensionsJson, dimensions))
            .max(Comparator.comparingInt(component -> AiAccountingJson.read(component.dimensionsJson).size()))
            .orElseThrow(() -> new ModelBillingMissingException("No effective model cost price for " + metric));
    }

    private AiModelPointPriceComponentEntity bestPoint(List<AiModelPointPriceComponentEntity> components, AiUsageMetric metric, Map<String, String> dimensions) {
        return components.stream()
            .filter(component -> metric.name().equals(component.metric))
            .filter(component -> matches(component.dimensionsJson, dimensions))
            .max(Comparator.comparingInt(component -> AiAccountingJson.read(component.dimensionsJson).size()))
            .orElseThrow(() -> new ModelBillingMissingException("No effective model point price for " + metric));
    }

    private boolean matches(String json, Map<String, String> dimensions) {
        return AiAccountingJson.read(json).entrySet().stream()
            .allMatch(entry -> entry.getValue().equals(dimensions.get(entry.getKey())));
    }
}

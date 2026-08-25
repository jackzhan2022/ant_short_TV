package com.antshorttv.accounting;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class AiModelPriceResolver {
    private final AiModelPriceVersionMapper versionMapper;
    private final AiModelPriceComponentMapper componentMapper;

    public AiModelPriceResolver(
        AiModelPriceVersionMapper versionMapper,
        AiModelPriceComponentMapper componentMapper
    ) {
        this.versionMapper = versionMapper;
        this.componentMapper = componentMapper;
    }

    public AiResolvedPrice resolve(
        Long modelId,
        AiUsageMetric metric,
        Map<String, String> usageDimensions,
        LocalDateTime observedAt
    ) {
        AiModelPriceVersionEntity version = versionMapper.selectEffective(modelId, observedAt);
        if (version == null) {
            return null;
        }
        AiModelPriceComponentEntity component = componentMapper
            .selectByVersionAndMetric(version.id, metric.name())
            .stream()
            .filter(candidate -> matches(AiAccountingJson.read(candidate.dimensionsJson), usageDimensions))
            .max(Comparator.comparingInt(candidate -> AiAccountingJson.read(candidate.dimensionsJson).size()))
            .orElse(null);
        return component == null ? null : new AiResolvedPrice(version, component);
    }

    private boolean matches(Map<String, String> priceDimensions, Map<String, String> usageDimensions) {
        return priceDimensions.entrySet().stream()
            .allMatch(entry -> entry.getValue().equals(usageDimensions.get(entry.getKey())));
    }
}

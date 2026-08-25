package com.antshorttv.accounting;

import java.math.BigDecimal;
import java.util.Map;

public record AiExecutionCostSummary(
    Long executionId,
    AiUsageCostStatus status,
    Map<String, BigDecimal> totalsByCurrency
) {
}

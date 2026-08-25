package com.antshorttv.accounting;

import java.math.BigDecimal;
import java.util.List;

public final class PlatformAiOperationsResponses {
    private PlatformAiOperationsResponses() {}

    public record ProviderFailureRate(String provider, long total, long failed, BigDecimal failureRate) {}

    public record PlatformAiOperationsOverview(
        long expiredClaims,
        long retryExhausted,
        long unpricedUsage,
        long incompleteUsage,
        long settlementReview,
        BigDecimal totalProviderCost,
        BigDecimal totalSettledPoints,
        List<ProviderFailureRate> providerFailureRates
    ) {}
}

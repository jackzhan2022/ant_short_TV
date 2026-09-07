package com.antshorttv.review;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public record ReviewWorkflowFeatureFlags(
    boolean cacheObservability,
    boolean dimensionalOrchestration,
    boolean semanticReview,
    boolean anomalyGate
) {
    public ReviewWorkflowFeatureFlags(
        @Value("${review.workflow.features.cache-observability:false}") boolean cacheObservability,
        @Value("${review.workflow.features.dimensional-orchestration:false}") boolean dimensionalOrchestration,
        @Value("${review.workflow.features.semantic-review:false}") boolean semanticReview,
        @Value("${review.workflow.features.anomaly-gate:false}") boolean anomalyGate
    ) {
        this.cacheObservability = cacheObservability;
        this.dimensionalOrchestration = dimensionalOrchestration;
        this.semanticReview = semanticReview;
        this.anomalyGate = anomalyGate;
    }
}

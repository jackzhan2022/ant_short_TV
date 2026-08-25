package com.antshorttv.ai;

import java.time.Duration;

public record AiProviderExecutionOutcome<T>(
    AiProviderExecutionState outcome,
    T response,
    String providerRequestId,
    String externalTaskId,
    Duration pollAfter,
    AiProviderReconciliationStatus reconciliationStatus
) {
    public static <T> AiProviderExecutionOutcome<T> completed(T response, String providerRequestId) {
        return new AiProviderExecutionOutcome<>(
            AiProviderExecutionState.COMPLETED,
            response,
            providerRequestId,
            null,
            null,
            AiProviderReconciliationStatus.NOT_REQUIRED
        );
    }

    public static <T> AiProviderExecutionOutcome<T> accepted(
        String providerRequestId,
        String externalTaskId,
        Duration pollAfter,
        AiProviderReconciliationStatus reconciliationStatus
    ) {
        return new AiProviderExecutionOutcome<>(
            AiProviderExecutionState.ACCEPTED,
            null,
            providerRequestId,
            externalTaskId,
            pollAfter,
            reconciliationStatus
        );
    }
}

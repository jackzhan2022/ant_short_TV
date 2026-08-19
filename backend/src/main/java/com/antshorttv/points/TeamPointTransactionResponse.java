package com.antshorttv.points;

import java.time.LocalDateTime;

public record TeamPointTransactionResponse(
    Long id,
    Long tenantId,
    Long userId,
    String transactionType,
    Integer changeAmount,
    Integer balanceAfter,
    String businessScene,
    Long businessId,
    String description,
    LocalDateTime createdAt
) {
}

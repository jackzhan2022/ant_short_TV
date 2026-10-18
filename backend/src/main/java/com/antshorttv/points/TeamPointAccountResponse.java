package com.antshorttv.points;

import java.time.LocalDateTime;
import java.math.BigDecimal;

public record TeamPointAccountResponse(
    Long tenantId,
    Integer balance,
    BigDecimal trialBalance,
    Integer totalGranted,
    Integer totalConsumed,
    LocalDateTime updatedAt
) {
}

package com.antshorttv.points;

import java.time.LocalDateTime;

public record TeamPointAccountResponse(
    Long tenantId,
    Integer balance,
    Integer totalGranted,
    Integer totalConsumed,
    LocalDateTime updatedAt
) {
}

package com.antshorttv.points;

import java.math.BigDecimal;

public record AiPointReconciliation(
    Long tenantId,
    BigDecimal accountAvailable,
    BigDecimal ledgerAvailable,
    BigDecimal accountReserved,
    BigDecimal ledgerReserved,
    boolean matches
) {
}

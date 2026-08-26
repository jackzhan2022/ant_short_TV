package com.antshorttv.accounting;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

record PublishModelPriceRequest(
    @NotNull LocalDateTime effectiveFrom,
    LocalDateTime effectiveTo,
    @NotEmpty List<@Valid ModelPriceComponentRequest> components
) {
}

record ModelPriceComponentRequest(
    @NotBlank String metric,
    @NotNull @Positive BigDecimal unitSize,
    @NotNull @PositiveOrZero BigDecimal unitPrice,
    @NotBlank String currency,
    Map<String, String> dimensions
) {
}

record PublishModelPointPriceRequest(
    @NotNull LocalDateTime effectiveFrom,
    LocalDateTime effectiveTo,
    @NotEmpty List<@Valid PointPolicyComponentRequest> components
) {
}

record PointPolicyComponentRequest(
    @NotBlank String metric,
    @NotNull @Positive BigDecimal unitSize,
    @NotNull @PositiveOrZero BigDecimal pointRate,
    Map<String, String> dimensions
) {
}

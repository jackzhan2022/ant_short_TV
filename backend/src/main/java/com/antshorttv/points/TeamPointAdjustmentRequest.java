package com.antshorttv.points;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TeamPointAdjustmentRequest(
    @NotNull @Min(-1000000) @Max(1000000) Integer amount,
    @Size(max = 500) String description
) {
}

package com.antshorttv.execution;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record AiConcurrencySettings(
    @Min(1) @Max(10000) int teamTextLimit,
    @Min(1) @Max(10000) int teamImageLimit,
    @Min(1) @Max(10000) int teamVideoLimit,
    @Min(1) @Max(10000) int modelLimit,
    @Min(1) @Max(10000) int providerLimit,
    @Min(1) @Max(200) int textWorkers,
    @Min(1) @Max(200) int imageWorkers,
    @Min(1) @Max(200) int videoSubmitWorkers,
    @Min(1) @Max(200) int videoQueryWorkers
) { }

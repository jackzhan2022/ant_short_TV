package com.antshorttv.execution;

import jakarta.validation.constraints.NotBlank;

public record AiExecutionRegenerateRequest(
    @NotBlank String clientIdempotencyKey,
    @NotBlank String traceId
) {
}

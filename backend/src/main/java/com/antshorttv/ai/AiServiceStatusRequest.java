package com.antshorttv.ai;

import jakarta.validation.constraints.NotNull;

public record AiServiceStatusRequest(
    @NotNull
    Boolean enabled
) {
}

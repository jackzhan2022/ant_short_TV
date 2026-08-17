package com.antshorttv.ai;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AiServiceConfigRequest(
    @NotBlank
    @Size(max = 100)
    String name,

    @NotBlank
    String serviceType,

    @NotBlank
    String provider,

    @NotBlank
    @Size(max = 500)
    String baseUrl,

    @Size(max = 500)
    String apiKey,

    @NotBlank
    @Size(max = 200)
    String model,

    @Size(max = 300)
    String endpoint,

    @Size(max = 300)
    String queryEndpoint,

    @NotNull
    @Min(1)
    @Max(999)
    Integer priority,

    Boolean isDefault,

    Boolean enabled,

    @Size(max = 500)
    String remark
) {
}

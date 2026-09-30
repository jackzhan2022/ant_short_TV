package com.antshorttv.storage;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

public record CosUploadAuthorizationRequest(
    @NotBlank String method,
    @NotBlank String pathname,
    Map<String, String> query,
    Map<String, String> headers
) {
}

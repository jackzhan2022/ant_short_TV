package com.antshorttv.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record SendVerificationCodeRequest(
    @NotBlank
    @Pattern(regexp = "^1\\d{10}$")
    String mobile
) {
}

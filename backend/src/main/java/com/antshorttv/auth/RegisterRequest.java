package com.antshorttv.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
    @NotBlank
    @Pattern(regexp = "^1\\d{10}$")
    String mobile,

    @NotBlank
    String verificationCode,

    @NotBlank
    @Size(min = 1, max = 64)
    String nickname,

    @NotBlank
    @Size(min = 8, max = 64)
    String password
) {
}

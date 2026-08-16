package com.antshorttv.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record LoginByMobileRequest(
    @NotBlank
    @Pattern(regexp = "^1\\d{10}$")
    String mobile,

    @NotBlank
    String password
) {
}

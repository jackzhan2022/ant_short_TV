package com.antshorttv.invitation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreateInvitationRequest(
    @NotBlank
    @Pattern(regexp = "^1\\d{10}$")
    String mobile
) {
}

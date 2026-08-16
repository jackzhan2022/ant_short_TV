package com.antshorttv.member;

import jakarta.validation.constraints.NotNull;

public record TransferOwnerRequest(@NotNull Long targetMemberId) {
}

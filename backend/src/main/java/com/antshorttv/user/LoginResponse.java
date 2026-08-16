package com.antshorttv.user;

public record LoginResponse(
    String status,
    String type,
    String currentAuthority
) {
}

package com.antshorttv.user;

public record CurrentUserResponse(
    String name,
    String avatar,
    String userid,
    String email,
    String phone,
    String signature,
    String title,
    String group,
    String access
) {
}

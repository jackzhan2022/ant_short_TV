package com.antshorttv.user;

public record UserProfileResponse(
    Long id,
    String mobile,
    String email,
    String nickname,
    String avatar,
    String status
) {

    public static UserProfileResponse from(UserEntity user) {
        return new UserProfileResponse(
            user.getId(),
            user.getMobile(),
            user.getEmail(),
            user.getNickname(),
            user.getAvatar(),
            user.getStatus()
        );
    }
}

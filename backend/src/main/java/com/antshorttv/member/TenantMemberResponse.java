package com.antshorttv.member;

import com.antshorttv.user.UserEntity;
import java.time.LocalDateTime;

public record TenantMemberResponse(
    Long id,
    Long tenantId,
    Long userId,
    String mobile,
    String nickname,
    String avatar,
    String memberType,
    String status,
    LocalDateTime joinedAt
) {

    public static TenantMemberResponse from(TenantMemberEntity member, UserEntity user) {
        return new TenantMemberResponse(
            member.getId(),
            member.getTenantId(),
            member.getUserId(),
            user == null ? null : user.getMobile(),
            user == null ? null : user.getNickname(),
            user == null ? null : user.getAvatar(),
            member.getMemberType(),
            member.getStatus(),
            member.getJoinedAt()
        );
    }
}

package com.antshorttv.authsession;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AuthSessionMapper extends BaseMapper<AuthSessionEntity> {

    default AuthSessionEntity selectByTokenHash(String tokenHash) {
        return selectOne(new LambdaQueryWrapper<AuthSessionEntity>()
            .eq(AuthSessionEntity::getTokenHash, tokenHash));
    }

    default AuthSessionEntity selectBySessionId(String sessionId) {
        return selectOne(new LambdaQueryWrapper<AuthSessionEntity>()
            .eq(AuthSessionEntity::getSessionId, sessionId));
    }

    default void revokeActiveByUserId(Long userId, String reason, LocalDateTime now) {
        update(null, new LambdaUpdateWrapper<AuthSessionEntity>()
            .eq(AuthSessionEntity::getUserId, userId)
            .eq(AuthSessionEntity::getStatus, AuthSessionStatus.ACTIVE.name())
            .set(AuthSessionEntity::getStatus, AuthSessionStatus.REVOKED.name())
            .set(AuthSessionEntity::getRevokedAt, now)
            .set(AuthSessionEntity::getRevokedReason, reason)
            .set(AuthSessionEntity::getUpdatedAt, now));
    }

    default int deleteExpiredAndRevokedBefore(LocalDateTime now, LocalDateTime revokedBefore) {
        return delete(new LambdaQueryWrapper<AuthSessionEntity>()
            .le(AuthSessionEntity::getExpiresAt, now)
            .or(wrapper -> wrapper
                .eq(AuthSessionEntity::getStatus, AuthSessionStatus.REVOKED.name())
                .le(AuthSessionEntity::getRevokedAt, revokedBefore)));
    }
}

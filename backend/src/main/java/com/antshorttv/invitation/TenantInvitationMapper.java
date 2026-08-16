package com.antshorttv.invitation;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TenantInvitationMapper extends BaseMapper<TenantInvitationEntity> {

    default TenantInvitationEntity selectByToken(String token) {
        return selectOne(new LambdaQueryWrapper<TenantInvitationEntity>()
            .eq(TenantInvitationEntity::getToken, token));
    }

    default TenantInvitationEntity selectPendingByTenantIdAndMobile(Long tenantId, String mobile) {
        return selectOne(new LambdaQueryWrapper<TenantInvitationEntity>()
            .eq(TenantInvitationEntity::getTenantId, tenantId)
            .eq(TenantInvitationEntity::getInviteMobile, mobile)
            .eq(TenantInvitationEntity::getStatus, InvitationStatus.PENDING.name()));
    }

    default List<TenantInvitationEntity> selectByInviteMobile(String mobile) {
        return selectList(new LambdaQueryWrapper<TenantInvitationEntity>()
            .eq(TenantInvitationEntity::getInviteMobile, mobile));
    }
}

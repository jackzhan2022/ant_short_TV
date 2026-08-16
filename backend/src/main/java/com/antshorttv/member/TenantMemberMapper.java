package com.antshorttv.member;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TenantMemberMapper extends BaseMapper<TenantMemberEntity> {

    default TenantMemberEntity selectByTenantIdAndUserId(Long tenantId, Long userId) {
        return selectOne(new LambdaQueryWrapper<TenantMemberEntity>()
            .eq(TenantMemberEntity::getTenantId, tenantId)
            .eq(TenantMemberEntity::getUserId, userId));
    }

    default List<TenantMemberEntity> selectActiveByUserId(Long userId) {
        return selectList(new LambdaQueryWrapper<TenantMemberEntity>()
            .eq(TenantMemberEntity::getUserId, userId)
            .eq(TenantMemberEntity::getStatus, MemberStatus.ACTIVE.name()));
    }

    default List<TenantMemberEntity> selectActiveByTenantId(Long tenantId) {
        return selectList(new LambdaQueryWrapper<TenantMemberEntity>()
            .eq(TenantMemberEntity::getTenantId, tenantId)
            .eq(TenantMemberEntity::getStatus, MemberStatus.ACTIVE.name()));
    }
}

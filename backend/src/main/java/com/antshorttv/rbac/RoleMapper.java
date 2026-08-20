package com.antshorttv.rbac;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RoleMapper extends BaseMapper<RoleEntity> {

    default RoleEntity selectActiveByTenantIdAndCode(Long tenantId, String code) {
        return selectOne(baseTenantQuery(tenantId)
            .eq(RoleEntity::getCode, code));
    }

    default RoleEntity selectByTenantIdAndCode(Long tenantId, String code) {
        return selectOne(new LambdaQueryWrapper<RoleEntity>()
            .eq(RoleEntity::getTenantId, tenantId)
            .eq(RoleEntity::getCode, code));
    }

    default List<RoleEntity> selectByTenantId(Long tenantId) {
        return selectList(baseTenantQuery(tenantId)
            .orderByAsc(RoleEntity::getRoleType)
            .orderByAsc(RoleEntity::getId));
    }

    default LambdaQueryWrapper<RoleEntity> baseTenantQuery(Long tenantId) {
        return new LambdaQueryWrapper<RoleEntity>()
            .eq(RoleEntity::getTenantId, tenantId)
            .isNull(RoleEntity::getDeletedAt);
    }
}

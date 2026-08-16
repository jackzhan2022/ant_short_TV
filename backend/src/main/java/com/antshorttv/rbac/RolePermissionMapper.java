package com.antshorttv.rbac;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RolePermissionMapper extends BaseMapper<RolePermissionEntity> {

    default List<RolePermissionEntity> selectByRoleId(Long roleId) {
        return selectList(new LambdaQueryWrapper<RolePermissionEntity>().eq(RolePermissionEntity::getRoleId, roleId));
    }

    default RolePermissionEntity selectByRoleIdAndPermissionId(Long roleId, Long permissionId) {
        return selectOne(new LambdaQueryWrapper<RolePermissionEntity>()
            .eq(RolePermissionEntity::getRoleId, roleId)
            .eq(RolePermissionEntity::getPermissionId, permissionId));
    }

    default List<RolePermissionEntity> selectByRoleIds(Collection<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapper<RolePermissionEntity>().in(RolePermissionEntity::getRoleId, roleIds));
    }

    default void deleteByRoleId(Long roleId) {
        delete(new LambdaQueryWrapper<RolePermissionEntity>().eq(RolePermissionEntity::getRoleId, roleId));
    }
}

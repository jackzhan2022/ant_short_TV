package com.antshorttv.project;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProjectRolePermissionMapper extends BaseMapper<ProjectRolePermissionEntity> {
    default List<ProjectRolePermissionEntity> selectByRoleIds(Long tenantId, Long projectId, Collection<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return List.of();
        }
        return selectList(new QueryWrapper<ProjectRolePermissionEntity>()
            .eq("tenant_id", tenantId)
            .eq("project_id", projectId)
            .in("role_id", roleIds));
    }

    default void deleteByRoleId(Long tenantId, Long projectId, Long roleId) {
        delete(new QueryWrapper<ProjectRolePermissionEntity>()
            .eq("tenant_id", tenantId)
            .eq("project_id", projectId)
            .eq("role_id", roleId));
    }
}

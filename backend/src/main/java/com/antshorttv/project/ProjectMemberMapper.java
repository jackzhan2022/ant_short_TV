package com.antshorttv.project;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProjectMemberMapper extends BaseMapper<ProjectMemberEntity> {
    default ProjectMemberEntity selectActiveByProjectIdAndUserId(Long tenantId, Long projectId, Long userId) {
        return selectOne(new QueryWrapper<ProjectMemberEntity>()
            .eq("tenant_id", tenantId)
            .eq("project_id", projectId)
            .eq("user_id", userId)
            .eq("status", ProjectMemberStatus.ACTIVE.name()));
    }

    default ProjectMemberEntity selectByProjectIdAndUserId(Long tenantId, Long projectId, Long userId) {
        return selectOne(new QueryWrapper<ProjectMemberEntity>()
            .eq("tenant_id", tenantId)
            .eq("project_id", projectId)
            .eq("user_id", userId));
    }

    default List<ProjectMemberEntity> selectActiveByProjectId(Long tenantId, Long projectId) {
        return selectList(new QueryWrapper<ProjectMemberEntity>()
            .eq("tenant_id", tenantId)
            .eq("project_id", projectId)
            .eq("status", ProjectMemberStatus.ACTIVE.name())
            .orderByAsc("id"));
    }

    default long countActiveByProjectId(Long tenantId, Long projectId) {
        return selectCount(new QueryWrapper<ProjectMemberEntity>()
            .eq("tenant_id", tenantId)
            .eq("project_id", projectId)
            .eq("status", ProjectMemberStatus.ACTIVE.name()));
    }

    default long countActiveByRoleId(Long tenantId, Long projectId, Long roleId) {
        return selectCount(new QueryWrapper<ProjectMemberEntity>()
            .eq("tenant_id", tenantId)
            .eq("project_id", projectId)
            .eq("role_id", roleId)
            .eq("status", ProjectMemberStatus.ACTIVE.name()));
    }
}

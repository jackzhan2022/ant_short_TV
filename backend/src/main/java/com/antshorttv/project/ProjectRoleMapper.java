package com.antshorttv.project;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProjectRoleMapper extends BaseMapper<ProjectRoleEntity> {
    default ProjectRoleEntity selectByProjectIdAndCode(Long tenantId, Long projectId, String code) {
        return selectOne(new QueryWrapper<ProjectRoleEntity>()
            .eq("tenant_id", tenantId)
            .eq("project_id", projectId)
            .eq("code", code));
    }

    default ProjectRoleEntity selectByTenantProjectAndId(Long tenantId, Long projectId, Long id) {
        return selectOne(new QueryWrapper<ProjectRoleEntity>()
            .eq("tenant_id", tenantId)
            .eq("project_id", projectId)
            .eq("id", id));
    }

    default List<ProjectRoleEntity> selectByProjectId(Long tenantId, Long projectId) {
        return selectList(new QueryWrapper<ProjectRoleEntity>()
            .eq("tenant_id", tenantId)
            .eq("project_id", projectId)
            .orderByAsc("is_system", "id"));
    }
}

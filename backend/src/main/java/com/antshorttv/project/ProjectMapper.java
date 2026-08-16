package com.antshorttv.project;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProjectMapper extends BaseMapper<ProjectEntity> {
    default ProjectEntity selectByTenantIdAndId(Long tenantId, Long id) {
        return selectOne(new QueryWrapper<ProjectEntity>()
            .eq("tenant_id", tenantId)
            .eq("id", id)
            .isNull("deleted_at"));
    }

    default ProjectEntity selectByTenantIdAndCode(Long tenantId, String code) {
        return selectOne(new QueryWrapper<ProjectEntity>()
            .eq("tenant_id", tenantId)
            .eq("code", code)
            .isNull("deleted_at"));
    }

    default List<ProjectEntity> selectByTenantId(Long tenantId) {
        return selectList(new QueryWrapper<ProjectEntity>()
            .eq("tenant_id", tenantId)
            .isNull("deleted_at")
            .orderByDesc("created_at"));
    }

    default long countByOrganizationId(Long tenantId, Long organizationId) {
        return selectCount(new QueryWrapper<ProjectEntity>()
            .eq("tenant_id", tenantId)
            .eq("organization_id", organizationId)
            .isNull("deleted_at"));
    }
}

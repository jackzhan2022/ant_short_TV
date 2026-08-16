package com.antshorttv.project;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OrganizationMapper extends BaseMapper<OrganizationEntity> {
    default OrganizationEntity selectByTenantIdAndId(Long tenantId, Long id) {
        return selectOne(new QueryWrapper<OrganizationEntity>()
            .eq("tenant_id", tenantId)
            .eq("id", id)
            .isNull("deleted_at"));
    }

    default OrganizationEntity selectByTenantIdAndCode(Long tenantId, String code) {
        return selectOne(new QueryWrapper<OrganizationEntity>()
            .eq("tenant_id", tenantId)
            .eq("code", code)
            .isNull("deleted_at"));
    }

    default List<OrganizationEntity> selectByTenantId(Long tenantId) {
        return selectList(new QueryWrapper<OrganizationEntity>()
            .eq("tenant_id", tenantId)
            .isNull("deleted_at")
            .orderByAsc("level", "sort", "id"));
    }

    default long countChildren(Long tenantId, Long parentId) {
        return selectCount(new QueryWrapper<OrganizationEntity>()
            .eq("tenant_id", tenantId)
            .eq("parent_id", parentId)
            .isNull("deleted_at"));
    }
}

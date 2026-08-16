package com.antshorttv.project;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OrganizationMemberMapper extends BaseMapper<OrganizationMemberEntity> {
    default List<OrganizationMemberEntity> selectByTenantIdAndOrganizationId(Long tenantId, Long organizationId) {
        return selectList(new QueryWrapper<OrganizationMemberEntity>()
            .eq("tenant_id", tenantId)
            .eq("organization_id", organizationId));
    }

    default long countByOrganizationId(Long tenantId, Long organizationId) {
        return selectCount(new QueryWrapper<OrganizationMemberEntity>()
            .eq("tenant_id", tenantId)
            .eq("organization_id", organizationId));
    }
}

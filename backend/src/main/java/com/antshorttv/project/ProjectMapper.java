package com.antshorttv.project;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

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

    @Select("""
        select p.*
        from project p
        join project_member pm
          on pm.tenant_id = p.tenant_id
         and pm.project_id = p.id
         and pm.user_id = #{userId}
         and pm.status = 'ACTIVE'
        join project_role pr
          on pr.tenant_id = p.tenant_id
         and pr.project_id = p.id
         and pr.id = pm.role_id
         and pr.status = 'ACTIVE'
        where p.tenant_id = #{tenantId}
          and p.deleted_at is null
        order by p.created_at desc
        """)
    List<ProjectEntity> selectAccessibleByMember(
        @Param("tenantId") Long tenantId,
        @Param("userId") Long userId
    );
}

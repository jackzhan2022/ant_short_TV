package com.antshorttv.project;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.antshorttv.common.PageBounds;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ProjectMapper extends BaseMapper<ProjectEntity> {
    String VISIBLE = """
        p.tenant_id = #{tenantId} and p.deleted_at is null
        and (#{keyword} is null or p.name like concat('%', #{keyword}, '%')
             or p.code like concat('%', #{keyword}, '%'))
        and (#{wide} = true or exists (
            select 1 from project_member pm
            join project_role pr on pr.id = pm.role_id and pr.project_id = p.id
                and pr.tenant_id = p.tenant_id and pr.status = 'ACTIVE'
            join project_role_permission rp on rp.role_id = pr.id and rp.project_id = p.id
                and rp.tenant_id = p.tenant_id
            join permission perm on perm.id = rp.permission_id and perm.code = 'PROJECT:VIEW'
            where pm.project_id = p.id and pm.tenant_id = p.tenant_id
                and pm.user_id = #{userId} and pm.status = 'ACTIVE'))
        """;

    @Select("select count(*) from project p where " + VISIBLE)
    long countVisible(@Param("tenantId") Long tenantId, @Param("userId") Long userId,
        @Param("wide") boolean wide, @Param("keyword") String keyword);

    @Select("""
        select p.id, p.tenant_id, p.name, p.code, p.description,
               case when p.cover_url is null or p.cover_url = '' then null else 'present' end as cover_url,
               p.cover_source, p.cover_version, p.cover_status,
               p.owner_id, p.status, p.start_date, p.end_date, p.aspect_ratio, p.file_format,
               p.video_resolution, p.video_generate_audio, p.video_watermark,
               p.script_type, p.breakdown_strength, p.visual_style, p.created_by,
               p.created_at, p.updated_at, p.deleted_at
          from project p where
        """ + VISIBLE + " order by p.created_at desc, p.id desc limit #{bounds.pageSize} offset #{bounds.offset}")
    List<ProjectEntity> selectVisiblePage(@Param("tenantId") Long tenantId, @Param("userId") Long userId,
        @Param("wide") boolean wide, @Param("keyword") String keyword, @Param("bounds") PageBounds bounds);

    @Select("""
        <script>
        select pm.project_id, pm.id as member_id, pr.id as role_id,
               pr.code as role_code, pr.name as role_name, perm.code as permission_code
          from project_member pm
          join project_role pr on pr.id = pm.role_id and pr.project_id = pm.project_id
             and pr.tenant_id = pm.tenant_id and pr.status = 'ACTIVE'
          join project_role_permission rp on rp.role_id = pr.id and rp.project_id = pm.project_id
             and rp.tenant_id = pm.tenant_id
          join permission perm on perm.id = rp.permission_id
         where pm.tenant_id = #{tenantId} and pm.user_id = #{userId} and pm.status = 'ACTIVE'
           and pm.project_id in
           <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
        </script>
        """)
    List<ProjectBrowsePermission> selectBrowsePermissions(@Param("tenantId") Long tenantId,
        @Param("userId") Long userId, @Param("ids") List<Long> ids);
    @Update("""
        update project set cover_version = #{version}, cover_status = 'PENDING', cover_error = null
         where id = #{project.id} and tenant_id = #{project.tenantId} and deleted_at is null
           and cover_url = #{source}
           and (cover_version is null or cover_version = #{version})
           and (cover_status is null or cover_status = 'UNPROCESSED'
                or (#{retry} = true and cover_status = 'FAILED'))
        """)
    int claimCover(@Param("project") ProjectEntity project, @Param("source") String source,
                   @Param("version") String version, @Param("retry") boolean retry);

    @Update("""
        update project set cover_status = #{status}, cover_error = #{error}
         where id = #{id} and cover_version = #{version} and deleted_at is null
        """)
    int updateCoverState(@Param("id") Long id, @Param("version") String version,
                        @Param("status") String status, @Param("error") String error);

    @Update("""
        update project set cover_url = #{key}
         where id = #{id} and cover_version = #{version} and deleted_at is null
        """)
    int canonicalizeCover(@Param("id") Long id, @Param("version") String version, @Param("key") String key);

    @Update("""
        update project set cover_url = #{project.coverUrl}, cover_source = #{project.coverSource},
               cover_version = #{project.coverVersion}, cover_status = #{project.coverStatus},
               cover_error = #{project.coverError}
         where id = #{project.id} and tenant_id = #{project.tenantId} and deleted_at is null
        """)
    int replaceCover(@Param("project") ProjectEntity project);

    @Update("""
        update project set cover_source = #{source}
         where id = #{id} and tenant_id = #{tenantId} and deleted_at is null
        """)
    int updateCoverLabel(@Param("id") Long id, @Param("tenantId") Long tenantId,
        @Param("source") String source);

    @Update("""
        update project set cover_url = #{key}, cover_version = #{version}, cover_source = 'UPLOAD',
               cover_status = 'UNPROCESSED', cover_error = null, updated_at = current_timestamp
         where id = #{id} and tenant_id = #{tenantId} and deleted_at is null
           and (cover_version is null or cover_version <> #{version})
        """)
    int installUploadCover(@Param("id") Long id, @Param("tenantId") Long tenantId,
        @Param("version") String version, @Param("key") String key);

    @Select("""
        select * from media_object where tenant_id = #{tenantId} and project_id = #{projectId}
          and object_key = #{key} limit 1
        """)
    ProjectCoverObject selectOwnedCoverObject(@Param("tenantId") Long tenantId,
        @Param("projectId") Long projectId, @Param("key") String key);
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
            .select(ProjectEntity.class, field -> !"initialScriptContent".equals(field.getProperty()))
            .eq("tenant_id", tenantId)
            .isNull("deleted_at")
            .orderByDesc("created_at"));
    }

    @Select("""
        select p.id, p.tenant_id, p.name, p.code, p.description, p.cover_url, p.cover_source,
               p.owner_id, p.status, p.start_date, p.end_date, p.aspect_ratio, p.file_format,
               p.video_resolution, p.video_generate_audio, p.video_watermark,
               p.script_type, p.breakdown_strength, p.visual_style, p.created_by,
               p.created_at, p.updated_at, p.deleted_at
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

package com.antshorttv.review;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
interface ReviewProjectMapper extends BaseMapper<ReviewProjectEntity> {
    @Select("""
        select rp.id, rp.main_project_id, rp.name, rp.source_file_name, rp.source_type,
               rp.current_version_id, rp.status, rp.created_at, rp.updated_at
          from review_project rp
         where rp.tenant_id = #{tenantId}
           and rp.deleted_at is null
           and (
             #{tenantWide} = true
             or (rp.main_project_id is null and rp.created_by = #{userId})
             or exists (
               select 1
                 from project_member pm
                 join project_role pr on pr.id = pm.role_id
                   and pr.tenant_id = pm.tenant_id
                   and pr.project_id = pm.project_id
                   and pr.status = 'ACTIVE'
                 join project_role_permission prp on prp.tenant_id = pm.tenant_id
                   and prp.project_id = pm.project_id
                   and prp.role_id = pm.role_id
                 join permission permission on permission.id = prp.permission_id
                where pm.tenant_id = rp.tenant_id
                  and pm.project_id = rp.main_project_id
                  and pm.user_id = #{userId}
                  and pm.status = 'ACTIVE'
                  and permission.code = 'PROJECT:VIEW'
             )
           )
         order by rp.updated_at desc, rp.id desc
        """)
    List<ReviewProjectListRow> selectVisibleListRows(
        @Param("tenantId") Long tenantId,
        @Param("userId") Long userId,
        @Param("tenantWide") boolean tenantWide
    );
    default List<ReviewProjectEntity> selectActive(Long tenantId) {
        return selectList(new LambdaQueryWrapper<ReviewProjectEntity>()
            .eq(ReviewProjectEntity::getTenantId, tenantId)
            .isNull(ReviewProjectEntity::getDeletedAt)
            .orderByDesc(ReviewProjectEntity::getUpdatedAt));
    }

    default ReviewProjectEntity selectByTenantAndId(Long tenantId, Long projectId) {
        return selectOne(new LambdaQueryWrapper<ReviewProjectEntity>()
            .eq(ReviewProjectEntity::getTenantId, tenantId)
            .eq(ReviewProjectEntity::getId, projectId)
            .isNull(ReviewProjectEntity::getDeletedAt)
            .last("limit 1"));
    }
}

@Mapper
interface ReviewScriptVersionMapper extends BaseMapper<ReviewScriptVersionEntity> {
    @Select({"<script>", """
        select project_id, count(*) as version_count
          from review_script_version
         where tenant_id = #{tenantId} and deleted_at is null
           and project_id in
        """, "<foreach collection='projectIds' item='projectId' open='(' separator=',' close=')'>#{projectId}</foreach>",
        "group by project_id", "</script>"})
    List<ReviewProjectCountRow> selectCountsByProjects(
        @Param("tenantId") Long tenantId,
        @Param("projectIds") List<Long> projectIds
    );
    default List<ReviewScriptVersionEntity> selectByProjects(Long tenantId, List<Long> projectIds) {
        if (projectIds == null || projectIds.isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapper<ReviewScriptVersionEntity>()
            .eq(ReviewScriptVersionEntity::getTenantId, tenantId)
            .in(ReviewScriptVersionEntity::getProjectId, projectIds)
            .isNull(ReviewScriptVersionEntity::getDeletedAt));
    }

    default List<ReviewScriptVersionEntity> selectByProject(Long tenantId, Long projectId) {
        return selectList(new LambdaQueryWrapper<ReviewScriptVersionEntity>()
            .eq(ReviewScriptVersionEntity::getTenantId, tenantId)
            .eq(ReviewScriptVersionEntity::getProjectId, projectId)
            .isNull(ReviewScriptVersionEntity::getDeletedAt)
            .orderByDesc(ReviewScriptVersionEntity::getVersionNo));
    }

    default ReviewScriptVersionEntity selectLatestByProject(Long tenantId, Long projectId) {
        return selectOne(new LambdaQueryWrapper<ReviewScriptVersionEntity>()
            .eq(ReviewScriptVersionEntity::getTenantId, tenantId)
            .eq(ReviewScriptVersionEntity::getProjectId, projectId)
            .isNull(ReviewScriptVersionEntity::getDeletedAt)
            .orderByDesc(ReviewScriptVersionEntity::getVersionNo)
            .last("limit 1"));
    }
}

@Mapper
interface ReviewTaskMapper extends BaseMapper<ReviewTaskEntity> {
    @Select({"<script>", """
        select task.project_id, task.id as task_id, task.round_no, task.status, task.result_format,
               case when task.report_markdown is null or trim(task.report_markdown) = ''
                    then false else true end as has_report_markdown
          from review_task task
         where task.tenant_id = #{tenantId}
           and task.project_id in
        """, "<foreach collection='projectIds' item='projectId' open='(' separator=',' close=')'>#{projectId}</foreach>", """
           and not exists (
             select 1 from review_task newer
              where newer.tenant_id = task.tenant_id
                and newer.project_id = task.project_id
                and (newer.created_at > task.created_at
                  or (newer.created_at = task.created_at and newer.id > task.id))
           )
        """, "</script>"})
    List<ReviewProjectLatestTaskRow> selectLatestRowsByProjects(
        @Param("tenantId") Long tenantId,
        @Param("projectIds") List<Long> projectIds
    );
    default long countByProject(Long tenantId, Long projectId) {
        return selectCount(new LambdaQueryWrapper<ReviewTaskEntity>()
            .eq(ReviewTaskEntity::getTenantId, tenantId)
            .eq(ReviewTaskEntity::getProjectId, projectId));
    }

    default List<ReviewTaskEntity> selectHistoryPage(
        Long tenantId, Long projectId, int offset, int pageSize
    ) {
        return selectList(new LambdaQueryWrapper<ReviewTaskEntity>()
            .eq(ReviewTaskEntity::getTenantId, tenantId)
            .eq(ReviewTaskEntity::getProjectId, projectId)
            .orderByDesc(ReviewTaskEntity::getCreatedAt)
            .orderByDesc(ReviewTaskEntity::getId)
            .last("limit " + pageSize + " offset " + offset));
    }

    default List<ReviewTaskEntity> selectByProjects(Long tenantId, List<Long> projectIds) {
        if (projectIds == null || projectIds.isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapper<ReviewTaskEntity>()
            .eq(ReviewTaskEntity::getTenantId, tenantId)
            .in(ReviewTaskEntity::getProjectId, projectIds)
            .orderByDesc(ReviewTaskEntity::getCreatedAt)
            .orderByDesc(ReviewTaskEntity::getId));
    }

    default List<ReviewTaskEntity> selectRunnable() {
        return selectList(new LambdaQueryWrapper<ReviewTaskEntity>()
            .eq(ReviewTaskEntity::getStatus, "PENDING")
            .orderByAsc(ReviewTaskEntity::getCreatedAt)
            .last("limit 10"));
    }

    default ReviewTaskEntity selectByIdempotencyKey(Long tenantId, String key) {
        return selectOne(new LambdaQueryWrapper<ReviewTaskEntity>()
            .eq(ReviewTaskEntity::getTenantId, tenantId)
            .eq(ReviewTaskEntity::getIdempotencyKey, key)
            .last("limit 1"));
    }

    default ReviewTaskEntity selectLatestByProject(Long tenantId, Long projectId) {
        return selectOne(new LambdaQueryWrapper<ReviewTaskEntity>()
            .eq(ReviewTaskEntity::getTenantId, tenantId)
            .eq(ReviewTaskEntity::getProjectId, projectId)
            .orderByDesc(ReviewTaskEntity::getCreatedAt)
            .last("limit 1"));
    }
}

@Mapper
interface ReviewIssueMapper extends BaseMapper<ReviewIssueEntity> {
    @Select({"<script>", """
        select task_id, count(*) as issue_count,
               sum(case when manually_resolved = false then 1 else 0 end) as outstanding_issue_count
          from review_issue
         where task_id in
        """, "<foreach collection='taskIds' item='taskId' open='(' separator=',' close=')'>#{taskId}</foreach>",
        "group by task_id", "</script>"})
    List<ReviewTaskIssueCountRow> selectCountsByTasks(@Param("taskIds") List<Long> taskIds);
    default List<ReviewIssueEntity> selectByTasks(List<Long> taskIds) {
        if (taskIds == null || taskIds.isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapper<ReviewIssueEntity>()
            .in(ReviewIssueEntity::getTaskId, taskIds));
    }

    default List<ReviewIssueEntity> selectByTask(Long taskId) {
        return selectList(new LambdaQueryWrapper<ReviewIssueEntity>()
            .eq(ReviewIssueEntity::getTaskId, taskId)
            .orderByAsc(ReviewIssueEntity::getId));
    }

    default List<ReviewIssueEntity> selectByLatestProject(Long tenantId, Long projectId) {
        return selectList(new LambdaQueryWrapper<ReviewIssueEntity>()
            .eq(ReviewIssueEntity::getTenantId, tenantId)
            .eq(ReviewIssueEntity::getProjectId, projectId)
            .orderByDesc(ReviewIssueEntity::getCreatedAt));
    }
}

@Mapper
interface ReviewIssueHitMapper extends BaseMapper<ReviewIssueHitEntity> {
    default List<ReviewIssueHitEntity> selectByIssues(List<Long> issueIds) {
        if (issueIds == null || issueIds.isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapper<ReviewIssueHitEntity>()
            .in(ReviewIssueHitEntity::getIssueId, issueIds)
            .orderByAsc(ReviewIssueHitEntity::getHitNo));
    }

    default List<ReviewIssueHitEntity> selectByIssue(Long issueId) {
        return selectList(new LambdaQueryWrapper<ReviewIssueHitEntity>()
            .eq(ReviewIssueHitEntity::getIssueId, issueId)
            .orderByAsc(ReviewIssueHitEntity::getHitNo));
    }
}

@Mapper
interface ReviewIssueEventMapper extends BaseMapper<ReviewIssueEventEntity> {
}

@Mapper
interface ReviewBatchRepairMapper extends BaseMapper<ReviewBatchRepairEntity> {
}

@Mapper
interface ReviewExportRecordMapper extends BaseMapper<ReviewExportRecordEntity> {
    default List<ReviewExportRecordEntity> selectByVersion(Long tenantId, Long projectId, Long versionId) {
        return selectList(new LambdaQueryWrapper<ReviewExportRecordEntity>()
            .eq(ReviewExportRecordEntity::getTenantId, tenantId)
            .eq(ReviewExportRecordEntity::getProjectId, projectId)
            .eq(ReviewExportRecordEntity::getVersionId, versionId)
            .orderByDesc(ReviewExportRecordEntity::getCreatedAt));
    }
}

@Mapper
interface ReviewFanoutSnapshotMapper extends BaseMapper<ReviewFanoutSnapshotEntity> {
    default ReviewFanoutSnapshotEntity selectAttempt(Long taskId, Integer attemptNo) {
        return selectOne(new LambdaQueryWrapper<ReviewFanoutSnapshotEntity>()
            .eq(ReviewFanoutSnapshotEntity::getTaskId, taskId)
            .eq(ReviewFanoutSnapshotEntity::getAttemptNo, attemptNo)
            .last("limit 1"));
    }

    default ReviewFanoutSnapshotEntity selectLatestMatching(
        Long taskId, String versionHash, String scopeHash, String dimensionsHash
    ) {
        return selectOne(new LambdaQueryWrapper<ReviewFanoutSnapshotEntity>()
            .eq(ReviewFanoutSnapshotEntity::getTaskId, taskId)
            .eq(ReviewFanoutSnapshotEntity::getVersionHash, versionHash)
            .eq(ReviewFanoutSnapshotEntity::getScopeHash, scopeHash)
            .eq(ReviewFanoutSnapshotEntity::getDimensionsHash, dimensionsHash)
            .orderByDesc(ReviewFanoutSnapshotEntity::getAttemptNo)
            .last("limit 1"));
    }
}

@Mapper
interface ReviewFanoutUnitMapper extends BaseMapper<ReviewFanoutUnitEntity> {
    default List<ReviewFanoutUnitEntity> selectOrdered(Long snapshotId) {
        return selectList(new LambdaQueryWrapper<ReviewFanoutUnitEntity>()
            .eq(ReviewFanoutUnitEntity::getSnapshotId, snapshotId)
            .orderByAsc(ReviewFanoutUnitEntity::getUnitNo)
            .orderByAsc(ReviewFanoutUnitEntity::getId));
    }
}

@Mapper
interface ReviewPipelineStageMapper extends BaseMapper<ReviewPipelineStageEntity> {
}

@Mapper
interface ReviewUnitResultMapper extends BaseMapper<ReviewUnitResultEntity> {
    default ReviewUnitResultEntity selectCurrent(Long snapshotId, Long unitId) {
        return selectOne(new LambdaQueryWrapper<ReviewUnitResultEntity>()
            .eq(ReviewUnitResultEntity::getSnapshotId, snapshotId)
            .eq(ReviewUnitResultEntity::getUnitId, unitId)
            .last("limit 1"));
    }

    default List<ReviewUnitResultEntity> selectOrdered(Long snapshotId) {
        return selectList(new LambdaQueryWrapper<ReviewUnitResultEntity>()
            .eq(ReviewUnitResultEntity::getSnapshotId, snapshotId)
            .orderByAsc(ReviewUnitResultEntity::getUnitId));
    }
}

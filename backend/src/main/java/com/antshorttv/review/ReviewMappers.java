package com.antshorttv.review;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
interface ReviewProjectMapper extends BaseMapper<ReviewProjectEntity> {
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

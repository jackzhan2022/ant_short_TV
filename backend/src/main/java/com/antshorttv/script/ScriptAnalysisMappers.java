package com.antshorttv.script;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
interface ScriptAnalysisTaskMapper extends BaseMapper<ScriptAnalysisTaskEntity> {
    default List<ScriptAnalysisTaskEntity> selectRunnable() {
        return selectList(new LambdaQueryWrapper<ScriptAnalysisTaskEntity>()
            .in(ScriptAnalysisTaskEntity::getStatus, List.of("PENDING", "RUNNING"))
            .orderByAsc(ScriptAnalysisTaskEntity::getCreatedAt)
            .last("limit 10"));
    }

    default List<ScriptAnalysisTaskEntity> selectRunnableWithoutExecution() {
        return selectList(new LambdaQueryWrapper<ScriptAnalysisTaskEntity>()
            .eq(ScriptAnalysisTaskEntity::getStatus, "PENDING")
            .isNull(ScriptAnalysisTaskEntity::getExecutionId)
            .orderByAsc(ScriptAnalysisTaskEntity::getCreatedAt)
            .last("limit 10"));
    }

    default ScriptAnalysisTaskEntity selectByIdempotencyKey(Long tenantId, String key) {
        return selectOne(new LambdaQueryWrapper<ScriptAnalysisTaskEntity>()
            .eq(ScriptAnalysisTaskEntity::getTenantId, tenantId)
            .eq(ScriptAnalysisTaskEntity::getIdempotencyKey, key)
            .last("limit 1"));
    }

    default ScriptAnalysisTaskEntity selectLatestByVersion(Long tenantId, Long projectId, Long versionId) {
        return selectOne(new LambdaQueryWrapper<ScriptAnalysisTaskEntity>()
            .eq(ScriptAnalysisTaskEntity::getTenantId, tenantId)
            .eq(ScriptAnalysisTaskEntity::getProjectId, projectId)
            .eq(ScriptAnalysisTaskEntity::getScriptVersionId, versionId)
            .orderByDesc(ScriptAnalysisTaskEntity::getCreatedAt)
            .last("limit 1"));
    }
}

@Mapper
interface ScriptAnalysisStageMapper extends BaseMapper<ScriptAnalysisStageEntity> {
    default List<ScriptAnalysisStageEntity> selectByTask(Long taskId) {
        return selectList(new LambdaQueryWrapper<ScriptAnalysisStageEntity>()
            .eq(ScriptAnalysisStageEntity::getTaskId, taskId)
            .orderByAsc(ScriptAnalysisStageEntity::getStageOrder));
    }
}

@Mapper
interface ScriptAnalysisResultMapper extends BaseMapper<ScriptAnalysisResultEntity> {
    default ScriptAnalysisResultEntity selectLatestByStage(Long stageId) {
        return selectOne(new LambdaQueryWrapper<ScriptAnalysisResultEntity>()
            .eq(ScriptAnalysisResultEntity::getStageId, stageId)
            .orderByDesc(ScriptAnalysisResultEntity::getCreatedAt)
            .last("limit 1"));
    }
}

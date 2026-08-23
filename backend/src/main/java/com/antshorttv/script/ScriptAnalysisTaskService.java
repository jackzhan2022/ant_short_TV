package com.antshorttv.script;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ScriptAnalysisTaskService {
    public static final String WORKFLOW_CODE = "SCRIPT_INITIAL_ANALYSIS";
    public static final String MANUAL_WORKFLOW_CODE = "SCRIPT_MANUAL_ANALYSIS";
    public static final List<StageDefinition> STAGES = List.of(
        new StageDefinition("GLOBAL_UNDERSTANDING", 1, "正在理解剧情全局"),
        new StageDefinition("EPISODE_SPLITTING", 2, "正在智能拆分剧集"),
        new StageDefinition("EPISODE_SUMMARY", 3, "正在提炼剧集概要"),
        new StageDefinition("CHARACTER_SCENE_RECOGNITION", 4, "正在识别角色和场景")
    );

    private final ScriptAnalysisTaskMapper taskMapper;
    private final ScriptAnalysisStageMapper stageMapper;

    public ScriptAnalysisTaskService(
        ScriptAnalysisTaskMapper taskMapper,
        ScriptAnalysisStageMapper stageMapper
    ) {
        this.taskMapper = taskMapper;
        this.stageMapper = stageMapper;
    }

    @Transactional
    public ScriptAnalysisTaskEntity createInitialTaskIfAbsent(
        Long tenantId,
        Long projectId,
        ScriptEntity script,
        ScriptVersionEntity version,
        Long createdBy,
        LocalDateTime now
    ) {
        if (version == null || version.getContent() == null || version.getContent().isBlank()) {
            return null;
        }
        String idempotencyKey = "%s:%d".formatted(WORKFLOW_CODE, version.getId());
        ScriptAnalysisTaskEntity existing = taskMapper.selectByIdempotencyKey(tenantId, idempotencyKey);
        if (existing != null) {
            return existing;
        }

        return createTask(tenantId, projectId, script, version, createdBy, now, WORKFLOW_CODE, idempotencyKey);
    }

    @Transactional
    public ScriptAnalysisTaskEntity createManualTask(
        Long tenantId,
        Long projectId,
        ScriptEntity script,
        ScriptVersionEntity version,
        Long createdBy,
        LocalDateTime now
    ) {
        if (version == null || version.getContent() == null || version.getContent().isBlank()) {
            return null;
        }
        String idempotencyKey = "%s:%d:%s".formatted(MANUAL_WORKFLOW_CODE, version.getId(), UUID.randomUUID());
        return createTask(tenantId, projectId, script, version, createdBy, now, MANUAL_WORKFLOW_CODE, idempotencyKey);
    }

    private ScriptAnalysisTaskEntity createTask(
        Long tenantId,
        Long projectId,
        ScriptEntity script,
        ScriptVersionEntity version,
        Long createdBy,
        LocalDateTime now,
        String workflowCode,
        String idempotencyKey
    ) {

        ScriptAnalysisTaskEntity task = new ScriptAnalysisTaskEntity();
        task.setTenantId(tenantId);
        task.setProjectId(projectId);
        task.setScriptId(script.getId());
        task.setScriptVersionId(version.getId());
        task.setWorkflowCode(workflowCode);
        task.setStatus("PENDING");
        task.setCurrentStage(STAGES.get(0).code());
        task.setOverallProgress(0);
        task.setCurrentAction("等待开始剧情全局理解");
        task.setIdempotencyKey(idempotencyKey);
        task.setCreatedBy(createdBy);
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        taskMapper.insert(task);

        for (StageDefinition definition : STAGES) {
            ScriptAnalysisStageEntity stage = new ScriptAnalysisStageEntity();
            stage.setTaskId(task.getId());
            stage.setStageCode(definition.code());
            stage.setStageOrder(definition.order());
            stage.setStatus("PENDING");
            stage.setProgressPercent(0);
            stage.setCompletedUnits(0);
            stage.setTotalUnits(0);
            stage.setCurrentAction(definition.waitingAction());
            stage.setAttemptNo(0);
            stage.setRetryable(false);
            stage.setCreatedAt(now);
            stage.setUpdatedAt(now);
            stageMapper.insert(stage);
        }
        return task;
    }

    public record StageDefinition(String code, int order, String waitingAction) {
    }

    @Transactional
    public ScriptAnalysisTaskEntity retryStage(
        Long tenantId,
        Long projectId,
        String stageCode
    ) {
        ScriptAnalysisTaskEntity task = taskMapper.selectOne(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ScriptAnalysisTaskEntity>()
                .eq(ScriptAnalysisTaskEntity::getTenantId, tenantId)
                .eq(ScriptAnalysisTaskEntity::getProjectId, projectId)
                .in(ScriptAnalysisTaskEntity::getWorkflowCode, List.of(WORKFLOW_CODE, MANUAL_WORKFLOW_CODE))
                .orderByDesc(ScriptAnalysisTaskEntity::getCreatedAt)
                .last("limit 1")
        );
        if (task == null) {
            throw new IllegalArgumentException("当前项目没有可重试的分析任务。");
        }
        String normalizedCode = stageCode == null ? "" : stageCode.trim().toUpperCase(Locale.ROOT);
        StageDefinition target = STAGES.stream()
            .filter(item -> item.code().equals(normalizedCode))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("未知分析阶段：" + stageCode));
        for (ScriptAnalysisStageEntity stage : stageMapper.selectByTask(task.getId())) {
            if (stage.getStageOrder() < target.order()) {
                continue;
            }
            stage.setStatus("PENDING");
            stage.setProgressPercent(0);
            stage.setCompletedUnits(0);
            stage.setTotalUnits(0);
            stage.setCurrentAction(stage.getStageOrder().equals(target.order())
                ? target.waitingAction()
                : "等待上一阶段完成");
            stage.setErrorCode(null);
            stage.setErrorMessage(null);
            stage.setRetryable(false);
            stage.setUpdatedAt(LocalDateTime.now());
            stageMapper.updateById(stage);
        }
        task.setStatus("PENDING");
        task.setCurrentStage(target.code());
        task.setCurrentAction(target.waitingAction());
        task.setErrorCode(null);
        task.setErrorMessage(null);
        task.setOverallProgress(Math.max(0, (target.order() - 1) * 25));
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
        return task;
    }
}

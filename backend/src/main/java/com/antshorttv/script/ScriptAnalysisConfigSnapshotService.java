package com.antshorttv.script;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Pins model selection; each Agent run freezes its own current definition and Skills. */
@Service
public class ScriptAnalysisConfigSnapshotService {
    private final ScriptAnalysisConfigSnapshotMapper snapshotMapper;
    private final ObjectMapper objectMapper;

    public ScriptAnalysisConfigSnapshotService(ScriptAnalysisConfigSnapshotMapper snapshotMapper,
        ObjectMapper objectMapper) {
        this.snapshotMapper = snapshotMapper;
        this.objectMapper = objectMapper;
    }

    public void snapshot(ScriptAnalysisTaskEntity task, Long modelId) {
        if (find(task.getId()) != null) return;
        if (modelId == null || modelId <= 0) throw new IllegalArgumentException("分析模型未配置。");
        ScriptAnalysisConfigSnapshotEntity entity = new ScriptAnalysisConfigSnapshotEntity();
        entity.setTaskId(task.getId());
        try {
            entity.setSnapshotJson(objectMapper.writeValueAsString(Map.of("modelId", modelId)));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法保存剧本分析配置快照。", exception);
        }
        entity.setCreatedAt(LocalDateTime.now());
        snapshotMapper.insert(entity);
    }

    public Long modelIdFor(Long taskId) {
        ScriptAnalysisConfigSnapshotEntity entity = find(taskId);
        if (entity == null) throw new IllegalStateException("剧本分析配置快照缺失。");
        try {
            var model = objectMapper.readTree(entity.getSnapshotJson()).path("modelId");
            if (!model.isIntegralNumber() || !model.canConvertToLong() || model.longValue() <= 0) {
                throw new IllegalStateException("剧本分析快照缺少有效模型。");
            }
            return model.longValue();
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("剧本分析配置快照损坏。", exception);
        }
    }

    public Long modelIdForFirstSubmission(ScriptAnalysisTaskEntity task,
        java.util.function.Supplier<Long> initialModel) {
        if (find(task.getId()) == null) {
            if (task.getExecutionId() != null) {
                throw new IllegalStateException("已派发分析任务的配置快照缺失。");
            }
            snapshot(task, initialModel.get());
        }
        return modelIdFor(task.getId());
    }

    private ScriptAnalysisConfigSnapshotEntity find(Long taskId) {
        return snapshotMapper.selectOne(new LambdaQueryWrapper<ScriptAnalysisConfigSnapshotEntity>()
            .eq(ScriptAnalysisConfigSnapshotEntity::getTaskId, taskId).last("limit 1"));
    }
}

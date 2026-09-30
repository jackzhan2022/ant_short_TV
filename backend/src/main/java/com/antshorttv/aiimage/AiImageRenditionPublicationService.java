package com.antshorttv.aiimage;

import com.antshorttv.execution.AiExecutionClaimLostException;
import com.antshorttv.execution.AiExecutionContext;
import com.antshorttv.execution.AiExecutionTaskMapper;
import com.antshorttv.script.AssetVisualVariantService;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiImageRenditionPublicationService {
    private final AiImageTaskMapper tasks;
    private final AiImageResultMapper results;
    private final AiExecutionTaskMapper executions;
    private final AssetVisualVariantService variants;

    public AiImageRenditionPublicationService(
        AiImageTaskMapper tasks,
        AiImageResultMapper results,
        AiExecutionTaskMapper executions,
        AssetVisualVariantService variants
    ) {
        this.tasks = tasks;
        this.results = results;
        this.executions = executions;
        this.variants = variants;
    }

    @Transactional
    public void publish(
        AiExecutionContext context,
        AiImageTaskEntity task,
        List<AiImageResultEntity> expected
    ) {
        if (expected == null || task.getImageCount() == null
            || expected.size() != task.getImageCount()
            || expected.stream().anyMatch(result -> !task.getId().equals(result.getTaskId())
                || !context.task().id.equals(result.getExecutionId())
                || !AiImageResultStatus.PROCESSING.name().equals(result.getStatus()))) {
            throw new IllegalStateException("AI 图片结果集合不满足原子发布条件。");
        }
        if (executions.lockActiveClaim(
            context.task().id, context.claim().claimToken()
        ) == null) {
            throw new AiExecutionClaimLostException(context.task().id);
        }
        int activated = results.activateForPublication(task.getId(), context.task().id);
        if (activated != expected.size()) {
            throw new IllegalStateException("AI 图片结果未能完整激活。");
        }

        AiImageResultEntity first = expected.get(0);
        if ("VISUAL_VARIANT".equals(task.getTargetType())) {
            boolean published = variants.generationSucceededIfClaimActive(
                task.getTenantId(), task.getProjectId(), task.getTargetId(), task.getId(),
                first.getId(), first.getImageUrl(), context.task().id, context.claim().claimToken()
            );
            if (!published) throw new AiExecutionClaimLostException(context.task().id);
        }
        task.setStatus(AiImageTaskStatus.SUCCESS.name());
        task.setCompletedAt(LocalDateTime.now());
        task.setUpdatedAt(task.getCompletedAt());
        if (tasks.updateById(task) != 1) {
            throw new IllegalStateException("AI 图片任务完成状态写入失败。");
        }
        expected.forEach(result -> result.setStatus(AiImageResultStatus.ACTIVE.name()));
    }
}

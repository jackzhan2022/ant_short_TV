package com.antshorttv.execution;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AiExecutionDispatcher {
    private final AiExecutionTaskMapper taskMapper;
    private final AiExecutionClaimService claimService;
    private final AiExecutionWorker worker;
    private final TaskExecutor taskExecutor;
    private final boolean enabled;
    private final int batchSize;

    public AiExecutionDispatcher(
        AiExecutionTaskMapper taskMapper,
        AiExecutionClaimService claimService,
        AiExecutionWorker worker,
        @Qualifier("aiExecutionTaskExecutor") TaskExecutor taskExecutor,
        @Value("${ai.execution.dispatcher.enabled:true}") boolean enabled,
        @Value("${ai.execution.dispatcher.batch-size:20}") int batchSize
    ) {
        this.taskMapper = taskMapper;
        this.claimService = claimService;
        this.worker = worker;
        this.taskExecutor = taskExecutor;
        this.enabled = enabled;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${ai.execution.dispatcher.fixed-delay-ms:5000}")
    public void dispatchScheduled() {
        if (enabled) {
            dispatchOnce();
        }
    }

    public int dispatchOnce() {
        LocalDateTime now = LocalDateTime.now();
        claimService.recoverExpiredClaims(now);
        List<Long> ids = eligibleExecutionIds(now, batchSize);
        ids.forEach(id -> taskExecutor.execute(() -> worker.run(id)));
        return ids.size();
    }

    public List<Long> eligibleExecutionIds(LocalDateTime now, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, batchSize));
        return taskMapper.selectList(new QueryWrapper<AiExecutionTaskEntity>()
                .eq("status", AiExecutionStatus.PENDING.name())
                .and(wrapper -> wrapper.isNull("next_run_at").or().le("next_run_at", now))
                .orderByAsc("priority", "created_at", "id")
                .last("limit " + safeLimit))
            .stream()
            .map(task -> task.id)
            .toList();
    }
}

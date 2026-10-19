package com.antshorttv.execution;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
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
    private final TaskExecutor publicationExecutor;
    private final Set<Long> scheduled = ConcurrentHashMap.newKeySet();
    private final int batchSize;

    public AiExecutionDispatcher(
        AiExecutionTaskMapper taskMapper,
        AiExecutionClaimService claimService,
        AiExecutionWorker worker,
        @Qualifier("aiExecutionTaskExecutor") TaskExecutor taskExecutor,
        @Qualifier("aiImagePublicationExecutor") TaskExecutor publicationExecutor,
        @Value("${ai.execution.dispatcher.batch-size:20}") int batchSize
    ) {
        this.taskMapper = taskMapper;
        this.claimService = claimService;
        this.worker = worker;
        this.taskExecutor = taskExecutor;
        this.publicationExecutor = publicationExecutor;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${ai.execution.dispatcher.fixed-delay-ms:5000}")
    public void dispatchScheduled() {
        dispatchOnce();
    }

    public int dispatchOnce() {
        LocalDateTime now = LocalDateTime.now();
        claimService.recoverExpiredClaims(now);
        int dispatched = 0;
        for (Long id : eligibleImagePublicationIds(now, batchSize)) {
            dispatched += enqueue(id, publicationExecutor);
        }
        for (Long id : eligibleExecutionIds(now, batchSize)) {
            dispatched += enqueue(id, taskExecutor);
        }
        return dispatched;
    }

    private int enqueue(Long id, TaskExecutor executor) {
        if (!scheduled.add(id)) return 0;
        try {
            executor.execute(() -> {
                try {
                    worker.run(id);
                } finally {
                    scheduled.remove(id);
                }
            });
            return 1;
        } catch (RejectedExecutionException exception) {
            // No claim has been acquired yet; leave the task pending for the next scan.
            scheduled.remove(id);
            return 0;
        }
    }

    public List<Long> eligibleExecutionIds(LocalDateTime now, int limit) {
        return eligibleIds(now, limit, false);
    }

    public List<Long> eligibleImagePublicationIds(LocalDateTime now, int limit) {
        return eligibleIds(now, limit, true);
    }

    private List<Long> eligibleIds(LocalDateTime now, int limit, boolean publication) {
        int safeLimit = Math.max(1, Math.min(limit, batchSize));
        List<Long> alreadyScheduled = List.copyOf(scheduled);
        return taskMapper.selectList(new QueryWrapper<AiExecutionTaskEntity>()
                .eq("status", AiExecutionStatus.PENDING.name())
                .and(wrapper -> wrapper.isNull("next_run_at").or().le("next_run_at", now))
                .apply((publication ? "" : "not ") + AiExecutionTaskMapper.IMAGE_PUBLICATION)
                .notIn(!alreadyScheduled.isEmpty(), "id", alreadyScheduled)
                .orderByAsc("priority", "created_at", "id")
                .last("limit " + safeLimit))
            .stream()
            .map(task -> task.id)
            .toList();
    }
}

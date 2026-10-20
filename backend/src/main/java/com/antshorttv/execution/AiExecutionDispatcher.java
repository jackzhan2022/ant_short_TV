package com.antshorttv.execution;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AiExecutionDispatcher {
    private final AiExecutionTaskMapper taskMapper;
    private final AiExecutionClaimService claimService;
    private final AiExecutionWorker worker;
    private final AiExecutionPools pools;
    private final int batchSize;
    private final Duration claimTimeout;
    private final EnumMap<AiExecutionPoolType, AiExecutionTaskEntity> cursors = new EnumMap<>(AiExecutionPoolType.class);
    private final EnumMap<AiExecutionPoolType, Long> sweepUpperIds = new EnumMap<>(AiExecutionPoolType.class);

    public AiExecutionDispatcher(
        AiExecutionTaskMapper taskMapper,
        AiExecutionClaimService claimService,
        AiExecutionWorker worker,
        AiExecutionPools pools,
        @Value("${ai.execution.dispatcher.batch-size:20}") int batchSize,
        @Value("${ai.execution.claim-timeout:PT10M}") Duration claimTimeout
    ) {
        this.taskMapper = taskMapper;
        this.claimService = claimService;
        this.worker = worker;
        this.pools = pools;
        this.batchSize = batchSize;
        this.claimTimeout = claimTimeout;
    }

    @Scheduled(fixedDelayString = "${ai.execution.dispatcher.fixed-delay-ms:5000}")
    public void dispatchScheduled() {
        dispatchOnce();
    }

    public synchronized int dispatchOnce() {
        LocalDateTime now = LocalDateTime.now();
        claimService.recoverExpiredClaims(now);
        int dispatched = 0;
        for (var type : List.of(AiExecutionPoolType.TEXT, AiExecutionPoolType.IMAGE, AiExecutionPoolType.IMAGE_PUBLICATION)) {
            List<AiExecutionTaskEntity> candidates = candidates(now, type, cursors.get(type));
            if (candidates.isEmpty()) {
                boolean scanned = cursors.remove(type) != null;
                sweepUpperIds.remove(type);
                if (scanned) candidates = candidates(now, type, null);
            }
            for (var candidate : candidates) {
                var slot = pools.tryAcquire(type);
                if (slot == null) break;
                try (slot) {
                    cursors.put(type, candidate);
                    var claim = claimService.claim(candidate.id, UUID.randomUUID().toString(), now, claimTimeout);
                    if (claim == null) continue;
                    try {
                        slot.submit(() -> worker.runClaimed(claim));
                        dispatched++;
                    } catch (RejectedExecutionException exception) {
                        claimService.defer(claim, "Local execution pool is unavailable.", LocalDateTime.now());
                    }
                }
            }
        }
        return dispatched;
    }

    private List<AiExecutionTaskEntity> candidates(LocalDateTime now, AiExecutionPoolType type,
        AiExecutionTaskEntity cursor) {
        long upperId = sweepUpperIds.computeIfAbsent(type, key -> {
            var latest = taskMapper.selectOne(typedPending(now, key)
                .select("id").orderByDesc("id").last("limit 1"));
            return latest == null ? 0L : latest.id;
        });
        var query = typedPending(now, type).le("id", upperId);
        // Rotate the bounded scan past saturated teams/models instead of scanning the same first page.
        // A fixed upper watermark makes every sweep finish even while new tasks keep arriving.
        if (cursor != null) query.and(q -> q.gt("priority", cursor.priority)
            .or(n -> n.eq("priority", cursor.priority).gt("created_at", cursor.createdAt))
            .or(n -> n.eq("priority", cursor.priority).eq("created_at", cursor.createdAt).gt("id", cursor.id)));
        return taskMapper.selectList(query.orderByAsc("priority", "created_at", "id").last("limit " + batchSize));
    }

    private QueryWrapper<AiExecutionTaskEntity> typedPending(LocalDateTime now, AiExecutionPoolType type) {
        return pending(now).eq("task_type", type == AiExecutionPoolType.IMAGE_PUBLICATION ? "IMAGE" : type.name())
            .apply((type == AiExecutionPoolType.IMAGE_PUBLICATION ? "" : "not ") + AiExecutionTaskMapper.IMAGE_PUBLICATION);
    }

    public List<Long> eligibleImagePublicationIds(LocalDateTime now, int limit) {
        return taskMapper.selectList(typedPending(now, AiExecutionPoolType.IMAGE_PUBLICATION)
            .orderByAsc("priority", "created_at", "id").last("limit " + Math.max(1, Math.min(limit, batchSize))))
            .stream().map(task -> task.id).toList();
    }

    private QueryWrapper<AiExecutionTaskEntity> pending(LocalDateTime now) {
        return new QueryWrapper<AiExecutionTaskEntity>()
            .eq("status", AiExecutionStatus.PENDING.name())
            .and(wrapper -> wrapper.isNull("next_run_at").or().le("next_run_at", now));
    }

    public List<Long> eligibleExecutionIds(LocalDateTime now, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, batchSize));
        return taskMapper.selectList(pending(now)
                .in("task_type", "TEXT", "IMAGE")
                .apply("not " + AiExecutionTaskMapper.IMAGE_PUBLICATION)
                .orderByAsc("priority", "created_at", "id")
                .last("limit " + safeLimit))
            .stream()
            .map(task -> task.id)
            .toList();
    }
}

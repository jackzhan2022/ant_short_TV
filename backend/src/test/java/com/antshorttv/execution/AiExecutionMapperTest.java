package com.antshorttv.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class AiExecutionMapperTest {

    @Autowired
    private AiExecutionTaskMapper taskMapper;

    @Autowired
    private AiExecutionAttemptMapper attemptMapper;

    @Test
    void persistsExecutionAndCorrelatedAttempt() {
        LocalDateTime now = LocalDateTime.now();
        AiExecutionTaskEntity task = new AiExecutionTaskEntity();
        task.tenantId = 7101L;
        task.userId = 7102L;
        task.projectId = 7103L;
        task.scene = "AI_IMAGE_GENERATE";
        task.capability = "IMAGE_GENERATION";
        task.businessType = "AI_IMAGE_TASK";
        task.businessId = 7104L;
        task.status = AiExecutionStatus.PENDING.name();
        task.phase = "SUBMIT";
        task.progress = 0;
        task.executionVersion = 1;
        task.clientIdempotencyKey = "execution-mapper-test";
        task.traceId = "trace-execution-mapper-test";
        task.retryable = false;
        task.usageCostStatus = "PENDING";
        task.pointSettlementStatus = "PENDING";
        task.createdAt = now;
        task.updatedAt = now;

        taskMapper.insert(task);

        AiExecutionAttemptEntity attempt = new AiExecutionAttemptEntity();
        attempt.executionId = task.id;
        attempt.executionVersion = 1;
        attempt.phase = "SUBMIT";
        attempt.attemptNo = 1;
        attempt.status = AiExecutionAttemptStatus.STARTED.name();
        attempt.idempotencyKey = "execution-mapper-test:1:submit";
        attempt.providerContacted = false;
        attempt.retryable = false;
        attempt.startedAt = now;
        attemptMapper.insert(attempt);

        assertThat(task.id).isNotNull();
        assertThat(attempt.id).isNotNull();
        assertThat(attemptMapper.selectByExecutionId(task.id))
            .extracting(item -> item.id)
            .containsExactly(attempt.id);
    }

    @Test
    void rejectsDuplicateExecutionAndAttemptIdempotencyKeys() {
        LocalDateTime now = LocalDateTime.now();
        AiExecutionTaskEntity first = pendingTask("duplicate-execution-key", now);
        taskMapper.insert(first);

        assertThatThrownBy(() -> taskMapper.insert(pendingTask("duplicate-execution-key", now)))
            .isInstanceOf(Exception.class);

        AiExecutionAttemptEntity attempt = startedAttempt(first.id, "duplicate-attempt-key", now);
        attemptMapper.insert(attempt);

        assertThatThrownBy(() -> attemptMapper.insert(startedAttempt(first.id, "duplicate-attempt-key", now)))
            .isInstanceOf(Exception.class);
    }

    private AiExecutionTaskEntity pendingTask(String idempotencyKey, LocalDateTime now) {
        AiExecutionTaskEntity task = new AiExecutionTaskEntity();
        task.tenantId = 7201L;
        task.userId = 7202L;
        task.scene = "SCRIPT_GENERATE";
        task.capability = "TEXT_GENERATION";
        task.businessType = "SCRIPT_OPERATION";
        task.status = AiExecutionStatus.PENDING.name();
        task.phase = "GENERATE";
        task.progress = 0;
        task.executionVersion = 1;
        task.clientIdempotencyKey = idempotencyKey;
        task.traceId = "trace-" + idempotencyKey;
        task.retryable = false;
        task.usageCostStatus = "PENDING";
        task.pointSettlementStatus = "PENDING";
        task.createdAt = now;
        task.updatedAt = now;
        return task;
    }

    private AiExecutionAttemptEntity startedAttempt(
        Long executionId,
        String idempotencyKey,
        LocalDateTime now
    ) {
        AiExecutionAttemptEntity attempt = new AiExecutionAttemptEntity();
        attempt.executionId = executionId;
        attempt.executionVersion = 1;
        attempt.phase = "GENERATE";
        attempt.attemptNo = 1;
        attempt.status = AiExecutionAttemptStatus.STARTED.name();
        attempt.idempotencyKey = idempotencyKey;
        attempt.providerContacted = false;
        attempt.retryable = false;
        attempt.startedAt = now;
        return attempt;
    }
}

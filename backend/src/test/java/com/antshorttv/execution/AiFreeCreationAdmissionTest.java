package com.antshorttv.execution;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = {"ai.video.max-concurrent-per-tenant=1", "ai.execution.max-concurrent-per-model=1"})
class AiFreeCreationAdmissionTest {
    @Autowired AiExecutionService executions;
    @Autowired AiExecutionClaimService claims;
    @Autowired JdbcTemplate jdbc;
    @Autowired AiConcurrencyConfigService config;

    @Test
    void acceptedPendingVideoKeepsTeamQuotaAndCanPoll() {
        var accepted = accepted(9910101L, 9910201L);
        assertThat(claim(create(9910101L, 9910202L))).isNull();
        assertThat(claim(accepted)).isNotNull();
    }

    @Test
    void acceptedPendingVideoKeepsModelQuotaAcrossTeamsAndCanPoll() {
        var accepted = accepted(9910102L, 9910203L);
        assertThat(claim(create(9910103L, 9910203L))).isNull();
        assertThat(claim(accepted)).isNotNull();
    }

    @Test
    void completedRemoteVideoReleasesQuota() {
        var accepted = accepted(9910104L, 9910204L);
        var polling = claim(accepted);
        assertThat(polling).isNotNull();
        claims.markSucceeded(polling.executionId(), polling.attemptId(), polling.claimToken(),
            "FREE_CREATION", 1L, LocalDateTime.now());
        assertThat(claim(create(9910104L, 9910204L))).isNotNull();
    }

    @Test
    void loweringLimitsNeverBlocksAlreadyAcceptedVideosFromFinishing() {
        var previous = config.current();
        try {
            config.save(new AiConcurrencySettings(previous.teamTextLimit(), previous.teamImageLimit(),
                2, 2, previous.providerLimit(), previous.textWorkers(), previous.imageWorkers(),
                previous.videoSubmitWorkers(), previous.videoQueryWorkers()));
            var first = accepted(9910105L, 9910205L);
            var second = accepted(9910105L, 9910205L);
            config.save(new AiConcurrencySettings(previous.teamTextLimit(), previous.teamImageLimit(),
                1, 1, previous.providerLimit(), previous.textWorkers(), previous.imageWorkers(),
                previous.videoSubmitWorkers(), previous.videoQueryWorkers()));
            assertThat(claim(create(9910105L, 9910205L))).isNull();
            assertThat(claim(first)).isNotNull();
            assertThat(claim(second)).isNotNull();
        } finally {
            config.save(previous);
        }
    }

    private AiExecutionTaskEntity accepted(long tenant, long model) {
        var task = create(tenant, model);
        var submitted = claim(task);
        assertThat(submitted).isNotNull();
        jdbc.update("""
            insert into free_creation(tenant_id,creator_id,execution_id,request_id,request_hash,
                media_type,model_id,model_name,provider_code,prompt,parameters_json,external_task_id,created_at,updated_at)
            values(?,?,?,?,?,'VIDEO',?,'test','test','test','{}','remote-task',current_timestamp,current_timestamp)
            """, tenant, tenant, task.id, UUID.randomUUID().toString(), "hash", model);
        claims.defer(task.id, submitted.attemptId(), submitted.claimToken(), "PENDING", "poll",
            LocalDateTime.now().minusSeconds(1), LocalDateTime.now());
        return task;
    }

    private AiExecutionTaskEntity create(long tenant, long model) {
        String key = UUID.randomUUID().toString();
        return executions.create(new AiExecutionCreateCommand(tenant, tenant, null,
            "free_creation_video", "VIDEO", "FREE_CREATION", null, model, "SUBMIT", key, key, true, null));
    }

    private AiExecutionClaim claim(AiExecutionTaskEntity task) {
        return claims.claim(task.id, UUID.randomUUID().toString(), LocalDateTime.now(), Duration.ofMinutes(10));
    }
}

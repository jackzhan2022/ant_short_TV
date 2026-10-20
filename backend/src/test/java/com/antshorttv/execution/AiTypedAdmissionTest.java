package com.antshorttv.execution;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {"ai.execution.max-concurrent-per-tenant=1", "ai.video.max-concurrent-per-tenant=1"})
class AiTypedAdmissionTest {
    @Autowired AiExecutionService executions;
    @Autowired AiExecutionClaimService claims;
    @Autowired AiExecutionTaskMapper tasks;

    @Test
    void teamTypesAreIndependentAndVideoHandoffRetainsQuotaWithoutExpiring() {
        long tenant = 9900101L;
        var text = create(tenant, "TEXT", 9900201L);
        var image = create(tenant, "IMAGE", 9900202L);
        var video = create(tenant, "VIDEO", 9900203L);
        assertThat(claim(text)).isNotNull();
        assertThat(claim(image)).isNotNull();
        var videoClaim = claim(video);
        assertThat(videoClaim).isNotNull();
        claims.handoffExternal(videoClaim, LocalDateTime.now());
        assertThat(tasks.selectById(video.id).status).isEqualTo("RUNNING");
        assertThat(tasks.selectById(video.id).claimExpiresAt).isNull();
        assertThat(claim(create(tenant, "VIDEO", 9900204L))).isNull();
        assertThat(claim(create(tenant, "TEXT", 9900205L))).isNull();
    }

    @Test
    void concurrentAdmissionsForDifferentModelsCannotOverbookOneTeam() throws Exception {
        var first = create(9900102L, "TEXT", 9900206L);
        var second = create(9900102L, "TEXT", 9900207L);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var results = new ArrayList<java.util.concurrent.Future<AiExecutionClaim>>();
            for (var task : java.util.List.of(first, second)) {
                results.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return claim(task);
                }));
            }
            assertThat(ready.await(2, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            int admitted = 0;
            for (var result : results) if (result.get(5, TimeUnit.SECONDS) != null) admitted++;
            assertThat(admitted).isEqualTo(1);
        } finally { executor.shutdownNow(); }
    }

    @Test
    void videoUnderstandingIsTextWork() {
        assertThat(create(9900103L, "VIDEO_UNDERSTANDING", null).taskType).isEqualTo("TEXT");
    }

    @Test
    void regenerationPreservesImageClassification() {
        var image = create(9900104L, "IMAGE", null);
        image.status = "SUCCEEDED";
        tasks.updateById(image);
        assertThat(executions.regenerate(image.id, UUID.randomUUID().toString(), "regeneration").taskType)
            .isEqualTo("IMAGE");
    }

    private AiExecutionTaskEntity create(long tenant, String capability, Long model) {
        String key = UUID.randomUUID().toString();
        return executions.create(new AiExecutionCreateCommand(tenant, tenant, null,
            "ADMISSION_TEST", capability, "TEST", null, model, "SUBMIT", key, key, false, null));
    }

    private AiExecutionClaim claim(AiExecutionTaskEntity task) {
        return claims.claim(task.id, UUID.randomUUID().toString(), LocalDateTime.now(), Duration.ofMinutes(10));
    }
}

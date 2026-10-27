package com.antshorttv.execution;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties="ark.avatar.worker-enabled=false")
class AiSubtitleEraseAdmissionTest {
    @Autowired AiExecutionService executions;
    @Autowired AiExecutionClaimService claims;
    @Autowired AiConcurrencyConfigService config;
    @Autowired JdbcTemplate jdbc;
    @MockBean AiExecutionPools pools;
    @Test void acceptedErasureKeepsVideoQuotaButCanAlwaysPollAndReleasesItOnCompletion() {
        var old=config.current();
        try {
            config.save(new AiConcurrencySettings(old.teamTextLimit(),old.teamImageLimit(),1,old.modelLimit(),old.providerLimit(),
                old.textWorkers(),old.imageWorkers(),old.videoSubmitWorkers(),old.videoQueryWorkers()));
            long tenant=9920101L;
            var accepted=create(tenant,"SUBTITLE_ERASE","VIDEO_SUBTITLE_ERASE","video_subtitle_erase");
            var first=claim(accepted);assertThat(first).isNotNull();
            jdbc.update("""
                insert into subtitle_erase_task(tenant_id,project_id,storyboard_id,source_result_id,execution_id,method,quality,client_key,provider_task_id,created_by,deadline_at)
                values(?,1,1,1,?,'AUTO','PRO',?,'remote-erase',?,?)
                """,tenant,accepted.id,UUID.randomUUID().toString(),tenant,LocalDateTime.now().plusHours(6));
            claims.defer(accepted.id,first.attemptId(),first.claimToken(),"PENDING","poll",LocalDateTime.now().minusSeconds(1),LocalDateTime.now());
            var waiting=create(tenant,"FREE_CREATION","VIDEO","free_creation_video");
            assertThat(claim(waiting)).isNull();
            var poll=claim(accepted);assertThat(poll).isNotNull();
            claims.markSucceeded(poll.executionId(),poll.attemptId(),poll.claimToken(),"AI_VIDEO_RESULT",1L,LocalDateTime.now());
            assertThat(claim(waiting)).isNotNull();
        } finally {config.save(old);}
    }
    private AiExecutionTaskEntity create(long tenant,String business,String capability,String scene) {
        String key=UUID.randomUUID().toString();
        return executions.create(new AiExecutionCreateCommand(tenant,tenant,null,scene,capability,business,null,null,"ERASE_SUBMIT",key,key,true,null));
    }
    private AiExecutionClaim claim(AiExecutionTaskEntity task) {
        return claims.claim(task.id,UUID.randomUUID().toString(),LocalDateTime.now(),Duration.ofMinutes(10));
    }
}

package com.antshorttv.workflowagent.tool;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.verifyNoInteractions;
import com.antshorttv.ai.AiInvocationService;
import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.script.StoryboardAssetReferenceRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Import;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@Import(StoryboardSaveTransactionTest.ProbeConfiguration.class)
class StoryboardSaveTransactionTest {
    @Autowired private WorkflowToolRegistry tools;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper json;
    @Autowired private ProbeDataService probe;
    @Autowired private PlatformTransactionManager transactions;
    @MockBean private AiInvocationService invocation;

    @BeforeEach void reset() {
        probe=org.springframework.test.util.AopTestUtils.getUltimateTargetObject(probe);
        jdbc.execute("create table if not exists storyboard_save_retry_probe (id bigint auto_increment primary key, marker varchar(80))");
        jdbc.update("delete from storyboard_save_retry_probe");
        probe.attempts.set(0); probe.failures=0; probe.failure=null; probe.interruptOnFailure=false;
        probe.fingerprints.clear(); probe.resources.clear();
    }
    private ToolExecutionContext context(Instant deadline) {
        return new ToolExecutionContext(1L,2L,3L,4L,5L,6L,null,7L,Set.of("SCRIPT:VIEW"),deadline,new WorkflowToolRunState());
    }
    private JsonNode payload() {
        return json.createObjectNode().put("schemaVersion",3).put("episodeFingerprint","original").set("storyboards",json.createArrayNode());
    }
    private JsonNode save(ToolExecutionContext context, JsonNode payload) {
        return tools.require("save_episode_storyboards").executor().execute(context,payload);
    }
    @Test void retriesRolledBackTransactionsWithoutAnotherModelRequestOrMutatedPayload() {
        probe.failures=2;var payload=payload();
        assertThat(save(context(null),payload).path("saved").asBoolean()).isTrue();
        assertThat(probe.attempts.get()).isEqualTo(3);
        assertThat(probe.fingerprints).containsExactly("original","original","original");
        assertThat(payload.path("episodeFingerprint").asText()).isEqualTo("original");
        assertThat(jdbc.queryForList("select marker from storyboard_save_retry_probe",String.class)).containsExactly("attempt-3");
        Set<Object> identities=Collections.newSetFromMap(new IdentityHashMap<>());identities.addAll(probe.resources);
        assertThat(identities).hasSize(3);
        verifyNoInteractions(invocation);
    }
    @Test void givesUpAfterThreeAttemptsAndLeavesNoPartialWrites() {
        probe.failures=10;
        assertThatThrownBy(()->save(context(null),payload())).isInstanceOf(CannotAcquireLockException.class);
        assertThat(probe.attempts.get()).isEqualTo(3);
        assertThat(jdbc.queryForObject("select count(*) from storyboard_save_retry_probe",Integer.class)).isZero();
        verifyNoInteractions(invocation);
    }
    @ParameterizedTest @ValueSource(strings={"VALIDATION","FORBIDDEN","DUPLICATE","LOCK_TIMEOUT","CONNECTION"})
    void neverRetriesOtherFailures(String kind) {
        probe.failure=switch(kind) {
            case "VALIDATION" -> new BusinessException(ErrorCode.VALIDATION_ERROR,"invalid content mentions deadlock");
            case "FORBIDDEN" -> new BusinessException(ErrorCode.FORBIDDEN,"denied");
            case "DUPLICATE" -> new DuplicateKeyException("duplicate",new SQLException("duplicate","23000",1062));
            case "LOCK_TIMEOUT" -> new CannotAcquireLockException("timeout",new SQLException("timeout","HY000",1205));
            default -> new DataAccessResourceFailureException("deadlock text is not a deadlock code",new SQLException("connection","08S01",0));
        };
        assertThatThrownBy(()->save(context(null),payload())).isSameAs(probe.failure);
        assertThat(probe.attempts.get()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from storyboard_save_retry_probe",Integer.class)).isZero();
        verifyNoInteractions(invocation);
    }
    @Test void recognizesDeadlockCodesInJdbcNextExceptionChains() {
        var wrapper=new SQLException("batch error","HY000",0);
        wrapper.setNextException(new SQLException("deadlock","40001",1213));
        probe.failure=new DataAccessResourceFailureException("wrapped SQL error",wrapper);
        assertThatThrownBy(()->save(context(null),payload())).isSameAs(probe.failure);
        assertThat(probe.attempts.get()).isEqualTo(3);
        assertThat(jdbc.queryForObject("select count(*) from storyboard_save_retry_probe",Integer.class)).isZero();
        verifyNoInteractions(invocation);
    }
    @Test void eachAttemptIsIndependentOfAnAmbientTransaction() {
        probe.failures=1;
        new TransactionTemplate(transactions).executeWithoutResult(status->{
            jdbc.update("insert into storyboard_save_retry_probe(marker) values ('outer')");
            assertThat(save(context(null),payload()).path("saved").asBoolean()).isTrue();
            assertThat(status.isRollbackOnly()).isFalse();
        });
        assertThat(jdbc.queryForList("select marker from storyboard_save_retry_probe",String.class)).containsExactlyInAnyOrder("outer","attempt-2");
    }
    @Test void anExpiredDeadlineDoesNotStartASaveAttempt() {
        assertThatThrownBy(()->save(context(Instant.now().minusSeconds(1)),payload()))
            .isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.getErrorCode()).isEqualTo(ErrorCode.WORKFLOW_AGENT_TIMEOUT));
        assertThat(probe.attempts.get()).isZero();
    }
    @Test void interruptionStopsDeadlockRetriesAndPreservesTheInterruptFlag() {
        probe.failures=2;probe.interruptOnFailure=true;
        try {
            assertThatThrownBy(()->save(context(null),payload())).isInstanceOf(CannotAcquireLockException.class);
            assertThat(probe.attempts.get()).isEqualTo(1);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {Thread.interrupted();}
    }

    @TestConfiguration static class ProbeConfiguration {
        @Bean @Primary ProbeDataService probeDataService(JdbcTemplate jdbc,ObjectMapper json,StoryboardAssetReferenceRepository references) {
            return new ProbeDataService(jdbc,json,references);
        }
    }
    static class ProbeDataService extends StoryboardToolDataService {
        final JdbcTemplate jdbc;final ObjectMapper json;final AtomicInteger attempts=new AtomicInteger();
        final List<String> fingerprints=new ArrayList<>();final List<Object> resources=new ArrayList<>();
        int failures;RuntimeException failure;boolean interruptOnFailure;
        ProbeDataService(JdbcTemplate jdbc,ObjectMapper json,StoryboardAssetReferenceRepository references) {
            super(jdbc,json,references);this.jdbc=jdbc;this.json=json;
        }
        @Override @Transactional public JsonNode saveEpisodeStoryboards(ToolExecutionContext context,JsonNode payload) {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            resources.add(TransactionSynchronizationManager.getResource(jdbc.getDataSource()));
            fingerprints.add(payload.path("episodeFingerprint").asText());
            int attempt=attempts.incrementAndGet();
            jdbc.update("insert into storyboard_save_retry_probe(marker) values (?)","attempt-"+attempt);
            if(failure!=null)throw failure;
            if(attempt<=failures) {
                ((com.fasterxml.jackson.databind.node.ObjectNode)payload).remove("episodeFingerprint");
                if(interruptOnFailure)Thread.currentThread().interrupt();
                throw new CannotAcquireLockException("simulated deadlock",new SQLException("deadlock","40001",1213));
            }
            return json.createObjectNode().put("saved",true).put("episodeId",4).put("storyboardCount",1).set("storyboardIds",json.createArrayNode().add(1));
        }
    }
}

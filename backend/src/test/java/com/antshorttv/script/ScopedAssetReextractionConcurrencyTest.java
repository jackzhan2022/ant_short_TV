package com.antshorttv.script;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.antshorttv.execution.AiExecutionClaim;
import com.antshorttv.execution.AiExecutionContext;
import com.antshorttv.execution.AiExecutionTaskEntity;
import com.antshorttv.workflowagent.run.WorkflowAgentRunRepository;
import com.antshorttv.workflowagent.run.WorkflowAgentRunner;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

@SpringBootTest
class ScopedAssetReextractionConcurrencyTest {
    @Autowired private JdbcTemplate database;
    @Autowired private PlatformTransactionManager transactionManager;

    @Test
    void overlapsRealScopedUnitTransactionsAtTheConfiguredBound() throws Exception {
        seedCommittedOperation();
        AssetRecognitionAgentAdapter recognition = mock(AssetRecognitionAgentAdapter.class);
        CountDownLatch bothStarted = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger peak = new AtomicInteger();
        when(recognition.executeChild(any(), any(), any(), anyLong(), any(), anyLong(), any(), any()))
            .thenAnswer(call -> {
                int current = active.incrementAndGet();
                peak.accumulateAndGet(current, Math::max);
                bothStarted.countDown();
                try {
                    release.await(5, TimeUnit.SECONDS);
                } finally {
                    active.decrementAndGet();
                }
                return new AssetRecognitionAgentAdapter.Execution(call.getArgument(3, Long.class), List.of());
            });
        ScopedAssetReextractionService service = new ScopedAssetReextractionService(
            database, mock(WorkflowAgentRunner.class), recognition, transactionManager,
            mock(AssetExtractionCoordination.class), mock(WorkflowAgentRunRepository.class),
            new ObjectMapper(), 2);

        CompletableFuture<Void> execution = CompletableFuture.runAsync(() ->
            assertThatThrownBy(() -> service.execute(operation(),
                new ScopedAssetReextractionRequest("ALL", "FILL_EMPTY"), context()))
                .hasMessageContaining("提交证据")
        );

        assertThat(bothStarted.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(peak).hasValue(2);
        release.countDown();
        execution.get(5, TimeUnit.SECONDS);
        assertThat(database.queryForList(
            "select status from scoped_asset_reextraction_unit order by episode_id", String.class))
            .containsExactly("SUCCEEDED", "SUCCEEDED", "SUCCEEDED");
    }

    private void seedCommittedOperation() {
        database.update("insert into tenant (id,code,name,type,status,created_at,updated_at) values (9801,'scope-concurrency','Scope','TEAM','ACTIVE',now(),now())");
        database.update("insert into project (id,tenant_id,name,code,owner_id,status,created_by,created_at,updated_at) values (9802,9801,'Scope','scope-concurrency',1,'ACTIVE',1,now(),now())");
        database.update("insert into script (id,tenant_id,project_id,title,source_type,content,status,created_by,created_at,updated_at) values (9803,9801,9802,'Script','MANUAL_EDIT','text','ACTIVE',1,now(),now())");
        database.update("insert into script_ai_operation (id,tenant_id,project_id,operation_type,script_id,redacted_input_json,idempotency_key,status,created_by,created_at,updated_at) values (9804,9801,9802,'SCOPED_ASSET_REEXTRACTION',9803,'{}','scope-concurrency','RUNNING',1,now(),now())");
        for (long id : List.of(9811L, 9812L, 9813L)) {
            database.update("insert into script_episode (id,tenant_id,project_id,script_id,stable_key,episode_no,title,content,content_fingerprint,reconciliation_status,status,created_at,updated_at) values (?,9801,9802,9803,?,?,'Episode','text','hash','NEW','ACTIVE',now(),now())",
                id, "e" + id, id - 9810);
            database.update("insert into ai_workflow_agent_run (id,agent_code,run_type,tenant_id,user_id,project_id,task_id,status,model_id,temperature,max_tokens,max_steps,prompt_snapshot,started_at,created_at) values (?,'short-drama-asset-recognition','FORMAL',9801,1,9802,9804,'SUCCEEDED',1,0.1,4096,20,'audit',now(),now())",
                id);
        }
    }

    private ScriptAiOperationEntity operation() {
        ScriptAiOperationEntity operation = new ScriptAiOperationEntity();
        operation.id = 9804L;
        operation.tenantId = 9801L;
        operation.projectId = 9802L;
        operation.scriptId = 9803L;
        operation.createdBy = 1L;
        return operation;
    }

    private AiExecutionContext context() {
        AiExecutionTaskEntity execution = new AiExecutionTaskEntity();
        execution.id = 9805L;
        execution.executionVersion = 1;
        execution.resolvedModelId = 1L;
        return new AiExecutionContext(
            execution, new AiExecutionClaim(9805L, 9806L, "scope-concurrency", 1, "RUNNING"));
    }
}

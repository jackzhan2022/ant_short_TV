package com.antshorttv.script;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

import com.antshorttv.ai.AiBusinessScene;
import com.antshorttv.ai.ProjectAiConfigService;
import com.antshorttv.execution.*;
import com.antshorttv.security.TenantContext;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Run against an isolated MySQL schema with Spring datasource system properties as well as H2. */
@SpringBootTest
class AssetExtractionCoordinationTest {
    @Autowired AssetExtractionCoordination coordination;
    @Autowired ScriptAiOperationService operations;
    @Autowired AiExecutionService executions;
    @Autowired AiExecutionClaimService claims;
    @Autowired AiExecutionTaskMapper tasks;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @MockBean ProjectAiConfigService models;
    @MockBean com.antshorttv.workflowagent.run.WorkflowAgentRunner runner;
    @MockBean AssetRecognitionAgentAdapter recognition;
    @MockBean com.antshorttv.rbac.ProjectPermissionGuard permissions;
    @Autowired ScopedAssetReextractionService scoped;
    @Autowired ScriptAiOperationMapper operationMapper;
    @Autowired com.antshorttv.workflowagent.tool.ScreenplayToolDataService tools;
    long tenant, project, script, model, version=1, episode;

    @BeforeEach void setup() {
        tenant = Math.abs(UUID.randomUUID().getMostSignificantBits() % 1_000_000_000) + 10_000_000;
        project=tenant+1; script=tenant+2; model=tenant+3;
        jdbc.update("insert into ai_model(id,provider_id,code,name,model_code,service_type,status,is_default,sort,created_at,updated_at) select ?,provider_id,?,'test','test','TEXT','DISABLED',false,0,now(),now() from ai_model order by id limit 1",model,"concurrency-"+tenant);
        when(models.resolveModelId(anyLong(),anyLong(),eq("TEXT"))).thenReturn(model);
        when(runner.freezeFormal(anyString())).thenReturn(new com.antshorttv.workflowagent.run.WorkflowAgentExecutionPlan(
            new com.antshorttv.workflowagent.agent.WorkflowAgentRecord(null,"short-drama-asset-recognition","test","","frozen prompt",
                model,java.math.BigDecimal.ZERO,16384,12,"ENABLED",1L,tenant,tenant,LocalDateTime.now(),LocalDateTime.now(),
                java.util.List.of(),java.util.List.of("read_current_episode","save_episode_assets")),java.util.List.of()));
        jdbc.update("insert into team_point_account (tenant_id,balance,reserved_balance,total_granted,total_consumed,total_reserved,total_released,total_refunded,version,created_at,updated_at) values (?,100,0,100,0,0,0,0,0,now(),now())",tenant);
        for(String prefix : new String[]{"ai_model_price", "ai_model_point_price"}) {
            jdbc.update("insert into "+prefix+"_version (model_id,version_no,status,effective_from,published_at,created_at) values (?,1,'PUBLISHED',?,now(),now())",model,LocalDateTime.now().minusHours(1));
            Long id=jdbc.queryForObject("select id from "+prefix+"_version where model_id=?",Long.class,model);
            if(prefix.equals("ai_model_price"))
                jdbc.update("insert into ai_model_price_component (price_version_id,metric,unit_size,unit_price,currency,dimensions_json,dimensions_key,created_at) values (?,'CALL',1,0.1,'USD','{}','',now())",id);
            else jdbc.update("insert into ai_model_point_price_component (price_version_id,metric,unit_size,point_rate,dimensions_json,dimensions_key,created_at) values (?,'CALL',1,2,'{}','',now())",id);
        }
    }

    private AiExecutionResponse submit(long user,String scope,String policy,String key) {
        return operations.submit(new TenantContext(user,tenant,user,"OWNER"),project,
            AiBusinessScene.SCOPED_ASSET_REEXTRACTION,"SCOPED_ASSET_REEXTRACTION",script,version,
            new ScopedAssetReextractionRequest(scope,policy),key,key);
    }

    @Test void competingClientKeysCreateOneExecutionAndOneReservation() throws Exception {
        ExecutorService pool=Executors.newFixedThreadPool(2);
        CountDownLatch ready=new CountDownLatch(2), go=new CountDownLatch(1);
        try {
            Callable<Long> submit=()-> {ready.countDown();assertThat(go.await(10,TimeUnit.SECONDS)).isTrue();
                return submit(tenant,"ALL","FILL_EMPTY",UUID.randomUUID().toString()).id();};
            Future<Long> a=pool.submit(submit),b=pool.submit(submit);
            assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue();go.countDown();
            assertThat(a.get(30,TimeUnit.SECONDS)).isEqualTo(b.get(30,TimeUnit.SECONDS));
            assertThat(count("ai_execution_task")).isEqualTo(1);
            assertThat(count("script_ai_operation")).isEqualTo(1);
            assertThat(count("ai_point_reservation")).isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from point_ledger where tenant_id=? and entry_type='RESERVE'",Integer.class,tenant)).isEqualTo(1);
        } finally { pool.shutdownNow(); }
    }

    @Test void differentUserScopeOrPolicyConflictsWithoutReservation() {
        long id=submit(tenant,"CHARACTER","FILL_EMPTY","first-"+tenant).id();
        assertThatThrownBy(()->submit(tenant+1,"CHARACTER","FILL_EMPTY","other-user-"+tenant))
            .isInstanceOf(AssetExtractionConflictException.class);
        assertThatThrownBy(()->submit(tenant,"PROP","FILL_EMPTY","other-scope-"+tenant))
            .isInstanceOf(AssetExtractionConflictException.class);
        assertThatThrownBy(()->submit(tenant,"CHARACTER","REGENERATE_ALL","other-policy-"+tenant))
            .isInstanceOf(AssetExtractionConflictException.class);
        assertThat(count("ai_point_reservation")).isEqualTo(1);
        assertThat(tasks.selectById(id).userId).isEqualTo(tenant);
        assertThatThrownBy(()->submit(tenant+1,"CHARACTER","FILL_EMPTY","first-"+tenant))
            .hasMessageContaining("幂等标识");
    }

    @Test void expiredLeaseIsFencedBeforeAndAfterReclaim() {
        var old=context(create("SCRIPT_AI_OPERATION"));
        coordination.acquire(script,old);
        jdbc.update("update ai_execution_task set claim_expires_at=? where id=?",LocalDateTime.now().minusMinutes(1),old.task().id);
        assertThatThrownBy(()->assertOwned(old,script)).isInstanceOf(AiExecutionClaimLostException.class);
        claims.recoverExpiredClaims(LocalDateTime.now());
        var replacement=context(old.task());
        coordination.acquire(script,replacement);
        coordination.release(script,old);
        assertOwned(replacement,script);
        assertThatThrownBy(()->assertOwned(old,script)).isInstanceOf(AiExecutionClaimLostException.class);
    }

    @Test void analysisAndAssetEntryWaitButOtherScriptCanProceed() {
        var first=context(create("SCRIPT_ANALYSIS_TASK"));
        coordination.acquire(script,first);
        var other=context(create("SCRIPT_AI_OPERATION"));
        assertThatThrownBy(()->coordination.acquire(script,other)).isInstanceOf(AiExecutionDeferredException.class);
        coordination.acquire(script+1,other);
        assertOwned(first,script);
        assertOwned(other,script+1);
        assertThatThrownBy(()->submit(tenant,"ALL","FILL_EMPTY","analysis-conflict-"+tenant))
            .isInstanceOf(AssetExtractionConflictException.class);
    }

    @Test void canceledAttemptCannotWriteOrReleaseReplacementOwner() {
        var old=context(create("SCRIPT_ANALYSIS_TASK"));
        coordination.acquire(script,old);
        executions.cancel(old.task().id);
        var replacement=context(create("SCRIPT_AI_OPERATION"));
        coordination.acquire(script,replacement);
        assertThatThrownBy(()->assertOwned(old,script)).isInstanceOf(AiExecutionClaimLostException.class);
        coordination.release(script,old);
        assertOwned(replacement,script);
    }

    @Test void deferredAttemptsDoNotConsumeFailureRetryBudgetAndOldAttemptIsFenced() {
        var old=context(create("SCRIPT_AI_OPERATION"));
        coordination.acquire(script,old);
        var current=old;
        for(int i=0;i<4;i++) {
            claims.defer(current.claim(),"resource wait",LocalDateTime.now().minusMinutes(1));
            current=context(current.task());
        }
        coordination.acquire(script,current);
        assertThatThrownBy(()->assertOwned(old,script)).isInstanceOf(AiExecutionClaimLostException.class);
        coordination.release(script,old);
        assertOwned(current,script);
        claims.markFailed(current.task().id,current.claim().attemptId(),current.claim().claimToken(),
            "TEST","first actual failure",new AiExecutionRetryPolicy(3,Duration.ZERO),LocalDateTime.now());
        assertThat(tasks.selectById(current.task().id).status).isEqualTo("PENDING");
    }

    @Test void differentScriptAdmissionDoesNotWaitForLockedOwner() throws Exception {
        ExecutorService pool=Executors.newSingleThreadExecutor();
        try {
            new TransactionTemplate(transactions).execute(status->{
                coordination.admit(tenant,project,script,"a");
                try { assertThat(pool.submit(()->coordination.admit(tenant,project,script+1,"b"))
                    .get(5,TimeUnit.SECONDS)).isNull(); }
                catch(Exception e) {throw new RuntimeException(e);}
                return null;
            });
        } finally {pool.shutdownNow();}
    }

    @Test void committedSaveIsRecoveredAfterCrashWithoutGeneratingAgain() throws Exception {
        seedScript();
        long id=submit(tenant,"PROP","FILL_EMPTY","crash-"+tenant).id();
        var original=context(tasks.selectById(id));
        var operation=operationMapper.selectById(original.task().businessId);
        var request=new ScopedAssetReextractionRequest("PROP","FILL_EMPTY");
        doAnswer(invocation->{ saveProp(operation,invocation.getArgument(4)); throw new IllegalStateException("crash after commit"); })
            .when(recognition).executeChild(any(),any(),any(),anyLong(),any(),anyLong(),any(),any());
        assertThatThrownBy(()->scoped.execute(operation,request,original)).hasMessageContaining("crash after commit");
        assertThat(count("prop_asset")).isEqualTo(1);
        assertThat(count("script_episode_asset_analysis")).isEqualTo(1);
        claims.markFailed(id,original.claim().attemptId(),original.claim().claimToken(),"CRASH","test",
            new AiExecutionRetryPolicy(3,Duration.ZERO),LocalDateTime.now());
        var recovered=context(tasks.selectById(id));
        scoped.execute(operation,request,recovered);
        scoped.execute(operation,request,recovered); // finalization replay is harmless
        verify(recognition,times(1)).executeChild(any(),any(),any(),anyLong(),any(),anyLong(),any(),any());
        verify(runner,times(1)).freezeFormal(anyString());
        assertThat(count("prop_asset")).isEqualTo(1);
        assertThat(count("asset_visual_variant_episode")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select status from scoped_asset_reextraction_snapshot where operation_id=?",String.class,operation.id)).isEqualTo("SUCCEEDED");
    }

    @Test void supersededCommitRequiresFreshSubmissionBeforeAnyFurtherGeneration() {
        seedScript();
        long id=submit(tenant,"PROP","FILL_EMPTY","superseded-"+tenant).id();
        var context=context(tasks.selectById(id));
        var operation=operationMapper.selectById(context.task().businessId);
        var request=new ScopedAssetReextractionRequest("PROP","FILL_EMPTY");
        doAnswer(invocation->{saveProp(operation,context);throw new IllegalStateException("crash after commit");})
            .when(recognition).executeChild(any(),any(),any(),anyLong(),any(),anyLong(),any(),any());
        assertThatThrownBy(()->scoped.execute(operation,request,context)).hasMessageContaining("crash after commit");
        jdbc.update("update script_episode_asset_analysis set generated_by_run_id=null where episode_id=?",episode);
        assertThatThrownBy(()->scoped.execute(operation,request,context)).hasMessageContaining("其他任务更新");
        verify(recognition,times(1)).executeChild(any(),any(),any(),anyLong(),any(),anyLong(),any(),any());
    }

    @Test void cancellationBeforeSaveRejectsOldWorkerWithoutPartialAssets() {
        seedScript();
        long id=submit(tenant,"PROP","FILL_EMPTY","cancel-"+tenant).id();
        var context=context(tasks.selectById(id));
        var operation=operationMapper.selectById(context.task().businessId);
        doAnswer(invocation->{executions.cancel(id);return saveProp(operation,context);})
            .when(recognition).executeChild(any(),any(),any(),anyLong(),any(),anyLong(),any(),any());
        assertThatThrownBy(()->scoped.execute(operation,new ScopedAssetReextractionRequest("PROP","FILL_EMPTY"),context))
            .isInstanceOf(AiExecutionClaimLostException.class);
        assertThat(count("prop_asset")).isZero();
        assertThat(count("script_episode_asset_analysis")).isZero();
    }

    @Test void changedSourceBlocksFinalizationAndProtectsManualAndOtherScopeAssets() {
        seedScript();
        jdbc.update("insert into character_asset(tenant_id,project_id,script_id,name,role_type,status,source,created_by,created_at,updated_at) values (?,?,?,'outside','SUPPORTING','DRAFT','USER',?,now(),now())",tenant,project,script,tenant);
        jdbc.update("insert into prop_asset(tenant_id,project_id,script_id,name,prop_type,status,source,created_by,created_at,updated_at) values (?,?,?,'manual','KEY_PROP','DRAFT','USER',?,now(),now())",tenant,project,script,tenant);
        long id=submit(tenant,"PROP","FILL_EMPTY","source-"+tenant).id();
        var context=context(tasks.selectById(id));
        var operation=operationMapper.selectById(context.task().businessId);
        doAnswer(invocation->{var saved=saveProp(operation,context);
            jdbc.update("update script_episode set content_fingerprint='changed' where id=?",episode);return saved;})
            .when(recognition).executeChild(any(),any(),any(),anyLong(),any(),anyLong(),any(),any());
        assertThatThrownBy(()->scoped.execute(operation,new ScopedAssetReextractionRequest("PROP","FILL_EMPTY"),context))
            .hasMessageContaining("变化");
        assertThat(jdbc.queryForObject("select count(*) from character_asset where script_id=? and deleted_at is null",Integer.class,script)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from prop_asset where script_id=? and deleted_at is null",Integer.class,script)).isEqualTo(2);
        long run=jdbc.queryForObject("select child_run_id from scoped_asset_reextraction_unit where episode_id=?",Long.class,episode);
        jdbc.update("insert into prop_asset(tenant_id,project_id,script_id,name,prop_type,status,source,generated_by_run_id,created_by,created_at,updated_at) values (?,?,?,'obsolete','KEY_PROP','DRAFT','AI',?,?,now(),now())",tenant,project,script,run,tenant);
        jdbc.update("update script_episode set content_fingerprint='original' where id=?",episode);
        scoped.execute(operation,new ScopedAssetReextractionRequest("PROP","FILL_EMPTY"),context);
        assertThat(jdbc.queryForObject("select count(*) from character_asset where script_id=? and deleted_at is null",Integer.class,script)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from prop_asset where script_id=? and deleted_at is null",Integer.class,script)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from prop_asset where script_id=? and name='obsolete' and deleted_at is not null",Integer.class,script)).isEqualTo(1);
    }

    @Test void activeIdentityConstraintAllowsSoftDeletedHistoryButRejectsDuplicates() {
        seedScript();
        String insert="insert into prop_asset(tenant_id,project_id,script_id,name,normalized_name,prop_type,status,source,created_by,created_at,updated_at) values (?,?,?,'徽章','徽章','KEY_PROP','DRAFT','USER',?,now(),now())";
        jdbc.update(insert,tenant,project,script,tenant);
        assertThatThrownBy(()->jdbc.update(insert,tenant,project,script,tenant))
            .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
        jdbc.update("update prop_asset set deleted_at=now() where script_id=?",script);
        jdbc.update(insert,tenant,project,script,tenant);
        assertThat(count("prop_asset")).isEqualTo(2);
    }

    @Test void changedScriptVersionRejectsSaveEvenIfEpisodeTextIsUnchanged() {
        seedScript();
        long id=submit(tenant,"PROP","FILL_EMPTY","version-"+tenant).id();
        var context=context(tasks.selectById(id));
        var operation=operationMapper.selectById(context.task().businessId);
        doAnswer(invocation->{jdbc.update("update script set current_version_id=null where id=?",script);
            return saveProp(operation,context);})
            .when(recognition).executeChild(any(),any(),any(),anyLong(),any(),anyLong(),any(),any());
        assertThatThrownBy(()->scoped.execute(operation,new ScopedAssetReextractionRequest("PROP","FILL_EMPTY"),context))
            .hasMessageContaining("版本已变化");
        assertThat(count("prop_asset")).isZero();
    }

    private AssetRecognitionAgentAdapter.Execution saveProp(ScriptAiOperationEntity operation,AiExecutionContext execution) throws Exception {
        long runModel=model;
        jdbc.update("""
            insert into ai_workflow_agent_run(agent_code,run_type,tenant_id,user_id,project_id,script_id,episode_id,task_id,status,model_id,
                temperature,max_tokens,max_steps,prompt_snapshot,started_at,created_at)
            values ('short-drama-asset-recognition','FORMAL',?,?,?,?,?,?,'RUNNING',?,0.2,16384,12,'test',now(),now())
            """,tenant,tenant,project,script,episode,operation.id,runModel);
        long run=jdbc.queryForObject("select max(id) from ai_workflow_agent_run where tenant_id=?",Long.class,tenant);
        var state=new com.antshorttv.workflowagent.tool.WorkflowToolRunState();
        state.put("assetScope","PROP");state.put("assetPromptPolicy","FILL_EMPTY");
        var context=new com.antshorttv.workflowagent.tool.ToolExecutionContext(tenant,tenant,project,episode,script,operation.id,null,run,
            execution.task().id,execution.claim().attemptId(),execution.task().executionVersion,
            java.util.Set.of("SCRIPT:VIEW","SCRIPT:EDIT"),null,state,null);
        tools.readCurrentEpisode(context);
        tools.saveEpisodeAssets(context,new com.fasterxml.jackson.databind.ObjectMapper().readTree("""
            {"schemaVersion":1,"characters":[],"characterLooks":[],"scenes":[],"props":[
             {"localKey":"p1","name":"徽章","aliases":[],"evidence":"徽章","prompt":"金色圆形徽章"}],"propVariants":[]}
            """));
        return new AssetRecognitionAgentAdapter.Execution(run,java.util.List.of());
    }

    private void seedScript() {
        jdbc.update("insert into project(id,tenant_id,name,code,owner_id,status,created_by,created_at,updated_at) values (?,?,'test',?,?,'ACTIVE',?,now(),now())",project,tenant,"ASSET_"+tenant,tenant,tenant);
        jdbc.update("insert into script(id,tenant_id,project_id,title,source_type,content,status,created_by,created_at,updated_at) values (?,?,?,'test','MANUAL_EDIT','徽章','DRAFT',?,now(),now())",script,tenant,project,tenant);
        jdbc.update("insert into script_version(tenant_id,project_id,script_id,version_no,source_type,content,status,created_by,created_at) values (?,?,?,1,'MANUAL_EDIT','徽章','DRAFT',?,now())",tenant,project,script,tenant);
        version=jdbc.queryForObject("select id from script_version where script_id=?",Long.class,script);
        jdbc.update("update script set current_version_id=? where id=?",version,script);
        jdbc.update("insert into script_episode(tenant_id,project_id,script_id,script_version_id,stable_key,episode_no,title,content,content_fingerprint,reconciliation_status,status,created_at,updated_at) values (?,?,?,?,'e1',1,'test','徽章','original','MATCHED','ACTIVE',now(),now())",tenant,project,script,version);
        episode=jdbc.queryForObject("select id from script_episode where script_id=?",Long.class,script);
    }

    private int count(String table) {return jdbc.queryForObject("select count(*) from "+table+" where tenant_id=?",Integer.class,tenant);}
    private AiExecutionTaskEntity create(String business) {
        return executions.create(new AiExecutionCreateCommand(tenant,tenant,project,"scoped_asset_reextraction","TEXT",
            business,script,null,"SUBMIT",UUID.randomUUID().toString(),"test",true,null));
    }
    private AiExecutionContext context(AiExecutionTaskEntity task) {
        var claim=claims.claim(task.id,UUID.randomUUID().toString(),LocalDateTime.now().plusSeconds(1),Duration.ofMinutes(5));
        assertThat(claim).isNotNull();
        return new AiExecutionContext(tasks.selectById(task.id),claim);
    }
    private void assertOwned(AiExecutionContext c,long targetScript) {
        coordination.requireOwned(tenant,project,targetScript,c.task().id,c.task().executionVersion,c.claim().attemptId());
    }
}

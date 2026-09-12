package com.antshorttv.script;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.antshorttv.execution.AiExecutionContext;
import com.antshorttv.execution.AiExecutionClaim;
import com.antshorttv.execution.AiExecutionTaskEntity;
import com.antshorttv.project.ProjectAccessContext;
import com.antshorttv.project.ProjectAccessResolver;
import com.antshorttv.rbac.ProjectPermissionGuard;
import com.antshorttv.security.TenantContext;
import com.antshorttv.security.TenantContextResolver;
import com.antshorttv.workflowagent.run.WorkflowAgentRunner;
import com.antshorttv.workflowagent.run.WorkflowAgentRunRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class ScopedAssetReextractionServiceTest {
    @Autowired private JdbcTemplate database;
    @Autowired private ScriptWorkflowService workflow;
    @Autowired private AssetVisualVariantService variants;
    @Autowired private org.springframework.transaction.PlatformTransactionManager transactionManager;
    @MockBean private TenantContextResolver tenants;
    @MockBean private ProjectAccessResolver projects;
    @MockBean private ProjectPermissionGuard permissions;
    private AssetRecognitionAgentAdapter recognition;
    private AssetExtractionCoordination coordination;
    private WorkflowAgentRunRepository runs;
    private ScopedAssetReextractionService lifecycle;

    @BeforeEach
    void seedLifecycle() {
        database.update("insert into tenant (id,code,name,type,status,created_at,updated_at) values (9501,'scope-test','Scope','TEAM','ACTIVE',now(),now())");
        database.update("insert into project (id,tenant_id,name,code,owner_id,status,created_by,created_at,updated_at) values (9502,9501,'Scope','scope-test',1,'ACTIVE',1,now(),now())");
        database.update("insert into script (id,tenant_id,project_id,title,source_type,content,status,created_by,created_at,updated_at) values (9503,9501,9502,'Script','MANUAL_EDIT','text','ACTIVE',1,now(),now())");
        database.update("insert into script_ai_operation (id,tenant_id,project_id,operation_type,script_id,redacted_input_json,idempotency_key,status,created_by,created_at,updated_at) values (9504,9501,9502,'SCOPED_ASSET_REEXTRACTION',9503,'{}','scope-test','RUNNING',1,now(),now())");
        for (long id : List.of(9511L, 9512L)) {
            database.update("insert into script_episode (id,tenant_id,project_id,script_id,stable_key,episode_no,title,content,content_fingerprint,reconciliation_status,status,created_at,updated_at) values (?,9501,9502,9503,?,?,'Episode','text','hash','NEW','ACTIVE',now(),now())", id, "e" + id, id - 9510);
            database.update("insert into ai_workflow_agent_run (id,agent_code,run_type,tenant_id,user_id,project_id,task_id,status,model_id,temperature,max_tokens,max_steps,prompt_snapshot,started_at,created_at) values (?,'short-drama-asset-recognition','FORMAL',9501,1,9502,9504,'SUCCEEDED',1,0.1,4096,20,'audit',now(),now())", id);
            database.update("insert into script_episode_asset_analysis (tenant_id,project_id,script_id,episode_id,schema_version,content_fingerprint,content_json,generated_by_run_id,created_by,updated_by,created_at,updated_at) values (9501,9502,9503,?,1,'hash','{}',?,1,1,now(),now())", id, id);
        }
        when(tenants.requireActiveMember(9501L)).thenReturn(new TenantContext(1L,9501L,1L,"OWNER"));
        when(projects.requireView(9501L,9502L)).thenReturn(mock(ProjectAccessContext.class));
        recognition = mock(AssetRecognitionAgentAdapter.class);
        coordination = mock(AssetExtractionCoordination.class);
        runs = mock(WorkflowAgentRunRepository.class);
        lifecycle = new ScopedAssetReextractionService(database, mock(WorkflowAgentRunner.class), recognition,
            transactionManager, coordination, runs, new ObjectMapper());
        when(recognition.executeChild(any(), any(), any(), anyLong(), any(), anyLong(), any(), any()))
            .thenAnswer(call -> new AssetRecognitionAgentAdapter.Execution(call.getArgument(3, Long.class), List.of()));
    }

    @ParameterizedTest
    @EnumSource(AssetRecognitionScope.class)
    void finalizesOnlyUnreferencedAiWithinScopeAndProtectsHumanEdits(AssetRecognitionScope scope) {
        Map<String,String> tables = Map.of("CHARACTER","character_asset","SCENE","scene_asset","PROP","prop_asset");
        long base = 9600;
        for (var entry : tables.entrySet()) {
            String type = entry.getKey();
            String table = entry.getValue();
            String category = type.equals("CHARACTER") ? "role_type" : type.equals("SCENE") ? "scene_type" : "prop_type";
            for (int i = 1; i <= 5; i++) {
                database.update("insert into " + table + " (id,tenant_id,project_id,script_id,name," + category + ",prompt,source,generated_by_run_id,status,created_by,created_at,updated_at) values (?,9501,9502,9503,?,'DEFAULT','original prompt',?,9511,'CONFIRMED',1,now(),now())", base+i,"asset"+i,i==2?"USER":"AI");
                database.update("insert into asset_visual_variant (id,tenant_id,project_id,asset_type,asset_id,name,prompt,source_type,generation_status,is_primary,generated_by_run_id,created_by,created_at,updated_at) values (?,9501,9502,?,?,'look','original variant prompt',?,'NOT_GENERATED',false,9511,1,now(),now())",base+i,type,base+i,i==2?"MANUAL":"AI");
            }
            workflow.updateElement(9501L,9502L,type,base+3,new UpdateScriptElementRequest("human name",null,null,null,null,List.of(),"human appearance",null,null,"human description",null,null,null,null,"human prompt","CONFIRMED"),null);
            variants.update(9501L,9502L,base+5,new AssetVisualVariantService.VariantCommand("human look","human appearance","human variant prompt",null,null,null,null,false));
            database.update("insert into asset_visual_variant_episode (tenant_id,project_id,script_id,episode_id,asset_type,asset_id,variant_id,is_preferred,binding_status,created_by,created_at,updated_at) values (9501,9502,9503,9511,?,?,?,false,'ACTIVE',1,now(),now())",type,base+4,base+4);
            base += 10;
        }
        var preflight = lifecycle.preflight(9501,9502,9503,scope);
        int selectedTypes = scope == AssetRecognitionScope.ALL ? 3 : 1;
        assertThat(preflight.existingAssets()).isEqualTo(5*selectedTypes);
        assertThat(preflight.existingVariants()).isEqualTo(5*selectedTypes);
        assertThat(preflight.existingPrompts()).isEqualTo(10*selectedTypes);
        lifecycle.execute(operation(),new ScopedAssetReextractionRequest(scope.name(),"REGENERATE_ALL"),context(11));
        base = 9600;
        for (var entry : tables.entrySet()) {
            String table = entry.getValue();
            assertThat(database.queryForObject("select count(*) from " + table + " where id=? and deleted_at is not null",Integer.class,base+1)).isEqualTo(scope.includes(entry.getKey())?1:0);
            assertThat(database.queryForObject("select count(*) from " + table + " where id between ? and ? and deleted_at is null",Integer.class,base+2,base+5)).isEqualTo(scope.includes(entry.getKey()) ? 3 : 4);
            assertThat(database.queryForMap("select source,name,prompt,generated_by_run_id from " + table + " where id=?",base+3)).containsEntry("source","USER").containsEntry("name","human name").containsEntry("prompt","human prompt").containsEntry("generated_by_run_id",9511L);
            assertThat(database.queryForMap("select source_type,prompt,generated_by_run_id,deleted_at from asset_visual_variant where id=?",base+5)).containsEntry("source_type","MANUAL").containsEntry("prompt","human variant prompt").containsEntry("generated_by_run_id",9511L).containsEntry("deleted_at",null);
            assertThat(database.queryForObject("select count(*) from asset_visual_variant where id=? and deleted_at is not null",Integer.class,base+1)).isEqualTo(scope.includes(entry.getKey())?1:0);
            base += 10;
        }
        assertThat(database.queryForObject("select status from scoped_asset_reextraction_snapshot where operation_id=9504",String.class)).isEqualTo("SUCCEEDED");
        // The regular analysis finalizer must apply the same human-edit protection.
        database.update("insert into script_analysis_task (id,tenant_id,project_id,script_id,script_version_id,workflow_code,status,idempotency_key,created_by,created_at,updated_at) values (9700,9501,9502,9503,1,'ANALYSIS','RUNNING','formal-scope',1,now(),now())");
        database.update("insert into script_analysis_stage (id,task_id,stage_code,stage_order,status,created_at,updated_at) values (9700,9700,'ASSET_RECOGNITION',1,'RUNNING',now(),now())");
        database.update("insert into script_analysis_fanout_snapshot (id,tenant_id,project_id,script_id,task_id,stage_id,stage_code,attempt_no,agent_code,agent_revision,model_id,episode_set_hash,status,total_units,created_at,updated_at) values (9700,9501,9502,9503,9700,9700,'ASSET_RECOGNITION',1,'short-drama-asset-recognition',1,1,'hash','RUNNING',2,now(),now())");
        for (long episode : List.of(9511L,9512L)) {
            database.update("insert into script_analysis_fanout_unit (snapshot_id,episode_id,episode_key,content_fingerprint,status,child_run_id,created_at,updated_at) values (9700,?,?,'hash','SUCCEEDED',?,now(),now())",episode,"e"+episode,episode);
        }
        base = 9600;
        for (var entry : tables.entrySet()) {
            database.update("update " + entry.getValue() + " set deleted_at=null where id=?",base+1);
            database.update("update asset_visual_variant set deleted_at=null where id=?",base+1);
            base += 10;
        }
        new AssetRecognitionFinalizer(database).finish(9700,scope);
        base = 9600;
        for (var entry : tables.entrySet()) {
            assertThat(database.queryForObject("select count(*) from " + entry.getValue() + " where id=? and deleted_at is not null",Integer.class,base+1)).isEqualTo(scope.includes(entry.getKey())?1:0);
            assertThat(database.queryForObject("select count(*) from " + entry.getValue() + " where id between ? and ? and deleted_at is null",Integer.class,base+2,base+5)).isEqualTo(scope.includes(entry.getKey()) ? 3 : 4);
            assertThat(database.queryForObject("select count(*) from asset_visual_variant where id=? and source_type='MANUAL' and deleted_at is null",Integer.class,base+5)).isOne();
            base += 10;
        }
    }

    @Test
    void retriesOnlyFailedEpisodeAndKeepsTheFirstModel() {
        when(recognition.executeChild(any(),any(),any(),eq(9512L),any(),anyLong(),any(),any()))
            .thenThrow(new IllegalStateException("provider failed"))
            .thenReturn(new AssetRecognitionAgentAdapter.Execution(9512L,List.of()));
        var request = new ScopedAssetReextractionRequest("ALL","FILL_EMPTY");
        assertThatThrownBy(() -> lifecycle.execute(operation(),request,context(11))).hasMessageContaining("provider failed");
        assertThat(database.queryForList("select status from scoped_asset_reextraction_unit order by episode_id",String.class)).containsExactly("SUCCEEDED","FAILED");
        lifecycle.execute(operation(),request,context(99));
        verify(recognition,times(1)).executeChild(any(),any(),any(),eq(9511L),any(),eq(11L),any(),any());
        verify(recognition,times(2)).executeChild(any(),any(),any(),eq(9512L),any(),eq(11L),any(),any());
        assertThat(database.queryForObject("select model_id from scoped_asset_reextraction_snapshot where operation_id=9504",Long.class)).isEqualTo(11L);
        assertThat(database.queryForMap("select status,completed_units,failed_units from scoped_asset_reextraction_snapshot where operation_id=9504")).containsEntry("status","SUCCEEDED").containsEntry("completed_units",2).containsEntry("failed_units",0);
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(longs = {9511L, 9512L})
    void rejectsChangedSuccessfulOrFailedEpisodeBeforeRetry(long changedEpisode) {
        when(recognition.executeChild(any(),any(),any(),eq(9512L),any(),anyLong(),any(),any()))
            .thenThrow(new IllegalStateException("provider failed"));
        var request = new ScopedAssetReextractionRequest("ALL","FILL_EMPTY");
        assertThatThrownBy(() -> lifecycle.execute(operation(),request,context(11)))
            .hasMessageContaining("provider failed");
        database.update("update script_episode set content='changed',content_fingerprint='changed' where id=?",changedEpisode);
        org.mockito.Mockito.clearInvocations(recognition);
        assertThatThrownBy(() -> lifecycle.execute(operation(),request,context(11)))
            .hasMessageContaining("变化");
        org.mockito.Mockito.verifyNoInteractions(recognition);
        assertThat(database.queryForObject("select status from scoped_asset_reextraction_snapshot where operation_id=9504",String.class))
            .isEqualTo("FAILED");
    }

    @Test
    void rejectsSourceChangeDuringRecognitionBeforeFinalization() {
        when(recognition.executeChild(any(),any(),any(),eq(9512L),any(),anyLong(),any(),any()))
            .thenAnswer(call -> {
                database.update("update script_episode set content='changed',content_fingerprint='changed' where id=9511");
                return new AssetRecognitionAgentAdapter.Execution(9512L,List.of());
            });
        assertThatThrownBy(() -> lifecycle.execute(operation(),new ScopedAssetReextractionRequest("ALL","FILL_EMPTY"),context(11)))
            .hasMessageContaining("变化");
        assertThat(database.queryForObject("select status from scoped_asset_reextraction_snapshot where operation_id=9504",String.class))
            .isEqualTo("FAILED");
    }

    @Test
    void rejectsChangedSubmittedScriptVersionBeforeCreatingSnapshot() {
        var submitted = operation();
        submitted.scriptVersionId=42L;
        assertThatThrownBy(() -> lifecycle.execute(submitted,new ScopedAssetReextractionRequest("ALL","FILL_EMPTY"),context(11)))
            .hasMessageContaining("变化");
        assertThat(database.queryForObject("select count(*) from scoped_asset_reextraction_snapshot",Integer.class)).isZero();
        org.mockito.Mockito.verifyNoInteractions(recognition);
    }

    private ScriptAiOperationEntity operation() {
        var operation = new ScriptAiOperationEntity();
        operation.id=9504L; operation.tenantId=9501L; operation.projectId=9502L; operation.scriptId=9503L; operation.createdBy=1L;
        return operation;
    }

    private AiExecutionContext context(long model) {
        var execution = new AiExecutionTaskEntity();
        execution.id = 9505L;
        execution.executionVersion = 1;
        execution.resolvedModelId=model;
        return new AiExecutionContext(execution, new AiExecutionClaim(9505L, 9506L, "scope-test", 1, "RUNNING"));
    }

    @Test
    void preflightCountsOnlyTheRequestedAssetScope() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(any(String.class), eq(Integer.class), any(Object[].class)))
            .thenReturn(2, 3, 5, 7);
        ScopedAssetReextractionService service = new ScopedAssetReextractionService(
            jdbc, mock(WorkflowAgentRunner.class), mock(AssetRecognitionAgentAdapter.class), transactionManager,
            mock(AssetExtractionCoordination.class), mock(WorkflowAgentRunRepository.class), new ObjectMapper());

        var result = service.preflight(1L, 2L, 3L, AssetRecognitionScope.SCENE);

        assertThat(result.targetType()).isEqualTo("SCENE");
        assertThat(result.existingAssets()).isEqualTo(2);
        assertThat(result.existingVariants()).isEqualTo(3);
        assertThat(result.existingPrompts()).isEqualTo(12);
        assertThat(result.requiresConfirmation()).isTrue();
    }
}
